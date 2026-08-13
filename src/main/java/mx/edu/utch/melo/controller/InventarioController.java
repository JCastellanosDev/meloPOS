package mx.edu.utch.melo.controller;

import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import mx.edu.utch.melo.app.AppContext;
import mx.edu.utch.melo.async.Async;
import mx.edu.utch.melo.dao.ProductoDAO;
import mx.edu.utch.melo.model.Producto;
import mx.edu.utch.melo.nav.Pantalla;
import mx.edu.utch.melo.sesion.SesionActual;

import java.util.List;

/** Vista de solo lectura: existencias de los productos de la sucursal activa, con alerta de stock bajo. */
public class InventarioController {

    @FXML
    private Label lblStockBajo;

    @FXML
    private VBox listaProductos;

    @FXML
    private SidebarController sidebarController;

    private final ProductoDAO productoDAO;
    private final SesionActual sesion;

    public InventarioController(AppContext contexto) {
        this.productoDAO = contexto.getProductoDAO();
        this.sesion = contexto.getSesion();
    }

    @FXML
    void initialize() {
        sidebarController.activar(Pantalla.INVENTARIO);
        cargarInventario();
    }

    private void cargarInventario() {
        Async.ejecutar(
                () -> productoDAO.obtenerPorSucursal(sesion.getSucursalActivaId()),
                this::renderizarInventario,
                error -> mostrarErrorCarga()
        );
    }

    private void renderizarInventario(List<Producto> productos) {
        long conStockBajo = productos.stream().filter(Producto::tieneStockBajo).count();
        lblStockBajo.setText(conStockBajo + " con stock bajo");

        if (productos.isEmpty()) {
            mostrarMensaje("No hay productos registrados en esta sucursal.");
            return;
        }
        listaProductos.getChildren().setAll(productos.stream().map(this::construirFilaProducto).toList());
    }

    private HBox construirFilaProducto(Producto producto) {
        Label lblNombre = new Label(producto.getNombre());
        lblNombre.getStyleClass().add("text-body-strong");

        Label lblDisponibilidad = new Label(producto.isDisponible() ? "Disponible" : "No disponible");
        lblDisponibilidad.getStyleClass().add("text-muted");

        VBox columnaNombre = new VBox(4, lblNombre, lblDisponibilidad);
        HBox.setHgrow(columnaNombre, Priority.ALWAYS);

        Label lblExistencias = new Label(producto.getCantidadDisponible() + " / mín. " + producto.getStockMinimo());
        lblExistencias.getStyleClass().add("text-muted");

        Label lblAlerta = new Label(producto.tieneStockBajo() ? "Stock Bajo" : "OK");
        lblAlerta.getStyleClass().add(producto.tieneStockBajo() ? "badge badge-urgent" : "badge badge-success");

        HBox fila = new HBox(14, columnaNombre, lblExistencias, lblAlerta);
        fila.setAlignment(Pos.CENTER_LEFT);
        fila.getStyleClass().add("order-card");
        return fila;
    }

    private void mostrarMensaje(String mensaje) {
        Label etiqueta = new Label(mensaje);
        etiqueta.getStyleClass().add("text-muted");
        listaProductos.getChildren().setAll(etiqueta);
    }

    private void mostrarErrorCarga() {
        Label etiqueta = new Label("No se pudo cargar el inventario. Revisa la conexión con la base de datos.");
        etiqueta.getStyleClass().add("form-error");
        listaProductos.getChildren().setAll(etiqueta);
    }
}
