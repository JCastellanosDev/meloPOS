package mx.edu.utch.melo.app;

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
import mx.edu.utch.melo.geo.Geocodificador;
import mx.edu.utch.melo.geo.ServicioRutas;
import mx.edu.utch.melo.nav.Navigator;
import mx.edu.utch.melo.service.CategoriaService;
import mx.edu.utch.melo.service.ClienteService;
import mx.edu.utch.melo.service.DashboardService;
import mx.edu.utch.melo.service.DeliveryService;
import mx.edu.utch.melo.service.PagoService;
import mx.edu.utch.melo.service.ProductoService;
import mx.edu.utch.melo.service.SucursalService;
import mx.edu.utch.melo.service.TurnoService;
import mx.edu.utch.melo.service.UsuarioService;
import mx.edu.utch.melo.service.VentaService;
import mx.edu.utch.melo.sesion.SesionActual;

/**
 * Punto único donde un controlador obtiene lo que necesita para hablar
 * con la base de datos y con el resto de la app -- Navigator, todos
 * los DAO, el geocodificador y la sesión activa. Es el registro de
 * servicios del "composition root" (ver HelloApplication): cada
 * controlador solo llama a los getters que realmente usa, no depende
 * de cómo se construyeron.
 *
 * Los controladores que SOLO necesitan navegar (p. ej. SidebarController)
 * siguen recibiendo {@link Navigator} directo por constructor, no AppContext
 * completo -- darles acceso a todos los DAO cuando no los usan violaría
 * Segregación de Interfaces. Todos los demás controladores, incluido
 * DashboardController, ya se migraron a pedir solo las dependencias
 * concretas que usan (Service/DAO/Navigator/SesionActual puntuales) --
 * ninguno recibe AppContext completo hoy. {@link mx.edu.utch.melo.nav.SceneManager}
 * sigue guardando AppContext completo porque necesita construir un
 * {@link mx.edu.utch.melo.nav.ControllerFactory} nuevo por cada pantalla y
 * validar el acceso por rol contra la sesión activa.
 */
public class AppContext {

    private final Navigator navigator;
    private final SesionActual sesion;
    private final Geocodificador geocodificador;
    private final UsuarioDAO usuarioDAO;
    private final SucursalDAO sucursalDAO;
    private final CategoriaDAO categoriaDAO;
    private final ClienteDAO clienteDAO;
    private final ModificadorDAO modificadorDAO;
    private final MesaDAO mesaDAO;
    private final TurnoDAO turnoDAO;
    private final ProductoDAO productoDAO;
    private final OrdenDAO ordenDAO;
    private final DetalleOrdenDAO detalleOrdenDAO;
    private final PagoDAO pagoDAO;
    private final ReporteDAO reporteDAO;
    private final DashboardDAO dashboardDAO;
    private final ServicioRutas servicioRutas;

    private final SucursalService sucursalService;
    private final CategoriaService categoriaService;
    private final ProductoService productoService;
    private final UsuarioService usuarioService;
    private final VentaService ventaService;
    private final PagoService pagoService;
    private final ClienteService clienteService;
    private final TurnoService turnoService;
    private final DashboardService dashboardService;
    private final DeliveryService deliveryService;

