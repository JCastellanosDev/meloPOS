package mx.edu.utch.melo.controller;

import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;
import mx.edu.utch.melo.async.Async;
import mx.edu.utch.melo.dao.ClienteDAO;
import mx.edu.utch.melo.dao.OrdenDAO;
import mx.edu.utch.melo.model.Cliente;
import mx.edu.utch.melo.model.EstadoOrden;
import mx.edu.utch.melo.model.Orden;
import mx.edu.utch.melo.model.TipoOrden;
import mx.edu.utch.melo.nav.Navigator;
import mx.edu.utch.melo.nav.Pantalla;
import mx.edu.utch.melo.sesion.SesionActual;
import mx.edu.utch.melo.util.Dinero;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Vista de las órdenes de DOMICILIO activas y el cliente/dirección asociados. No crea ni asigna
 * repartidores todavía -- eso requiere una entidad "repartidor" que no existe en el esquema (ver
 * CLAUDE.md); el mapa central sigue sin implementar (mockup ya retirado por separado).
 *
 * Sí abre dos ventanas emergentes por tarjeta, sobre la orden real de esa tarjeta: "Agregar"
 * (MenuPedido, en modo edición -- ver SesionActual.setOrdenAEditar) y "Cobrar" (PaymentPortal,
 * igual que MenuPOSController.onCobrarCuenta para COMEDOR). Por eso, a diferencia de antes, ya no
 * es de solo lectura para navegación (sigue siendo de solo lectura para datos: no inserta ni edita
 * nada directamente, solo prepara SesionActual y delega a las pantallas que sí lo hacen).
 */
public class DeliveryController {

    @FXML
    private BorderPane raiz;

    @FXML
    private FlowPane contenedorPedidos;

    @FXML
    private Label lblTotalPedidos;

    @FXML
    private Label lblSumaTotales;

    @FXML
    private TextField txtBuscarPedido;

    @FXML
    private SidebarController sidebarController;

    private final OrdenDAO ordenDAO;
    private final ClienteDAO clienteDAO;
    private final Navigator navigator;
    private final SesionActual sesion;

    /** Último resultado cargado de BD (ver cargarPedidos); el filtro de búsqueda opera sobre esta copia sin volver a consultar. */
    private List<DatosPedido> ultimosDatos = List.of();

    public DeliveryController(OrdenDAO ordenDAO, ClienteDAO clienteDAO, Navigator navigator, SesionActual sesion) {
        this.ordenDAO = ordenDAO;
        this.clienteDAO = clienteDAO;
        this.navigator = navigator;
        this.sesion = sesion;
    }

    @FXML
    void initialize() {
        sidebarController.activar(Pantalla.DOMICILIO);
        txtBuscarPedido.textProperty().addListener((observable, antes, actual) -> aplicarFiltro());
        habilitarRecargarAlRegresarElFoco();
        cargarPedidos();
    }

    /**
     * "Agregar"/"Cobrar" abren una ventana emergente sobre la orden de una tarjeta (ver
     * onAgregarArticulos/onCobrar) -- cuando esa ventana se cierra y esta pantalla recupera el
     * foco, el estado de esa orden ya pudo cambiar (cobrada, con más artículos), así que se
     * recarga la lista. Mismo patrón que
     * PaymentPortalController.habilitarRecogerDescuentoAlRegresarElFoco.
     */
    private void habilitarRecargarAlRegresarElFoco() {
        raiz.sceneProperty().addListener((obsScene, escenaAnterior, escenaNueva) -> {
            if (escenaNueva == null) {
                return;
            }
            escenaNueva.windowProperty().addListener((obsWindow, ventanaAnterior, ventanaNueva) -> {
                if (ventanaNueva == null) {
                    return;
                }
                ventanaNueva.focusedProperty().addListener((obsFoco, antes, tieneFoco) -> {
                    if (tieneFoco) {
                        cargarPedidos();
                    }
                });
            });
        });
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
        ultimosDatos = datos;
        // Las estadísticas siempre reflejan TODOS los pedidos activos, no solo lo que el filtro
        // de búsqueda deja visible -- solo la lista de tarjetas se acota (ver aplicarFiltro).
        actualizarEstadisticas(datos);
        aplicarFiltro();
    }

    /** Filtra ultimosDatos por # de pedido (ver txtBuscarPedido) sin volver a consultar la base de datos. */
    private void aplicarFiltro() {
        String filtro = txtBuscarPedido.getText();
        List<DatosPedido> visibles = (filtro == null || filtro.isBlank())
                ? ultimosDatos
                : ultimosDatos.stream()
                        .filter(dato -> String.valueOf(dato.orden().getId()).contains(filtro.trim()))
                        .toList();

        if (visibles.isEmpty()) {
            mostrarMensaje(ultimosDatos.isEmpty()
                    ? "No hay pedidos a domicilio activos."
                    : "Ningún pedido activo coincide con esa búsqueda.");
            return;
        }
        contenedorPedidos.getChildren().setAll(visibles.stream().map(this::construirTarjetaPedido).toList());
    }

