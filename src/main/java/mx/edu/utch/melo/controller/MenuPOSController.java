package mx.edu.utch.melo.controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import mx.edu.utch.melo.app.AppContext;
import mx.edu.utch.melo.async.Async;
import mx.edu.utch.melo.dao.CategoriaDAO;
import mx.edu.utch.melo.dao.DetalleOrdenDAO;
import mx.edu.utch.melo.dao.OrdenDAO;
import mx.edu.utch.melo.dao.ProductoDAO;
import mx.edu.utch.melo.dao.SucursalDAO;
import mx.edu.utch.melo.model.Categoria;
import mx.edu.utch.melo.model.DetalleOrden;
import mx.edu.utch.melo.model.EstadoOrden;
import mx.edu.utch.melo.model.ItemOrden;
import mx.edu.utch.melo.model.Orden;
import mx.edu.utch.melo.model.Producto;
import mx.edu.utch.melo.model.Sucursal;
import mx.edu.utch.melo.model.TipoOrden;
import mx.edu.utch.melo.nav.Navigator;
import mx.edu.utch.melo.nav.Pantalla;
import mx.edu.utch.melo.sesion.SesionActual;
import mx.edu.utch.melo.util.Dinero;
import mx.edu.utch.melo.util.Totales;
import mx.edu.utch.melo.view.FilaArticuloFactory;
import mx.edu.utch.melo.view.TarjetaProductoFactory;
import org.kordamp.ikonli.javafx.FontIcon;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MenuPOSController {

    private static final DateTimeFormatter FORMATO_FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy · HH:mm");

    /** Contenedor dinámico: se llena en tiempo de ejecución con las tarjetas de platillos leídas de la base de datos. */
    @FXML
    private FlowPane contenedorMenu;

    /** Contenedor dinámico: se llena con un botón por categoría real (ver AjustesController), más "Todos". */
    @FXML
    private HBox filaCategorias;

    @FXML
    private Label lblNumeroOrden;

    @FXML
    private Label lblSucursal;

    @FXML
    private Label lblTelefonoSucursal;

    @FXML
    private Label lblFechaHora;

    @FXML
    private Label lblCajero;

    @FXML
    private VBox listaArticulos;

    @FXML
    private Label lblSubtotal;

    @FXML
    private HBox filaIva;

    @FXML
    private Label lblIva;

    @FXML
    private Label lblTotal;

    @FXML
    private Label lblErrorCobro;

    @FXML
    private Button btnCobrar;

    @FXML
    private Button btnEnEspera;

    @FXML
    private FlowPane filaPedidosEnEspera;

    @FXML
    private SidebarController sidebarController;

    private final Navigator navigator;
    private final ProductoDAO productoDAO;
    private final CategoriaDAO categoriaDAO;
    private final OrdenDAO ordenDAO;
    private final DetalleOrdenDAO detalleOrdenDAO;
    private final SucursalDAO sucursalDAO;
    private final SesionActual sesion;

    /** Carrito en memoria de la mesa activa. Se persiste solo hasta que se cobra (ver onCobrarCuenta). */
    private final ObservableList<ItemOrden> articulos = FXCollections.observableArrayList();

    /** Si la sucursal activa cobra IVA (ver Sucursal.isAplicaIva); se resuelve al cargar la pantalla. */
    private boolean aplicaIva = true;

    /** id que tendrá la orden que se está armando ahora mismo, antes de crearla (ver OrdenDAO.siguienteNumeroOrden). */
    private int numeroOrdenActual = 1;

    /** Carritos pausados con "Poner en Espera" mientras el cliente decide; ninguno toca la base de datos todavía. */
    private final ObservableList<PedidoEnEspera> pedidosEnEspera = FXCollections.observableArrayList();

    /** Todos los productos activos cargados de la sucursal, sin filtrar -- la categoría solo filtra en memoria. */
    private List<Producto> todosLosProductos = List.of();

    /** Categorías reales cargadas (ver AjustesController, donde se crean); "Todos" no es una fila de esta lista. */
    private List<Categoria> categoriasCargadas = List.of();

    /** null = "Todos"; si no, solo se muestran productos de esta categoría (ver onSeleccionarCategoria). */
    private Integer categoriaSeleccionadaId;

    public MenuPOSController(AppContext contexto) {
        this.navigator = contexto.getNavigator();
        this.productoDAO = contexto.getProductoDAO();
        this.categoriaDAO = contexto.getCategoriaDAO();
        this.ordenDAO = contexto.getOrdenDAO();
        this.detalleOrdenDAO = contexto.getDetalleOrdenDAO();
        this.sucursalDAO = contexto.getSucursalDAO();
        this.sesion = contexto.getSesion();
    }

    @FXML
    void initialize() {
        sidebarController.activar(Pantalla.MENU);
        actualizarTotales();
        cargarDatosIniciales();
    }

    private void cargarDatosIniciales() {
        Async.ejecutar(
                this::construirDatosIniciales,
                this::renderizarDatosIniciales,
                error -> mostrarErrorEnMenu("No se pudo cargar el menú. Revisa la conexión con la base de datos.")
        );
    }

    /** Se ejecuta en un hilo aparte (ver Async): productos, categorías, sucursal, cajero y siguiente # de orden, juntos. */
    private DatosIniciales construirDatosIniciales() {
        int sucursalId = sesion.getSucursalActivaId();
        List<Producto> productos = productoDAO.obtenerTodosActivos();
        List<Categoria> categorias = categoriaDAO.obtenerTodos();
        Sucursal sucursal = sucursalDAO.obtenerPorId(sucursalId).orElse(null);
        boolean iva = sucursal == null || sucursal.isAplicaIva();
        int siguienteNumero = ordenDAO.siguienteNumeroOrden(sucursalId);
        String nombreCajero = sesion.getUsuarioActivo().getNombre();
        return new DatosIniciales(productos, categorias, sucursal, iva, siguienteNumero, nombreCajero, LocalDateTime.now());
    }

    private void renderizarDatosIniciales(DatosIniciales datos) {
        this.aplicaIva = datos.aplicaIva();
        this.todosLosProductos = datos.productos();
        this.categoriasCargadas = datos.categorias();
        mostrarNumeroOrden(datos.siguienteNumero());
        lblCajero.setText("Cajero: " + datos.nombreCajero());
        lblFechaHora.setText(datos.fechaHora().format(FORMATO_FECHA_HORA));
        Sucursal sucursal = datos.sucursal();
        lblSucursal.setText(sucursal == null ? "—" : sucursal.getNombre());
        lblTelefonoSucursal.setText(sucursal == null || sucursal.getTelefono() == null || sucursal.getTelefono().isBlank()
                ? "—" : sucursal.getTelefono());
        renderizarCategorias();
        renderizarMenu();
        actualizarTotales();
    }

    private void mostrarNumeroOrden(int numero) {
        this.numeroOrdenActual = numero;
        lblNumeroOrden.setText("Orden #" + numero);
    }

    /** "Todos" + un botón por cada categoría real (ver CLAUDE.md/Ajustes: las categorías las crea el propio usuario). */
    private void renderizarCategorias() {
        List<Button> botones = new ArrayList<>();
        botones.add(construirBotonCategoria("Todos", null));
        for (Categoria categoria : categoriasCargadas) {
            botones.add(construirBotonCategoria(categoria.getNombre(), categoria.getId()));
        }
        filaCategorias.getChildren().setAll(botones);
    }

    private Button construirBotonCategoria(String texto, Integer categoriaId) {
        boolean activo = Objects.equals(categoriaId, categoriaSeleccionadaId);
        Button boton = new Button(texto);
        boton.getStyleClass().add(activo ? "tab-item-active" : "tab-item");
        boton.setOnAction(e -> onSeleccionarCategoria(categoriaId));
        return boton;
    }

    private void onSeleccionarCategoria(Integer categoriaId) {
        if (Objects.equals(categoriaId, categoriaSeleccionadaId)) {
            return;
        }
        categoriaSeleccionadaId = categoriaId;
        renderizarCategorias();
        renderizarMenu();
    }

    private void renderizarMenu() {
        List<Producto> productos = categoriaSeleccionadaId == null
                ? todosLosProductos
                : todosLosProductos.stream().filter(p -> p.getCategoriaId() == categoriaSeleccionadaId).toList();

        if (productos.isEmpty()) {
            mostrarErrorEnMenu(todosLosProductos.isEmpty()
                    ? "Todavía no hay platillos activos en esta sucursal."
                    : "No hay productos en esta categoría todavía.");
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
        double iva = Totales.iva(subtotal, aplicaIva);
        double total = Totales.total(subtotal, aplicaIva);
        lblSubtotal.setText(Dinero.formatear(subtotal));
        lblIva.setText(Dinero.formatear(iva));
        lblTotal.setText(Dinero.formatear(total));
        filaIva.setVisible(aplicaIva);
        filaIva.setManaged(aplicaIva);
        btnCobrar.setDisable(articulos.isEmpty());
        btnEnEspera.setDisable(articulos.isEmpty());
    }

    @FXML
    void onCobrarCuenta() {
        if (articulos.isEmpty()) {
            return;
        }
        ocultarErrorCobro();
        btnCobrar.setDisable(true);
        List<ItemOrden> copiaArticulos = List.copyOf(articulos);
        boolean cobrarIva = aplicaIva;

        Async.ejecutar(
                () -> crearOrden(copiaArticulos, cobrarIva),
                ordenCreada -> {
                    sesion.setOrdenEnProceso(ordenCreada.getId());
                    articulos.clear();
                    renderizarArticulos();
                    mostrarNumeroOrden(ordenCreada.getId() + 1);
                    // El cobro es una ventana emergente (ver Pantalla.ORDENES), no una pestaña:
                    // el Menú se queda abierto detrás, ya con el carrito vacío.
                    navigator.abrirVentana(Pantalla.ORDENES, "melo - Cobrar");
                },
                error -> {
                    btnCobrar.setDisable(false);
                    mostrarErrorCobro("No se pudo crear la orden. Intenta de nuevo.");
                }
        );
    }

    /** Se ejecuta en un hilo aparte (ver Async): crea la orden y su detalle en una sola operación lógica. */
    private Orden crearOrden(List<ItemOrden> items, boolean cobrarIva) {
        double subtotalDouble = Totales.subtotal(items);
        BigDecimal subtotal = BigDecimal.valueOf(subtotalDouble);
        BigDecimal impuestos = BigDecimal.valueOf(Totales.iva(subtotalDouble, cobrarIva));
        BigDecimal total = BigDecimal.valueOf(Totales.total(subtotalDouble, cobrarIva));

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
            detalle.setNota(item.getNota().isBlank() ? null : item.getNota());
            detalleOrdenDAO.crear(detalle);
        }

        return orden;
    }

    /** Pausa el carrito actual (ej. el cliente todavía no decide) y deja la pantalla libre para tomar otro pedido. */
    @FXML
    void onPonerEnEspera() {
        if (articulos.isEmpty()) {
            return;
        }
        pedidosEnEspera.add(new PedidoEnEspera(numeroOrdenActual, List.copyOf(articulos)));
        articulos.clear();
        renderizarArticulos();
        mostrarNumeroOrden(numeroOrdenActual + 1);
        renderizarPedidosEnEspera();
    }

    /** Retoma un carrito en espera; si ya había uno activo (sin cobrar), ese pasa a la lista de espera. */
    private void onResumirPedido(PedidoEnEspera pedido) {
        if (!articulos.isEmpty()) {
            pedidosEnEspera.add(new PedidoEnEspera(numeroOrdenActual, List.copyOf(articulos)));
        }
        pedidosEnEspera.remove(pedido);
        articulos.setAll(pedido.articulos());
        mostrarNumeroOrden(pedido.numeroOrden());
        renderizarArticulos();
        renderizarPedidosEnEspera();
    }

    private void renderizarPedidosEnEspera() {
        filaPedidosEnEspera.getChildren().setAll(pedidosEnEspera.stream()
                .map(this::construirChipEnEspera)
                .toList());
        filaPedidosEnEspera.setVisible(!pedidosEnEspera.isEmpty());
        filaPedidosEnEspera.setManaged(!pedidosEnEspera.isEmpty());
    }

    private Button construirChipEnEspera(PedidoEnEspera pedido) {
        Button chip = new Button("Orden #" + pedido.numeroOrden() + "  ·  " + pedido.articulos().size() + " art.");
        chip.setGraphic(new FontIcon("mdi2c-clock-outline"));
        chip.setGraphicTextGap(6);
        chip.getStyleClass().add("pill-button");
        chip.setOnAction(e -> onResumirPedido(pedido));
        return chip;
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

    private record DatosIniciales(List<Producto> productos, List<Categoria> categorias, Sucursal sucursal,
                                   boolean aplicaIva, int siguienteNumero, String nombreCajero,
                                   LocalDateTime fechaHora) {
    }

    private record PedidoEnEspera(int numeroOrden, List<ItemOrden> articulos) {
    }
}
