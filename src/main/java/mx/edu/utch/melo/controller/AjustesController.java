package mx.edu.utch.melo.controller;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;
import mx.edu.utch.melo.async.Async;
import mx.edu.utch.melo.model.Categoria;
import mx.edu.utch.melo.model.Producto;
import mx.edu.utch.melo.model.Rol;
import mx.edu.utch.melo.model.Sucursal;
import mx.edu.utch.melo.model.TipoDescuento;
import mx.edu.utch.melo.nav.Pantalla;
import mx.edu.utch.melo.service.CategoriaService;
import mx.edu.utch.melo.service.DescuentoService;
import mx.edu.utch.melo.service.ProductoService;
import mx.edu.utch.melo.service.SucursalService;
import mx.edu.utch.melo.service.UsuarioService;
import mx.edu.utch.melo.sesion.SesionActual;
import mx.edu.utch.melo.util.Dinero;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Datos de la sucursal activa (solo lectura) y alta de productos para el Menú. */
public class AjustesController {

    private static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger(AjustesController.class.getName());

    @FXML
    private Label lblNombre;

    @FXML
    private Label lblDireccion;

    @FXML
    private Label lblTelefono;

    @FXML
    private Label lblTarifaBase;

    @FXML
    private Label lblTarifaPorKm;

    @FXML
    private Label lblEstado;

    @FXML
    private CheckBox chkAplicaIva;

    @FXML
    private Label lblErrorIva;

    @FXML
    private TextField txtNombreProducto;

    @FXML
    private TextArea txtDescripcionProducto;

    @FXML
    private TextField txtPrecioProducto;

    @FXML
    private TextField txtCantidadInicial;

    @FXML
    private ComboBox<Categoria> comboCategoriaProducto;

    @FXML
    private TextField txtNuevaCategoria;

    @FXML
    private Button btnAgregarCategoria;

    @FXML
    private Button btnSeleccionarImagen;

    @FXML
    private Label lblImagenSeleccionada;

    @FXML
    private Label lblErrorProducto;

    @FXML
    private Label lblExitoProducto;

    @FXML
    private Button btnGuardarProducto;

    @FXML
    private VBox listaProductosExistentes;

    @FXML
    private TextField txtNombrePersonal;

    @FXML
    private TextField txtPinPersonal;

    @FXML
    private ComboBox<Rol> comboRolPersonal;

    @FXML
    private Label lblErrorPersonal;

    @FXML
    private Label lblExitoPersonal;

    @FXML
    private Button btnRegistrarPersonal;

    @FXML
    private TextField txtPorcentajeEmpleado;

    @FXML
    private TextField txtPorcentajeCortesia;

    @FXML
    private TextField txtPorcentajePromocion;

    @FXML
    private TextField txtPorcentajeAjuste;

    @FXML
    private Label lblErrorDescuentos;

    @FXML
    private Label lblExitoDescuentos;

    @FXML
    private Button btnGuardarDescuentos;

    @FXML
    private SidebarController sidebarController;

    private final SucursalService sucursalService;
    private final ProductoService productoService;
    private final CategoriaService categoriaService;
    private final UsuarioService usuarioService;
    private final DescuentoService descuentoService;
    private final SesionActual sesion;

    /** Foto elegida con el FileChooser antes de guardar; null si no se eligió ninguna (ver onSeleccionarImagen). */
    private File imagenSeleccionada;

    /** Sucursal activa ya cargada (ver mostrarSucursal), para poder guardar el interruptor de IVA sin recargarla. */
    private Sucursal sucursalActual;

    public AjustesController(SucursalService sucursalService, ProductoService productoService,
                              CategoriaService categoriaService, UsuarioService usuarioService,
                              DescuentoService descuentoService, SesionActual sesion) {
        this.sucursalService = sucursalService;
        this.productoService = productoService;
        this.categoriaService = categoriaService;
        this.usuarioService = usuarioService;
        this.descuentoService = descuentoService;
        this.sesion = sesion;
    }

