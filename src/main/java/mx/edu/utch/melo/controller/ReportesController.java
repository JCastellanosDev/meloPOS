package mx.edu.utch.melo.controller;

import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import mx.edu.utch.melo.app.AppContext;
import mx.edu.utch.melo.async.Async;
import mx.edu.utch.melo.dao.ReporteDAO;
import mx.edu.utch.melo.model.Producto;
import mx.edu.utch.melo.model.Turno;
import mx.edu.utch.melo.model.Usuario;
import mx.edu.utch.melo.model.dashboard.CategoriaVendida;
import mx.edu.utch.melo.model.dashboard.ComparacionVentas;
import mx.edu.utch.melo.model.dashboard.ProductoSinVentas;
import mx.edu.utch.melo.model.dashboard.ResumenDelivery;
import mx.edu.utch.melo.model.dashboard.VentaPorEmpleado;
import mx.edu.utch.melo.model.dashboard.VentaPorMetodoPago;
import mx.edu.utch.melo.model.reporte.PlatilloVendido;
import mx.edu.utch.melo.model.reporte.VentaDiaria;
import mx.edu.utch.melo.nav.Pantalla;
import mx.edu.utch.melo.service.DashboardService;
import mx.edu.utch.melo.sesion.SesionActual;
import mx.edu.utch.melo.util.Dinero;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reportes: rango de fechas elegido por el usuario (gráfica + top platillos, ya existía) más el
 * panel analítico de KPIs fijos (ver auditoría de Fase 9, reubicado aquí desde Panel Principal a
 * petición del dueño). El panel analítico se carga una sola vez al entrar -- sus periodos
 * (hoy/semana/mes/mes en curso) son fijos, no dependen del selector de fechas de arriba.
 */
public class ReportesController {

    private static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger(ReportesController.class.getName());
    private static final int DIAS_PERIODO_INICIAL = 7;
    private static final int LIMITE_PLATILLOS = 10;
    private static final DateTimeFormatter FORMATO_DIA = DateTimeFormatter.ofPattern("d MMM", new Locale("es", "MX"));

    @FXML
    private DatePicker fechaDesde;

    @FXML
    private DatePicker fechaHasta;

    @FXML
    private Button btnGenerar;

    @FXML
    private Label lblError;

    @FXML
    private BarChart<String, Number> graficoVentas;

    @FXML
    private VBox listaPlatillos;

    @FXML
    private Label lblVentasHoyTotal;
    @FXML
    private Label lblVentasHoyVariacion;
    @FXML
    private Label lblVentasSemanaTotal;
    @FXML
    private Label lblVentasSemanaVariacion;
    @FXML
    private Label lblVentasMesTotal;
    @FXML
    private Label lblVentasMesVariacion;

    @FXML
    private Label lblTicketPromedio;
    @FXML
    private Label lblNumeroOrdenes;
    @FXML
    private Label lblHoraPico;
    @FXML
    private VBox listaMetodosPago;

    @FXML
    private VBox listaMasVendidos;
    @FXML
    private VBox listaMenosVendidos;
    @FXML
    private VBox listaCategorias;

    @FXML
    private VBox listaBajoStock;
    @FXML
    private VBox listaSinVentas;

    @FXML
    private Label lblTurnoPropio;
    @FXML
    private VBox listaVentasEmpleado;

    @FXML
    private VBox panelDelivery;
    @FXML
    private Label lblDeliveryPedidos;
    @FXML
    private Label lblDeliveryDistancia;
    @FXML
    private Label lblDeliveryCosto;
    @FXML
    private Label lblDeliveryIngresos;

    @FXML
    private SidebarController sidebarController;

    private final ReporteDAO reporteDAO;
    private final DashboardService dashboardService;
    private final SesionActual sesion;

    public ReportesController(AppContext contexto) {
        this.reporteDAO = contexto.getReporteDAO();
        this.dashboardService = contexto.getDashboardService();
        this.sesion = contexto.getSesion();
    }

    @FXML
    void initialize() {
        sidebarController.activar(Pantalla.REPORTES);
        fechaHasta.setValue(LocalDate.now());
        fechaDesde.setValue(LocalDate.now().minusDays(DIAS_PERIODO_INICIAL - 1));
        cargarReportes();
        cargarPanelAnalitico();
    }

    @FXML
    private void onGenerar() {
        cargarReportes();
    }

    private void cargarReportes() {
        LocalDate desde = fechaDesde.getValue();
        LocalDate hasta = fechaHasta.getValue();
        if (desde == null || hasta == null || desde.isAfter(hasta)) {
            mostrarError("Selecciona un rango de fechas válido.");
            return;
        }
        ocultarError();
        btnGenerar.setDisable(true);

        Async.ejecutar(
                () -> construirDatosReportes(sesion.getSucursalActivaId(), desde, hasta),
                datos -> {
                    btnGenerar.setDisable(false);
                    renderizarVentas(datos.ventas());
                    renderizarPlatillos(datos.platillos());
                },
                error -> {
                    btnGenerar.setDisable(false);
                    mostrarError("No se pudieron generar los reportes. Intenta de nuevo.");
                }
        );
    }

