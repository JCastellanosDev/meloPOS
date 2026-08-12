package mx.edu.utch.melo.nav;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import mx.edu.utch.melo.app.AppContext;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Implementación de {@link Navigator} para una aplicación de escritorio de
 * ventana única: sustituye la raíz de la Scene activa por la pantalla pedida.
 * Es un objeto normal (no un singleton estático): se construye una vez en
 * HelloApplication y se inyecta en los controladores que lo necesitan.
 *
 * El contexto se asigna con {@link #setContexto} después de construir el
 * SceneManager, no por constructor: AppContext necesita un Navigator para
 * existir, y el único Navigator es este SceneManager -- inyectarlo por
 * constructor sería una dependencia circular. Es la única inyección por
 * setter de todo el proyecto, y es por esta razón puntual.
 */
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
        if (contexto == null) {
            throw new IllegalStateException("SceneManager.setContexto(...) no se llamó antes de navegar.");
        }
        Parent raiz;
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(RUTA_BASE + pantalla.getArchivoFxml()));
            loader.setControllerFactory(new ControllerFactory(contexto));
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
