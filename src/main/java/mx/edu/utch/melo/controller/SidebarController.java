package mx.edu.utch.melo.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;
import mx.edu.utch.melo.nav.Navigator;
import mx.edu.utch.melo.nav.Pantalla;

import java.util.List;
import java.util.Map;

public class SidebarController {

    /** Antes sin tooltip (ver auditoría UI/UX): es la única forma de volver al Dashboard, sin
     * ningún botón de navegación propio ni etiqueta visible que lo indique. */
    @FXML
    private VBox logoContenedor;

    @FXML
    private Button btnMesas;

    @FXML
    private Button btnMenu;

    @FXML
    private Button btnCocina;

    @FXML
    private Button btnDomicilio;

    @FXML
    private Button btnPedidos;

    @FXML
    private Button btnPersonal;

    @FXML
    private Button btnInventario;

    @FXML
    private Button btnReportes;

    @FXML
    private Button btnCaja;

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
        botonesNav = List.of(btnMesas, btnMenu, btnCocina, btnDomicilio,
                btnPedidos, btnPersonal, btnInventario, btnReportes, btnCaja, btnAjustes);

        mapaBotones = Map.of(
                Pantalla.MENU, btnMenu,
                Pantalla.DOMICILIO, btnDomicilio,
                Pantalla.PEDIDOS, btnPedidos,
                Pantalla.PERSONAL, btnPersonal,
                Pantalla.INVENTARIO, btnInventario,
                Pantalla.REPORTES, btnReportes,
                Pantalla.CAJA, btnCaja,
                Pantalla.AJUSTES, btnAjustes
        );

        btnMenu.setOnAction(e -> navigator.navigateTo(Pantalla.MENU));
        // Cocina no es una pestaña de la app: abre en su propia ventana (ver Navigator.abrirVentana),
        // pensada para correr como un monitor/terminal aparte -- por eso no entra en mapaBotones.
        // Órdenes tampoco es una pestaña: es la ventana emergente de cobro que abre MenuPOSController
        // al presionar "Cobrar Cuenta" (ver Pantalla.ORDENES ahí) -- por eso no tiene botón propio aquí.
        btnCocina.setOnAction(e -> navigator.abrirVentana(Pantalla.COCINA, "melo - Pantalla de Cocina"));
        btnDomicilio.setOnAction(e -> navigator.navigateTo(Pantalla.DOMICILIO));
        btnPedidos.setOnAction(e -> navigator.navigateTo(Pantalla.PEDIDOS));
        btnPersonal.setOnAction(e -> navigator.navigateTo(Pantalla.PERSONAL));
        btnInventario.setOnAction(e -> navigator.navigateTo(Pantalla.INVENTARIO));
        btnReportes.setOnAction(e -> navigator.navigateTo(Pantalla.REPORTES));
        btnCaja.setOnAction(e -> navigator.navigateTo(Pantalla.CAJA));
        btnAjustes.setOnAction(e -> navigator.navigateTo(Pantalla.AJUSTES));

        // Mesas sigue deshabilitado a propósito (ver CLAUDE.md: "no es prioridad ahora").
        deshabilitarProximamente(btnMesas);

        Tooltip.install(logoContenedor, new Tooltip("Ir al Panel Principal"));
    }

    @FXML
    private void onLogoClick() {
        navigator.navigateTo(Pantalla.DASHBOARD);
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
