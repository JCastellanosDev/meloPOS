package mx.edu.utch.melo.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import mx.edu.utch.melo.async.Async;
import mx.edu.utch.melo.model.Usuario;
import mx.edu.utch.melo.nav.Navigator;
import mx.edu.utch.melo.nav.Pantalla;
import mx.edu.utch.melo.security.PinBloqueadoException;
import mx.edu.utch.melo.service.UsuarioService;
import mx.edu.utch.melo.sesion.SesionActual;

import java.util.Optional;

/**
 * Ventana emergente para cambiar de usuario en la misma terminal (ver
 * Topbar.fxml, botón de usuario): captura un PIN y, si coincide con alguien
 * de la sucursal activa, reinicia la sesión con esa persona y regresa al
 * Dashboard. No es un logout real -- la sucursal activa no cambia, solo
 * quién está operando la terminal.
 */
public class CambiarUsuarioController {

    private static final int PIN_MAXIMO = 6;

    @FXML
    private VBox raiz;

    @FXML
    private Label lblPin;

    @FXML
    private Label lblError;

    @FXML
    private Button btnConfirmar;

    private final UsuarioService usuarioService;
    private final SesionActual sesion;
    private final Navigator navigator;

    private final StringBuilder pin = new StringBuilder();

    public CambiarUsuarioController(UsuarioService usuarioService, SesionActual sesion, Navigator navigator) {
        this.usuarioService = usuarioService;
        this.sesion = sesion;
        this.navigator = navigator;
    }

    @FXML
    void initialize() {
        actualizarPin();
        habilitarCapturaPorTeclado();
    }

    /** Además del teclado numérico en pantalla, se puede escribir el PIN con el teclado físico. */
    private void habilitarCapturaPorTeclado() {
        raiz.sceneProperty().addListener((obs, escenaAnterior, escenaNueva) -> {
            if (escenaNueva == null) {
                return;
            }
            escenaNueva.addEventFilter(KeyEvent.KEY_TYPED, this::onTeclaFisicaCaracter);
            escenaNueva.addEventFilter(KeyEvent.KEY_PRESSED, this::onTeclaFisicaControl);
        });
    }

    private void onTeclaFisicaCaracter(KeyEvent evento) {
        String caracter = evento.getCharacter();
        if (caracter.length() == 1 && Character.isDigit(caracter.charAt(0)) && pin.length() < PIN_MAXIMO) {
            pin.append(caracter);
            actualizarPin();
        }
    }

    private void onTeclaFisicaControl(KeyEvent evento) {
        if (evento.getCode() == KeyCode.BACK_SPACE) {
            onBorrar();
        } else if (evento.getCode() == KeyCode.ENTER && !btnConfirmar.isDisable()) {
            onConfirmar();
        } else if (evento.getCode() == KeyCode.ESCAPE) {
            onCancelar();
        }
    }

    @FXML
    void onTecla(ActionEvent evento) {
        if (pin.length() >= PIN_MAXIMO) {
            return;
        }
        pin.append(((Button) evento.getSource()).getText());
        actualizarPin();
    }

    @FXML
    void onBorrar() {
        if (!pin.isEmpty()) {
            pin.deleteCharAt(pin.length() - 1);
            actualizarPin();
        }
    }

    private void actualizarPin() {
        lblPin.setText(pin.isEmpty() ? "—" : "• ".repeat(pin.length()).stripTrailing());
        btnConfirmar.setDisable(pin.isEmpty());
    }

    @FXML
    void onConfirmar() {
        if (pin.isEmpty()) {
            return;
        }
        ocultarError();
        btnConfirmar.setDisable(true);
        String pinCapturado = pin.toString();

        Async.ejecutar(
                () -> usuarioService.autenticarPorPin(sesion.getSucursalActivaId(), pinCapturado),
                this::procesarResultado,
                error -> {
                    btnConfirmar.setDisable(false);
                    pin.setLength(0);
                    actualizarPin();
                    // Mensaje específico de bloqueo (ver PinBloqueadoException, melo-security): no
                    // revela más que hasta cuándo dura, pero sí distingue "bloqueado" de "PIN
                    // incorrecto" o "error de conexión" -- son situaciones distintas para el cajero.
                    mostrarError(error instanceof PinBloqueadoException bloqueado
                            ? bloqueado.getMessage()
                            : "No se pudo verificar el PIN. Intenta de nuevo.");
                }
        );
    }

    private void procesarResultado(Optional<Usuario> resultado) {
        if (resultado.isEmpty()) {
            btnConfirmar.setDisable(false);
            mostrarError("PIN incorrecto.");
            pin.setLength(0);
            actualizarPin();
            return;
        }
        sesion.iniciarSesion(resultado.get());
        // navigator envuelve la ventana principal (ver SceneManager), no esta ventana emergente:
        // así el cambio de usuario se refleja ahí aunque el clic haya pasado aquí.
        navigator.navigateTo(Pantalla.DASHBOARD);
        cerrarVentana();
    }

    @FXML
    void onCancelar() {
        cerrarVentana();
    }

    private void cerrarVentana() {
        ((Stage) btnConfirmar.getScene().getWindow()).close();
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
}
