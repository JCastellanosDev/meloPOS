package mx.edu.utch.melo.controller;

import javafx.fxml.FXML;
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
import mx.edu.utch.melo.model.reporte.PlatilloVendido;
import mx.edu.utch.melo.model.reporte.VentaDiaria;
import mx.edu.utch.melo.nav.Pantalla;
import mx.edu.utch.melo.sesion.SesionActual;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ReportesController {

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
    private SidebarController sidebarController;

    private final ReporteDAO reporteDAO;
    private final SesionActual sesion;

    public ReportesController(AppContext contexto) {
        this.reporteDAO = contexto.getReporteDAO();
        this.sesion = contexto.getSesion();
    }

    @FXML
    void initialize() {
        sidebarController.activar(Pantalla.REPORTES);
        fechaHasta.setValue(LocalDate.now());
        fechaDesde.setValue(LocalDate.now().minusDays(DIAS_PERIODO_INICIAL - 1));
        cargarReportes();
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
}
