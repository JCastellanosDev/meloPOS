package mx.edu.utch.melo.controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import mx.edu.utch.melo.app.AppContext;
import mx.edu.utch.melo.async.Async;
import mx.edu.utch.melo.dao.DetalleOrdenDAO;
import mx.edu.utch.melo.dao.OrdenDAO;
import mx.edu.utch.melo.dao.ProductoDAO;
import mx.edu.utch.melo.model.DetalleOrden;
import mx.edu.utch.melo.model.EstadoOrden;
import mx.edu.utch.melo.model.ItemOrden;
import mx.edu.utch.melo.model.Orden;
import mx.edu.utch.melo.model.Producto;
import mx.edu.utch.melo.model.TipoOrden;
import mx.edu.utch.melo.nav.Navigator;
import mx.edu.utch.melo.nav.Pantalla;
import mx.edu.utch.melo.sesion.SesionActual;
import mx.edu.utch.melo.util.Dinero;
import mx.edu.utch.melo.util.Totales;
import mx.edu.utch.melo.view.FilaArticuloFactory;
import mx.edu.utch.melo.view.TarjetaProductoFactory;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class MenuPOSController {

    /** Contenedor dinámico: se llena en tiempo de ejecución con las tarjetas de platillos leídas de la base de datos. */
    @FXML
    private FlowPane contenedorMenu;

    @FXML
    private Button btnCategoriaPlatillos;

    @FXML
    private Button btnCategoriaBebidas;

    @FXML
    private Button btnCategoriaPostres;

    @FXML
    private VBox listaArticulos;

    @FXML
    private Label lblSubtotal;

    @FXML
    private Label lblIva;

    @FXML
    private Label lblTotal;

    @FXML
    private Label lblErrorCobro;

    @FXML
    private Button btnCobrar;

    @FXML
    private SidebarController sidebarController;

    private final Navigator navigator;
    private final ProductoDAO productoDAO;
    private final OrdenDAO ordenDAO;
    private final DetalleOrdenDAO detalleOrdenDAO;
    private final SesionActual sesion;

    /** Carrito en memoria de la mesa activa. Se persiste solo hasta que se cobra (ver onCobrarCuenta). */
    private final ObservableList<ItemOrden> articulos = FXCollections.observableArrayList();

    public MenuPOSController(AppContext contexto) {
        this.navigator = contexto.getNavigator();
        this.productoDAO = contexto.getProductoDAO();
        this.ordenDAO = contexto.getOrdenDAO();
        this.detalleOrdenDAO = contexto.getDetalleOrdenDAO();
        this.sesion = contexto.getSesion();
    }

    @FXML
    void initialize() {
        sidebarController.activar(Pantalla.MENU);
        actualizarTotales();
        cargarProductos();
    }

    private void cargarProductos() {
        Async.ejecutar(
                productoDAO::obtenerTodosActivos,
                this::renderizarMenu,
                error -> mostrarErrorEnMenu("No se pudo cargar el menú. Revisa la conexión con la base de datos.")
        );
    }

    private void renderizarMenu(List<Producto> productos) {
        if (productos.isEmpty()) {
            mostrarErrorEnMenu("Todavía no hay platillos activos en esta sucursal.");
            return;
        }
        contenedorMenu.getChildren().setAll(productos.stream()
                .map(producto -> TarjetaProductoFactory.crear(producto, () -> agregarAlCarrito(producto)))
                .toList());
    }

    private void mostrarErrorEnMenu(String mensaje) {
        Label etiqueta = new Label(mensaje);
        etiqueta.getStyleClass().add("form-error");
        contenedorMenu.getChildren().setAll(etiqueta);
    }

    private void agregarAlCarrito(Producto producto) {
        for (ItemOrden item : articulos) {
            if (item.getProductoId() == producto.getId()) {
                item.cantidadProperty().set(item.getCantidad() + 1);
                renderizarArticulos();
                return;
            }
        }
        articulos.add(new ItemOrden(producto.getId(), producto.getNombre(), producto.getPrecio().doubleValue(), 1));
        renderizarArticulos();
    }

    private void renderizarArticulos() {
        listaArticulos.getChildren().setAll(articulos.stream()
                .map(item -> FilaArticuloFactory.crear(
                        item,
                        delta -> cambiarCantidad(item, delta),
                        () -> eliminarArticulo(item)))
                .toList());
        actualizarTotales();
    }

    private void cambiarCantidad(ItemOrden item, int delta) {
        int nuevaCantidad = item.getCantidad() + delta;
        if (nuevaCantidad < 1) {
            return;
        }
        item.cantidadProperty().set(nuevaCantidad);
        renderizarArticulos();
    }

    private void eliminarArticulo(ItemOrden item) {
        articulos.remove(item);
        renderizarArticulos();
    }

    private void actualizarTotales() {
        double subtotal = Totales.subtotal(articulos);
        double iva = Totales.iva(subtotal);
        double total = Totales.total(subtotal);
        lblSubtotal.setText(Dinero.formatear(subtotal));
        lblIva.setText(Dinero.formatear(iva));
        lblTotal.setText(Dinero.formatear(total));
        btnCobrar.setDisable(articulos.isEmpty());
    }

    @FXML
    void onCobrarCuenta() {
        if (articulos.isEmpty()) {
            return;
        }
        ocultarErrorCobro();
        btnCobrar.setDisable(true);
        List<ItemOrden> copiaArticulos = List.copyOf(articulos);

        Async.ejecutar(
                () -> crearOrden(copiaArticulos),
                ordenCreada -> {
                    sesion.setOrdenEnProceso(ordenCreada.getId());
                    articulos.clear();
                    navigator.navigateTo(Pantalla.ORDENES);
                },
                error -> {
                    btnCobrar.setDisable(false);
                    mostrarErrorCobro("No se pudo crear la orden. Intenta de nuevo.");
                }
        );
    }

    /** Se ejecuta en un hilo aparte (ver Async): crea la orden y su detalle en una sola operación lógica. */
    private Orden crearOrden(List<ItemOrden> items) {
        double subtotalDouble = Totales.subtotal(items);
        BigDecimal subtotal = BigDecimal.valueOf(subtotalDouble);
        BigDecimal impuestos = BigDecimal.valueOf(Totales.iva(subtotalDouble));
        BigDecimal total = BigDecimal.valueOf(Totales.total(subtotalDouble));

        Orden orden = new Orden();
        orden.setTipoOrden(TipoOrden.COMEDOR);
        orden.setMesaId(null);
        orden.setUsuarioId(sesion.getUsuarioActivo().getId());
        orden.setClienteId(null);
        orden.setTurnoId(null);
        orden.setEstado(EstadoOrden.PENDIENTE);
        orden.setSubtotal(subtotal);
        orden.setImpuestos(impuestos);
        orden.setDistanciaKm(null);
        orden.setCostoEnvio(BigDecimal.ZERO);
        orden.setTotal(total);
        orden.setFechaCreacion(LocalDateTime.now());
        orden = ordenDAO.crear(orden);

        for (ItemOrden item : items) {
            DetalleOrden detalle = new DetalleOrden();
            detalle.setOrdenId(orden.getId());
            detalle.setProductoId(item.getProductoId());
            detalle.setCantidad(item.getCantidad());
            detalle.setPrecioUnitario(BigDecimal.valueOf(item.getPrecioUnitario()));
            detalleOrdenDAO.crear(detalle);
        }

        return orden;
    }

    private void mostrarErrorCobro(String mensaje) {
        lblErrorCobro.setText("⚠ " + mensaje);
        lblErrorCobro.setVisible(true);
        lblErrorCobro.setManaged(true);
    }

    private void ocultarErrorCobro() {
        lblErrorCobro.setVisible(false);
        lblErrorCobro.setManaged(false);
    }
}
