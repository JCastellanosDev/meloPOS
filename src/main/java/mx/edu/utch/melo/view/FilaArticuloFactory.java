package mx.edu.utch.melo.view;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import mx.edu.utch.melo.model.ItemOrden;
import mx.edu.utch.melo.util.Dinero;

import java.util.function.IntConsumer;

/**
 * Construye la fila visual de un {@link ItemOrden} para el panel de orden.
 * Responsabilidad única: traducir un artículo a nodos JavaFX. No conoce la
 * lista de artículos ni recalcula totales -- eso lo decide quien la use a
 * través de los callbacks.
 */
public final class FilaArticuloFactory {

    private FilaArticuloFactory() {
    }

    public static Node crear(ItemOrden item, IntConsumer onCambiarCantidad, Runnable onEliminar) {
        Label lblNombre = new Label(item.getNombre());
        lblNombre.getStyleClass().add("text-body-strong");
        HBox.setHgrow(lblNombre, Priority.ALWAYS);

        Label lblPrecio = new Label(Dinero.formatear(item.getSubtotal()));
        lblPrecio.getStyleClass().add("text-body");

        HBox filaSuperior = new HBox(lblNombre, lblPrecio);
        filaSuperior.setAlignment(Pos.CENTER_LEFT);

        Button btnMenos = new Button("−");
        btnMenos.getStyleClass().add("btn-qty");
        btnMenos.setOnAction(e -> onCambiarCantidad.accept(-1));

        Label lblCantidad = new Label(String.valueOf(item.getCantidad()));
        lblCantidad.getStyleClass().add("text-body");

        Button btnMas = new Button("+");
        btnMas.getStyleClass().add("btn-qty");
        btnMas.setOnAction(e -> onCambiarCantidad.accept(1));

        Region espaciador = new Region();
        HBox.setHgrow(espaciador, Priority.ALWAYS);

        Button btnEliminar = new Button("🗑");
        btnEliminar.getStyleClass().add("btn-icon-danger");
        btnEliminar.setOnAction(e -> onEliminar.run());

        HBox filaCantidad = new HBox(8, btnMenos, lblCantidad, btnMas, espaciador, btnEliminar);
        filaCantidad.setAlignment(Pos.CENTER_LEFT);

        VBox fila = new VBox(4, filaSuperior, filaCantidad);

        if (!item.getModificadores().isEmpty()) {
            HBox chips = new HBox(6);
            for (String modificador : item.getModificadores()) {
                Button chip = new Button(modificador);
                chip.getStyleClass().add("chip-modifier");
                chips.getChildren().add(chip);
            }
            fila.getChildren().add(chips);
        }

        return fila;
    }
}
