package mx.edu.utch.melo.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import mx.edu.utch.melo.async.Async;
import mx.edu.utch.melo.dao.SucursalDAO;
import mx.edu.utch.melo.geo.Ruta;
import mx.edu.utch.melo.model.Cliente;
import mx.edu.utch.melo.model.Sucursal;
import mx.edu.utch.melo.nav.Navigator;
import mx.edu.utch.melo.nav.Pantalla;
import mx.edu.utch.melo.service.ClienteService;
import mx.edu.utch.melo.service.ClienteService.ClienteDuplicadoException;
import mx.edu.utch.melo.service.ClienteService.SucursalSinCoordenadasException;
import mx.edu.utch.melo.sesion.SesionActual;
import mx.edu.utch.melo.validation.ClienteValidator;

import java.awt.Desktop;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;


public class PedidosController {

    private static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger(PedidosController.class.getName());

    @FXML
    private TextField txtNombre;

    @FXML
    private TextField txtPhone;

    @FXML
    private TextField txtAddress;

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
    private SidebarController sidebarController;

    private final Navigator navigator;
    private final ClienteService clienteService;
    private final SucursalDAO sucursalDAO;
    private final SesionActual sesion;

    /** Cliente encontrado al buscar por teléfono (ver onBuscarPorTelefono); null si es un cliente nuevo. */
    private Cliente clienteEncontrado;

    /** Distancia real calculada al presionar "Ubicar" (ver onUbicar); null si no se ha calculado ninguna ruta. */
    private BigDecimal distanciaCalculadaKm;

    public PedidosController(Navigator navigator, ClienteService clienteService, SucursalDAO sucursalDAO,
                              SesionActual sesion) {
        this.navigator = navigator;
        this.clienteService = clienteService;
        this.sucursalDAO = sucursalDAO;
        this.sesion = sesion;
    }

    @FXML
    void initialize() {
        sidebarController.activar(Pantalla.PEDIDOS);
        // Si edita el teléfono después de un autocompletado, ese match ya no aplica.
        txtPhone.textProperty().addListener((observable, anterior, actual) -> clienteEncontrado = null);
        // Si edita la dirección después de calcular una ruta, esa ruta ya no es válida.
        txtAddress.textProperty().addListener((observable, anterior, actual) -> ocultarMapa());
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
}
