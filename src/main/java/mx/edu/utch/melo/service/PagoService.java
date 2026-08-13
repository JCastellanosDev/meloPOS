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
import mx.edu.utch.melo.model.Sucursal;
import mx.edu.utch.melo.model.Turno;
import mx.edu.utch.melo.model.Usuario;

import java.math.BigDecimal;
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
 * auditoría de Fase 4) -- {@link #registrarPago} corre ambos pasos en una sola transacción: si
 * falla al actualizar la orden, el pago tampoco queda registrado (nunca hay un pago cobrado sin
 * que la orden avance a cocina).
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
        // Lectura fuera de la transacción a propósito: solo decide a qué turno se le asigna la
        // venta, no es parte de lo que debe revertirse si falla el registro del pago.
        Integer turnoAbiertoId = turnoDAO.obtenerTurnoAbierto(usuarioId).map(Turno::getId).orElse(null);

        return transaccionador.ejecutarEnTransaccion(conexion -> {
            Pago pago = new Pago();
            pago.setOrdenId(ordenId);
            pago.setMetodoPago(metodo);
            pago.setMonto(monto);
            pago.setFechaPago(LocalDateTime.now());
            pagoDAO.crear(pago, conexion);

            Orden orden = ordenDAO.obtenerPorId(ordenId, conexion).orElseThrow();
            orden.setEstado(EstadoOrden.EN_PREPARACION);
            orden.setTurnoId(turnoAbiertoId);
            ordenDAO.actualizar(orden, conexion);
            return true;
        });
    }

    public record LineaRecibo(String nombre, int cantidad, BigDecimal monto, String nota) {
    }

    public record DatosRecibo(Orden orden, Usuario cajero, Sucursal sucursal, List<LineaRecibo> lineas) {
    }
}
