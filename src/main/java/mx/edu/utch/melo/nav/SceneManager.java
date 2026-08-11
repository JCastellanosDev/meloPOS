package mx.edu.utch.melo.nav;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Implementación de {@link Navigator} para una aplicación de escritorio de
 * ventana única: sustituye la raíz de la Scene activa por la pantalla pedida.
 * Es un objeto normal (no un singleton estático): se construye una vez en
 * HelloApplication y se inyecta en los controladores que lo necesitan.
 */
public class SceneManager implements Navigator {

    private static final String RUTA_BASE = "/mx/edu/utch/melo/";

    private final Stage stage;

    public SceneManager(Stage stage) {
        this.stage = stage;
    }

    @Override
    public void navigateTo(Pantalla pantalla) {
        Parent raiz;
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(RUTA_BASE + pantalla.getArchivoFxml()));
            loader.setControllerFactory(new ControllerFactory(this));
            raiz = loader.load();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo cargar la pantalla: " + pantalla, e);
        }

        Scene scene = stage.getScene();
        if (scene == null) {
            stage.setScene(new Scene(raiz));
        } else {
            scene.setRoot(raiz);
        }
    }
}
