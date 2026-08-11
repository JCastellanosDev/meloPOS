package mx.edu.utch.melo.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;

public class TopbarController {

    @FXML
    private Button btnNotificaciones;

    @FXML
    private Button btnUsuario;

    public Button getBtnNotificaciones() {
        return btnNotificaciones;
    }

    public Button getBtnUsuario() {
        return btnUsuario;
    }
}
