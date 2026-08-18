package mx.edu.utch.melo.dao;

import mx.edu.utch.melo.model.dashboard.CategoriaVendida;
import mx.edu.utch.melo.model.dashboard.ClienteRecurrente;
import mx.edu.utch.melo.model.dashboard.ProductoSinVentas;
import mx.edu.utch.melo.model.dashboard.ResumenDelivery;
import mx.edu.utch.melo.model.dashboard.ResumenVentas;
import mx.edu.utch.melo.model.dashboard.VentaPorEmpleado;
import mx.edu.utch.melo.model.dashboard.VentaPorHora;
import mx.edu.utch.melo.model.dashboard.VentaPorMetodoPago;
import mx.edu.utch.melo.model.reporte.PlatilloVendido;

import java.time.LocalDate;
import java.util.List;

/**
 * Consultas de agregación para el panel de Dashboard (ver auditoría de Fase 9). No extiende
 * {@link CrudDAO}: como {@link ReporteDAO}, no hay una entidad "dashboard" que se cree/actualice --
 * son lecturas que combinan varias tablas. Cada método agrega sobre un rango [desde, hasta] fijo;
 * decidir QUÉ rango comparar contra cuál (p. ej. "esta semana" vs. "la semana pasada") es una
 * decisión de negocio que vive en DashboardService, no aquí.
 */
public interface DashboardDAO {

    ResumenVentas obtenerResumenVentas(int sucursalId, LocalDate desde, LocalDate hasta);

    /** Total vendido por hora del día (0-23), para detectar la hora pico. */
    List<VentaPorHora> obtenerVentasPorHora(int sucursalId, LocalDate desde, LocalDate hasta);

    List<PlatilloVendido> obtenerProductosMasVendidos(int sucursalId, LocalDate desde, LocalDate hasta, int limite);

    /** Los de menor cantidad vendida ENTRE los que sí se vendieron -- para "sin ninguna venta" ver {@link #obtenerProductosSinVentas}. */
    List<PlatilloVendido> obtenerProductosMenosVendidos(int sucursalId, LocalDate desde, LocalDate hasta, int limite);

    List<CategoriaVendida> obtenerCategoriasPrincipales(int sucursalId, LocalDate desde, LocalDate hasta, int limite);

    List<VentaPorMetodoPago> obtenerVentasPorMetodoPago(int sucursalId, LocalDate desde, LocalDate hasta);

    List<VentaPorEmpleado> obtenerVentasPorEmpleado(int sucursalId, LocalDate desde, LocalDate hasta);

    /** Productos activos del catálogo sin ninguna venta en el rango (ver ProductoSinVentas: aproximación a "sin movimiento"). */
    List<ProductoSinVentas> obtenerProductosSinVentas(int sucursalId, LocalDate desde, LocalDate hasta);

    ResumenDelivery obtenerResumenDelivery(int sucursalId, LocalDate desde, LocalDate hasta);

    /**
     * Clientes con más de una orden en el rango (ver auditoría de Fase 8). Solo cuenta órdenes con
     * cliente_id capturado -- hoy únicamente el canal DOMICILIO/PEDIDOS lo hace (ver CLAUDE.md), así
     * que COMEDOR nunca puede aparecer aquí con el esquema actual.
     */
    List<ClienteRecurrente> obtenerClientesRecurrentes(int sucursalId, LocalDate desde, LocalDate hasta);
}
