package mx.edu.utch.melo.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import mx.edu.utch.melo.app.AppContext;
import mx.edu.utch.melo.async.Async;
import mx.edu.utch.melo.dao.ClienteDAO;
import mx.edu.utch.melo.dao.SucursalDAO;
import mx.edu.utch.melo.geo.Coordenadas;
import mx.edu.utch.melo.geo.Geocodificador;
import mx.edu.utch.melo.geo.Ruta;
import mx.edu.utch.melo.geo.ServicioRutas;
import mx.edu.utch.melo.model.Cliente;
import mx.edu.utch.melo.model.Sucursal;
import mx.edu.utch.melo.nav.Navigator;
import mx.edu.utch.melo.nav.Pantalla;
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
    private final ClienteDAO clienteDAO;
    private final SucursalDAO sucursalDAO;
    private final Geocodificador geocodificador;
    private final ServicioRutas servicioRutas;
    private final SesionActual sesion;

    /** Cliente encontrado al buscar por teléfono (ver onBuscarPorTelefono); null si es un cliente nuevo. */
    private Cliente clienteEncontrado;

    /** Distancia real calculada al presionar "Ubicar" (ver onUbicar); null si no se ha calculado ninguna ruta. */
    private BigDecimal distanciaCalculadaKm;

    public PedidosController(AppContext contexto) {
        this.navigator = contexto.getNavigator();
        this.clienteDAO = contexto.getClienteDAO();
        this.sucursalDAO = contexto.getSucursalDAO();
        this.geocodificador = contexto.getGeocodificador();
        this.servicioRutas = contexto.getServicioRutas();
        this.sesion = contexto.getSesion();
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
                () -> clienteDAO.obtenerPorTelefono(telefono),
                this::autocompletarSiExiste,
                error -> { }
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
                () -> calcularRuta(direccion),
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


    private Optional<Ruta> calcularRuta(String direccion) {
        Sucursal sucursal = sucursalDAO.obtenerPorId(sesion.getSucursalActivaId()).orElseThrow();
        if (sucursal.getLatitud() == null || sucursal.getLongitud() == null) {
            throw new SucursalSinCoordenadasException();
        }
        Coordenadas origen = new Coordenadas(sucursal.getLatitud(), sucursal.getLongitud());

        Optional<Coordenadas> destino = geocodificador.geocodificar(completarDireccion(direccion, sucursal), origen);
        if (destino.isEmpty()) {
            return Optional.empty();
        }
        return servicioRutas.calcularRuta(origen, destino.get());
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
                () -> crearCliente(nombre, telefono, direccion),
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

    /** Se ejecuta en un hilo aparte (ver Async): valida duplicado, geocodifica la dirección y persiste. */
    private Cliente crearCliente(String nombre, String telefono, String direccion) {
        if (clienteDAO.obtenerPorTelefono(telefono).isPresent()) {
            throw new ClienteDuplicadoException(telefono);
        }

        Cliente cliente = new Cliente();
        cliente.setNombre(nombre);
        cliente.setTelefono(telefono);
        cliente.setDireccion(direccion);

        // La geocodificación es "best effort": si Mapbox no está configurado,
        // no encuentra la dirección, o falla la red, igual se guarda el
        // cliente -- solo con lat/lng en NULL. Nunca debe bloquear el registro.
        try {
            Sucursal sucursal = sucursalDAO.obtenerPorId(sesion.getSucursalActivaId()).orElse(null);
            String direccionParaGeocodificar = sucursal == null ? direccion : completarDireccion(direccion, sucursal);
            Coordenadas cercaDe = sucursal == null || sucursal.getLatitud() == null || sucursal.getLongitud() == null
                    ? null
                    : new Coordenadas(sucursal.getLatitud(), sucursal.getLongitud());
            geocodificador.geocodificar(direccionParaGeocodificar, cercaDe).ifPresent(coordenadas -> {
                cliente.setLatitud(coordenadas.getLatitud());
                cliente.setLongitud(coordenadas.getLongitud());
            });
        } catch (RuntimeException geocodificacionFallida) {
            // Sin coordenadas por ahora; el cliente se guarda de todas formas.
        }

        return clienteDAO.crear(cliente);
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

    private static class ClienteDuplicadoException extends RuntimeException {
        ClienteDuplicadoException(String telefono) {
            super("Cliente duplicado: " + telefono);
        }
    }

    private static class SucursalSinCoordenadasException extends RuntimeException {
        SucursalSinCoordenadasException() {
            super("La sucursal activa no tiene latitud/longitud configuradas.");
        }
    }
}
