package mx.edu.utch.melo.view;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Construye la fila estándar "entidad con estado" reutilizada en listas de solo lectura (avatar u
 * ícono opcional + columna título/subtítulo + nota intermedia opcional + insignia de estado a la
 * derecha, dentro de {@code .order-card}). Antes {@code InventarioController.construirFilaProducto}
 * y {@code PersonalController.construirFilaUsuario} reconstruían este mismo esqueleto por
 * separado, con el mismo layout y las mismas clases CSS copiadas a mano -- ver auditoría UI/UX,
 * "componentes a convertir en reutilizables". Responsabilidad única: traducir los datos ya
 * resueltos por el controlador a nodos JavaFX; no conoce el modelo de dominio ni la base de datos.
 */
public final class FilaEntidadFactory {

    private FilaEntidadFactory() {
    }

    /**
     * @param inicialAvatar  si no es null/blanco, dibuja un avatar circular con esa inicial a la
     *                       izquierda (ver Personal); si es null, la fila no lleva avatar (ver
     *                       Inventario, que no tiene una entidad "persona").
     * @param titulo         nombre principal, en negritas.
     * @param subtitulo      texto secundario debajo del título (rol, disponibilidad, etc.).
     * @param notaIntermedia texto opcional entre la columna de nombre y la insignia (ver
     *                       existencias en Inventario); null si esta lista no lo necesita.
     * @param etiquetaEstado texto de la insignia de la derecha.
     * @param claseBadge     clases CSS completas de la insignia (ver badge-success/badge-urgent/badge-muted).
     */
    public static Node crear(String inicialAvatar, String titulo, String subtitulo, String notaIntermedia,
                              String etiquetaEstado, String claseBadge) {
        HBox fila = new HBox(14);
        fila.setAlignment(Pos.CENTER_LEFT);
        fila.getStyleClass().add("order-card");

        if (inicialAvatar != null && !inicialAvatar.isBlank()) {
            StackPane avatar = new StackPane();
            avatar.getStyleClass().add("avatar");
            Label lblInicial = new Label(inicialAvatar);
            lblInicial.getStyleClass().add("avatar-label");
            avatar.getChildren().add(lblInicial);
            fila.getChildren().add(avatar);
        }

        Label lblTitulo = new Label(titulo);
        lblTitulo.getStyleClass().add("text-body-strong");

        Label lblSubtitulo = new Label(subtitulo);
        lblSubtitulo.getStyleClass().add("text-muted");

        VBox columnaTitulo = new VBox(4, lblTitulo, lblSubtitulo);
        HBox.setHgrow(columnaTitulo, Priority.ALWAYS);
        fila.getChildren().add(columnaTitulo);

        if (notaIntermedia != null && !notaIntermedia.isBlank()) {
            Label lblNota = new Label(notaIntermedia);
            lblNota.getStyleClass().add("text-muted");
            fila.getChildren().add(lblNota);
        }

        Label lblEstado = new Label(etiquetaEstado);
        lblEstado.getStyleClass().addAll(claseBadge.split(" "));
        fila.getChildren().add(lblEstado);

        return fila;
    }
}
