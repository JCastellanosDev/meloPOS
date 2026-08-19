package mx.edu.utch.melo.service;

import mx.edu.utch.melo.dao.DescuentoDAO;
import mx.edu.utch.melo.dao.DetalleOrdenDAO;
import mx.edu.utch.melo.dao.OrdenDAO;
import mx.edu.utch.melo.dao.PagoDAO;
import mx.edu.utch.melo.dao.ProductoDAO;
import mx.edu.utch.melo.dao.SucursalDAO;
import mx.edu.utch.melo.dao.TurnoDAO;
import mx.edu.utch.melo.dao.UsuarioDAO;
import mx.edu.utch.melo.db.Transaccionador;
import mx.edu.utch.melo.model.DetalleOrden;
import mx.edu.utch.melo.model.EstadoOrden;
import mx.edu.utch.melo.model.MetodoPago;
import mx.edu.utch.melo.model.Orden;
import mx.edu.utch.melo.model.Pago;
import mx.edu.utch.melo.model.Producto;
import mx.edu.utch.melo.model.Rol;
import mx.edu.utch.melo.model.Sucursal;
import mx.edu.utch.melo.model.TipoDescuento;
import mx.edu.utch.melo.model.TipoOrden;
import mx.edu.utch.melo.model.Turno;
import mx.edu.utch.melo.model.Usuario;
import mx.edu.utch.melo.security.Auditoria;
import mx.edu.utch.melo.security.ControlAcceso;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Recibo de cobro y registro de pagos (ver PaymentPortalController). Antes de esta extracción, el
 * Controller tocaba 7 DAO directamente (Orden, DetalleOrden, Producto, Pago, Usuario, Turno, Sucursal)
 * para armar el recibo y cobrar -- el caso de mayor acoplamiento a DAO de todo el proyecto (ver
 * auditoría de Fase 1).
 *
 * Registrar el pago y avanzar la orden es una sola operación lógica ("Registrar venta", ver
 * auditoría de Fase 4) -- {@link #registrarPago}/{@link #registrarPagoDividido} corren todos sus
 * pasos en una sola transacción: si falla a media inserción, no queda ni un pago registrado ni la
 * orden avanzada.
 */
public class PagoService {

    private final OrdenDAO ordenDAO;
    private final DetalleOrdenDAO detalleOrdenDAO;
    private final ProductoDAO productoDAO;
    private final PagoDAO pagoDAO;
    private final UsuarioDAO usuarioDAO;
    private final TurnoDAO turnoDAO;
    private final SucursalDAO sucursalDAO;
    private final DescuentoDAO descuentoDAO;
    private final Transaccionador transaccionador;

    public PagoService(OrdenDAO ordenDAO, DetalleOrdenDAO detalleOrdenDAO, ProductoDAO productoDAO,
                        PagoDAO pagoDAO, UsuarioDAO usuarioDAO, TurnoDAO turnoDAO, SucursalDAO sucursalDAO,
                        DescuentoDAO descuentoDAO, Transaccionador transaccionador) {
        this.ordenDAO = ordenDAO;
        this.detalleOrdenDAO = detalleOrdenDAO;
        this.productoDAO = productoDAO;
        this.pagoDAO = pagoDAO;
        this.usuarioDAO = usuarioDAO;
        this.turnoDAO = turnoDAO;
        this.sucursalDAO = sucursalDAO;
        this.descuentoDAO = descuentoDAO;
        this.transaccionador = transaccionador;
    }

    /**
     * Junta la orden, su cajero, la sucursal activa, las líneas de detalle y el porcentaje vigente
     * de cada categoría de descuento (ver DescuentoDAO -- ya no es una constante fija, PaymentPortal
     * necesita el valor real para mostrarlo antes de autorizar, no el que tenía la app al compilar).
     */
    public DatosRecibo obtenerRecibo(int ordenId, int sucursalId) {
        Orden orden = ordenDAO.obtenerPorId(ordenId).orElseThrow();
        Usuario cajero = usuarioDAO.obtenerPorId(orden.getUsuarioId()).orElse(null);
        Sucursal sucursal = sucursalDAO.obtenerPorId(sucursalId).orElse(null);

        List<LineaRecibo> lineas = new ArrayList<>();
        for (DetalleOrden detalle : detalleOrdenDAO.obtenerPorOrden(ordenId)) {
            Producto producto = productoDAO.obtenerPorId(detalle.getProductoId()).orElse(null);
            String nombre = producto == null ? "Producto #" + detalle.getProductoId() : producto.getNombre();
            lineas.add(new LineaRecibo(nombre, detalle.getCantidad(), detalle.getSubtotal(), detalle.getNota()));
        }
        return new DatosRecibo(orden, cajero, sucursal, lineas, descuentoDAO.obtenerTodos());
    }

    /**
     * Registra el pago y avanza la orden a EN_PREPARACION (para COMEDOR/PARA_LLEVAR se cobra antes de
     * preparar, ver CLAUDE.md). Si el cajero tiene un turno abierto, la venta se cuenta ahí para el
     * corte de caja; si no (olvidó abrir turno), el cobro no se bloquea -- queda con turno_id null.
     */
    public boolean registrarPago(int ordenId, MetodoPago metodo, BigDecimal monto, int usuarioId) {
        return registrarPagos(ordenId, List.of(new PagoACrear(metodo, monto)), usuarioId);
    }

    /**
     * Pago dividido entre efectivo y tarjeta (ver CLAUDE.md, "división de cuenta entre varios
     * métodos"): crea un {@link Pago} por cada monto mayor a cero, en la misma transacción que
     * {@link #registrarPago}. Un monto en cero se omite -- si el cajero solo llenó un campo, esto
     * termina siendo equivalente a un pago único.
     */
    public boolean registrarPagoDividido(int ordenId, BigDecimal montoEfectivo, BigDecimal montoTarjeta, int usuarioId) {
        List<PagoACrear> pagos = new ArrayList<>();
        if (montoEfectivo.signum() > 0) {
            pagos.add(new PagoACrear(MetodoPago.EFECTIVO, montoEfectivo));
        }
        if (montoTarjeta.signum() > 0) {
            pagos.add(new PagoACrear(MetodoPago.TARJETA, montoTarjeta));
        }
        return registrarPagos(ordenId, pagos, usuarioId);
    }

    /**
     * Un pago (o varios, si es dividido) es una sola operación lógica: crear cada {@link Pago} y
     * avanzar la orden. Valida que la suma coincida exactamente con el total de la orden dentro de
     * la propia transacción -- no confía en que el Controller ya lo haya validado antes de llamar
     * aquí (ver auditoría de Fase 7, "el Service no debe confiar en que la UI repita la guarda").
     *
     * Guardias adicionales (auditoría de integridad de pagos, esta sesión):
     * <ul>
     *   <li>Ningún monto capturado puede ser negativo -- no hay ningún escenario de negocio válido
     *       para "pagar" un monto negativo. Monto cero sí se permite a este nivel (lo filtra
     *       {@link #registrarPagoDividido} para no crear un Pago vacío, pero {@link #registrarPago}
     *       de un solo monto en cero debe seguir funcionando para CORTESIA, el único
     *       {@link TipoDescuento} de 100% -- un total legítimamente en cero).</li>
     *   <li>La orden debe estar en un estado cobrable para su canal (ver
     *       {@link #estaEnEstadoCobrable}). Sin esta guardia, un segundo cobro sobre una
     *       orden ya pagada pasaría la validación de "la suma coincide con el total" sin problema
     *       (el total no cambia entre el primer y el segundo intento) y crearía un segundo juego de
     *       filas Pago -- doble cobro registrado en el sistema aunque el cliente solo pagó una vez.
     *       CANCELADA se rechaza aparte, con su propio mensaje, porque es un caso distinto (no es
     *       que ya se haya cobrado, es que la orden ya no es cobrable en absoluto).</li>
     * </ul>
     */
    private boolean registrarPagos(int ordenId, List<PagoACrear> pagos, int usuarioId) {
        for (PagoACrear pagoACrear : pagos) {
            if (pagoACrear.monto().signum() < 0) {
                throw new MontoInvalidoException(pagoACrear.monto());
            }
        }
        BigDecimal sumaCapturada = pagos.stream().map(PagoACrear::monto).reduce(BigDecimal.ZERO, BigDecimal::add);
        // Lectura fuera de la transacción a propósito: solo decide a qué turno se le asigna la
        // venta, no es parte de lo que debe revertirse si falla el registro del pago.
        Integer turnoAbiertoId = turnoDAO.obtenerTurnoAbierto(usuarioId).map(Turno::getId).orElse(null);

        return transaccionador.ejecutarEnTransaccion(conexion -> {
            Orden orden = ordenDAO.obtenerPorId(ordenId, conexion).orElseThrow();
            if (orden.getEstado() == EstadoOrden.CANCELADA) {
                throw new OrdenCanceladaException(ordenId);
            }
            if (!estaEnEstadoCobrable(orden)) {
                throw new OrdenYaFueCobradaException(ordenId, orden.getEstado());
            }
            if (sumaCapturada.compareTo(orden.getTotal()) != 0) {
                throw new MontoPagadoNoCoincideException(orden.getTotal(), sumaCapturada);
            }
            for (PagoACrear pagoACrear : pagos) {
                Pago pago = new Pago();
                pago.setOrdenId(ordenId);
                pago.setMetodoPago(pagoACrear.metodo());
                pago.setMonto(pagoACrear.monto());
                pago.setFechaPago(LocalDateTime.now());
                pagoDAO.crear(pago, conexion);
            }
            orden.setEstado(estadoTrasPago(orden.getTipoOrden()));
            orden.setTurnoId(turnoAbiertoId);
            ordenDAO.actualizar(orden, conexion);
            return true;
        });
    }

    /**
     * COMEDOR/PARA_LLEVAR se cobran antes de preparar (ver CLAUDE.md), así que el pago es lo que
     * dispara la preparación -- EN_PREPARACION, igual que siempre. DOMICILIO/PARA_RECOGER hacen
     * el recorrido al revés: "Ordenar -> Preparar -> Recoger/Entregar -> Pagar", así que cuando se
     * cobran ya están preparados (y en la práctica, ya entregados/recogidos -- todavía no existe un
     * paso explícito de "confirmar entrega" en el sistema). Forzarlos de vuelta a EN_PREPARACION al
     * cobrar los resucitaría como pedido activo en Kitchen Display, que es justo lo que se quiere
     * evitar.
     */
    private EstadoOrden estadoTrasPago(TipoOrden tipoOrden) {
        return switch (tipoOrden) {
            case DOMICILIO, PARA_RECOGER -> EstadoOrden.ENTREGADA;
            case COMEDOR, PARA_LLEVAR -> EstadoOrden.EN_PREPARACION;
        };
    }

    /**
     * Si la orden todavía se puede cobrar, según su canal -- usado por {@link #registrarPagos}
     * para rechazar un cobro repetido o fuera de lugar (ver CLAUDE.md: "el momento en que se
     * cobra cambia según el canal"). COMEDOR/PARA_LLEVAR nacen en PENDIENTE
     * (VentaService.crearOrdenComedor) y ahí se cobran, antes de mandarse a preparar -- un solo
     * estado válido.
     *
     * DOMICILIO/PARA_RECOGER nacen en EN_PREPARACION (VentaService.crearOrdenDomicilio), pero
     * ADEMÁS pueden llegar a cobrarse en LISTA: cuando Cocina completa uno de estos pedidos
     * (ver KitchenDisplayController.marcarEntregada), la orden pasa a LISTA en vez de saltar
     * directo a ENTREGADA, justo para que siga viéndose y sea cobrable en DeliveryController
     * hasta que de verdad se pague -- así que EN_PREPARACION *o* LISTA son ambos estados
     * cobrables para estos dos canales (bug real detectado en producción: una versión anterior
     * de esta guardia solo aceptaba EN_PREPARACION y rechazaba cualquier pedido a domicilio que
     * ya hubiera pasado por cocina, que es el caso normal). Cualquier otro estado (ENTREGADA,
     * PAGADA) significa que el pago ya se registró antes; CANCELADA se distingue y rechaza
     * aparte, con su propio mensaje, en {@link #registrarPagos}.
     */
    private boolean estaEnEstadoCobrable(Orden orden) {
        return switch (orden.getTipoOrden()) {
            case COMEDOR, PARA_LLEVAR -> orden.getEstado() == EstadoOrden.PENDIENTE;
            case DOMICILIO, PARA_RECOGER ->
                    orden.getEstado() == EstadoOrden.EN_PREPARACION || orden.getEstado() == EstadoOrden.LISTA;
        };
    }

    /**
     * Aplica (o reemplaza, si ya tenía uno) un descuento por categoría, con el porcentaje vigente
     * de esa categoría (ver DescuentoDAO -- configurable desde Ajustes, ya no una constante fija en
     * TipoDescuento) -- requiere PIN de Administrador, verificado antes de llamar aquí (ver
     * UsuarioService.autenticarAdministrador); {@code rolAutorizante} es el rol de quien autorizó,
     * no del cajero que está cobrando. Vuelve a exigir ADMINISTRADOR aquí también (ver ControlAcceso,
     * "no basta con que la UI ya lo haya pedido") por si algún día se llama desde otro lugar.
     *
     * El porcentaje se lee aquí, en el momento del cobro -- no se recibe como parámetro del
     * Controller -- para que un cambio en Ajustes entre EMPLEADO/CORTESIA... siempre aplique el
     * valor real vigente, nunca uno que el cliente ya tenía cacheado desde antes.
     *
     * El descuento se resta directo del total ya calculado (post-IVA) -- no se recalculan
     * subtotal/impuestos, que siguen reflejando lo que realmente se vendió; monto_descuento es el
     * ajuste aplicado al cobro, no una corrección de la venta.
     */
    public Orden aplicarDescuento(Rol rolAutorizante, int ordenId, TipoDescuento tipo) {
        ControlAcceso.exigirRol(rolAutorizante, "aplicar un descuento", Rol.ADMINISTRADOR);
        Orden orden = ordenDAO.obtenerPorId(ordenId).orElseThrow();

        BigDecimal totalSinDescuento = orden.getTotal().add(orden.getMontoDescuento());
        BigDecimal porcentaje = descuentoDAO.obtenerPorcentaje(tipo);
        BigDecimal montoDescuento = totalSinDescuento.multiply(porcentaje).setScale(2, RoundingMode.HALF_UP);

        orden.setTipoDescuento(tipo);
        orden.setMontoDescuento(montoDescuento);
        orden.setTotal(totalSinDescuento.subtract(montoDescuento).max(BigDecimal.ZERO));
        ordenDAO.actualizar(orden);

        Auditoria.registrar(rolAutorizante, "aplicación de descuento",
                "ordenId=" + ordenId + " tipo=" + tipo + " monto=" + montoDescuento);
        return orden;
    }

    /**
     * Quita el descuento de la orden, regresando el total a lo que era antes de aplicarlo --
     * versión que SÍ exige autorización (ver auditoría de esta sesión, "aplicar/quitar descuento").
     * Quitar un descuento cambia el total que se cobra tanto como aplicarlo (lo aumenta, en vez de
     * reducirlo) -- un cajero podría revertir por su cuenta un descuento que un Administrador ya
     * autorizó para un cliente, cobrándole de más sin que nadie vuelva a autorizar ese cambio. Usa
     * la misma regla que {@link #aplicarDescuento} (ADMINISTRADOR únicamente) por consistencia, no
     * porque se haya diseñado una política nueva para este caso.
     */
    public Orden quitarDescuento(Rol rolAutorizante, int ordenId) {
        ControlAcceso.exigirRol(rolAutorizante, "quitar un descuento", Rol.ADMINISTRADOR);
        Orden orden = quitarDescuentoSinAutorizar(ordenId);
        Auditoria.registrar(rolAutorizante, "remoción de descuento", "ordenId=" + ordenId);
        return orden;
    }

    /**
     * @deprecated Sin guardia de autorización -- existe solo porque
     * {@code PaymentPortalController.onQuitarDescuento} todavía llama esta sobrecarga directo
     * desde el botón "Quitar Descuento", sin pedir PIN (a diferencia de "Aplicar Descuento", que sí
     * pasa por {@code onAutorizarDescuento}); hoy cualquier rol con acceso a la pantalla de cobro
     * puede revertir un descuento ya autorizado por un Administrador sin volver a autorizar. Use
     * {@link #quitarDescuento(Rol, int)} en su lugar, que sí exige ADMINISTRADOR igual que
     * {@link #aplicarDescuento}. Migrar el Controller requiere agregarle un flujo de PIN igual al
     * de aplicar descuento -- cambio de UI fuera del alcance de esta auditoría (Service-layer
     * únicamente); queda documentado aquí para quien lo retome.
     */
    @Deprecated
    public Orden quitarDescuento(int ordenId) {
        return quitarDescuentoSinAutorizar(ordenId);
    }

    private Orden quitarDescuentoSinAutorizar(int ordenId) {
        Orden orden = ordenDAO.obtenerPorId(ordenId).orElseThrow();
        if (orden.getTipoDescuento() == null) {
            return orden;
        }
        orden.setTotal(orden.getTotal().add(orden.getMontoDescuento()));
        orden.setTipoDescuento(null);
        orden.setMontoDescuento(BigDecimal.ZERO);
        ordenDAO.actualizar(orden);
        return orden;
    }

    private record PagoACrear(MetodoPago metodo, BigDecimal monto) {
    }

    public record LineaRecibo(String nombre, int cantidad, BigDecimal monto, String nota) {
    }

    public record DatosRecibo(Orden orden, Usuario cajero, Sucursal sucursal, List<LineaRecibo> lineas,
                               Map<TipoDescuento, BigDecimal> porcentajesDescuento) {
    }

    public static class MontoPagadoNoCoincideException extends RuntimeException {
        public MontoPagadoNoCoincideException(BigDecimal esperado, BigDecimal capturado) {
            super("El monto capturado (" + capturado + ") no coincide con el total de la orden (" + esperado + ").");
        }
    }

    /** Ningún monto de un pago (único o una pierna de un pago dividido) puede ser negativo. */
    public static class MontoInvalidoException extends RuntimeException {
        public MontoInvalidoException(BigDecimal monto) {
            super("El monto pagado no puede ser negativo: " + monto + ".");
        }
    }

    /** Se intentó cobrar una orden que ya está CANCELADA -- no hay nada que cobrar. */
    public static class OrdenCanceladaException extends RuntimeException {
        public OrdenCanceladaException(int ordenId) {
            super("No se puede registrar un pago sobre la orden " + ordenId + ": está cancelada.");
        }
    }

    /**
     * Se intentó cobrar una orden que ya no está en un estado cobrable para su canal (ver
     * {@link #estaEnEstadoCobrable}) -- lo más probable es que ya se haya pagado antes.
     */
    public static class OrdenYaFueCobradaException extends RuntimeException {
        public OrdenYaFueCobradaException(int ordenId, EstadoOrden estadoActual) {
            super("No se puede registrar un pago sobre la orden " + ordenId
                    + ": ya fue cobrada (estado actual: " + estadoActual + ").");
        }
    }
}
