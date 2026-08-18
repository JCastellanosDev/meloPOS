package mx.edu.utch.melo.controller;

import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import mx.edu.utch.melo.async.Async;
import mx.edu.utch.melo.dao.UsuarioDAO;
import mx.edu.utch.melo.model.Usuario;
import mx.edu.utch.melo.nav.Pantalla;
import mx.edu.utch.melo.sesion.SesionActual;
import mx.edu.utch.melo.view.EstadoVacioFactory;
import mx.edu.utch.melo.view.FilaEntidadFactory;

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

    /** Ver auditoría UI/UX, "componentes a convertir en reutilizables": antes esta fila y la de
     * InventarioController se reconstruían por separado a mano con el mismo esqueleto. De paso
     * corrige un bug real de estilo -- ver FilaEntidadFactory y el aviso al inicio de styles.css:
     * el .add() anterior insertaba "badge badge-x" como un solo token de styleClass, que JavaFX
     * nunca separa en dos clases CSS, así que la insignia se veía sin fondo ni color. */
    private Node construirFilaUsuario(Usuario usuario) {
        boolean activo = usuario.isActivo();
        return FilaEntidadFactory.crear(
                inicial(usuario.getNombre()),
                usuario.getNombre(),
                usuario.getRol().getEtiqueta(),
                null,
                activo ? "Activo" : "Inactivo",
                activo ? "badge badge-success" : "badge badge-muted");
    }

    private String inicial(String nombre) {
        return nombre == null || nombre.isBlank() ? "?" : nombre.substring(0, 1).toUpperCase(Locale.forLanguageTag("es-MX"));
    }

    private void mostrarMensaje(String mensaje) {
        listaPersonal.getChildren().setAll(EstadoVacioFactory.crear("mdi2a-account-tie", mensaje, 400));
    }

    private void mostrarErrorCarga() {
        Label etiqueta = new Label("No se pudo cargar el personal. Revisa la conexión con la base de datos.");
        etiqueta.getStyleClass().add("form-error");
        listaPersonal.getChildren().setAll(etiqueta);
    }
}
