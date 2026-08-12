package mx.edu.utch.melo.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import mx.edu.utch.melo.app.AppContext;
import mx.edu.utch.melo.async.Async;
import mx.edu.utch.melo.dao.ClienteDAO;
import mx.edu.utch.melo.geo.Coordenadas;
import mx.edu.utch.melo.geo.Geocodificador;
import mx.edu.utch.melo.model.Cliente;
import mx.edu.utch.melo.nav.Pantalla;
import mx.edu.utch.melo.validation.ClienteValidator;

import java.util.Optional;

public class RegisterClientController {

    @FXML
    private TextField txtNombre;

    @FXML
    private TextField txtPhone;

    @FXML
    private TextField txtAddress;

    @FXML
    private Label lblError;

    @FXML
    private Button btnGuardar;

    @FXML
    private SidebarController sidebarController;

    private final ClienteDAO clienteDAO;
    private final Geocodificador geocodificador;

    public RegisterClientController(AppContext contexto) {
        this.clienteDAO = contexto.getClienteDAO();
        this.geocodificador = contexto.getGeocodificador();
    }

    @FXML
    void initialize() {
        sidebarController.activar(Pantalla.CLIENTES);
    }

    @FXML
    void onGuardarCliente() {
        Optional<String> error = ClienteValidator.validar(txtNombre.getText(), txtPhone.getText(), txtAddress.getText());
        if (error.isPresent()) {
            mostrarError(error.get());
            return;
        }
        ocultarError();
        guardarClienteEnSegundoPlano();
    }

    @FXML
    void onCancelar() {
        ocultarError();
        limpiarFormulario();
    }

    private void guardarClienteEnSegundoPlano() {
        String nombre = txtNombre.getText();
        String telefono = txtPhone.getText();
        String direccion = txtAddress.getText();

        btnGuardar.setDisable(true);
        Async.ejecutar(
                () -> crearCliente(nombre, telefono, direccion),
                clienteCreado -> {
                    btnGuardar.setDisable(false);
                    limpiarFormulario();
                },
                excepcion -> {
                    btnGuardar.setDisable(false);
                    if (excepcion instanceof ClienteDuplicadoException) {
                        mostrarError("Ya existe un cliente registrado con ese teléfono.");
                    } else {
                        mostrarError("No se pudo guardar el cliente. Intenta de nuevo.");
                    }
                }
        );
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
            geocodificador.geocodificar(direccion).ifPresent(coordenadas -> {
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
    }

    private static class ClienteDuplicadoException extends RuntimeException {
        ClienteDuplicadoException(String telefono) {
            super("Cliente duplicado: " + telefono);
        }
    }
}