    @FXML
    void initialize() {
        sidebarController.activar(Pantalla.AJUSTES);
        cargarDescuentos();
        comboCategoriaProducto.setConverter(new StringConverter<>() {
            @Override
            public String toString(Categoria categoria) {
                return categoria == null ? "" : categoria.getNombre();
            }

            @Override
            public Categoria fromString(String texto) {
                return null;
            }
        });
        comboRolPersonal.setConverter(new StringConverter<>() {
            @Override
            public String toString(Rol rol) {
                return rol == null ? "" : rol.getEtiqueta();
            }

            @Override
            public Rol fromString(String texto) {
                return null;
            }
        });
        comboRolPersonal.setItems(FXCollections.observableArrayList(Rol.values()));
        comboRolPersonal.getSelectionModel().select(Rol.MESERO);
        cargarSucursal();
        cargarCategorias();
        cargarProductosExistentes();
    }

    private void cargarSucursal() {
        Async.ejecutar(
                () -> sucursalService.obtenerPorId(sesion.getSucursalActivaId()),
                this::mostrarSucursal,
                error -> mostrarError()
        );
    }

    private void mostrarSucursal(Sucursal sucursal) {
        this.sucursalActual = sucursal;
        lblNombre.setText(sucursal.getNombre());
        lblDireccion.setText(sucursal.getDireccionCompleta());
        lblTelefono.setText(sucursal.getTelefono() == null || sucursal.getTelefono().isBlank() ? "—" : sucursal.getTelefono());
        lblTarifaBase.setText(Dinero.formatear(sucursal.getTarifaBaseEnvio()));
        lblTarifaPorKm.setText(Dinero.formatear(sucursal.getTarifaPorKm()) + " / km");
        lblEstado.setText(sucursal.isActiva() ? "Activa" : "Inactiva");
        lblEstado.getStyleClass().setAll("badge", sucursal.isActiva() ? "badge-success" : "badge-muted");
        chkAplicaIva.setSelected(sucursal.isAplicaIva());
    }

    private void mostrarError() {
        lblNombre.setText("No se pudo cargar la sucursal");
        lblDireccion.setText("Revisa la conexión con la base de datos.");
    }

    /** Cada restaurante decide si cobra IVA o no (ver CLAUDE.md, sección Precios e IVA); se guarda al instante. */
    @FXML
    void onToggleAplicaIva() {
        if (sucursalActual == null) {
            return;
        }
        boolean nuevoValor = chkAplicaIva.isSelected();
        ocultarErrorIva();
        chkAplicaIva.setDisable(true);

        Async.ejecutar(
                () -> sucursalService.actualizarAplicaIva(sesion.getUsuarioActivo().getRol(), sucursalActual, nuevoValor),
                exito -> chkAplicaIva.setDisable(false),
                error -> {
                    chkAplicaIva.setDisable(false);
                    sucursalActual.setAplicaIva(!nuevoValor);
                    chkAplicaIva.setSelected(!nuevoValor);
                    mostrarErrorIva("No se pudo guardar. Intenta de nuevo.");
                }
        );
    }

    private void mostrarErrorIva(String mensaje) {
        lblErrorIva.setText("⚠ " + mensaje);
        lblErrorIva.setVisible(true);
        lblErrorIva.setManaged(true);
    }

    private void ocultarErrorIva() {
        lblErrorIva.setVisible(false);
        lblErrorIva.setManaged(false);
    }

    /** Porcentajes vigentes de cada categoría (ver DescuentoService) -- ya no una constante fija en TipoDescuento. */
    private void cargarDescuentos() {
        Async.ejecutar(
                descuentoService::obtenerPorcentajes,
                this::mostrarDescuentos,
                error -> mostrarErrorDescuentos("No se pudieron cargar los porcentajes. Revisa la conexión con la base de datos.")
        );
    }

