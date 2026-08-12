package mx.edu.utch.melo.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import mx.edu.utch.melo.app.AppContext;
import mx.edu.utch.melo.async.Async;
import mx.edu.utch.melo.dao.DetalleOrdenDAO;
import mx.edu.utch.melo.dao.OrdenDAO;
import mx.edu.utch.melo.dao.ProductoDAO;
import mx.edu.utch.melo.model.DetalleOrden;
import mx.edu.utch.melo.model.EstadoOrden;
import mx.edu.utch.melo.model.Orden;
import mx.edu.utch.melo.model.Producto;
import mx.edu.utch.melo.nav.Pantalla;
import mx.edu.utch.melo.view.TicketFactory;

import java.util.ArrayList;
import java.util.List;

public class KitchenDisplayController {

    @FXML
    private Label lblClock;

    @FXML
    private Label lblActivas;

    @FXML
    private HBox contenedorTickets;

    @FXML
    private SidebarController sidebarController;

    private final OrdenDAO ordenDAO;
    private final DetalleOrdenDAO detalleOrdenDAO;
    private final ProductoDAO productoDAO;

    public KitchenDisplayController(AppContext contexto) {
        this.ordenDAO = contexto.getOrdenDAO();
        this.detalleOrdenDAO = contexto.getDetalleOrdenDAO();
        this.productoDAO = contexto.getProductoDAO();
    }

    @FXML
    void initialize() {
        sidebarController.activar(Pantalla.COCINA);
        cargarTickets();
    }

    private void cargarTickets() {
        Async.ejecutar(
                this::construirDatosTickets,
                this::renderizarTickets,
                error -> mostrarErrorCarga()
        );
    }

    /** Se ejecuta en un hilo aparte (ver Async): todas las consultas a BD juntas, la UI se arma después. */
    private List<DatosTicket> construirDatosTickets() {
        List<DatosTicket> resultado = new ArrayList<>();
        for (Orden orden : ordenDAO.obtenerPorEstado(EstadoOrden.EN_PREPARACION)) {
            List<String> lineas = new ArrayList<>();
            for (DetalleOrden detalle : detalleOrdenDAO.obtenerPorOrden(orden.getId())) {
                Producto producto = productoDAO.obtenerPorId(detalle.getProductoId()).orElse(null);
                String nombre = producto == null ? "Producto #" + detalle.getProductoId() : producto.getNombre();
                lineas.add(detalle.getCantidad() + "x " + nombre);
            }
            resultado.add(new DatosTicket(orden, lineas));
        }
        return resultado;
    }

    private void renderizarTickets(List<DatosTicket> datos) {
        contenedorTickets.getChildren().setAll(datos.stream()
                .map(datoTicket -> TicketFactory.crear(
                        datoTicket.orden(),
                        datoTicket.lineas(),
                        () -> completarPedido(datoTicket.orden().getId())))
                .toList());
        lblActivas.setText(datos.size() + " Activas");
    }

    private void completarPedido(int ordenId) {
        Async.ejecutar(
                () -> marcarEntregada(ordenId),
                exito -> cargarTickets(),
                error -> mostrarErrorCarga()
        );
    }

    /** Se ejecuta en un hilo aparte (ver Async): avanza la orden a ENTREGADA. */
    private Boolean marcarEntregada(int ordenId) {
        Orden orden = ordenDAO.obtenerPorId(ordenId).orElseThrow();
        orden.setEstado(EstadoOrden.ENTREGADA);
        ordenDAO.actualizar(orden);
        return Boolean.TRUE;
    }

    private void mostrarErrorCarga() {
        Label mensaje = new Label("No se pudo cargar la cocina. Revisa la conexión con la base de datos.");
        mensaje.getStyleClass().add("form-error");
        contenedorTickets.getChildren().setAll(mensaje);
    }

    private record DatosTicket(Orden orden, List<String> lineas) {
    }
}
