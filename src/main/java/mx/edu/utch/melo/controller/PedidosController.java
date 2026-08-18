package mx.edu.utch.melo.controller;

import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;
import mx.edu.utch.melo.async.Async;
import mx.edu.utch.melo.dao.SucursalDAO;
import mx.edu.utch.melo.geo.Ruta;
import mx.edu.utch.melo.model.Cliente;
import mx.edu.utch.melo.model.EstadoOrden;
import mx.edu.utch.melo.model.Orden;
import mx.edu.utch.melo.model.Sucursal;
import mx.edu.utch.melo.model.TipoOrden;
import mx.edu.utch.melo.nav.Navigator;
import mx.edu.utch.melo.nav.Pantalla;
import mx.edu.utch.melo.service.ClienteService;
import mx.edu.utch.melo.service.ClienteService.ClienteDuplicadoException;
import mx.edu.utch.melo.service.ClienteService.SucursalSinCoordenadasException;
import mx.edu.utch.melo.service.DeliveryService;
import mx.edu.utch.melo.sesion.SesionActual;
import mx.edu.utch.melo.util.Dinero;
import mx.edu.utch.melo.validation.ClienteValidator;
import mx.edu.utch.melo.view.EstadoVacioFactory;

import java.awt.Desktop;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;


public class PedidosController {

    private static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger(PedidosController.class.getName());

    @FXML
    private BorderPane raiz;

    @FXML
    private TextField txtNombre;

    @FXML
    private TextField txtPhone;

    @FXML
    private TextField txtAddress;

    /** Envolturas de los 3 campos de arriba, para marcar cuál tiene el foco (ver auditoría UI/UX,
     * marcarFocoEnWrapper) -- antes ningún campo del formulario tenía señal visual de foco más
     * allá del cursor de texto del propio TextField. */
    @FXML
    private HBox envolturaPhone;

    @FXML
    private HBox envolturaNombre;

    @FXML
    private HBox envolturaAddress;

    @FXML
    private Button btnUbicar;

    @FXML
    private ImageView imgMapa;

    @FXML
    private VBox mapaVacio;

    @FXML
    private Label lblDistancia;

    @FXML
    private Label lblError;

    @FXML
    private Button btnTomarPedido;

    @FXML
    private VBox contenedorParaRecoger;

    @FXML
    private Label lblTotalParaRecoger;

    @FXML
    private Label lblSumaParaRecoger;

    @FXML
    private TextField txtBuscarParaRecoger;

    @FXML
    private SidebarController sidebarController;

    private final Navigator navigator;
    private final ClienteService clienteService;
    private final SucursalDAO sucursalDAO;
    private final SesionActual sesion;
    private final DeliveryService deliveryService;

    /** Cliente encontrado al buscar por teléfono (ver onBuscarPorTelefono); null si es un cliente nuevo. */
    private Cliente clienteEncontrado;

    /** Distancia real calculada al presionar "Ubicar" (ver onUbicar); null si no se ha calculado ninguna ruta. */
    private BigDecimal distanciaCalculadaKm;

    /** Último resultado cargado de BD para el panel "Pedidos Para Recoger" (ver cargarPedidosParaRecoger);
     * el filtro de búsqueda opera sobre esta copia sin volver a consultar (mismo patrón que DeliveryController). */
    private List<DeliveryService.PedidoActivo> ultimosDatosParaRecoger = List.of();

    public PedidosController(Navigator navigator, ClienteService clienteService, SucursalDAO sucursalDAO,
                              SesionActual sesion, DeliveryService deliveryService) {
        this.navigator = navigator;
        this.clienteService = clienteService;
        this.sucursalDAO = sucursalDAO;
        this.sesion = sesion;
        this.deliveryService = deliveryService;
    }

