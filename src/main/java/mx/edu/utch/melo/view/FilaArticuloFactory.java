package mx.edu.utch.melo.view;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import mx.edu.utch.melo.model.ItemOrden;
import mx.edu.utch.melo.util.Dinero;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.function.IntConsumer;


public final class FilaArticuloFactory {

    private FilaArticuloFactory() {
    }

    /**
     * Línea de un ticket real: el nombre domina la fila (grande, en negritas) y
     * el precio a la derecha, como en un recibo de compra. La nota y los
     * controles de cantidad/eliminar quedan más discretos debajo, ya que son
     * de edición, no algo que un ticket impreso normalmente mostraría tan grande.
     */
    public static Node crear(ItemOrden item, IntConsumer onCambiarCantidad, Runnable onEliminar) {
        Label lblNombre = new Label(item.getCantidad() + "×  " + item.getNombre());
        lblNombre.getStyleClass().add("cart-item-name");
        lblNombre.setWrapText(true);
        HBox.setHgrow(lblNombre, Priority.ALWAYS);

        Label lblPrecio = new Label(Dinero.formatear(item.getSubtotal()));
        lblPrecio.getStyleClass().add("text-price");

        HBox filaSuperior = new HBox(8, lblNombre, lblPrecio);
        filaSuperior.setAlignment(Pos.TOP_LEFT);

        // Nota para cocina (ej. "sin cebolla"): se guarda directo en el item al escribir,
        // sin volver a dibujar la fila (eso perdería el foco del campo en cada tecla).
        TextField txtNota = new TextField(item.getNota());
        txtNota.setPromptText("Agregar nota para cocina (ej. sin cebolla)");
        txtNota.getStyleClass().add("cart-item-note-input");
        txtNota.setMaxWidth(Double.MAX_VALUE);
        txtNota.textProperty().addListener((obs, anterior, actual) -> item.setNota(actual));

        Button btnMenos = new Button("−");
        btnMenos.getStyleClass().add("btn-qty-sm");
        btnMenos.setOnAction(e -> onCambiarCantidad.accept(-1));
        Tooltip.install(btnMenos, new Tooltip("Quitar una unidad"));

        Label lblCantidad = new Label(String.valueOf(item.getCantidad()));
        lblCantidad.getStyleClass().add("text-muted");

        Button btnMas = new Button("+");
        btnMas.getStyleClass().add("btn-qty-sm");
        btnMas.setOnAction(e -> onCambiarCantidad.accept(1));
        Tooltip.install(btnMas, new Tooltip("Agregar una unidad"));

        Region espaciador = new Region();
        HBox.setHgrow(espaciador, Priority.ALWAYS);

        // Ícono solo, y además la única acción destructiva de esta fila (ver auditoría UI/UX):
        // el tooltip es más importante aquí que en +/- por el riesgo de un toque accidental.
        Button btnEliminar = new Button();
        btnEliminar.setGraphic(new FontIcon("mdi2t-trash-can-outline"));
        btnEliminar.getStyleClass().add("btn-icon-danger-sm");
        btnEliminar.setOnAction(e -> onEliminar.run());
        Tooltip.install(btnEliminar, new Tooltip("Quitar " + item.getNombre() + " del pedido"));

        HBox filaCantidad = new HBox(6, btnMenos, lblCantidad, btnMas, espaciador, btnEliminar);
        filaCantidad.setAlignment(Pos.CENTER_LEFT);

        VBox fila = new VBox(6, filaSuperior, txtNota, filaCantidad);
        fila.getStyleClass().add("cart-item-row");

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
