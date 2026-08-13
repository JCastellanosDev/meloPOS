package mx.edu.utch.melo.nav;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import mx.edu.utch.melo.app.AppContext;

import java.io.IOException;
import java.io.UncheckedIOException;


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
        Parent raiz = cargarRaiz(pantalla);
        Scene scene = stage.getScene();
        if (scene == null) {
            stage.setScene(new Scene(raiz));
        } else {
            scene.setRoot(raiz);
        }
    }

    @Override
    public void abrirVentana(Pantalla pantalla, String titulo) {
        Parent raiz = cargarRaiz(pantalla);
        Stage ventana = new Stage();
        ventana.setTitle(titulo);
        ventana.setScene(new Scene(raiz));
        ventana.show();
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
