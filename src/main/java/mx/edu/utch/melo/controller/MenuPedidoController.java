package mx.edu.utch.melo.controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import mx.edu.utch.melo.async.Async;
import mx.edu.utch.melo.dao.ClienteDAO;
import mx.edu.utch.melo.dao.DetalleOrdenDAO;
import mx.edu.utch.melo.dao.OrdenDAO;
import mx.edu.utch.melo.dao.ProductoDAO;
import mx.edu.utch.melo.dao.SucursalDAO;
import mx.edu.utch.melo.model.Cliente;
import mx.edu.utch.melo.model.DetalleOrden;
import mx.edu.utch.melo.model.ItemOrden;
import mx.edu.utch.melo.model.Orden;
import mx.edu.utch.melo.model.Producto;
import mx.edu.utch.melo.model.Sucursal;
import mx.edu.utch.melo.service.VentaService;
import mx.edu.utch.melo.sesion.SesionActual;
import mx.edu.utch.melo.util.Dinero;
import mx.edu.utch.melo.util.Totales;
import mx.edu.utch.melo.view.FilaArticuloFactory;
import mx.edu.utch.melo.view.TarjetaProductoFactory;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Ventana emergente para armar un pedido a domicilio (ver Pantalla.MENU_PEDIDO,
 * abierta desde PedidosController). "Mandar a Cocina" crea la orden directo en
 * EN_PREPARACION -- a diferencia de COMEDOR, en domicilio se cobra al entregar,
 * no antes de preparar (ver CLAUDE.md, sección "Flujo de un pedido").
 *
 * También sirve para AGREGAR artículos a una orden de DOMICILIO ya existente (ver
 * DeliveryController: botón "Agregar" en una tarjeta de pedido activo) -- ese modo se activa si
 * SesionActual trae una orden a editar (ver SesionActual.consumirOrdenAEditar); en ese caso el
 * carrito editable (articulos) solo contiene los artículos NUEVOS que se están agregando, los ya
 * existentes se muestran aparte, de solo lectura, y "Agregar al Pedido" llama
 * VentaService.agregarArticulos en vez de crear una orden nueva.
 */
public class MenuPedidoController {

    @FXML
    private Label lblCliente;

    /** Contenedor dinámico: se llena en tiempo de ejecución con las tarjetas de platillos leídas de la base de datos. */
    @FXML
    private FlowPane contenedorMenu;

    @FXML
    private VBox listaArticulos;

    @FXML
    private VBox seccionArticulosExistentes;

    @FXML
    private VBox listaArticulosExistentes;

    @FXML
    private Label lblSubtotal;

    @FXML
    private Label lblIva;

    @FXML
    private Label lblTotal;

    @FXML
    private Label lblTotalTexto;

    @FXML
    private Label lblEnvio;

    @FXML
    private Label lblErrorPedido;

    @FXML
    private Button btnMandarCocina;

    private final ProductoDAO productoDAO;
    private final VentaService ventaService;
    private final ClienteDAO clienteDAO;
    private final SucursalDAO sucursalDAO;
    private final OrdenDAO ordenDAO;
    private final DetalleOrdenDAO detalleOrdenDAO;
    private final SesionActual sesion;

    /** Carrito en memoria de este pedido. Se persiste solo hasta que se manda a cocina/se agrega (ver onMandarCocina). */
    private final ObservableList<ItemOrden> articulos = FXCollections.observableArrayList();

    private Integer clienteId;

    /** Presente solo en modo edición (ver SesionActual.consumirOrdenAEditar); null = pedido nuevo. */
    private Integer ordenAEditarId;
    private boolean modoEdicion;

    /** Total ya cobrado/registrado de la orden que se está editando; ZERO en modo creación. */
    private BigDecimal totalOrdenExistente = BigDecimal.ZERO;

    /** Distancia y costo de envío calculados en Pedidos (ver SesionActual); null/cero si no se calculó ninguna ruta. */
    private BigDecimal distanciaKmActual;
    private BigDecimal costoEnvioCalculado = BigDecimal.ZERO;

