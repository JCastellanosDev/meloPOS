package mx.edu.utch.melo;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import mx.edu.utch.melo.app.AppContext;
import mx.edu.utch.melo.dao.CategoriaDAO;
import mx.edu.utch.melo.dao.ClienteDAO;
import mx.edu.utch.melo.dao.ControlIntentosPinDAO;
import mx.edu.utch.melo.dao.DashboardDAO;
import mx.edu.utch.melo.dao.DetalleOrdenDAO;
import mx.edu.utch.melo.dao.MesaDAO;
import mx.edu.utch.melo.dao.ModificadorDAO;
import mx.edu.utch.melo.dao.OrdenDAO;
import mx.edu.utch.melo.dao.PagoDAO;
import mx.edu.utch.melo.dao.ProductoDAO;
import mx.edu.utch.melo.dao.ReporteDAO;
import mx.edu.utch.melo.dao.SucursalDAO;
import mx.edu.utch.melo.dao.TurnoDAO;
import mx.edu.utch.melo.dao.UsuarioDAO;
import mx.edu.utch.melo.db.ConexionDB;
import mx.edu.utch.melo.geo.ServicioRutas;
import mx.edu.utch.melo.nav.ControllerFactory;
import mx.edu.utch.melo.nav.Navigator;
import mx.edu.utch.melo.nav.Pantalla;
import mx.edu.utch.melo.sesion.SesionActual;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Prueba de humo: cada pantalla debe cargar con FXMLLoader sin lanzar
 * excepción. No usa TestFX ni abre ventanas -- solo inicializa el toolkit de
 * JavaFX con Platform.startup() para poder construir el árbol de nodos.
 *
 * Usa un AppContext "falso" (Navigator sin operación, DAOs proxy que no
 * tocan la base de datos, sesión sin iniciar) inyectado vía ControllerFactory:
 * esto también verifica que la inyección por constructor de SidebarController
 * funciona incluso cuando se carga como fx:include. Los DAO proxy son seguros
 * porque las consultas reales de cada controlador (ver Async.ejecutar) se
 * disparan en un hilo aparte después de que FXMLLoader.load() ya retornó --
 * nunca se ejecutan durante esta prueba.
 *
 * Importante: esto detecta fx:id mal escritos, recursos faltantes o
 * controladores rotos, pero NO detecta bugs puramente visuales/CSS (como el
 * de combinar "card" + "card-lg" documentado en styles.css) porque esos no
 * lanzan excepción, solo se ven mal. Para eso se necesitaría una prueba
 * visual con TestFX, deliberadamente fuera de alcance por ahora.
 */
class FxmlSmokeTest {

    private static final Navigator NAVEGADOR_FALSO = new Navigator() {
        @Override
        public void navigateTo(Pantalla pantalla) {
        }

        @Override
        public void abrirVentana(Pantalla pantalla, String titulo) {
        }
    };

    private static final AppContext CONTEXTO_FALSO = new AppContext(
            NAVEGADOR_FALSO,
            new SesionActual(),
            direccion -> Optional.empty(),
            daoInactivo(UsuarioDAO.class),
            daoInactivo(SucursalDAO.class),
            daoInactivo(CategoriaDAO.class),
            daoInactivo(ClienteDAO.class),
            daoInactivo(ModificadorDAO.class),
            daoInactivo(MesaDAO.class),
            daoInactivo(TurnoDAO.class),
            daoInactivo(ProductoDAO.class),
            daoInactivo(OrdenDAO.class),
            daoInactivo(DetalleOrdenDAO.class),
            daoInactivo(PagoDAO.class),
            daoInactivo(ReporteDAO.class),
            daoInactivo(DashboardDAO.class),
            (origen, destino) -> Optional.empty(),
            daoInactivo(ControlIntentosPinDAO.class),
            // Singleton real, pero seguro aquí: solo lo usan VentaService/PagoService para abrir una
            // transacción (ver ConexionDB#ejecutarEnTransaccion), y eso nunca se dispara en esta prueba
            // (solo se llama initialize(), nunca "Cobrar"/"Confirmar Pago"). Construirlo no valida
            // credenciales -- eso se pospone a obtenerConexion(), que aquí nunca se invoca.
            ConexionDB.getInstancia());

    /** DAO proxy que nunca toca la base de datos: solo devuelve valores vacíos/por defecto. */
    private static <T> T daoInactivo(Class<T> tipoDao) {
        return tipoDao.cast(Proxy.newProxyInstance(
                tipoDao.getClassLoader(),
                new Class<?>[]{tipoDao},
                (proxy, metodo, argumentos) -> valorPorDefecto(metodo.getReturnType())));
    }

    private static Object valorPorDefecto(Class<?> tipoRetorno) {
        if (tipoRetorno == List.class) {
            return List.of();
        }
        if (tipoRetorno == Optional.class) {
            return Optional.empty();
        }
        if (tipoRetorno == boolean.class) {
            return false;
        }
        return null;
    }

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
    void pedidosCarga() {
        assertPantallaCarga("Pedidos.fxml");
    }

    @Test
    void menuPedidoCarga() {
        assertPantallaCarga("MenuPedido.fxml");
    }

    @Test
    void reportesCarga() {
        assertPantallaCarga("Reportes.fxml");
    }

    @Test
    void personalCarga() {
        assertPantallaCarga("Personal.fxml");
    }

    @Test
    void inventarioCarga() {
        assertPantallaCarga("Inventario.fxml");
    }

    @Test
    void ajustesCarga() {
        assertPantallaCarga("Ajustes.fxml");
    }

    @Test
    void cajaCarga() {
        assertPantallaCarga("Caja.fxml");
    }

    @Test
    void cambiarUsuarioCarga() {
        assertPantallaCarga("CambiarUsuario.fxml");
    }

    private void assertPantallaCarga(String fxml) {
        Parent[] raiz = new Parent[1];
        Exception[] error = new Exception[1];
        CountDownLatch cerrojo = new CountDownLatch(1);

        Platform.runLater(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource(fxml));
                loader.setControllerFactory(new ControllerFactory(CONTEXTO_FALSO));
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
