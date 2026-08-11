package mx.edu.utch.melo.controller;

import java.net.URL;
import java.util.ResourceBundle;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import mx.edu.utch.melo.nav.Navigator;
import mx.edu.utch.melo.nav.Pantalla;

public class DashboardController {

    @FXML
    private ResourceBundle resources;

    @FXML
    private URL location;

    @FXML
    private Button btnComedor;

    @FXML
    private Button btnDomicilio;

    @FXML
    private Button btnReservaciones;

    @FXML
    private Label txtPedidosActivos;

    @FXML
    private Label txtSaludo;

    @FXML
    private Label txtUsuario;

    @FXML
    private Label txtVentaActual;

    private final Navigator navigator;

    public DashboardController(Navigator navigator) {
        this.navigator = navigator;
    }

    @FXML
    void initialize() {
        btnComedor.setOnAction(e -> navigator.navigateTo(Pantalla.MENU));
        btnDomicilio.setOnAction(e -> navigator.navigateTo(Pantalla.DOMICILIO));

        btnReservaciones.setDisable(true);
        Tooltip.install(btnReservaciones, new Tooltip("Próximamente"));
    }

}
