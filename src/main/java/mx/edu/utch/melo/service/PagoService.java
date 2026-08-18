package mx.edu.utch.melo.service;

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
import mx.edu.utch.melo.model.Turno;
import mx.edu.utch.melo.model.Usuario;
import mx.edu.utch.melo.security.Auditoria;
import mx.edu.utch.melo.security.ControlAcceso;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
    private final Transaccionador transaccionador;

    public PagoService(OrdenDAO ordenDAO, DetalleOrdenDAO detalleOrdenDAO, ProductoDAO productoDAO,
                        PagoDAO pagoDAO, UsuarioDAO usuarioDAO, TurnoDAO turnoDAO, SucursalDAO sucursalDAO,
                        Transaccionador transaccionador) {
        this.ordenDAO = ordenDAO;
        this.detalleOrdenDAO = detalleOrdenDAO;
        this.productoDAO = productoDAO;
        this.pagoDAO = pagoDAO;
        this.usuarioDAO = usuarioDAO;
        this.turnoDAO = turnoDAO;
        this.sucursalDAO = sucursalDAO;
        this.transaccionador = transaccionador;
    }

    /** Junta la orden, su cajero, la sucursal activa y las líneas de detalle para armar el recibo. */
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
        return new DatosRecibo(orden, cajero, sucursal, lineas);
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
     */
    private boolean registrarPagos(int ordenId, List<PagoACrear> pagos, int usuarioId) {
        BigDecimal sumaCapturada = pagos.stream().map(PagoACrear::monto).reduce(BigDecimal.ZERO, BigDecimal::add);
        // Lectura fuera de la transacción a propósito: solo decide a qué turno se le asigna la
        // venta, no es parte de lo que debe revertirse si falla el registro del pago.
        Integer turnoAbiertoId = turnoDAO.obtenerTurnoAbierto(usuarioId).map(Turno::getId).orElse(null);

        return transaccionador.ejecutarEnTransaccion(conexion -> {
            Orden orden = ordenDAO.obtenerPorId(ordenId, conexion).orElseThrow();
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
            orden.setEstado(EstadoOrden.EN_PREPARACION);
            orden.setTurnoId(turnoAbiertoId);
            ordenDAO.actualizar(orden, conexion);
            return true;
        });
    }

    /**
     * Aplica (o reemplaza, si ya tenía uno) un descuento por categoría con porcentaje predefinido
     * (ver TipoDescuento) -- requiere PIN de Administrador, verificado antes de llamar aquí (ver
     * UsuarioService.autenticarAdministrador); {@code rolAutorizante} es el rol de quien autorizó,
     * no del cajero que está cobrando. Vuelve a exigir ADMINISTRADOR aquí también (ver ControlAcceso,
     * "no basta con que la UI ya lo haya pedido") por si algún día se llama desde otro lugar.
     *
     * El descuento se resta directo del total ya calculado (post-IVA) -- no se recalculan
     * subtotal/impuestos, que siguen reflejando lo que realmente se vendió; monto_descuento es el
     * ajuste aplicado al cobro, no una corrección de la venta.
     */
    public Orden aplicarDescuento(Rol rolAutorizante, int ordenId, TipoDescuento tipo) {
        ControlAcceso.exigirRol(rolAutorizante, "aplicar un descuento", Rol.ADMINISTRADOR);
        Orden orden = ordenDAO.obtenerPorId(ordenId).orElseThrow();

        BigDecimal totalSinDescuento = orden.getTotal().add(orden.getMontoDescuento());
        BigDecimal montoDescuento = totalSinDescuento.multiply(tipo.getPorcentaje()).setScale(2, RoundingMode.HALF_UP);

        orden.setTipoDescuento(tipo);
        orden.setMontoDescuento(montoDescuento);
        orden.setTotal(totalSinDescuento.subtract(montoDescuento).max(BigDecimal.ZERO));
        ordenDAO.actualizar(orden);

        Auditoria.registrar(rolAutorizante, "aplicación de descuento",
                "ordenId=" + ordenId + " tipo=" + tipo + " monto=" + montoDescuento);
        return orden;
    }

    /**
     * Quita el descuento de la orden, regresando el total a lo que era antes de aplicarlo. No
     * exige un rol específico -- quitar un descuento nunca reduce lo que se cobra, así que no
     * representa el mismo riesgo que aplicarlo (ver aplicarDescuento).
     */
    public Orden quitarDescuento(int ordenId) {
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

    public record DatosRecibo(Orden orden, Usuario cajero, Sucursal sucursal, List<LineaRecibo> lineas) {
    }

    public static class MontoPagadoNoCoincideException extends RuntimeException {
        public MontoPagadoNoCoincideException(BigDecimal esperado, BigDecimal capturado) {
            super("El monto capturado (" + capturado + ") no coincide con el total de la orden (" + esperado + ").");
        }
    }
}
