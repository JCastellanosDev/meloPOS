package mx.edu.utch.melo.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import mx.edu.utch.melo.app.AppContext;
import mx.edu.utch.melo.async.Async;
import mx.edu.utch.melo.dao.OrdenDAO;
import mx.edu.utch.melo.dao.ReporteDAO;
import mx.edu.utch.melo.model.TipoOrden;
import mx.edu.utch.melo.model.reporte.VentaDiaria;
import mx.edu.utch.melo.nav.Navigator;
import mx.edu.utch.melo.nav.Pantalla;
import mx.edu.utch.melo.sesion.SesionActual;
import mx.edu.utch.melo.util.Dinero;
import org.kordamp.ikonli.javafx.FontIcon;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public class DashboardController {

    @FXML
    private Button btnComedor;

    @FXML
    private Button btnDomicilio;

    @FXML
    private Button btnReservaciones;

    @FXML
    private Button btnVerReportes;

    @FXML
    private Label txtPedidosActivos;

    @FXML
    private Label txtSaludo;

    @FXML
    private Label txtUsuario;

    @FXML
    private Label txtVentaActual;

    @FXML
    private Label txtFecha;

    @FXML
    private FontIcon iconoSaludo;

    private final Navigator navigator;
    private final OrdenDAO ordenDAO;
    private final ReporteDAO reporteDAO;
    private final SesionActual sesion;

    public DashboardController(AppContext contexto) {
        this.navigator = contexto.getNavigator();
        this.ordenDAO = contexto.getOrdenDAO();
        this.reporteDAO = contexto.getReporteDAO();
        this.sesion = contexto.getSesion();
    }

    @FXML
    void initialize() {
        btnComedor.setOnAction(e -> navigator.navigateTo(Pantalla.MENU));
        // "Domicilio" lleva a Pedidos (captura del cliente), no a la pantalla de solo lectura Domicilio/DeliveryView.
        btnDomicilio.setOnAction(e -> navigator.navigateTo(Pantalla.PEDIDOS));
        btnVerReportes.setOnAction(e -> navigator.navigateTo(Pantalla.REPORTES));

        btnReservaciones.setDisable(true);
        Tooltip.install(btnReservaciones, new Tooltip("Próximamente"));

        mostrarFechaEIcono();
        cargarResumen();
    }

    private void mostrarFechaEIcono() {
        DateTimeFormatter formatoFecha = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", new Locale("es", "MX"));
        String fecha = LocalDate.now().format(formatoFecha);
        txtFecha.setText(Character.toUpperCase(fecha.charAt(0)) + fecha.substring(1));

        boolean esDeNoche = LocalTime.now().getHour() >= 19 || LocalTime.now().getHour() < 6;
        iconoSaludo.setIconLiteral(esDeNoche ? "mdi2w-weather-night" : "mdi2w-weather-sunny");
    }

    private void cargarResumen() {
        Async.ejecutar(
                this::construirResumen,
                this::mostrarResumen,
                error -> { }
        );
    }

    /** Se ejecuta en un hilo aparte (ver Async): nombre de usuario, pedidos a domicilio activos y ventas de hoy. */
    private ResumenDashboard construirResumen() {
        String nombreUsuario = sesion.getUsuarioActivo().getNombre();
        int sucursalId = sesion.getSucursalActivaId();

        int pedidosActivos = ordenDAO.obtenerActivasPorTipo(TipoOrden.DOMICILIO).size();

        LocalDate hoy = LocalDate.now();
        List<VentaDiaria> ventasHoy = reporteDAO.obtenerVentasPorPeriodo(sucursalId, hoy, hoy);
        BigDecimal totalHoy = ventasHoy.isEmpty() ? BigDecimal.ZERO : ventasHoy.get(0).total();

        return new ResumenDashboard(nombreUsuario, pedidosActivos, totalHoy);
    }

    private void mostrarResumen(ResumenDashboard resumen) {
        txtSaludo.setText(saludoSegunHora() + ",");
        txtUsuario.setText(resumen.nombreUsuario());
        txtPedidosActivos.setText(resumen.pedidosActivos() + " activos");
        txtVentaActual.setText(Dinero.formatear(resumen.totalHoy()) + " hoy");
    }

    private String saludoSegunHora() {
        int hora = LocalTime.now().getHour();
        if (hora < 12) {
            return "Buenos días";
        }
        if (hora < 19) {
            return "Buenas tardes";
        }
        return "Buenas noches";
    }

    private record ResumenDashboard(String nombreUsuario, int pedidosActivos, BigDecimal totalHoy) {
    }
}