    @FXML
    void initialize() {
        sidebarController.activar(Pantalla.PEDIDOS);
        // Si edita el teléfono después de un autocompletado, ese match ya no aplica.
        txtPhone.textProperty().addListener((observable, anterior, actual) -> clienteEncontrado = null);
        // Si edita la dirección después de calcular una ruta, esa ruta ya no es válida.
        txtAddress.textProperty().addListener((observable, anterior, actual) -> ocultarMapa());

        marcarFocoEnWrapper(txtPhone, envolturaPhone);
        marcarFocoEnWrapper(txtNombre, envolturaNombre);
        marcarFocoEnWrapper(txtAddress, envolturaAddress);

        txtBuscarParaRecoger.textProperty().addListener((observable, antes, actual) -> aplicarFiltroParaRecoger());
        habilitarRecargarAlRegresarElFoco();
        cargarPedidosParaRecoger();
    }

    /** Refleja el foco del TextField en su envoltura (ver auditoría UI/UX, input-wrapper-focused
     * en styles.css): el TextField interior es transparente (.input-plain), así que JavaFX no
     * propaga :focused de un hijo a su contenedor por sí solo. */
    private void marcarFocoEnWrapper(TextField campo, HBox envoltura) {
        campo.focusedProperty().addListener((observable, antes, tieneFoco) -> {
            if (tieneFoco) {
                envoltura.getStyleClass().add("input-wrapper-focused");
            } else {
                envoltura.getStyleClass().remove("input-wrapper-focused");
            }
        });
    }


    @FXML
    void onBuscarPorTelefono() {
        String telefono = txtPhone.getText();
        if (telefono == null || telefono.isBlank()) {
            return;
        }
        Async.ejecutar(
                () -> clienteService.buscarPorTelefono(telefono),
                this::autocompletarSiExiste,
                error -> LOG.log(java.util.logging.Level.WARNING, "No se pudo buscar el cliente por teléfono", error)
        );
    }

    private void autocompletarSiExiste(Optional<Cliente> resultado) {
        resultado.ifPresent(cliente -> {
            clienteEncontrado = cliente;
            txtNombre.setText(cliente.getNombre());
            txtAddress.setText(cliente.getDireccion() == null ? "" : cliente.getDireccion());
        });
    }


    @FXML
    void onUbicar() {
        String direccion = txtAddress.getText();
        if (direccion == null || direccion.isBlank()) {
            mostrarError("Captura una dirección antes de ubicarla.");
            return;
        }
        ocultarError();
        btnUbicar.setDisable(true);

        Async.ejecutar(
                () -> clienteService.calcularRuta(direccion, sesion.getSucursalActivaId()),
                resultado -> {
                    btnUbicar.setDisable(false);
                    if (resultado.isEmpty()) {
                        mostrarError("No se pudo ubicar esa dirección en el mapa.");
                        return;
                    }
                    mostrarRuta(resultado.get());
                },
                error -> {
                    btnUbicar.setDisable(false);
                    if (error instanceof SucursalSinCoordenadasException) {
                        mostrarError("La sucursal activa no tiene coordenadas configuradas todavía (falta capturarlas en `sucursales`).");
                    } else {
                        mostrarError("No se pudo calcular la ruta. Verifica tu conexión e intenta de nuevo.");
                    }
                }
        );
    }


    private String completarDireccion(String direccion, Sucursal sucursal) {
        return direccion + ", " + sucursal.getCiudad() + ", " + sucursal.getEstado() + ", " + sucursal.getPais();
    }


    @FXML
    private void onVerEnGoogleMaps() {
        String direccion = txtAddress.getText();
        if (direccion == null || direccion.isBlank()) {
            mostrarError("Captura una dirección antes de verla en Google Maps.");
            return;
        }
        ocultarError();
        Async.ejecutar(
                () -> abrirGoogleMaps(direccion),
                exito -> { },
                error -> mostrarError("No se pudo abrir Google Maps.")
        );
    }


