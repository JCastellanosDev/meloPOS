package mx.edu.utch.melo;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import mx.edu.utch.melo.nav.ControllerFactory;
import mx.edu.utch.melo.nav.Navigator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Prueba de humo: cada pantalla debe cargar con FXMLLoader sin lanzar
 * excepción. No usa TestFX ni abre ventanas -- solo inicializa el toolkit de
 * JavaFX con Platform.startup() para poder construir el árbol de nodos.
 *
 * Usa un Navigator "falso" (no hace nada al navegar) inyectado vía
 * ControllerFactory: esto también verifica que la inyección por constructor
 * de SidebarController funciona incluso cuando se carga como fx:include.
 *
 * Importante: esto detecta fx:id mal escritos, recursos faltantes o
 * controladores rotos, pero NO detecta bugs puramente visuales/CSS (como el
 * de combinar "card" + "card-lg" documentado en styles.css) porque esos no
 * lanzan excepción, solo se ven mal. Para eso se necesitaría una prueba
 * visual con TestFX, deliberadamente fuera de alcance por ahora.
 */
class FxmlSmokeTest {

    private static final Navigator NAVEGADOR_FALSO = pantalla -> { };

    @BeforeAll
    static void iniciarToolkitJavaFx() throws InterruptedException {
        CountDownLatch cerrojo = new CountDownLatch(1);
        Platform.startup(cerrojo::countDown);
        cerrojo.await();
    }

    @AfterAll
    static void cerrarToolkitJavaFx() {
        Platform.exit();
    }

    @Test
    void dashboardCarga() {
        assertPantallaCarga("Dashboard.fxml");
    }

    @Test
    void deliveryViewCarga() {
        assertPantallaCarga("DeliveryView.fxml");
    }

    @Test
    void kitchenDisplayCarga() {
        assertPantallaCarga("KitchenDisplay.fxml");
    }

    @Test
    void menuPosCarga() {
        assertPantallaCarga("MenuPOS.fxml");
    }

    @Test
    void paymentPortalCarga() {
        assertPantallaCarga("PaymentPortal.fxml");
    }

    @Test
    void registerClientCarga() {
        assertPantallaCarga("RegisterClient.fxml");
    }

    private void assertPantallaCarga(String fxml) {
        Parent[] raiz = new Parent[1];
        Exception[] error = new Exception[1];
        CountDownLatch cerrojo = new CountDownLatch(1);

        Platform.runLater(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource(fxml));
                loader.setControllerFactory(new ControllerFactory(NAVEGADOR_FALSO));
                raiz[0] = loader.load();
            } catch (Exception e) {
                error[0] = e;
            } finally {
                cerrojo.countDown();
            }
        });

        try {
            cerrojo.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (error[0] != null) {
            throw new AssertionError(fxml + " no cargó correctamente", error[0]);
        }
        assertNotNull(raiz[0], fxml + " no debería devolver una raíz nula");
    }
}