    /** Si la sucursal activa cobra IVA (ver Sucursal.isAplicaIva); se resuelve al cargar la pantalla. */
    private boolean aplicaIva = true;

    public MenuPedidoController(ProductoDAO productoDAO, VentaService ventaService, ClienteDAO clienteDAO,
                                 SucursalDAO sucursalDAO, OrdenDAO ordenDAO, DetalleOrdenDAO detalleOrdenDAO,
                                 SesionActual sesion) {
        this.productoDAO = productoDAO;
        this.ventaService = ventaService;
        this.clienteDAO = clienteDAO;
        this.sucursalDAO = sucursalDAO;
        this.ordenDAO = ordenDAO;
        this.detalleOrdenDAO = detalleOrdenDAO;
        this.sesion = sesion;
    }

    @FXML
    void initialize() {
        ordenAEditarId = sesion.consumirOrdenAEditar().orElse(null);
        modoEdicion = ordenAEditarId != null;
        if (!modoEdicion) {
            clienteId = sesion.getClienteEnProceso().orElse(null);
        }
        if (modoEdicion) {
            btnMandarCocina.setText("Agregar al Pedido");
            lblTotalTexto.setText("Nuevo total");
        }
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

    /** Se ejecuta en un hilo aparte (ver Async): productos activos, nombre del cliente, costo de envío e IVA, juntos. */
    private DatosIniciales construirDatosIniciales() {
        List<Producto> productos = productoDAO.obtenerTodosActivos();
        Sucursal sucursal = sucursalDAO.obtenerPorId(sesion.getSucursalActivaId()).orElse(null);
        boolean iva = sucursal == null || sucursal.isAplicaIva();

        if (modoEdicion) {
            Orden orden = ordenDAO.obtenerPorId(ordenAEditarId).orElse(null);
            String nombreCliente = orden != null && orden.getClienteId() != null
                    ? clienteDAO.obtenerPorId(orden.getClienteId()).map(Cliente::getNombre).orElse(null)
                    : null;
            List<DetalleOrden> existentes = detalleOrdenDAO.obtenerPorOrden(ordenAEditarId);
            BigDecimal distanciaKm = orden == null ? null : orden.getDistanciaKm();
            BigDecimal costoEnvio = orden == null ? BigDecimal.ZERO : orden.getCostoEnvio();
            BigDecimal totalExistente = orden == null ? BigDecimal.ZERO : orden.getTotal();
            return new DatosIniciales(productos, nombreCliente, distanciaKm, costoEnvio, iva, existentes, totalExistente);
        }

        String nombreCliente = clienteId == null
                ? null
                : clienteDAO.obtenerPorId(clienteId).map(Cliente::getNombre).orElse(null);
        BigDecimal distanciaKm = sesion.getDistanciaKmEnProceso().orElse(null);
        BigDecimal costoEnvio = sucursal == null ? BigDecimal.ZERO : sucursal.calcularCostoEnvio(distanciaKm);
        return new DatosIniciales(productos, nombreCliente, distanciaKm, costoEnvio, iva, List.of(), BigDecimal.ZERO);
    }

    private void renderizarDatosIniciales(DatosIniciales datos) {
        lblCliente.setText(datos.nombreCliente() == null ? "Cliente sin identificar" : datos.nombreCliente());
        distanciaKmActual = datos.distanciaKm();
        costoEnvioCalculado = datos.costoEnvio();
        aplicaIva = datos.aplicaIva();
        totalOrdenExistente = datos.totalExistente();
        actualizarEnvio();
        renderizarMenu(datos.productos());
        renderizarArticulosExistentes(datos.articulosExistentes(), datos.productos());
        actualizarTotales();
    }

    private void actualizarEnvio() {
        if (distanciaKmActual == null) {
            lblEnvio.setText("Sin ubicar");
        } else {
            lblEnvio.setText(distanciaKmActual + " km · " + Dinero.formatear(costoEnvioCalculado));
        }
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

    /** Fila de solo lectura por artículo que ya estaba en la orden antes de abrir esta ventana (ver modoEdicion). */
    private void renderizarArticulosExistentes(List<DetalleOrden> existentes, List<Producto> productos) {
        boolean hayExistentes = modoEdicion && !existentes.isEmpty();
        seccionArticulosExistentes.setVisible(hayExistentes);
        seccionArticulosExistentes.setManaged(hayExistentes);
        if (!hayExistentes) {
            return;
        }
        Map<Integer, String> nombresPorProducto = productos.stream()
                .collect(Collectors.toMap(Producto::getId, Producto::getNombre));
        listaArticulosExistentes.getChildren().setAll(existentes.stream().map(detalle -> {
            String nombre = nombresPorProducto.getOrDefault(detalle.getProductoId(), "Producto #" + detalle.getProductoId());
            Label fila = new Label(detalle.getCantidad() + "x " + nombre + " · " + Dinero.formatear(detalle.getSubtotal()));
            fila.getStyleClass().add("text-muted");
            return fila;
        }).toList());
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
        articulos.add(new ItemOrden(producto.getId(), producto.getNombre(), producto.getPrecio(), 1));
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
        BigDecimal subtotal = Totales.subtotal(articulos);
        BigDecimal iva = Totales.iva(subtotal, aplicaIva);
        // El envío ya se muestra en su propia fila (ver actualizarEnvio), pero también debe
        // sumarse aquí -- si no, lo que ve el mesero en pantalla mientras arma el carrito no
        // coincide con lo que VentaService.calcularTotales realmente cobra/persiste al mandar
        // el pedido (ese sí ya sumaba el envío desde siempre, esto era solo un bug de display).
        BigDecimal totalNuevo = Totales.total(subtotal, aplicaIva).add(costoEnvioCalculado);
        lblSubtotal.setText(Dinero.formatear(subtotal));
        lblIva.setText(Dinero.formatear(iva));
        lblTotal.setText(Dinero.formatear(modoEdicion ? totalOrdenExistente.add(totalNuevo) : totalNuevo));
        btnMandarCocina.setDisable(articulos.isEmpty());
    }

    @FXML
    void onMandarCocina() {
        if (articulos.isEmpty()) {
            return;
        }
        ocultarErrorPedido();
        btnMandarCocina.setDisable(true);
        List<ItemOrden> copiaArticulos = List.copyOf(articulos);
        boolean cobrarIva = aplicaIva;

        if (modoEdicion) {
            Async.ejecutar(
                    () -> ventaService.agregarArticulos(ordenAEditarId, copiaArticulos, cobrarIva),
                    ordenActualizada -> cerrarVentana(),
                    error -> {
                        btnMandarCocina.setDisable(false);
                        mostrarErrorPedido("No se pudo agregar los artículos al pedido. Intenta de nuevo.");
                    }
            );
            return;
        }

        Async.ejecutar(
                () -> ventaService.crearOrdenDomicilio(sesion.getUsuarioActivo().getId(), clienteId, copiaArticulos,
                        cobrarIva, distanciaKmActual, costoEnvioCalculado),
                ordenCreada -> cerrarVentana(),
                error -> {
                    btnMandarCocina.setDisable(false);
                    mostrarErrorPedido("No se pudo mandar el pedido a cocina. Intenta de nuevo.");
                }
        );
    }

    /** Esta pantalla es una ventana emergente (ver PedidosController.abrirMenuPedido): al terminar, se cierra sola. */
    private void cerrarVentana() {
        ((Stage) btnMandarCocina.getScene().getWindow()).close();
    }

    private void mostrarErrorPedido(String mensaje) {
        lblErrorPedido.setText("⚠ " + mensaje);
        lblErrorPedido.setVisible(true);
        lblErrorPedido.setManaged(true);
    }

    private void ocultarErrorPedido() {
        lblErrorPedido.setVisible(false);
        lblErrorPedido.setManaged(false);
    }

    private record DatosIniciales(List<Producto> productos, String nombreCliente, BigDecimal distanciaKm,
                                   BigDecimal costoEnvio, boolean aplicaIva, List<DetalleOrden> articulosExistentes,
                                   BigDecimal totalExistente) {
    }
}