    private void mostrarDescuentos(Map<TipoDescuento, BigDecimal> porcentajes) {
        txtPorcentajeEmpleado.setText(comoEnteroPorcentaje(porcentajes.get(TipoDescuento.EMPLEADO)));
        txtPorcentajeCortesia.setText(comoEnteroPorcentaje(porcentajes.get(TipoDescuento.CORTESIA)));
        txtPorcentajePromocion.setText(comoEnteroPorcentaje(porcentajes.get(TipoDescuento.PROMOCION)));
        txtPorcentajeAjuste.setText(comoEnteroPorcentaje(porcentajes.get(TipoDescuento.AJUSTE)));
    }

    /** BigDecimal (0-1, ej. 0.20) <-> texto de porcentaje entero (ej. "20") para el campo en pantalla. */
    private String comoEnteroPorcentaje(BigDecimal fraccion) {
        if (fraccion == null) {
            return "";
        }
        return fraccion.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).toPlainString();
    }

    private Optional<BigDecimal> parsearPorcentaje(String texto) {
        if (texto == null || texto.isBlank()) {
            return Optional.empty();
        }
        try {
            BigDecimal entero = new BigDecimal(texto.trim());
            if (entero.signum() < 0 || entero.compareTo(BigDecimal.valueOf(100)) > 0) {
                return Optional.empty();
            }
            return Optional.of(entero.divide(BigDecimal.valueOf(100)));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** Guarda las 4 categorías de una sola vez -- son pocos campos relacionados, no amerita un botón por fila. */
    @FXML
    void onGuardarDescuentos() {
        Optional<BigDecimal> empleado = parsearPorcentaje(txtPorcentajeEmpleado.getText());
        Optional<BigDecimal> cortesia = parsearPorcentaje(txtPorcentajeCortesia.getText());
        Optional<BigDecimal> promocion = parsearPorcentaje(txtPorcentajePromocion.getText());
        Optional<BigDecimal> ajuste = parsearPorcentaje(txtPorcentajeAjuste.getText());

        if (empleado.isEmpty() || cortesia.isEmpty() || promocion.isEmpty() || ajuste.isEmpty()) {
            mostrarErrorDescuentos("Cada porcentaje debe ser un número entre 0 y 100.");
            return;
        }

        ocultarMensajesDescuentos();
        btnGuardarDescuentos.setDisable(true);
        Rol rol = sesion.getUsuarioActivo().getRol();
        Map<TipoDescuento, BigDecimal> aGuardar = Map.of(
                TipoDescuento.EMPLEADO, empleado.get(),
                TipoDescuento.CORTESIA, cortesia.get(),
                TipoDescuento.PROMOCION, promocion.get(),
                TipoDescuento.AJUSTE, ajuste.get()
        );

        Async.ejecutar(
                () -> {
                    aGuardar.forEach((tipo, porcentaje) -> descuentoService.actualizarPorcentaje(rol, tipo, porcentaje));
                    return true;
                },
                exito -> {
                    btnGuardarDescuentos.setDisable(false);
                    mostrarExitoDescuentos("Porcentajes de descuento actualizados.");
                },
                error -> {
                    btnGuardarDescuentos.setDisable(false);
                    mostrarErrorDescuentos("No se pudieron guardar los porcentajes. Intenta de nuevo.");
                }
        );
    }

    private void mostrarErrorDescuentos(String mensaje) {
        lblExitoDescuentos.setVisible(false);
        lblExitoDescuentos.setManaged(false);
        lblErrorDescuentos.setText("⚠ " + mensaje);
        lblErrorDescuentos.setVisible(true);
        lblErrorDescuentos.setManaged(true);
    }

    private void mostrarExitoDescuentos(String mensaje) {
        lblErrorDescuentos.setVisible(false);
        lblErrorDescuentos.setManaged(false);
        lblExitoDescuentos.setText("✔ " + mensaje);
        lblExitoDescuentos.setVisible(true);
        lblExitoDescuentos.setManaged(true);
    }

    private void ocultarMensajesDescuentos() {
        lblErrorDescuentos.setVisible(false);
        lblErrorDescuentos.setManaged(false);
        lblExitoDescuentos.setVisible(false);
        lblExitoDescuentos.setManaged(false);
    }

    private void cargarCategorias() {
        cargarCategorias(null);
    }

    /** idASeleccionar: útil tras crear una categoría nueva, para dejarla ya elegida en el combo. */
    private void cargarCategorias(Integer idASeleccionar) {
        Async.ejecutar(
                categoriaService::obtenerTodos,
                categorias -> {
                    comboCategoriaProducto.setItems(FXCollections.observableArrayList(categorias));
                    Optional<Categoria> aSeleccionar = categorias.stream()
                            .filter(c -> idASeleccionar != null && c.getId() == idASeleccionar)
                            .findFirst();
                    if (aSeleccionar.isPresent()) {
                        comboCategoriaProducto.getSelectionModel().select(aSeleccionar.get());
                    } else if (!categorias.isEmpty()) {
                        comboCategoriaProducto.getSelectionModel().selectFirst();
                    }
                },
                error -> LOG.log(java.util.logging.Level.WARNING, "No se pudieron cargar las categorías", error)
        );
    }

    /** Cada restaurante puede tener sus propias categorías de menú, no solo "General". */
    @FXML
    void onAgregarCategoria() {
        String nombre = txtNuevaCategoria.getText();
        if (nombre == null || nombre.isBlank()) {
            mostrarErrorProducto("Captura el nombre de la nueva categoría.");
            return;
        }

        ocultarMensajesProducto();
        btnAgregarCategoria.setDisable(true);

        Async.ejecutar(
                () -> categoriaService.crear(sesion.getUsuarioActivo().getRol(), nombre),
                categoriaCreada -> {
                    btnAgregarCategoria.setDisable(false);
                    txtNuevaCategoria.clear();
                    mostrarExitoProducto("Categoría \"" + categoriaCreada.getNombre() + "\" creada.");
                    cargarCategorias(categoriaCreada.getId());
                },
                error -> {
                    btnAgregarCategoria.setDisable(false);
                    mostrarErrorProducto("No se pudo crear la categoría (¿ya existe una con ese nombre?).");
                }
        );
    }

    @FXML
    void onSeleccionarImagen() {
        FileChooser selector = new FileChooser();
        selector.setTitle("Elegir foto del producto");
        selector.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Imágenes", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.webp"));

        File elegido = selector.showOpenDialog(btnSeleccionarImagen.getScene().getWindow());
        if (elegido != null) {
            imagenSeleccionada = elegido;
            lblImagenSeleccionada.setText(elegido.getName());
        }
    }

    @FXML
    void onGuardarProducto() {
        String nombre = txtNombreProducto.getText();
        Optional<BigDecimal> precio = parsearPrecio(txtPrecioProducto.getText());
        Optional<Integer> cantidadInicial = parsearCantidad(txtCantidadInicial.getText());
        Categoria categoria = comboCategoriaProducto.getValue();

        if (nombre == null || nombre.isBlank()) {
            mostrarErrorProducto("Captura el nombre del producto.");
            return;
        }
        if (precio.isEmpty()) {
            mostrarErrorProducto("Captura un precio válido.");
            return;
        }
        if (cantidadInicial.isEmpty()) {
            mostrarErrorProducto("Captura la cantidad inicial en existencia (puede ser 0).");
            return;
        }
        if (categoria == null) {
            mostrarErrorProducto("Selecciona una categoría.");
            return;
        }

        ocultarMensajesProducto();
        btnGuardarProducto.setDisable(true);

        String descripcion = txtDescripcionProducto.getText();
        int categoriaId = categoria.getId();
        BigDecimal precioValor = precio.get();
        int cantidadValor = cantidadInicial.get();
        File imagen = imagenSeleccionada;

        Async.ejecutar(
                () -> productoService.crear(sesion.getUsuarioActivo().getRol(), sesion.getSucursalActivaId(),
                        categoriaId, nombre, descripcion, precioValor, cantidadValor, imagen),
                productoCreado -> {
                    btnGuardarProducto.setDisable(false);
                    limpiarFormularioProducto();
                    mostrarExitoProducto("\"" + productoCreado.getNombre() + "\" se agregó al Menú.");
                    cargarProductosExistentes();
                },
                error -> {
                    btnGuardarProducto.setDisable(false);
                    mostrarErrorProducto("No se pudo guardar el producto. Intenta de nuevo.");
                }
        );
    }

    private Optional<BigDecimal> parsearPrecio(String texto) {
        if (texto == null || texto.isBlank()) {
            return Optional.empty();
        }
        try {
            BigDecimal precio = new BigDecimal(texto.trim());
            return precio.compareTo(BigDecimal.ZERO) > 0 ? Optional.of(precio) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private Optional<Integer> parsearCantidad(String texto) {
        if (texto == null || texto.isBlank()) {
            return Optional.empty();
        }
        try {
            int cantidad = Integer.parseInt(texto.trim());
            return cantidad >= 0 ? Optional.of(cantidad) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private void limpiarFormularioProducto() {
        txtNombreProducto.clear();
        txtDescripcionProducto.clear();
        txtPrecioProducto.clear();
        txtCantidadInicial.clear();
        imagenSeleccionada = null;
        lblImagenSeleccionada.setText("Sin imagen seleccionada");
    }

    private void cargarProductosExistentes() {
        Async.ejecutar(
                () -> productoService.obtenerPorSucursal(sesion.getSucursalActivaId()),
                this::renderizarProductosExistentes,
                error -> LOG.log(java.util.logging.Level.WARNING, "No se pudieron cargar los productos existentes", error)
        );
    }

    private void renderizarProductosExistentes(List<Producto> productos) {
        if (productos.isEmpty()) {
            Label etiqueta = new Label("Todavía no hay productos registrados en esta sucursal.");
            etiqueta.getStyleClass().add("text-muted");
            listaProductosExistentes.getChildren().setAll(etiqueta);
            return;
        }
        listaProductosExistentes.getChildren().setAll(productos.stream().map(this::construirFilaProductoExistente).toList());
    }

    private HBox construirFilaProductoExistente(Producto producto) {
        Label lblNombre = new Label(producto.getNombre());
        lblNombre.getStyleClass().add("text-body-strong");

        Label lblPrecio = new Label(Dinero.formatear(producto.getPrecio()));
        lblPrecio.getStyleClass().add("text-muted");

        VBox columnaNombre = new VBox(2, lblNombre, lblPrecio);
        HBox.setHgrow(columnaNombre, Priority.ALWAYS);

        Button btnEliminar = new Button();
        btnEliminar.getStyleClass().add("icon-button");
        btnEliminar.setGraphic(new FontIcon("mdi2t-trash-can-outline"));
        btnEliminar.setOnAction(e -> confirmarEliminarProducto(producto));

        HBox fila = new HBox(14, columnaNombre, btnEliminar);
        fila.setAlignment(Pos.CENTER_LEFT);
        fila.getStyleClass().add("order-card");
        return fila;
    }

    private void confirmarEliminarProducto(Producto producto) {
        Alert confirmacion = new Alert(Alert.AlertType.CONFIRMATION,
                "¿Eliminar \"" + producto.getNombre() + "\" del Menú? Esta acción no se puede deshacer.",
                ButtonType.OK, ButtonType.CANCEL);
        confirmacion.setHeaderText(null);
        confirmacion.showAndWait().filter(boton -> boton == ButtonType.OK)
                .ifPresent(boton -> eliminarProducto(producto));
    }

    private void eliminarProducto(Producto producto) {
        Async.ejecutar(
                () -> productoService.eliminar(sesion.getUsuarioActivo().getRol(), producto.getId()),
                exito -> cargarProductosExistentes(),
                error -> mostrarErrorProducto(
                        "No se pudo eliminar \"" + producto.getNombre() + "\": probablemente ya tiene pedidos registrados.")
        );
    }

    private void mostrarErrorProducto(String mensaje) {
        lblExitoProducto.setVisible(false);
        lblExitoProducto.setManaged(false);
        lblErrorProducto.setText("⚠ " + mensaje);
        lblErrorProducto.setVisible(true);
        lblErrorProducto.setManaged(true);
    }

    private void mostrarExitoProducto(String mensaje) {
        lblErrorProducto.setVisible(false);
        lblErrorProducto.setManaged(false);
        lblExitoProducto.setText("✔ " + mensaje);
        lblExitoProducto.setVisible(true);
        lblExitoProducto.setManaged(true);
    }

    private void ocultarMensajesProducto() {
        lblErrorProducto.setVisible(false);
        lblErrorProducto.setManaged(false);
        lblExitoProducto.setVisible(false);
        lblExitoProducto.setManaged(false);
    }

    /** Alta de personal con su PIN de acceso a la terminal (ver CambiarUsuarioController, que lo consume). */
    @FXML
    void onRegistrarPersonal() {
        String nombre = txtNombrePersonal.getText();
        String pin = txtPinPersonal.getText();
        Rol rol = comboRolPersonal.getValue();

        if (nombre == null || nombre.isBlank()) {
            mostrarErrorPersonal("Captura el nombre del empleado.");
            return;
        }
        if (pin == null || pin.isBlank() || !pin.chars().allMatch(Character::isDigit)) {
            mostrarErrorPersonal("El PIN debe ser solo números.");
            return;
        }
        if (rol == null) {
            mostrarErrorPersonal("Selecciona un rol.");
            return;
        }

        ocultarMensajesPersonal();
        btnRegistrarPersonal.setDisable(true);
        Rol rolValor = rol;

        Async.ejecutar(
                () -> usuarioService.registrar(sesion.getUsuarioActivo().getRol(), sesion.getSucursalActivaId(),
                        nombre, pin, rolValor),
                usuarioCreado -> {
                    btnRegistrarPersonal.setDisable(false);
                    limpiarFormularioPersonal();
                    mostrarExitoPersonal("\"" + usuarioCreado.getNombre() + "\" se registró correctamente.");
                },
                error -> {
                    btnRegistrarPersonal.setDisable(false);
                    mostrarErrorPersonal("No se pudo registrar (¿el PIN ya está en uso en esta sucursal?).");
                }
        );
    }

    private void limpiarFormularioPersonal() {
        txtNombrePersonal.clear();
        txtPinPersonal.clear();
        comboRolPersonal.getSelectionModel().select(Rol.MESERO);
    }

    private void mostrarErrorPersonal(String mensaje) {
        lblExitoPersonal.setVisible(false);
        lblExitoPersonal.setManaged(false);
        lblErrorPersonal.setText("⚠ " + mensaje);
        lblErrorPersonal.setVisible(true);
        lblErrorPersonal.setManaged(true);
    }

    private void mostrarExitoPersonal(String mensaje) {
        lblErrorPersonal.setVisible(false);
        lblErrorPersonal.setManaged(false);
        lblExitoPersonal.setText("✔ " + mensaje);
        lblExitoPersonal.setVisible(true);
        lblExitoPersonal.setManaged(true);
    }

    private void ocultarMensajesPersonal() {
        lblErrorPersonal.setVisible(false);
        lblErrorPersonal.setManaged(false);
        lblExitoPersonal.setVisible(false);
        lblExitoPersonal.setManaged(false);
    }
}