    private Boolean abrirGoogleMaps(String direccion) {
        Sucursal sucursal = sucursalDAO.obtenerPorId(sesion.getSucursalActivaId()).orElse(null);
        String direccionCompleta = sucursal == null ? direccion : completarDireccion(direccion, sucursal);
        String url = "https://www.google.com/maps/search/?api=1&query="
                + URLEncoder.encode(direccionCompleta, StandardCharsets.UTF_8);

        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            throw new IllegalStateException("Este equipo no puede abrir el navegador del sistema.");
        }
        try {
            Desktop.getDesktop().browse(URI.create(url));
            return Boolean.TRUE;
        } catch (IOException e) {
            throw new RuntimeException("No se pudo abrir el navegador.", e);
        }
    }

    private void mostrarRuta(Ruta ruta) {
        distanciaCalculadaKm = ruta.distanciaKm();
        imgMapa.setImage(new Image(ruta.urlMapaEstatico(), true));
        imgMapa.setVisible(true);
        imgMapa.setManaged(true);
        mapaVacio.setVisible(false);
        mapaVacio.setManaged(false);
        lblDistancia.setText(ruta.distanciaKm() + " km desde la sucursal.");
        lblDistancia.setVisible(true);
        lblDistancia.setManaged(true);
    }

    private void ocultarMapa() {
        distanciaCalculadaKm = null;
        imgMapa.setImage(null);
        imgMapa.setVisible(false);
        imgMapa.setManaged(false);
        mapaVacio.setVisible(true);
        mapaVacio.setManaged(true);
        lblDistancia.setVisible(false);
        lblDistancia.setManaged(false);
    }

    @FXML
    void onTomarPedido() {
        Optional<String> error = ClienteValidator.validar(txtNombre.getText(), txtPhone.getText(), txtAddress.getText());
        if (error.isPresent()) {
            mostrarError(error.get());
            return;
        }
        ocultarError();

        if (clienteEncontrado != null) {

            abrirMenuPedido(clienteEncontrado.getId());
            return;
        }
        tomarPedidoEnSegundoPlano();
    }

    @FXML
    void onCancelar() {
        ocultarError();
        limpiarFormulario();
    }

    private void tomarPedidoEnSegundoPlano() {
        String nombre = txtNombre.getText();
        String telefono = txtPhone.getText();
        String direccion = txtAddress.getText();

        btnTomarPedido.setDisable(true);
        Async.ejecutar(
                () -> clienteService.crear(nombre, telefono, direccion, sesion.getSucursalActivaId()),
                clienteCreado -> {
                    btnTomarPedido.setDisable(false);
                    abrirMenuPedido(clienteCreado.getId());
                },
                excepcion -> {
                    btnTomarPedido.setDisable(false);
                    if (excepcion instanceof ClienteDuplicadoException) {
                        mostrarError("Ya existe un cliente registrado con ese teléfono.");
                    } else {
                        mostrarError("No se pudo tomar el pedido. Intenta de nuevo.");
                    }
                }
        );
    }

    /** Deja el id del cliente y la distancia calculada para MenuPedidoController (ver SesionActual) y abre esa pantalla. */
    private void abrirMenuPedido(int clienteId) {
        sesion.setClienteEnProceso(clienteId);
        sesion.setDistanciaKmEnProceso(distanciaCalculadaKm);
        limpiarFormulario();
        navigator.abrirVentana(Pantalla.MENU_PEDIDO, "melo - Nuevo Pedido");
    }

    private void mostrarError(String mensaje) {
        lblError.setText("⚠ " + mensaje);
        lblError.setVisible(true);
        lblError.setManaged(true);
    }

    private void ocultarError() {
        lblError.setVisible(false);
        lblError.setManaged(false);
    }

    private void limpiarFormulario() {
        txtNombre.clear();
        txtPhone.clear();
        txtAddress.clear();
        clienteEncontrado = null;
        ocultarMapa();
    }

    // ===== Panel "Pedidos Para Recoger" (órdenes PARA_RECOGER activas, ver DeliveryService) =====
    //
    // Mismo patrón que DeliveryController (tarjeta con Agregar/Cobrar, búsqueda por # de pedido,
    // recarga al recuperar el foco), solo que filtrado a PARA_RECOGER en vez de DOMICILIO: son las
    // órdenes que esta misma pantalla crea cuando el mesero no calculó una ruta con "Ubicar" (ver
    // VentaService.crearOrdenDomicilio). Antes ninguna pantalla las mostraba.

    /**
     * Igual que DeliveryController.habilitarRecargarAlRegresarElFoco: "Agregar"/"Cobrar" abren una
     * ventana emergente sobre la orden de una tarjeta, y al recuperar el foco esa orden ya pudo
     * cambiar de estado, así que se recarga el panel.
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
                        cargarPedidosParaRecoger();
                    }
                });
            });
        });
    }

    private void cargarPedidosParaRecoger() {
        Async.ejecutar(
                this::construirDatosParaRecoger,
                this::renderizarParaRecoger,
                error -> mostrarErrorCargaParaRecoger()
        );
    }

    /** Se ejecuta en un hilo aparte (ver Async): la consulta agregada vive en DeliveryService, la UI se arma después. */
    private List<DeliveryService.PedidoActivo> construirDatosParaRecoger() {
        return deliveryService.obtenerPedidosActivos(TipoOrden.PARA_RECOGER);
    }

    private void renderizarParaRecoger(List<DeliveryService.PedidoActivo> datos) {
        ultimosDatosParaRecoger = datos;
        // Las estadísticas siempre reflejan TODOS los pedidos activos, no solo lo que el filtro
        // de búsqueda deja visible -- solo la lista de tarjetas se acota (ver aplicarFiltroParaRecoger).
        actualizarEstadisticasParaRecoger(datos);
        aplicarFiltroParaRecoger();
    }

    /** Filtra ultimosDatosParaRecoger por # de pedido sin volver a consultar la base de datos. */
    private void aplicarFiltroParaRecoger() {
        String filtro = txtBuscarParaRecoger.getText();
        List<DeliveryService.PedidoActivo> visibles = (filtro == null || filtro.isBlank())
                ? ultimosDatosParaRecoger
                : ultimosDatosParaRecoger.stream()
                        .filter(dato -> String.valueOf(dato.orden().getId()).contains(filtro.trim()))
                        .toList();

        if (visibles.isEmpty()) {
            mostrarMensajeParaRecoger(ultimosDatosParaRecoger.isEmpty()
                    ? "No hay pedidos para recoger activos."
                    : "Ningún pedido activo coincide con esa búsqueda.");
            return;
        }
        contenedorParaRecoger.getChildren().setAll(visibles.stream().map(this::construirTarjetaParaRecoger).toList());
    }

    private void actualizarEstadisticasParaRecoger(List<DeliveryService.PedidoActivo> datos) {
        lblTotalParaRecoger.setText(String.valueOf(datos.size()));
        BigDecimal sumaTotales = datos.stream()
                .map(dato -> dato.orden().getTotal())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        lblSumaParaRecoger.setText(Dinero.formatear(sumaTotales));
    }

    /**
     * Tarjeta adaptada a la columna angosta de esta pantalla (300px, contra los 340px de ancho fijo
     * de DeliveryController.construirTarjetaPedido): sin ancho fijo (ocupa el ancho disponible) y con
     * los botones "Agregar"/"Cobrar" en su propia fila en vez de compartir fila con el total, porque
     * a este ancho no entran el total y dos botones lado a lado sin verse apretados.
     */
    private VBox construirTarjetaParaRecoger(DeliveryService.PedidoActivo dato) {
        Orden orden = dato.orden();
        Cliente cliente = dato.cliente();

        Label lblCliente = new Label(cliente == null ? "Sin registrar" : cliente.getNombre());
        lblCliente.getStyleClass().add("text-body-strong");
        lblCliente.setWrapText(true);
        HBox.setHgrow(lblCliente, Priority.ALWAYS);

        Label lblEstado = new Label(descripcionEstado(orden.getEstado()));
        lblEstado.getStyleClass().addAll(claseBadgeEstado(orden.getEstado()).split(" "));

        HBox encabezado = new HBox(6, lblCliente, lblEstado);
        encabezado.setAlignment(Pos.CENTER_LEFT);

        Label lblFolio = new Label("Pedido #" + orden.getId());
        lblFolio.getStyleClass().add("text-muted");

        FontIcon iconoDireccion = new FontIcon("mdi2m-map-marker-outline");
        iconoDireccion.getStyleClass().add("text-muted");
        Label lblDireccion = new Label(cliente == null ? "Dirección no registrada" : cliente.getDireccion());
        lblDireccion.getStyleClass().add("text-muted");
        lblDireccion.setWrapText(true);
        HBox filaDireccion = new HBox(6, iconoDireccion, lblDireccion);
        filaDireccion.setAlignment(Pos.CENTER_LEFT);

        Label lblTotal = new Label(Dinero.formatear(orden.getTotal()));
        lblTotal.getStyleClass().add("text-price");

        Button btnAgregar = new Button("Agregar", new FontIcon("mdi2p-plus-circle-outline"));
        btnAgregar.getStyleClass().add("btn-card-action");
        btnAgregar.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(btnAgregar, Priority.ALWAYS);
        btnAgregar.setOnAction(evento -> onAgregarArticulosParaRecoger(orden));

        Button btnCobrar = new Button("Cobrar", new FontIcon("mdi2c-cash"));
        btnCobrar.getStyleClass().add("btn-card-action-accent");
        btnCobrar.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(btnCobrar, Priority.ALWAYS);
        btnCobrar.setOnAction(evento -> onCobrarParaRecoger(orden));

        HBox filaBotones = new HBox(8, btnAgregar, btnCobrar);
        filaBotones.setAlignment(Pos.CENTER_LEFT);

        VBox tarjeta = new VBox(8, encabezado, lblFolio, filaDireccion, lblTotal, filaBotones);
        tarjeta.getStyleClass().add("order-card");
        tarjeta.setMaxWidth(Double.MAX_VALUE);
        VBox.setMargin(tarjeta, new Insets(0));
        return tarjeta;
    }

    /** Reabre MenuPedido en modo edición sobre esta orden (ver SesionActual.setOrdenAEditar, VentaService.agregarArticulos). */
    private void onAgregarArticulosParaRecoger(Orden orden) {
        sesion.setOrdenAEditar(orden.getId());
        navigator.abrirVentana(Pantalla.MENU_PEDIDO, "melo - Agregar al Pedido");
    }

    /** Abre el cobro para esta orden -- mismo patrón que DeliveryController.onCobrar. */
    private void onCobrarParaRecoger(Orden orden) {
        sesion.setOrdenEnProceso(orden.getId());
        navigator.abrirVentana(Pantalla.ORDENES, "melo - Cobrar");
    }

    /** Igual que DeliveryController.descripcionEstado -- ver ese comentario para el porqué del formato. */
    private String descripcionEstado(EstadoOrden estado) {
        return switch (estado) {
            case PENDIENTE -> "Pendiente";
            case EN_PREPARACION -> "En Preparación";
            case LISTA -> "Lista para Recoger";
            case ENTREGADA -> "Entregada";
            case PAGADA, CANCELADA -> estado.name();
        };
    }

    private String claseBadgeEstado(EstadoOrden estado) {
        return switch (estado) {
            case LISTA, ENTREGADA -> "badge badge-success";
            case EN_PREPARACION -> "badge badge-accent";
            default -> "badge badge-muted";
        };
    }

    private void mostrarMensajeParaRecoger(String mensaje) {
        contenedorParaRecoger.getChildren().setAll(EstadoVacioFactory.crear("mdi2b-bag-personal-outline", mensaje, 250));
    }

    private void mostrarErrorCargaParaRecoger() {
        ultimosDatosParaRecoger = List.of();
        lblTotalParaRecoger.setText("—");
        lblSumaParaRecoger.setText("—");
        Label etiqueta = new Label("No se pudieron cargar los pedidos. Revisa la conexión con la base de datos.");
        etiqueta.getStyleClass().add("form-error");
        etiqueta.setWrapText(true);
        contenedorParaRecoger.getChildren().setAll(etiqueta);
    }
}