    /** Se ejecuta en un hilo aparte (ver Async): las dos consultas de agregación juntas. */
    private DatosReportes construirDatosReportes(int sucursalId, LocalDate desde, LocalDate hasta) {
        List<VentaDiaria> ventas = reporteDAO.obtenerVentasPorPeriodo(sucursalId, desde, hasta);
        List<PlatilloVendido> platillos = reporteDAO.obtenerPlatillosMasVendidos(sucursalId, desde, hasta, LIMITE_PLATILLOS);
        return new DatosReportes(ventas, platillos);
    }

    private void renderizarVentas(List<VentaDiaria> ventas) {
        XYChart.Series<String, Number> serie = new XYChart.Series<>();
        for (VentaDiaria venta : ventas) {
            serie.getData().add(new XYChart.Data<>(venta.fecha().format(FORMATO_DIA), venta.total()));
        }
        graficoVentas.getData().setAll(serie);
    }

    private void renderizarPlatillos(List<PlatilloVendido> platillos) {
        if (platillos.isEmpty()) {
            Label vacio = new Label("No hay ventas registradas en este periodo.");
            vacio.getStyleClass().add("text-muted");
            listaPlatillos.getChildren().setAll(vacio);
            return;
        }
        List<HBox> filas = new ArrayList<>();
        for (int indice = 0; indice < platillos.size(); indice++) {
            filas.add(construirFilaPlatillo(indice + 1, platillos.get(indice)));
        }
        listaPlatillos.getChildren().setAll(filas);
    }

    private HBox construirFilaPlatillo(int posicion, PlatilloVendido platillo) {
        Label lblPosicion = new Label(posicion + ".");
        lblPosicion.getStyleClass().add("text-muted");

        Label lblNombre = new Label(platillo.nombreProducto());
        lblNombre.getStyleClass().add("text-body-strong");
        HBox.setHgrow(lblNombre, Priority.ALWAYS);

        Label lblCantidad = new Label(platillo.cantidadVendida() + " vendidos");
        lblCantidad.getStyleClass().add("text-muted");

        HBox fila = new HBox(10, lblPosicion, lblNombre, lblCantidad);
        fila.getStyleClass().add("order-card");
        return fila;
    }

    private void mostrarError(String mensaje) {
        lblError.setText("⚠ " + mensaje);
        lblError.setVisible(true);
        lblError.setManaged(true);
    }

    private void ocultarError() {
        lblError.setVisible(false);
        lblError.setManaged(false);
    }

    private record DatosReportes(List<VentaDiaria> ventas, List<PlatilloVendido> platillos) {
    }

    // ================================================================
    // Panel analítico (ver DashboardService) -- reubicado aquí desde
    // Panel Principal. Se carga una sola vez, no depende del selector
    // de fechas de arriba (sus periodos son fijos: hoy/semana/mes).
    // ================================================================

    private void cargarPanelAnalitico() {
        Async.ejecutar(
                this::construirDatosAnalitico,
                this::mostrarDatosAnalitico,
                error -> LOG.log(java.util.logging.Level.WARNING, "No se pudo cargar el panel analítico", error)
        );
    }

    /** Se ejecuta en un hilo aparte (ver Async): el usuario en sesión se lee aquí adentro, nunca antes de despachar. */
    private DashboardService.DatosDashboard construirDatosAnalitico() {
        Usuario usuarioActivo = sesion.getUsuarioActivo();
        return dashboardService.construirDatosDashboard(usuarioActivo.getSucursalId(), usuarioActivo.getId());
    }

    private void mostrarDatosAnalitico(DashboardService.DatosDashboard datos) {
        mostrarComparacion(datos.ventasHoy(), lblVentasHoyTotal, lblVentasHoyVariacion, "vs. ayer");
        mostrarComparacion(datos.ventasSemana(), lblVentasSemanaTotal, lblVentasSemanaVariacion, "vs. semana pasada");
        mostrarComparacion(datos.ventasMes(), lblVentasMesTotal, lblVentasMesVariacion, "vs. mes pasado");

        lblTicketPromedio.setText(Dinero.formatear(datos.ventasMes().actual().ticketPromedio()));
        lblNumeroOrdenes.setText(String.valueOf(datos.ventasMes().actual().numeroOrdenes()));
        lblHoraPico.setText(datos.horaPico() == null ? "Sin datos" : String.format("%02d:00", datos.horaPico()));

        mostrarMetodosPago(datos.ventasPorMetodoPago());
        mostrarRankingProductos(listaMasVendidos, datos.masVendidos());
        mostrarRankingProductos(listaMenosVendidos, datos.menosVendidos());
        mostrarCategorias(datos.categoriasPrincipales());
        mostrarBajoStock(datos.productosBajoStock());
        mostrarSinVentas(datos.productosSinVentas());
        mostrarVentasPorEmpleado(datos.ventasPorEmpleado());
        mostrarTurnoPropio(datos.turnoActivo());
        mostrarDelivery(datos.delivery());
    }

