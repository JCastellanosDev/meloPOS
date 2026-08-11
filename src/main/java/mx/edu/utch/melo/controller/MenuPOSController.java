package mx.edu.utch.melo.controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import mx.edu.utch.melo.model.ItemOrden;
import mx.edu.utch.melo.nav.Pantalla;
import mx.edu.utch.melo.util.Dinero;
import mx.edu.utch.melo.util.Totales;
import mx.edu.utch.melo.view.FilaArticuloFactory;

import java.util.List;

public class MenuPOSController {

    /** Contenedor dinámico: se llena en tiempo de ejecución con las tarjetas de platillos leídas de la base de datos. */
    @FXML
    private FlowPane contenedorMenu;

    @FXML
    private Button btnCategoriaPlatillos;

    @FXML
    private Button btnCategoriaBebidas;

    @FXML
    private Button btnCategoriaPostres;

    @FXML
    private VBox listaArticulos;

    @FXML
    private Label lblSubtotal;

    @FXML
    private Label lblIva;

    @FXML
    private Label lblTotal;

    @FXML
    private SidebarController sidebarController;

    /** Orden en memoria de la mesa activa. Se pierde al cerrar la app: no hay persistencia todavía. */
    private final ObservableList<ItemOrden> articulos = FXCollections.observableArrayList(
            new ItemOrden("Tacos al Pastor", 145.00, 1),
            new ItemOrden("Pozole Rojo", 210.00, 1, List.of("Sin cebolla", "Extra limón")),
            new ItemOrden("Agua de Jamaica", 90.00, 2)
    );

    @FXML
    void initialize() {
        sidebarController.activar(Pantalla.MENU);
        renderizarArticulos();
    }

    private void renderizarArticulos() {
        listaArticulos.getChildren().setAll(articulos.stream()
                .map(item -> FilaArticuloFactory.crear(
                        item,
                        delta -> cambiarCantidad(item, delta),
                        () -> eliminarArticulo(item)))
                .toList());
        actualizarTotales();
    }

    private void cambiarCantidad(ItemOrden item, int delta) {
        int nuevaCantidad = item.getCantidad() + delta;
        if (nuevaCantidad < 1) {
            return;
        }
        item.cantidadProperty().set(nuevaCantidad);
        renderizarArticulos();
    }

    private void eliminarArticulo(ItemOrden item) {
        articulos.remove(item);
        renderizarArticulos();
    }

    private void actualizarTotales() {
        double subtotal = Totales.subtotal(articulos);
        double iva = Totales.iva(subtotal);
        double total = Totales.total(subtotal);
        lblSubtotal.setText(Dinero.formatear(subtotal));
        lblIva.setText(Dinero.formatear(iva));
        lblTotal.setText(Dinero.formatear(total));
    }
}
