package mx.edu.utch.melo.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import mx.edu.utch.melo.nav.Navigator;
import mx.edu.utch.melo.nav.Pantalla;

public class TopbarController {

    @FXML
    private Button btnUsuario;

    private final Navigator navigator;

    public TopbarController(Navigator navigator) {
        this.navigator = navigator;
    }

    @FXML
    void initialize() {
        btnUsuario.setOnAction(e -> navigator.abrirVentana(Pantalla.CAMBIAR_USUARIO, "melo - Cambiar Usuario"));
    }

    public Button getBtnUsuario() {
        return btnUsuario;
    }
}