    private void mostrarComparacion(ComparacionVentas comparacion, Label lblTotal, Label lblVariacion, String contexto) {
        lblTotal.setText(Dinero.formatear(comparacion.actual().total()));
        BigDecimal variacion = comparacion.variacionPorcentual();
        String signo = variacion.compareTo(BigDecimal.ZERO) > 0 ? "▲ +" : variacion.compareTo(BigDecimal.ZERO) < 0 ? "▼ " : "■ ";
        lblVariacion.setText(signo + variacion.setScale(1, java.math.RoundingMode.HALF_UP) + "% " + contexto);
    }

    private void mostrarMetodosPago(List<VentaPorMetodoPago> ventas) {
        if (ventas.isEmpty()) {
            listaMetodosPago.getChildren().setAll(etiquetaVacia("Todavía no hay pagos registrados este mes."));
            return;
        }
        listaMetodosPago.getChildren().setAll(ventas.stream()
                .map(v -> construirFilaAnalitico(v.metodoPago().name(), Dinero.formatear(v.total())))
                .toList());
    }

    private void mostrarRankingProductos(VBox contenedor, List<PlatilloVendido> productos) {
        if (productos.isEmpty()) {
            contenedor.getChildren().setAll(etiquetaVacia("Sin ventas este mes."));
            return;
        }
        contenedor.getChildren().setAll(productos.stream()
                .map(p -> construirFilaAnalitico(p.nombreProducto(), p.cantidadVendida() + " uds."))
                .toList());
    }

    private void mostrarCategorias(List<CategoriaVendida> categorias) {
        if (categorias.isEmpty()) {
            listaCategorias.getChildren().setAll(etiquetaVacia("Sin ventas este mes."));
            return;
        }
        listaCategorias.getChildren().setAll(categorias.stream()
                .map(c -> construirFilaAnalitico(c.nombreCategoria(), c.unidadesVendidas() + " uds."))
                .toList());
    }

    private void mostrarBajoStock(List<Producto> productos) {
        if (productos.isEmpty()) {
            listaBajoStock.getChildren().setAll(etiquetaVacia("Ningún producto bajo en stock."));
            return;
        }
        listaBajoStock.getChildren().setAll(productos.stream()
                .map(p -> construirFilaAnalitico(p.getNombre(), p.getCantidadDisponible() + " / mín. " + p.getStockMinimo()))
                .toList());
    }

    private void mostrarSinVentas(List<ProductoSinVentas> productos) {
        if (productos.isEmpty()) {
            listaSinVentas.getChildren().setAll(etiquetaVacia("Todo el catálogo tuvo al menos una venta."));
            return;
        }
        listaSinVentas.getChildren().setAll(productos.stream()
                .map(p -> construirFilaAnalitico(p.nombre(), Dinero.formatear(p.precio())))
                .toList());
    }

    private void mostrarVentasPorEmpleado(List<VentaPorEmpleado> ventas) {
        if (ventas.isEmpty()) {
            listaVentasEmpleado.getChildren().setAll(etiquetaVacia("Sin órdenes registradas este mes."));
            return;
        }
        listaVentasEmpleado.getChildren().setAll(ventas.stream()
                .map(v -> construirFilaAnalitico(v.nombreEmpleado(), Dinero.formatear(v.totalVendido()) + " · " + v.numeroOrdenes() + " ord."))
                .toList());
    }

    private void mostrarTurnoPropio(Turno turnoActivo) {
        if (turnoActivo == null) {
            lblTurnoPropio.setText("No tienes un turno de caja abierto ahora mismo.");
            return;
        }
        lblTurnoPropio.setText("Turno abierto desde " + turnoActivo.getFechaApertura() + " con "
                + Dinero.formatear(turnoActivo.getMontoApertura()) + " de apertura.");
    }

    private void mostrarDelivery(ResumenDelivery delivery) {
        boolean hayDatos = delivery.numeroPedidos() > 0;
        panelDelivery.setVisible(hayDatos);
        panelDelivery.setManaged(hayDatos);
        if (!hayDatos) {
            return;
        }
        lblDeliveryPedidos.setText(String.valueOf(delivery.numeroPedidos()));
        lblDeliveryDistancia.setText(delivery.distanciaPromedioKm().setScale(1, java.math.RoundingMode.HALF_UP) + " km");
        lblDeliveryCosto.setText(Dinero.formatear(delivery.costoEnvioPromedio()));
        lblDeliveryIngresos.setText(Dinero.formatear(delivery.ingresosTotales()));
    }

    private Label etiquetaVacia(String mensaje) {
        Label etiqueta = new Label(mensaje);
        etiqueta.getStyleClass().add("text-muted-sm");
        return etiqueta;
    }

    private HBox construirFilaAnalitico(String etiqueta, String valor) {
        Label lblEtiqueta = new Label(etiqueta);
        lblEtiqueta.getStyleClass().add("text-body");
        lblEtiqueta.setWrapText(true);
        HBox.setHgrow(lblEtiqueta, Priority.ALWAYS);

        Label lblValor = new Label(valor);
        lblValor.getStyleClass().add("text-body-strong");

        HBox fila = new HBox(8, lblEtiqueta, lblValor);
        fila.setAlignment(Pos.CENTER_LEFT);
        return fila;
    }
}
