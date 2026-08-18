package mx.edu.utch.melo.controller;

import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import mx.edu.utch.melo.async.Async;
import mx.edu.utch.melo.dao.UsuarioDAO;
import mx.edu.utch.melo.model.Usuario;
import mx.edu.utch.melo.nav.Pantalla;
import mx.edu.utch.melo.sesion.SesionActual;

import java.util.List;
import java.util.Locale;

/** Vista de solo lectura: personal (usuarios) de la sucursal activa. */
public class PersonalController {

    @FXML
    private VBox listaPersonal;

    @FXML
    private SidebarController sidebarController;

    private final UsuarioDAO usuarioDAO;
    private final SesionActual sesion;

    public PersonalController(UsuarioDAO usuarioDAO, SesionActual sesion) {
        this.usuarioDAO = usuarioDAO;
        this.sesion = sesion;
    }

    @FXML
    void initialize() {
        sidebarController.activar(Pantalla.PERSONAL);
        cargarPersonal();
    }

    private void cargarPersonal() {
        Async.ejecutar(
                () -> usuarioDAO.obtenerPorSucursal(sesion.getSucursalActivaId()),
                this::renderizarPersonal,
                error -> mostrarErrorCarga()
        );
    }

    private void renderizarPersonal(List<Usuario> usuarios) {
        if (usuarios.isEmpty()) {
            mostrarMensaje("No hay personal registrado en esta sucursal.");
            return;
        }
        listaPersonal.getChildren().setAll(usuarios.stream().map(this::construirFilaUsuario).toList());
    }

    private HBox construirFilaUsuario(Usuario usuario) {
        StackPane avatar = new StackPane();
        avatar.getStyleClass().add("avatar");
        Label lblInicial = new Label(inicial(usuario.getNombre()));
        lblInicial.getStyleClass().add("avatar-label");
        avatar.getChildren().add(lblInicial);

        Label lblNombre = new Label(usuario.getNombre());
        lblNombre.getStyleClass().add("text-body-strong");

        Label lblRol = new Label(usuario.getRol().getEtiqueta());
        lblRol.getStyleClass().add("text-muted");

        VBox columnaNombre = new VBox(4, lblNombre, lblRol);
        HBox.setHgrow(columnaNombre, Priority.ALWAYS);

        Label lblEstado = new Label(usuario.isActivo() ? "Activo" : "Inactivo");
        lblEstado.getStyleClass().add(usuario.isActivo() ? "badge badge-success" : "badge badge-muted");

        HBox fila = new HBox(14, avatar, columnaNombre, lblEstado);
        fila.setAlignment(Pos.CENTER_LEFT);
        fila.getStyleClass().add("order-card");
        return fila;
    }

    private String inicial(String nombre) {
        return nombre == null || nombre.isBlank() ? "?" : nombre.substring(0, 1).toUpperCase(Locale.forLanguageTag("es-MX"));
    }

    private void mostrarMensaje(String mensaje) {
        Label etiqueta = new Label(mensaje);
        etiqueta.getStyleClass().add("text-muted");
        listaPersonal.getChildren().setAll(etiqueta);
    }

    private void mostrarErrorCarga() {
        Label etiqueta = new Label("No se pudo cargar el personal. Revisa la conexión con la base de datos.");
        etiqueta.getStyleClass().add("form-error");
        listaPersonal.getChildren().setAll(etiqueta);
    }
}
