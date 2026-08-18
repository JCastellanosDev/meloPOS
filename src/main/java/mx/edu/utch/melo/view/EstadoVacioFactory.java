package mx.edu.utch.melo.view;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Construye el "estado vacío" estándar de una lista (ícono + mensaje dentro de una tarjeta clara,
 * ver estilo {@code .empty-state} en styles.css). Antes solo {@code DeliveryController} usaba este
 * tratamiento; {@code InventarioController}/{@code PersonalController} mostraban un {@link Label}
 * suelto con {@code .text-muted} para el mismo caso ("esta lista no tiene nada") -- ver auditoría
 * UI/UX, "unificar estados vacíos". Responsabilidad única: traducir un ícono + un mensaje a nodos
 * JavaFX, igual que el resto de {@code view/*Factory}.
 */
public final class EstadoVacioFactory {

    private EstadoVacioFactory() {
    }

    /**
     * @param iconLiteral literal Ikonli MaterialDesign2 (ej. "mdi2b-box") -- debe existir en el
     *                    pack ya usado por el proyecto.
     * @param mensaje     texto a mostrar debajo del ícono.
     * @param prefWidth   ancho preferido de la tarjeta, para que se vea bien centrada en el
     *                    contenedor que la recibe (cada pantalla tiene un ancho de lista distinto).
     */
    public static Node crear(String iconLiteral, String mensaje, double prefWidth) {
        FontIcon icono = new FontIcon(iconLiteral);
        icono.getStyleClass().add("empty-state-icon");

        Label etiqueta = new Label(mensaje);
        etiqueta.getStyleClass().add("empty-state-text");

        VBox estado = new VBox(8, icono, etiqueta);
        estado.getStyleClass().add("empty-state");
        estado.setAlignment(Pos.CENTER);
        estado.setPrefWidth(prefWidth);
        return estado;
    }
}