    /** Estadísticas con datos reales ya disponibles -- nada de tiempos de entrega ni repartidores (ver CLAUDE.md). */
    private void actualizarEstadisticas(List<DatosPedido> datos) {
        lblTotalPedidos.setText(String.valueOf(datos.size()));
        BigDecimal sumaTotales = datos.stream()
                .map(dato -> dato.orden().getTotal())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        lblSumaTotales.setText(Dinero.formatear(sumaTotales));
    }

    private VBox construirTarjetaPedido(DatosPedido dato) {
        Orden orden = dato.orden();
        Cliente cliente = dato.cliente();

        Label lblCliente = new Label(cliente == null ? "Sin registrar" : cliente.getNombre());
        lblCliente.getStyleClass().add("text-body-strong");
        HBox.setHgrow(lblCliente, Priority.ALWAYS);

        Label lblEstado = new Label(descripcionEstado(orden.getEstado()));
        lblEstado.getStyleClass().add(claseBadgeEstado(orden.getEstado()));

        HBox encabezado = new HBox(lblCliente, lblEstado);
        encabezado.setAlignment(Pos.CENTER_LEFT);

        Label lblFolio = new Label("Pedido #" + orden.getId());
        lblFolio.getStyleClass().add("text-muted");

        FontIcon lblIcono = new FontIcon("mdi2m-map-marker-outline");
        lblIcono.getStyleClass().add("text-muted");
        Label lblDireccion = new Label(cliente == null ? "Dirección no registrada" : cliente.getDireccion());
        lblDireccion.getStyleClass().add("text-muted");
        lblDireccion.setWrapText(true);
        HBox filaDireccion = new HBox(6, lblIcono, lblDireccion);
        filaDireccion.setAlignment(Pos.CENTER_LEFT);

        Label lblTotal = new Label(Dinero.formatear(orden.getTotal()));
        lblTotal.getStyleClass().add("text-price");

        Button btnAgregar = new Button("Agregar", new FontIcon("mdi2p-plus-circle-outline"));
        btnAgregar.getStyleClass().add("btn-card-action");
        btnAgregar.setOnAction(evento -> onAgregarArticulos(orden));

        Button btnCobrar = new Button("Cobrar", new FontIcon("mdi2c-cash"));
        btnCobrar.getStyleClass().add("btn-card-action-accent");
        btnCobrar.setOnAction(evento -> onCobrar(orden));

        Region espaciador = new Region();
        HBox.setHgrow(espaciador, Priority.ALWAYS);
        HBox filaInferior = new HBox(8, lblTotal, espaciador, btnAgregar, btnCobrar);
        filaInferior.setAlignment(Pos.CENTER_LEFT);

        VBox tarjeta = new VBox(8, encabezado, lblFolio, filaDireccion, filaInferior);
        tarjeta.getStyleClass().add("order-card");
        tarjeta.setPrefWidth(340);
        VBox.setMargin(tarjeta, new Insets(0));
        return tarjeta;
    }

    /** Reabre MenuPedido en modo edición sobre esta orden (ver SesionActual.setOrdenAEditar, VentaService.agregarArticulos). */
    private void onAgregarArticulos(Orden orden) {
        sesion.setOrdenAEditar(orden.getId());
        navigator.abrirVentana(Pantalla.MENU_PEDIDO, "melo - Agregar al Pedido");
    }

    /** Abre el cobro para esta orden -- mismo patrón que MenuPOSController.onCobrarCuenta para COMEDOR. */
    private void onCobrar(Orden orden) {
        sesion.setOrdenEnProceso(orden.getId());
        navigator.abrirVentana(Pantalla.ORDENES, "melo - Cobrar");
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
        FontIcon icono = new FontIcon("mdi2t-truck-delivery-outline");
        icono.getStyleClass().add("empty-state-icon");
        Label etiqueta = new Label(mensaje);
        etiqueta.getStyleClass().add("empty-state-text");
        VBox estado = new VBox(8, icono, etiqueta);
        estado.getStyleClass().add("empty-state");
        estado.setAlignment(Pos.CENTER);
        estado.setPrefWidth(420);
        contenedorPedidos.getChildren().setAll(estado);
    }

    private void mostrarErrorCarga() {
        ultimosDatos = List.of();
        lblTotalPedidos.setText("—");
        lblSumaTotales.setText("—");
        Label etiqueta = new Label("No se pudieron cargar los pedidos. Revisa la conexión con la base de datos.");
        etiqueta.getStyleClass().add("form-error");
        contenedorPedidos.getChildren().setAll(etiqueta);
    }

    private record DatosPedido(Orden orden, Cliente cliente) {
    }
}
