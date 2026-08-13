package mx.edu.utch.melo;

import javafx.application.Application;
import javafx.stage.Stage;
import mx.edu.utch.melo.app.AppContext;
import mx.edu.utch.melo.config.Configuracion;
import mx.edu.utch.melo.dao.CategoriaDAO;
import mx.edu.utch.melo.dao.ClienteDAO;
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
import mx.edu.utch.melo.dao.impl.CategoriaDAOImpl;
import mx.edu.utch.melo.dao.impl.ClienteDAOImpl;
import mx.edu.utch.melo.dao.impl.DetalleOrdenDAOImpl;
import mx.edu.utch.melo.dao.impl.MesaDAOImpl;
import mx.edu.utch.melo.dao.impl.ModificadorDAOImpl;
import mx.edu.utch.melo.dao.impl.OrdenDAOImpl;
import mx.edu.utch.melo.dao.impl.PagoDAOImpl;
import mx.edu.utch.melo.dao.impl.ProductoDAOImpl;
import mx.edu.utch.melo.dao.impl.ReporteDAOImpl;
import mx.edu.utch.melo.dao.impl.SucursalDAOImpl;
import mx.edu.utch.melo.dao.impl.TurnoDAOImpl;
import mx.edu.utch.melo.dao.impl.UsuarioDAOImpl;
import mx.edu.utch.melo.db.ConexionDB;
import mx.edu.utch.melo.geo.Geocodificador;
import mx.edu.utch.melo.geo.MapboxGeocodificador;
import mx.edu.utch.melo.geo.MapboxServicioRutas;
import mx.edu.utch.melo.geo.ServicioRutas;
import mx.edu.utch.melo.model.Usuario;
import mx.edu.utch.melo.nav.Navigator;
import mx.edu.utch.melo.nav.Pantalla;
import mx.edu.utch.melo.nav.SceneManager;
import mx.edu.utch.melo.sesion.SesionActual;

/**
 * Composition root: aquí, y solo aquí, se construyen la conexión a base de
 * datos, todos los DAO y el AppContext que se inyecta al resto de la app.
 */
public class HelloApplication extends Application {

    /** No hay pantalla de login por PIN todavía (ver SesionActual) -- se arranca con el usuario semilla de db/schema.sql. */
    private static final int SUCURSAL_SEMILLA_ID = 1;
    private static final String PIN_SEMILLA = "0000";

    @Override
    public void start(Stage stage) {
        stage.setTitle("melo - Terminal de Ventas");

        SceneManager sceneManager = new SceneManager(stage);
        sceneManager.setContexto(construirContexto(sceneManager));

        sceneManager.navigateTo(Pantalla.DASHBOARD);
        stage.show();
    }

    private AppContext construirContexto(Navigator navigator) {
        ConexionDB conexionDB = ConexionDB.getInstancia();

        UsuarioDAO usuarioDAO = new UsuarioDAOImpl(conexionDB);
        SucursalDAO sucursalDAO = new SucursalDAOImpl(conexionDB);
        CategoriaDAO categoriaDAO = new CategoriaDAOImpl(conexionDB);
        ClienteDAO clienteDAO = new ClienteDAOImpl(conexionDB);
        ModificadorDAO modificadorDAO = new ModificadorDAOImpl(conexionDB);
        MesaDAO mesaDAO = new MesaDAOImpl(conexionDB);
        TurnoDAO turnoDAO = new TurnoDAOImpl(conexionDB);
        ProductoDAO productoDAO = new ProductoDAOImpl(conexionDB);
        OrdenDAO ordenDAO = new OrdenDAOImpl(conexionDB);
        DetalleOrdenDAO detalleOrdenDAO = new DetalleOrdenDAOImpl(conexionDB);
        PagoDAO pagoDAO = new PagoDAOImpl(conexionDB);
        ReporteDAO reporteDAO = new ReporteDAOImpl(conexionDB);

        SesionActual sesion = new SesionActual();
        Usuario usuarioSemilla = usuarioDAO.obtenerPorPin(SUCURSAL_SEMILLA_ID, PIN_SEMILLA)
                .orElseThrow(() -> new IllegalStateException(
                        "No se encontró el usuario semilla (sucursal " + SUCURSAL_SEMILLA_ID + ", PIN "
                                + PIN_SEMILLA + "). ¿Se aplicó src/main/resources/db/schema.sql en esta base?"));
        sesion.iniciarSesion(usuarioSemilla);

        String mapboxToken = Configuracion.obtener("mapbox.token", "");
        Geocodificador geocodificador = new MapboxGeocodificador(mapboxToken);
        ServicioRutas servicioRutas = new MapboxServicioRutas(mapboxToken);

        return new AppContext(navigator, sesion, geocodificador, usuarioDAO, sucursalDAO, categoriaDAO,
                clienteDAO, modificadorDAO, mesaDAO, turnoDAO, productoDAO, ordenDAO, detalleOrdenDAO, pagoDAO,
                reporteDAO, servicioRutas);
    }
}
