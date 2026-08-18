package mx.edu.utch.melo.service;

import mx.edu.utch.melo.dao.DashboardDAO;
import mx.edu.utch.melo.dao.OrdenDAO;
import mx.edu.utch.melo.dao.ProductoDAO;
import mx.edu.utch.melo.model.Producto;
import mx.edu.utch.melo.model.TipoOrden;
import mx.edu.utch.melo.model.Turno;
import mx.edu.utch.melo.model.dashboard.CategoriaVendida;
import mx.edu.utch.melo.model.dashboard.ClienteRecurrente;
import mx.edu.utch.melo.model.dashboard.ComparacionVentas;
import mx.edu.utch.melo.model.dashboard.ProductoSinVentas;
import mx.edu.utch.melo.model.dashboard.ResumenDelivery;
import mx.edu.utch.melo.model.dashboard.ResumenVentas;
import mx.edu.utch.melo.model.dashboard.VentaPorEmpleado;
import mx.edu.utch.melo.model.dashboard.VentaPorHora;
import mx.edu.utch.melo.model.dashboard.VentaPorMetodoPago;
import mx.edu.utch.melo.model.reporte.PlatilloVendido;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * Orquesta el panel de Dashboard (ver DashboardController): decide qué rangos de fecha comparar
 * y calcula lo que no es agregación SQL pura (variación %, hora pico) -- {@link DashboardDAO} solo
 * agrega sobre un rango fijo que se le pasa, nunca decide rangos ni compara periodos.
 */
public class DashboardService {

    private static final int TOP_N_POR_DEFECTO = 5;

    private final DashboardDAO dashboardDAO;
    private final ProductoDAO productoDAO;
    private final OrdenDAO ordenDAO;
    private final TurnoService turnoService;

    public DashboardService(DashboardDAO dashboardDAO, ProductoDAO productoDAO, OrdenDAO ordenDAO, TurnoService turnoService) {
        this.dashboardDAO = dashboardDAO;
        this.productoDAO = productoDAO;
        this.ordenDAO = ordenDAO;
        this.turnoService = turnoService;
    }

    /** Todo lo que necesita el Dashboard, en una sola llamada (ver Async.ejecutar en el Controller). */
    public DatosDashboard construirDatosDashboard(int sucursalId, int usuarioId) {
        LocalDate hoy = LocalDate.now();
        LocalDate inicioMes = hoy.withDayOfMonth(1);

        ComparacionVentas ventasHoy = obtenerVentasDeHoy(sucursalId);
        ComparacionVentas ventasSemana = obtenerVentasDeLaSemana(sucursalId);
        ComparacionVentas ventasMes = obtenerVentasDelMes(sucursalId);

        // El resto de los paneles (productos, operación, inventario, empleados, delivery) usan el
        // mes en curso como ventana -- mismo rango que "Ventas del mes", para que todo el tablero
        // cuente la misma historia en vez de mezclar ventanas de tiempo distintas sin avisar.
        Integer horaPico = obtenerHoraPico(sucursalId, inicioMes, hoy);
        List<VentaPorMetodoPago> ventasPorMetodoPago = dashboardDAO.obtenerVentasPorMetodoPago(sucursalId, inicioMes, hoy);
        List<PlatilloVendido> masVendidos = dashboardDAO.obtenerProductosMasVendidos(sucursalId, inicioMes, hoy, TOP_N_POR_DEFECTO);
        List<PlatilloVendido> menosVendidos = dashboardDAO.obtenerProductosMenosVendidos(sucursalId, inicioMes, hoy, TOP_N_POR_DEFECTO);
        List<CategoriaVendida> categoriasPrincipales = dashboardDAO.obtenerCategoriasPrincipales(sucursalId, inicioMes, hoy, TOP_N_POR_DEFECTO);
        List<Producto> productosBajoStock = obtenerProductosBajoStock(sucursalId);
        List<ProductoSinVentas> productosSinVentas = dashboardDAO.obtenerProductosSinVentas(sucursalId, inicioMes, hoy);
        List<VentaPorEmpleado> ventasPorEmpleado = dashboardDAO.obtenerVentasPorEmpleado(sucursalId, inicioMes, hoy);
        Turno turnoActivo = turnoService.obtenerTurnoAbierto(usuarioId).orElse(null);
        ResumenDelivery delivery = dashboardDAO.obtenerResumenDelivery(sucursalId, inicioMes, hoy);
        int pedidosActivosDomicilio = obtenerPedidosActivosDomicilio();

        return new DatosDashboard(ventasHoy, ventasSemana, ventasMes, horaPico, ventasPorMetodoPago,
                masVendidos, menosVendidos, categoriasPrincipales, productosBajoStock, productosSinVentas,
                ventasPorEmpleado, turnoActivo, delivery, pedidosActivosDomicilio);
    }

