package mx.edu.utch.melo.controller;

import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import mx.edu.utch.melo.async.Async;
import mx.edu.utch.melo.dao.ProductoDAO;
import mx.edu.utch.melo.model.Producto;
import mx.edu.utch.melo.nav.Pantalla;
import mx.edu.utch.melo.sesion.SesionActual;
import mx.edu.utch.melo.view.EstadoVacioFactory;
import mx.edu.utch.melo.view.FilaEntidadFactory;

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

    public InventarioController(ProductoDAO productoDAO, SesionActual sesion) {
        this.productoDAO = productoDAO;
        this.sesion = sesion;
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

    /** Ver auditoría UI/UX, "componentes a convertir en reutilizables": antes esta fila y la de
     * PersonalController se reconstruían por separado a mano con el mismo esqueleto. De paso
     * corrige un bug real de estilo -- ver FilaEntidadFactory y el aviso al inicio de styles.css:
     * el .add() anterior insertaba "badge badge-x" como un solo token de styleClass, que JavaFX
     * nunca separa en dos clases CSS, así que la insignia se veía sin fondo ni color. */
    private Node construirFilaProducto(Producto producto) {
        String existencias = producto.getCantidadDisponible() + " / mín. " + producto.getStockMinimo();
        boolean stockBajo = producto.tieneStockBajo();
        return FilaEntidadFactory.crear(
                null,
                producto.getNombre(),
                producto.isDisponible() ? "Disponible" : "No disponible",
                existencias,
                stockBajo ? "Stock Bajo" : "OK",
                stockBajo ? "badge badge-urgent" : "badge badge-success");
    }

    private void mostrarMensaje(String mensaje) {
        listaProductos.getChildren().setAll(EstadoVacioFactory.crear("mdi2b-box", mensaje, 400));
    }

    private void mostrarErrorCarga() {
        Label etiqueta = new Label("No se pudo cargar el inventario. Revisa la conexión con la base de datos.");
        etiqueta.getStyleClass().add("form-error");
        listaProductos.getChildren().setAll(etiqueta);
    }
}
