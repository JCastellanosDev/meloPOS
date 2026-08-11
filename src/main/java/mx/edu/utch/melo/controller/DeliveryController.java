package mx.edu.utch.melo.controller;

import javafx.fxml.FXML;
import mx.edu.utch.melo.nav.Pantalla;

public class DeliveryController {

    @FXML
    private SidebarController sidebarController;

    @FXML
    void initialize() {
        sidebarController.activar(Pantalla.DOMICILIO);
    }
}