    /** Pedidos DOMICILIO todavía activos (ni pagados ni cancelados) -- para el acceso rápido del Dashboard. */
    public int obtenerPedidosActivosDomicilio() {
        return ordenDAO.obtenerActivasPorTipo(TipoOrden.DOMICILIO).size();
    }

    /**
     * Clientes con más de una orden en el rango (ver auditoría de Fase 8, "clientes recurrentes").
     * Deliberadamente NO forma parte de {@link #construirDatosDashboard}/{@link DatosDashboard}
     * todavía -- exponerla en el panel principal requeriría agregar la sección al FXML/Controller
     * del Dashboard, un cambio de UI que esta fase no incluye ("no migres todo de golpe", ver
     * CLAUDE.md/melo-architecture). Queda disponible para que ReportesController la consuma
     * cuando se decida mostrarla.
     */
    public List<ClienteRecurrente> obtenerClientesRecurrentes(int sucursalId, LocalDate desde, LocalDate hasta) {
        return dashboardDAO.obtenerClientesRecurrentes(sucursalId, desde, hasta);
    }

    public ComparacionVentas obtenerVentasDeHoy(int sucursalId) {
        LocalDate hoy = LocalDate.now();
        return compararPeriodos(sucursalId, hoy, hoy, hoy.minusDays(1), hoy.minusDays(1));
    }

    public ComparacionVentas obtenerVentasDeLaSemana(int sucursalId) {
        LocalDate hoy = LocalDate.now();
        LocalDate inicioSemana = hoy.with(DayOfWeek.MONDAY);
        LocalDate inicioSemanaAnterior = inicioSemana.minusWeeks(1);
        LocalDate finSemanaAnterior = inicioSemana.minusDays(1);
        return compararPeriodos(sucursalId, inicioSemana, hoy, inicioSemanaAnterior, finSemanaAnterior);
    }

    public ComparacionVentas obtenerVentasDelMes(int sucursalId) {
        LocalDate hoy = LocalDate.now();
        LocalDate inicioMes = hoy.withDayOfMonth(1);
        LocalDate inicioMesAnterior = inicioMes.minusMonths(1);
        LocalDate finMesAnterior = inicioMes.minusDays(1);
        return compararPeriodos(sucursalId, inicioMes, hoy, inicioMesAnterior, finMesAnterior);
    }

    private ComparacionVentas compararPeriodos(int sucursalId, LocalDate desdeActual, LocalDate hastaActual,
                                                LocalDate desdeAnterior, LocalDate hastaAnterior) {
        ResumenVentas actual = dashboardDAO.obtenerResumenVentas(sucursalId, desdeActual, hastaActual);
        ResumenVentas anterior = dashboardDAO.obtenerResumenVentas(sucursalId, desdeAnterior, hastaAnterior);
        return new ComparacionVentas(actual, anterior, calcularVariacionPorcentual(actual.total(), anterior.total()));
    }

    /** 100% si antes había cero y ahora hay algo; 0% si ambos son cero (no "indefinido"/división entre cero). */
    private BigDecimal calcularVariacionPorcentual(BigDecimal totalActual, BigDecimal totalAnterior) {
        if (totalAnterior.compareTo(BigDecimal.ZERO) == 0) {
            return totalActual.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO : new BigDecimal("100");
        }
        return totalActual.subtract(totalAnterior)
                .divide(totalAnterior, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    /** Hora (0-23) con más ventas en el rango; null si no hubo ninguna venta. */
    public Integer obtenerHoraPico(int sucursalId, LocalDate desde, LocalDate hasta) {
        List<VentaPorHora> ventasPorHora = dashboardDAO.obtenerVentasPorHora(sucursalId, desde, hasta);
        return ventasPorHora.stream()
                .max(Comparator.comparing(VentaPorHora::total))
                .map(VentaPorHora::hora)
                .orElse(null);
    }

    /** Reutiliza Producto.tieneStockBajo() (ver auditoría de Fase 7): la regla vive en el modelo, no se reinventa en SQL. */
    public List<Producto> obtenerProductosBajoStock(int sucursalId) {
        return productoDAO.obtenerPorSucursal(sucursalId).stream()
                .filter(Producto::tieneStockBajo)
                .toList();
    }

    public record DatosDashboard(ComparacionVentas ventasHoy, ComparacionVentas ventasSemana, ComparacionVentas ventasMes,
                                  Integer horaPico, List<VentaPorMetodoPago> ventasPorMetodoPago,
                                  List<PlatilloVendido> masVendidos, List<PlatilloVendido> menosVendidos,
                                  List<CategoriaVendida> categoriasPrincipales, List<Producto> productosBajoStock,
                                  List<ProductoSinVentas> productosSinVentas, List<VentaPorEmpleado> ventasPorEmpleado,
                                  Turno turnoActivo, ResumenDelivery delivery, int pedidosActivosDomicilio) {
    }
}
