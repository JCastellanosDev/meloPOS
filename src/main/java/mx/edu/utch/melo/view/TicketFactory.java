package mx.edu.utch.melo.view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import mx.edu.utch.melo.model.Orden;
import org.kordamp.ikonli.javafx.FontIcon;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.function.IntConsumer;

/**
 * Construye el ticket visual de una {@link Orden} para KitchenDisplay.
 * Responsabilidad única: traducir una orden + sus líneas de texto a
 * nodos JavaFX. No consulta la base de datos ni conoce el resto de
 * tickets -- eso lo arma KitchenDisplayController.
 */
public final class TicketFactory {

    private static final long MINUTOS_URGENTE = 20;
    private static final long MINUTOS_ATENCION = 10;

    private TicketFactory() {
    }

    /**
     * @param ampliado         si esta tarjeta es la que está ocupando las 2 filas de su columna
     *                         ahora mismo (ver KitchenDisplayController.renderizarTickets) -- solo
     *                         cambia la tipografía (más grande) y el ícono/texto del botón, la
     *                         tarjeta en sí no sabe de GridPane ni de su propia posición.
     * @param lineasTachadas   índices (dentro de la lista de líneas) que el cocinero ya marcó como
     *                         preparados -- se dibujan con tachado. Estado puramente visual de esta
     *                         sesión de KitchenDisplay, no se persiste en base de datos (ver
     *                         CLAUDE.md: no se pidió un estado de "producto preparado" en el modelo).
     * @param onTacharLinea    se llama con el índice de la línea que el cocinero acaba de tocar.
     */
    public static Node crear(Orden orden, List<LineaTicket> lineasArticulos, Runnable onCompletar,
                              Runnable onAmpliar, boolean ampliado, Set<Integer> lineasTachadas,
                              IntConsumer onTacharLinea) {
        long minutos = Duration.between(orden.getFechaCreacion(), LocalDateTime.now()).toMinutes();
        Estilo estilo = elegirEstilo(minutos);

        Label titulo = new Label("Orden #" + orden.getId());
        titulo.getStyleClass().add("ticket-title");
        HBox.setHgrow(titulo, Priority.ALWAYS);

        Label tiempo = new Label(String.format("%02d:%02d", minutos / 60, minutos % 60));
        tiempo.getStyleClass().add(estilo.claseTiempo);
        Label etiquetaTiempo = new Label("Transcurrido");
        etiquetaTiempo.getStyleClass().add("text-muted-sm");
        VBox columnaTiempo = new VBox(tiempo, etiquetaTiempo);
        columnaTiempo.setAlignment(Pos.CENTER_RIGHT);

        Button btnAmpliar = new Button(ampliado ? "Reducir" : "Ampliar");
        btnAmpliar.setGraphic(new FontIcon(ampliado ? "mdi2a-arrow-collapse" : "mdi2a-arrow-expand"));
        btnAmpliar.setGraphicTextGap(6);
        btnAmpliar.getStyleClass().add("ticket-expand-btn");
        btnAmpliar.setOnAction(e -> onAmpliar.run());

        HBox encabezado = new HBox(8, titulo, btnAmpliar, columnaTiempo);
        encabezado.setAlignment(Pos.CENTER_LEFT);
        encabezado.getStyleClass().add("ticket-header");

        Label meta = new Label(descripcionCanal(orden));
        meta.getStyleClass().add("ticket-meta");

        Separator separador = new Separator();
        separador.getStyleClass().add(estilo.claseSeparador);

        // Cada artículo se puede tachar tocándolo (ver onTacharLinea) para que el cocinero lleve
        // el avance de la comanda sin salir de esta misma tarjeta.
        VBox items = new VBox();
        items.getStyleClass().add("ticket-items");
        for (int indice = 0; indice < lineasArticulos.size(); indice++) {
            LineaTicket linea = lineasArticulos.get(indice);
            boolean tachada = lineasTachadas.contains(indice);

            Label lblItem = new Label(linea.texto());
            lblItem.getStyleClass().add(tachada ? "ticket-item-tachado" : "ticket-item");
            int indiceLinea = indice;
            lblItem.setOnMouseClicked(e -> onTacharLinea.accept(indiceLinea));
            items.getChildren().add(lblItem);

            if (linea.nota() != null && !linea.nota().isBlank()) {
                Label lblNota = new Label("↳ " + linea.nota());
                lblNota.getStyleClass().add("ticket-item-note");
                lblNota.setWrapText(true);
                items.getChildren().add(lblNota);
            }
        }

        // Con scroll propio: si la tarjeta no alcanza para todos los artículos (cuadrícula 4x2
        // chica, o un pedido con muchos productos), no se recortan ni se encima con la de al lado.
        ScrollPane scrollItems = new ScrollPane(items);
        scrollItems.setFitToWidth(true);
        scrollItems.getStyleClass().add("kds-scroll");
        VBox.setVgrow(scrollItems, Priority.ALWAYS);

        Button btnCompletar = new Button("✓  Completar Pedido");
        btnCompletar.setMaxWidth(Double.MAX_VALUE);
        btnCompletar.getStyleClass().add(estilo.claseBoton);
        btnCompletar.setOnAction(e -> onCompletar.run());
        VBox.setMargin(btnCompletar, new Insets(8, 0, 0, 0));

        VBox ticket = new VBox(encabezado, meta, separador, scrollItems, btnCompletar);
        ticket.getStyleClass().add(estilo.claseTicket);
        if (ampliado) {
            ticket.getStyleClass().add("ticket-ampliado");
        }
        // Permite que la tarjeta llene su celda en la cuadrícula 4x2 de KitchenDisplay (ancho
        // fijo, y alto cuando ocupa 2 filas al estar ampliada), en vez de quedarse en el tamaño
        // fijo de -fx-pref-width (ver .ticket en styles.css).
        ticket.setMaxWidth(Double.MAX_VALUE);
        ticket.setMaxHeight(Double.MAX_VALUE);
        return ticket;
    }

    private static Estilo elegirEstilo(long minutosTranscurridos) {
        if (minutosTranscurridos >= MINUTOS_URGENTE) {
            return new Estilo("ticket-urgent", "ticket-separator-urgent", "text-time-urgent", "ticket-bump-btn-urgent");
        }
        if (minutosTranscurridos >= MINUTOS_ATENCION) {
            return new Estilo("ticket-active", "ticket-separator-active", "text-time-warn", "ticket-bump-btn-active");
        }
        return new Estilo("ticket", "ticket-separator", "text-time-ok", "ticket-bump-btn");
    }

    private static String descripcionCanal(Orden orden) {
        return switch (orden.getTipoOrden()) {
            case COMEDOR -> orden.getMesaId() != null ? "Mesa " + orden.getMesaId() + " • Comedor" : "Comedor";
            case PARA_LLEVAR -> "Para Llevar";
            case PARA_RECOGER -> "Para Recoger";
            case DOMICILIO -> "Domicilio";
        };
    }

    private record Estilo(String claseTicket, String claseSeparador, String claseTiempo, String claseBoton) {
    }

    /** Una línea de artículo del ticket, con su nota opcional para cocina (ej. "sin cebolla"). */
    public record LineaTicket(String texto, String nota) {
    }
}
