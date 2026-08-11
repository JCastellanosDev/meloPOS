package mx.edu.utch.melo.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import mx.edu.utch.melo.nav.Navigator;
import mx.edu.utch.melo.nav.Pantalla;

import java.util.List;
import java.util.Map;

public class SidebarController {

    @FXML
    private Button btnOrdenes;

    @FXML
    private Button btnMesas;

    @FXML
    private Button btnMenu;

    @FXML
    private Button btnCocina;

    @FXML
    private Button btnDomicilio;

    @FXML
    private Button btnClientes;

    @FXML
    private Button btnPersonal;

    @FXML
    private Button btnInventario;

    @FXML
    private Button btnReportes;

    @FXML
    private Button btnAjustes;

    private final Navigator navigator;

    private List<Button> botonesNav;
    private Map<Pantalla, Button> mapaBotones;

    public SidebarController(Navigator navigator) {
        this.navigator = navigator;
    }

    @FXML
    void initialize() {
        botonesNav = List.of(btnOrdenes, btnMesas, btnMenu, btnCocina, btnDomicilio,
                btnClientes, btnPersonal, btnInventario, btnReportes, btnAjustes);

        mapaBotones = Map.of(
                Pantalla.ORDENES, btnOrdenes,
                Pantalla.MENU, btnMenu,
                Pantalla.COCINA, btnCocina,
                Pantalla.DOMICILIO, btnDomicilio,
                Pantalla.CLIENTES, btnClientes
        );

        btnOrdenes.setOnAction(e -> navigator.navigateTo(Pantalla.ORDENES));
        btnMenu.setOnAction(e -> navigator.navigateTo(Pantalla.MENU));
        btnCocina.setOnAction(e -> navigator.navigateTo(Pantalla.COCINA));
        btnDomicilio.setOnAction(e -> navigator.navigateTo(Pantalla.DOMICILIO));
        btnClientes.setOnAction(e -> navigator.navigateTo(Pantalla.CLIENTES));

        deshabilitarProximamente(btnMesas, btnPersonal, btnInventario, btnReportes, btnAjustes);
    }

    /** Ítems de navegación que aún no tienen pantalla propia: se muestran pero no se pueden activar. */
    private void deshabilitarProximamente(Button... botones) {
        for (Button boton : botones) {
            boton.setDisable(true);
            Tooltip.install(boton, new Tooltip("Próximamente"));
        }
    }

    /** Marca la sección activa a partir de la pantalla actual y desmarca el resto. */
    public void activar(Pantalla pantalla) {
        Button botonActivo = mapaBotones.get(pantalla);
        if (botonActivo == null) {
            return;
        }
        for (Button boton : botonesNav) {
            boton.getStyleClass().remove("nav-button-active");
            if (!boton.getStyleClass().contains("nav-button")) {
                boton.getStyleClass().add("nav-button");
            }
        }
        botonActivo.getStyleClass().remove("nav-button");
        botonActivo.getStyleClass().add("nav-button-active");
    }
}
