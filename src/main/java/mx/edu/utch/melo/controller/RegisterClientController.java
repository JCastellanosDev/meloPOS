package mx.edu.utch.melo.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import mx.edu.utch.melo.nav.Pantalla;
import mx.edu.utch.melo.validation.ClienteValidator;

import java.util.Optional;

public class RegisterClientController {

    @FXML
    private TextField txtNombre;

    @FXML
    private TextField txtPhone;

    @FXML
    private TextField txtAddress;

    @FXML
    private Label lblError;

    @FXML
    private SidebarController sidebarController;

    @FXML
    void initialize() {
        sidebarController.activar(Pantalla.CLIENTES);
    }

    @FXML
    void onGuardarCliente() {
        Optional<String> error = ClienteValidator.validar(txtNombre.getText(), txtPhone.getText(), txtAddress.getText());
        if (error.isPresent()) {
            mostrarError(error.get());
            return;
        }
        ocultarError();
        limpiarFormulario();
    }

    @FXML
    void onCancelar() {
        ocultarError();
        limpiarFormulario();
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

    private void limpiarFormulario() {
        txtNombre.clear();
        txtPhone.clear();
        txtAddress.clear();
    }
}
