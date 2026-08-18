package mx.edu.utch.melo.service;

import mx.edu.utch.melo.dao.DetalleOrdenDAO;
import mx.edu.utch.melo.dao.OrdenDAO;
import mx.edu.utch.melo.db.Transaccionador;
import mx.edu.utch.melo.model.DetalleOrden;
import mx.edu.utch.melo.model.EstadoOrden;
import mx.edu.utch.melo.model.ItemOrden;
import mx.edu.utch.melo.model.Orden;
import mx.edu.utch.melo.model.TipoOrden;
import mx.edu.utch.melo.util.Totales;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Creación de órdenes: cálculo de totales, armado de la orden y su detalle. Antes vivía
 * duplicado casi idéntico en MenuPOSController.crearOrden y MenuPedidoController.crearOrdenDomicilio
 * (ver auditoría de Fase 1); el canal cambia el estado inicial y si hay envío (ver CLAUDE.md,
 * sección "Flujo de un pedido"): comedor se cobra antes de preparar, domicilio al entregar.
 *
 * Crear la orden y sus líneas de detalle es una sola operación lógica ("Registrar venta", ver
 * auditoría de Fase 4) -- {@link #persistir} corre ambos pasos en una sola transacción: si falla
 * a media inserción de detalles, se revierte todo (nunca queda una orden con solo parte de sus
 * artículos guardados).
 */
public class VentaService {

    private final OrdenDAO ordenDAO;
    private final DetalleOrdenDAO detalleOrdenDAO;
    private final Transaccionador transaccionador;

    public VentaService(OrdenDAO ordenDAO, DetalleOrdenDAO detalleOrdenDAO, Transaccionador transaccionador) {
        this.ordenDAO = ordenDAO;
        this.detalleOrdenDAO = detalleOrdenDAO;
        this.transaccionador = transaccionador;
    }

    public int siguienteNumeroOrden(int sucursalId) {
        return ordenDAO.siguienteNumeroOrden(sucursalId);
    }

    /** Orden para comer en el local: se cobra antes de preparar, por eso queda PENDIENTE (ver PaymentPortalController). */
    public Orden crearOrdenComedor(int usuarioId, List<ItemOrden> items, boolean cobrarIva) {
        validarItems(items);
        TotalesOrden totales = calcularTotales(items, cobrarIva, BigDecimal.ZERO);
        Orden orden = nuevaOrden(TipoOrden.COMEDOR, usuarioId, null, EstadoOrden.PENDIENTE,
                totales, null, BigDecimal.ZERO);
        return persistir(orden, items);
    }

    /**
     * Pedido a domicilio o para recoger: se cobra al entregar/recoger, por eso entra directo a
     * EN_PREPARACION (sin pasar por cobro). Sin ruta calculada (distanciaKm null -- el mesero no
     * presionó "Ubicar" en Pedidos, ver PedidosController.onUbicar) no hay forma de cobrar un envío
     * real, así que la orden se registra como PARA_RECOGER en vez de DOMICILIO: el cliente pasa por
     * ella, no se le entrega en una dirección. costoEnvio ya viene en 0 en ese caso
     * (Sucursal.calcularCostoEnvio es null-safe), esto solo decide el canal correcto.
     */
    public Orden crearOrdenDomicilio(int usuarioId, Integer clienteId, List<ItemOrden> items, boolean cobrarIva,
                                      BigDecimal distanciaKm, BigDecimal costoEnvio) {
        validarItems(items);
        TotalesOrden totales = calcularTotales(items, cobrarIva, costoEnvio);
        TipoOrden tipo = distanciaKm == null ? TipoOrden.PARA_RECOGER : TipoOrden.DOMICILIO;
        Orden orden = nuevaOrden(tipo, usuarioId, clienteId, EstadoOrden.EN_PREPARACION,
                totales, distanciaKm, costoEnvio);
        return persistir(orden, items);
    }

    /**
     * Los Controllers actuales ya evitan cobrar/mandar a cocina con el carrito vacío (ver
     * MenuPOSController.onCobrarCuenta, MenuPedidoController.onMandarCocina), pero esa guarda vive
     * en la UI -- el Service no debe confiar en que todo llamador futuro (otro Controller, una
     * prueba, una API) la repita. Sin esto, una orden vacía se podría persistir con
     * subtotal/total en cero y cero filas en detalle_orden (ver auditoría de Fase 7, "orden sin
     * detalle").
     */
    private void validarItems(List<ItemOrden> items) {
        if (items.isEmpty()) {
            throw new IllegalArgumentException("No se puede crear una orden sin artículos.");
        }
    }

    private TotalesOrden calcularTotales(List<ItemOrden> items, boolean cobrarIva, BigDecimal costoEnvio) {
        BigDecimal subtotal = Totales.subtotal(items);
        BigDecimal impuestos = Totales.iva(subtotal, cobrarIva);
        BigDecimal total = Totales.total(subtotal, cobrarIva).add(costoEnvio);
        return new TotalesOrden(subtotal, impuestos, total);
    }

    private Orden nuevaOrden(TipoOrden tipo, int usuarioId, Integer clienteId, EstadoOrden estado,
                              TotalesOrden totales, BigDecimal distanciaKm, BigDecimal costoEnvio) {
        Orden orden = new Orden();
        orden.setTipoOrden(tipo);
        orden.setMesaId(null);
        orden.setUsuarioId(usuarioId);
        orden.setClienteId(clienteId);
        orden.setTurnoId(null);
        orden.setEstado(estado);
        orden.setSubtotal(totales.subtotal());
        orden.setImpuestos(totales.impuestos());
        orden.setDistanciaKm(distanciaKm);
        orden.setCostoEnvio(costoEnvio);
        orden.setTotal(totales.total());
        orden.setFechaCreacion(LocalDateTime.now());
        return orden;
    }

    /**
     * Agrega artículos a una orden de DOMICILIO ya existente (ver DeliveryController: botón
     * "Agregar" en una tarjeta de pedido activo) -- el mesero/cajero reabre el mismo menú que
     * armó el pedido original y sigue agregando encima, sin crear una segunda orden. Inserta un
     * DetalleOrden por artículo nuevo y recalcula subtotal/impuestos/total sobre TODOS los
     * artículos de la orden (los que ya tenía + los nuevos, leídos de vuelta dentro de la misma
     * transacción) -- el envío (costoEnvio/distanciaKm) no se toca: ya se fijó al crear la orden
     * (ver CLAUDE.md, domicilios) y agregar comida no cambia la ruta ni la distancia.
     */
    public Orden agregarArticulos(int ordenId, List<ItemOrden> nuevosItems, boolean cobrarIva) {
        if (nuevosItems.isEmpty()) {
            throw new IllegalArgumentException("No se puede agregar una lista de artículos vacía.");
        }
        return transaccionador.ejecutarEnTransaccion(conexion -> {
            Orden orden = ordenDAO.obtenerPorId(ordenId, conexion).orElseThrow();
            for (ItemOrden item : nuevosItems) {
                DetalleOrden detalle = new DetalleOrden();
                detalle.setOrdenId(ordenId);
                detalle.setProductoId(item.getProductoId());
                detalle.setCantidad(item.getCantidad());
                detalle.setPrecioUnitario(item.getPrecioUnitario());
                detalle.setNota(item.getNota().isBlank() ? null : item.getNota());
                detalleOrdenDAO.crear(detalle, conexion);
            }

            List<ItemOrden> todosLosArticulos = detalleOrdenDAO.obtenerPorOrden(ordenId, conexion).stream()
                    .map(detalle -> new ItemOrden(detalle.getProductoId(), "", detalle.getPrecioUnitario(), detalle.getCantidad()))
                    .toList();
            BigDecimal subtotal = Totales.subtotal(todosLosArticulos);
            orden.setSubtotal(subtotal);
            orden.setImpuestos(Totales.iva(subtotal, cobrarIva));
            orden.setTotal(Totales.total(subtotal, cobrarIva).add(orden.getCostoEnvio()));
            ordenDAO.actualizar(orden, conexion);
            return orden;
        });
    }

    private Orden persistir(Orden orden, List<ItemOrden> items) {
        return transaccionador.ejecutarEnTransaccion(conexion -> {
            Orden creada = ordenDAO.crear(orden, conexion);
            for (ItemOrden item : items) {
                DetalleOrden detalle = new DetalleOrden();
                detalle.setOrdenId(creada.getId());
                detalle.setProductoId(item.getProductoId());
                detalle.setCantidad(item.getCantidad());
                detalle.setPrecioUnitario(item.getPrecioUnitario());
                detalle.setNota(item.getNota().isBlank() ? null : item.getNota());
                detalleOrdenDAO.crear(detalle, conexion);
            }
            return creada;
        });
    }

    private record TotalesOrden(BigDecimal subtotal, BigDecimal impuestos, BigDecimal total) {
    }
}
