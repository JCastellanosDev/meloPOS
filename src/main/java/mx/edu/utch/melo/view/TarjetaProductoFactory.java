package mx.edu.utch.melo.view;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import mx.edu.utch.melo.model.Producto;
import mx.edu.utch.melo.util.Dinero;

import java.io.File;
import java.util.Optional;

/**
 * Construye la tarjeta visual de un {@link Producto} para la rejilla de
 * MenuPOS. Responsabilidad única: traducir un producto a nodos JavaFX.
 * No conoce el carrito ni la base de datos -- solo informa al llamador
 * que se apretó "agregar" a través del callback.
 */
public final class TarjetaProductoFactory {

    private TarjetaProductoFactory() {
    }

    public static Node crear(Producto producto, Runnable onAgregar) {
        Label titulo = new Label(producto.getNombre());
        titulo.getStyleClass().add("menu-card-title");

        Label precio = new Label(Dinero.formatear(producto.getPrecio()));
        precio.getStyleClass().add("text-price");

        Region espaciador = new Region();
        HBox.setHgrow(espaciador, Priority.ALWAYS);

        Button btnAgregar = new Button("+");
        btnAgregar.getStyleClass().add("btn-round-add");
        btnAgregar.setOnAction(e -> onAgregar.run());
        // Solo se checa "disponible": vender todavía no descuenta cantidad_disponible (ver CLAUDE.md,
        // sección Inventario), así que bloquear por existencia dejaría productos invendibles sin razón.
        if (!producto.isDisponible()) {
            btnAgregar.setDisable(true);
        }

        HBox pie = new HBox(precio, espaciador, btnAgregar);
        pie.getStyleClass().add("menu-card-footer");

        VBox cuerpo = new VBox(titulo, pie);
        cuerpo.getStyleClass().add("menu-card-body");

        VBox tarjeta = new VBox(cuerpo);
        tarjeta.getStyleClass().add("menu-card");

        // Sin foto, la tarjeta se queda en blanco -- no reservamos el espacio de la imagen para nada.
        imagenSiExiste(producto).ifPresent(imagen -> tarjeta.getChildren().add(0, imagen));

        return tarjeta;
    }

    /** Foto del producto como fondo de un panel, solo si existe y el archivo sigue en disco. */
    private static Optional<Pane> imagenSiExiste(Producto producto) {
        if (producto.getImagenPath() == null || producto.getImagenPath().isBlank()) {
            return Optional.empty();
        }
        File archivo = new File(producto.getImagenPath());
        if (!archivo.exists()) {
            return Optional.empty();
        }
        Pane imagen = new Pane();
        imagen.getStyleClass().add("menu-card-image");
        imagen.setStyle("-fx-background-image: url('" + archivo.toURI() + "'); "
                + "-fx-background-size: cover; -fx-background-position: center center;");
        return Optional.of(imagen);
    }
}
