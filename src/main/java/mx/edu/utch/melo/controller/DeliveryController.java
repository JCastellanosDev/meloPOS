package mx.edu.utch.melo.controller;

import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;
import mx.edu.utch.melo.async.Async;
import mx.edu.utch.melo.dao.ClienteDAO;
import mx.edu.utch.melo.dao.OrdenDAO;
import mx.edu.utch.melo.model.Cliente;
import mx.edu.utch.melo.model.EstadoOrden;
import mx.edu.utch.melo.model.Orden;
import mx.edu.utch.melo.model.TipoOrden;
import mx.edu.utch.melo.nav.Pantalla;

import java.util.ArrayList;
import java.util.List;

/**
 * Vista de solo lectura: muestra las órdenes de DOMICILIO activas y el
 * cliente/dirección asociados. No crea ni asigna repartidores todavía --
 * eso requiere una entidad "repartidor" que no existe en el esquema (ver
 * CLAUDE.md); el mapa y el panel de analítica siguen siendo maqueta visual.
 */
public class DeliveryController {

    @FXML
    private VBox contenedorPedidos;

    @FXML
    private SidebarController sidebarController;

    private final OrdenDAO ordenDAO;
    private final ClienteDAO clienteDAO;

    public DeliveryController(OrdenDAO ordenDAO, ClienteDAO clienteDAO) {
        this.ordenDAO = ordenDAO;
        this.clienteDAO = clienteDAO;
    }

    @FXML
    void initialize() {
        sidebarController.activar(Pantalla.DOMICILIO);
        cargarPedidos();
    }

    private void cargarPedidos() {
        Async.ejecutar(
                this::construirDatosPedidos,
                this::renderizarPedidos,
                error -> mostrarErrorCarga()
        );
    }

    /** Se ejecuta en un hilo aparte (ver Async): todas las consultas a BD juntas, la UI se arma después. */
    private List<DatosPedido> construirDatosPedidos() {
        List<DatosPedido> resultado = new ArrayList<>();
        for (Orden orden : ordenDAO.obtenerActivasPorTipo(TipoOrden.DOMICILIO)) {
            Cliente cliente = orden.getClienteId() == null
                    ? null
                    : clienteDAO.obtenerPorId(orden.getClienteId()).orElse(null);
            resultado.add(new DatosPedido(orden, cliente));
        }
        return resultado;
    }

    private void renderizarPedidos(List<DatosPedido> datos) {
        if (datos.isEmpty()) {
            mostrarMensaje("No hay pedidos a domicilio activos.");
            return;
        }
        contenedorPedidos.getChildren().setAll(datos.stream().map(this::construirTarjetaPedido).toList());
    }

    private VBox construirTarjetaPedido(DatosPedido dato) {
        Orden orden = dato.orden();
        Cliente cliente = dato.cliente();

        Label lblFolio = new Label("Pedido #" + orden.getId());
        lblFolio.getStyleClass().add("text-body-strong");
        HBox.setHgrow(lblFolio, Priority.ALWAYS);

        Label lblEstado = new Label(descripcionEstado(orden.getEstado()));
        lblEstado.getStyleClass().add(claseBadgeEstado(orden.getEstado()));

        HBox encabezado = new HBox(lblFolio, lblEstado);
        encabezado.setAlignment(Pos.CENTER_LEFT);

        Label lblCliente = new Label("Cliente: " + (cliente == null ? "Sin registrar" : cliente.getNombre()));
        lblCliente.getStyleClass().add("text-muted");

        FontIcon lblIcono = new FontIcon("mdi2m-map-marker-outline");
        lblIcono.getStyleClass().add("text-muted");
        Label lblDireccion = new Label(cliente == null ? "Dirección no registrada" : cliente.getDireccion());
        lblDireccion.getStyleClass().add("text-muted");
        HBox filaDireccion = new HBox(6, lblIcono, lblDireccion);
        filaDireccion.setAlignment(Pos.CENTER_LEFT);

        VBox tarjeta = new VBox(6, encabezado, lblCliente, filaDireccion);
        tarjeta.getStyleClass().add("order-card");
        VBox.setMargin(tarjeta, new Insets(0));
        return tarjeta;
    }

    private String descripcionEstado(EstadoOrden estado) {
        return switch (estado) {
            case PENDIENTE -> "● Pendiente";
            case EN_PREPARACION -> "● En Preparación";
            case LISTA -> "● Lista para Recoger";
            case ENTREGADA -> "● Entregada";
            case PAGADA, CANCELADA -> "● " + estado.name();
        };
    }

    private String claseBadgeEstado(EstadoOrden estado) {
        return switch (estado) {
            case LISTA, ENTREGADA -> "badge badge-success";
            case EN_PREPARACION -> "badge badge-accent";
            default -> "badge badge-muted";
        };
    }

    private void mostrarMensaje(String mensaje) {
        Label etiqueta = new Label(mensaje);
        etiqueta.getStyleClass().add("text-muted");
        contenedorPedidos.getChildren().setAll(etiqueta);
    }

    private void mostrarErrorCarga() {
        Label etiqueta = new Label("No se pudieron cargar los pedidos. Revisa la conexión con la base de datos.");
        etiqueta.getStyleClass().add("form-error");
        contenedorPedidos.getChildren().setAll(etiqueta);
    }

    private record DatosPedido(Orden orden, Cliente cliente) {
    }
}
