package mx.edu.utch.melo.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import mx.edu.utch.melo.nav.Pantalla;

public class KitchenDisplayController {

    @FXML
    private Label lblClock;

    @FXML
    private Label lblActivas;

    @FXML
    private HBox contenedorTickets;

    @FXML
    private SidebarController sidebarController;

    @FXML
    void initialize() {
        sidebarController.activar(Pantalla.COCINA);
    }

    @FXML
    void onCompletarPedido(ActionEvent evento) {
        Node boton = (Node) evento.getSource();
        Node ticket = boton.getParent();
        contenedorTickets.getChildren().remove(ticket);
        lblActivas.setText(contenedorTickets.getChildren().size() + " Activas");
    }
}
