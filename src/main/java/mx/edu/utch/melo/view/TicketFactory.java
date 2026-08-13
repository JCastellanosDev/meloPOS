package mx.edu.utch.melo.view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import mx.edu.utch.melo.model.Orden;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

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

    public static Node crear(Orden orden, List<LineaTicket> lineasArticulos, Runnable onCompletar) {
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

        HBox encabezado = new HBox(titulo, columnaTiempo);
        encabezado.getStyleClass().add("ticket-header");

        Label meta = new Label(descripcionCanal(orden));
        meta.getStyleClass().add("ticket-meta");

        Separator separador = new Separator();
        separador.getStyleClass().add(estilo.claseSeparador);

        VBox items = new VBox();
        items.getStyleClass().add("ticket-items");
        for (LineaTicket linea : lineasArticulos) {
            Label lblItem = new Label(linea.texto());
            lblItem.getStyleClass().add("ticket-item");
            items.getChildren().add(lblItem);
            if (linea.nota() != null && !linea.nota().isBlank()) {
                Label lblNota = new Label("↳ " + linea.nota());
                lblNota.getStyleClass().add("ticket-item-note");
                lblNota.setWrapText(true);
                items.getChildren().add(lblNota);
            }
        }

        Region espaciador = new Region();
        VBox.setVgrow(espaciador, Priority.ALWAYS);

        Button btnCompletar = new Button("✓  Completar Pedido");
        btnCompletar.setMaxWidth(Double.MAX_VALUE);
        btnCompletar.getStyleClass().add(estilo.claseBoton);
        btnCompletar.setOnAction(e -> onCompletar.run());
        VBox.setMargin(btnCompletar, new Insets(8, 0, 0, 0));

        VBox ticket = new VBox(encabezado, meta, separador, items, espaciador, btnCompletar);
        ticket.getStyleClass().add(estilo.claseTicket);
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