    public AppContext(Navigator navigator, SesionActual sesion, Geocodificador geocodificador,
                       UsuarioDAO usuarioDAO, SucursalDAO sucursalDAO, CategoriaDAO categoriaDAO,
                       ClienteDAO clienteDAO, ModificadorDAO modificadorDAO, MesaDAO mesaDAO, TurnoDAO turnoDAO,
                       ProductoDAO productoDAO, OrdenDAO ordenDAO, DetalleOrdenDAO detalleOrdenDAO, PagoDAO pagoDAO,
                       ReporteDAO reporteDAO, DashboardDAO dashboardDAO, ServicioRutas servicioRutas,
                       ControlIntentosPinDAO controlIntentosPinDAO, ConexionDB conexionDB) {
        this.navigator = navigator;
        this.sesion = sesion;
        this.geocodificador = geocodificador;
        this.usuarioDAO = usuarioDAO;
        this.sucursalDAO = sucursalDAO;
        this.categoriaDAO = categoriaDAO;
        this.clienteDAO = clienteDAO;
        this.modificadorDAO = modificadorDAO;
        this.mesaDAO = mesaDAO;
        this.turnoDAO = turnoDAO;
        this.productoDAO = productoDAO;
        this.ordenDAO = ordenDAO;
        this.detalleOrdenDAO = detalleOrdenDAO;
        this.pagoDAO = pagoDAO;
        this.reporteDAO = reporteDAO;
        this.dashboardDAO = dashboardDAO;
        this.servicioRutas = servicioRutas;

        this.sucursalService = new SucursalService(sucursalDAO);
        this.categoriaService = new CategoriaService(categoriaDAO);
        this.productoService = new ProductoService(productoDAO);
        this.usuarioService = new UsuarioService(usuarioDAO, controlIntentosPinDAO);
        this.ventaService = new VentaService(ordenDAO, detalleOrdenDAO, productoDAO, pagoDAO, conexionDB);
        this.pagoService = new PagoService(ordenDAO, detalleOrdenDAO, productoDAO, pagoDAO, usuarioDAO, turnoDAO,
                sucursalDAO, conexionDB);
        this.clienteService = new ClienteService(clienteDAO, sucursalDAO, geocodificador, servicioRutas);
        this.turnoService = new TurnoService(turnoDAO, reporteDAO);
        this.dashboardService = new DashboardService(dashboardDAO, productoDAO, ordenDAO, this.turnoService);
        this.deliveryService = new DeliveryService(ordenDAO, clienteDAO);
    }

    public Navigator getNavigator() {
        return navigator;
    }

    public SesionActual getSesion() {
        return sesion;
    }

    public Geocodificador getGeocodificador() {
        return geocodificador;
    }

    public UsuarioDAO getUsuarioDAO() {
        return usuarioDAO;
    }

    public SucursalDAO getSucursalDAO() {
        return sucursalDAO;
    }

    public CategoriaDAO getCategoriaDAO() {
        return categoriaDAO;
    }

    public ClienteDAO getClienteDAO() {
        return clienteDAO;
    }

    public ModificadorDAO getModificadorDAO() {
        return modificadorDAO;
    }

    public MesaDAO getMesaDAO() {
        return mesaDAO;
    }

    public TurnoDAO getTurnoDAO() {
        return turnoDAO;
    }

    public ProductoDAO getProductoDAO() {
        return productoDAO;
    }

    public OrdenDAO getOrdenDAO() {
        return ordenDAO;
    }

    public DetalleOrdenDAO getDetalleOrdenDAO() {
        return detalleOrdenDAO;
    }

    public PagoDAO getPagoDAO() {
        return pagoDAO;
    }

    public ReporteDAO getReporteDAO() {
        return reporteDAO;
    }

    public DashboardDAO getDashboardDAO() {
        return dashboardDAO;
    }

    public ServicioRutas getServicioRutas() {
        return servicioRutas;
    }

    public SucursalService getSucursalService() {
        return sucursalService;
    }

    public CategoriaService getCategoriaService() {
        return categoriaService;
    }

    public ProductoService getProductoService() {
        return productoService;
    }

    public UsuarioService getUsuarioService() {
        return usuarioService;
    }

    public VentaService getVentaService() {
        return ventaService;
    }

    public PagoService getPagoService() {
        return pagoService;
    }

    public ClienteService getClienteService() {
        return clienteService;
    }

    public TurnoService getTurnoService() {
        return turnoService;
    }

    public DashboardService getDashboardService() {
        return dashboardService;
    }

    public DeliveryService getDeliveryService() {
        return deliveryService;
    }
}
