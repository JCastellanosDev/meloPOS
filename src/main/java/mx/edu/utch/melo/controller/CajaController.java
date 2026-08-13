package mx.edu.utch.melo.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import mx.edu.utch.melo.app.AppContext;
import mx.edu.utch.melo.async.Async;
import mx.edu.utch.melo.model.Turno;
import mx.edu.utch.melo.model.reporte.ResumenTurno;
import mx.edu.utch.melo.nav.Pantalla;
import mx.edu.utch.melo.service.TurnoService;
import mx.edu.utch.melo.service.TurnoService.ResultadoCierre;
import mx.edu.utch.melo.sesion.SesionActual;
import mx.edu.utch.melo.util.Dinero;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Corte de caja: abre/cierra el turno del cajero en sesión (ver CLAUDE.md,
 * sección "Turno y caja"). El monto esperado al cerrar solo suma el efectivo
 * -- tarjeta/transferencia no mueven el cajón físico, así que no cuentan
 * contra lo contado.
 */
public class CajaController {

    private static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger(CajaController.class.getName());

    @FXML
    private VBox panelApertura;
    @FXML
    private TextField txtMontoApertura;
    @FXML
    private Label lblErrorApertura;
    @FXML
    private Button btnAbrirTurno;

    @FXML
    private VBox panelTurnoAbierto;
    @FXML
    private Label lblFechaApertura;
    @FXML
    private Label lblMontoApertura;
    @FXML
    private Label lblVentasTotales;
    @FXML
    private Label lblTotalEfectivo;
    @FXML
    private Label lblTotalTarjeta;
    @FXML
    private Label lblTotalTransferencia;
    @FXML
    private Label lblNumeroOrdenes;
    @FXML
    private TextField txtMontoContado;
    @FXML
    private Label lblErrorCierre;
    @FXML
    private Button btnCerrarTurno;

    @FXML
    private VBox panelCierre;
    @FXML
    private Label lblCierreVentas;
    @FXML
    private Label lblCierreEsperado;
    @FXML
    private Label lblCierreContado;
    @FXML
    private Label lblCierreDiferencia;

    @FXML
    private SidebarController sidebarController;

    private final TurnoService turnoService;
    private final SesionActual sesion;

    private Turno turnoActivo;

    public CajaController(AppContext contexto) {
        this.turnoService = contexto.getTurnoService();
        this.sesion = contexto.getSesion();
    }

    @FXML
    void initialize() {
        sidebarController.activar(Pantalla.CAJA);
        cargarTurnoAbierto();
    }

    private void cargarTurnoAbierto() {
        Async.ejecutar(
                () -> turnoService.obtenerTurnoAbierto(sesion.getUsuarioActivo().getId()),
                this::mostrarSegunTurno,
                error -> mostrarPanel(panelApertura)
        );
    }

    private void mostrarSegunTurno(Optional<Turno> turno) {
        if (turno.isEmpty()) {
            mostrarPanel(panelApertura);
            return;
        }
        turnoActivo = turno.get();
        mostrarPanel(panelTurnoAbierto);
        lblFechaApertura.setText(turnoActivo.getFechaApertura().format(
                DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", new Locale("es", "MX"))));
        lblMontoApertura.setText(Dinero.formatear(turnoActivo.getMontoApertura()));
        cargarResumenTurno();
    }

    private void cargarResumenTurno() {
        Async.ejecutar(
                () -> turnoService.obtenerResumen(turnoActivo.getId()),
                this::mostrarResumen,
                error -> LOG.log(java.util.logging.Level.WARNING, "No se pudo cargar el resumen del turno", error)
        );
    }

    private void mostrarResumen(ResumenTurno resumen) {
        lblVentasTotales.setText(Dinero.formatear(resumen.ventasTotales()));
        lblTotalEfectivo.setText(Dinero.formatear(resumen.totalEfectivo()));
        lblTotalTarjeta.setText(Dinero.formatear(resumen.totalTarjeta()));
        lblTotalTransferencia.setText(Dinero.formatear(resumen.totalTransferencia()));
        lblNumeroOrdenes.setText(String.valueOf(resumen.numeroOrdenes()));
    }

    @FXML
    void onAbrirTurno() {
        Optional<BigDecimal> monto = parsearMonto(txtMontoApertura.getText());
        if (monto.isEmpty()) {
            mostrarError(lblErrorApertura, "Captura un monto inicial válido.");
            return;
        }
        ocultarError(lblErrorApertura);
        btnAbrirTurno.setDisable(true);

        Async.ejecutar(
                () -> turnoService.abrirTurno(sesion.getUsuarioActivo().getRol(), sesion.getSucursalActivaId(),
                        sesion.getUsuarioActivo().getId(), monto.get()),
                turnoCreado -> {
                    btnAbrirTurno.setDisable(false);
                    txtMontoApertura.clear();
                    mostrarSegunTurno(Optional.of(turnoCreado));
                },
                error -> {
                    btnAbrirTurno.setDisable(false);
                    mostrarError(lblErrorApertura, "No se pudo abrir el turno. Intenta de nuevo.");
                }
        );
    }

    @FXML
    void onCerrarTurno() {
        Optional<BigDecimal> monto = parsearMonto(txtMontoContado.getText());
        if (monto.isEmpty()) {
            mostrarError(lblErrorCierre, "Captura el monto contado en caja.");
            return;
        }
        ocultarError(lblErrorCierre);
        btnCerrarTurno.setDisable(true);

        int turnoId = turnoActivo.getId();
        BigDecimal montoContado = monto.get();

        Async.ejecutar(
                () -> turnoService.cerrarTurno(sesion.getUsuarioActivo().getRol(), turnoId, montoContado),
                this::mostrarCierre,
                error -> {
                    btnCerrarTurno.setDisable(false);
                    mostrarError(lblErrorCierre, "No se pudo cerrar el turno. Intenta de nuevo.");
                }
        );
    }

    private void mostrarCierre(ResultadoCierre resultado) {
        turnoActivo = null;
        mostrarPanel(panelCierre);
        lblCierreVentas.setText(Dinero.formatear(resultado.resumen().ventasTotales()));
        lblCierreEsperado.setText(Dinero.formatear(resultado.montoEsperado()));
        lblCierreContado.setText(Dinero.formatear(resultado.montoContado()));
        lblCierreDiferencia.setText(Dinero.formatear(resultado.diferencia()));
    }

    @FXML
    void onNuevoTurno() {
        txtMontoContado.clear();
        mostrarPanel(panelApertura);
    }

    private void mostrarPanel(VBox panel) {
        for (VBox candidato : List.of(panelApertura, panelTurnoAbierto, panelCierre)) {
            boolean activo = candidato == panel;
            candidato.setVisible(activo);
            candidato.setManaged(activo);
        }
    }

    private Optional<BigDecimal> parsearMonto(String texto) {
        if (texto == null || texto.isBlank()) {
            return Optional.empty();
        }
        try {
            BigDecimal monto = new BigDecimal(texto.trim());
            return monto.compareTo(BigDecimal.ZERO) >= 0 ? Optional.of(monto) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private void mostrarError(Label etiqueta, String mensaje) {
        etiqueta.setText("⚠ " + mensaje);
        etiqueta.setVisible(true);
        etiqueta.setManaged(true);
    }

    private void ocultarError(Label etiqueta) {
        etiqueta.setVisible(false);
        etiqueta.setManaged(false);
    }
}
