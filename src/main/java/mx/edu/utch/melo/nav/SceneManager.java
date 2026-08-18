package mx.edu.utch.melo.nav;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.Window;
import mx.edu.utch.melo.app.AppContext;
import mx.edu.utch.melo.model.Rol;
import mx.edu.utch.melo.security.AccesoDenegadoException;
import mx.edu.utch.melo.security.ControlAcceso;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.EnumSet;
import java.util.Set;


public class SceneManager implements Navigator {

    private static final String RUTA_BASE = "/mx/edu/utch/melo/";

    private final Stage stage;
    private AppContext contexto;

    public SceneManager(Stage stage) {
        this.stage = stage;
    }

    public void setContexto(AppContext contexto) {
        this.contexto = contexto;
    }

    @Override
    public void navigateTo(Pantalla pantalla) {
        validarAcceso(pantalla);
        Parent raiz = cargarRaiz(pantalla);
        Scene scene = stage.getScene();
        if (scene == null) {
            stage.setScene(new Scene(raiz));
        } else {
            scene.setRoot(raiz);
        }
    }

    /**
     * Pantallas emergentes que deben quedar como diálogo flotante: tamaño justo de su contenido,
     * centradas, sin poder redimensionarse -- a diferencia de COCINA, pensada como monitor aparte
     * (ver CLAUDE.md). Antes esto congelaba la ventana principal cuando ella usaba pantalla
     * completa NATIVA de macOS (ver HelloApplication) -- ya no aplica: la ventana principal ahora
     * es UNDECORATED + maximizada, no fullscreen nativo, así que abrir una ventana emergente ya
     * no choca con el modo Space de macOS.
     */
    private static final Set<Pantalla> VENTANAS_FLOTANTES =
            EnumSet.of(Pantalla.CAMBIAR_USUARIO, Pantalla.ORDENES, Pantalla.SELECCIONAR_DESCUENTO);

    @Override
    public void abrirVentana(Pantalla pantalla, String titulo) {
        validarAcceso(pantalla);
        Parent raiz = cargarRaiz(pantalla);
        Stage ventana = new Stage();
        ventana.setTitle(titulo);
        ventana.setScene(new Scene(raiz));
        if (VENTANAS_FLOTANTES.contains(pantalla)) {
            ventana.initOwner(ventanaActiva());
            ventana.setResizable(false);
            ventana.sizeToScene();
            ventana.centerOnScreen();
        }
        ventana.show();
    }

    /**
     * La ventana que realmente tiene el foco ahora mismo -- puede ser otra ventana emergente ya
     * abierta, no siempre la ventana principal. Bug real: SELECCIONAR_DESCUENTO abierta desde
     * PaymentPortal (ella misma otra ventana emergente) se dueñaba siempre de {@code stage} (la
     * ventana principal) en vez de PaymentPortal -- quedaban como hermanas, no como padre/hija, así
     * que al cerrar SELECCIONAR_DESCUENTO el foco no regresaba de forma confiable a PaymentPortal, y
     * su listener de "recuperó el foco" (ver PaymentPortalController.habilitarRecogerDescuentoAlRegresarElFoco)
     * no disparaba -- el cajero tenía que volver a interactuar con la ventana de Cobrar a mano para
     * que apareciera el PIN. Dueñar cada ventana emergente de la que esté realmente activa en ese
     * momento arregla esto para cualquier ventana flotante abierta desde otra, no solo esta.
     */
    private Window ventanaActiva() {
        return Window.getWindows().stream()
                .filter(Window::isFocused)
                .findFirst()
                .orElse(stage);
    }

    /**
     * "No basta con ocultar botones" (ver auditoría de Fase 5): cada pantalla se valida aquí,
     * en el único lugar por el que pasa toda la navegación real -- no depende de que cada
     * Controller recuerde revisarlo.
     */
    private void validarAcceso(Pantalla pantalla) {
        if (contexto == null) {
            throw new IllegalStateException("SceneManager.setContexto(...) no se llamó antes de navegar.");
        }
        Rol rol = contexto.getSesion().getUsuarioActivo().getRol();
        if (!ControlAcceso.puedeAcceder(rol, pantalla)) {
            throw new AccesoDenegadoException(rol, "abrir la pantalla " + pantalla);
        }
    }

    private Parent cargarRaiz(Pantalla pantalla) {
        if (contexto == null) {
            throw new IllegalStateException("SceneManager.setContexto(...) no se llamó antes de navegar.");
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(RUTA_BASE + pantalla.getArchivoFxml()));
            loader.setControllerFactory(new ControllerFactory(contexto));
            return loader.load();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo cargar la pantalla: " + pantalla, e);
        }
    }
}
