package mx.edu.utch.melo.app;

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
import mx.edu.utch.melo.geo.Geocodificador;
import mx.edu.utch.melo.geo.ServicioRutas;
import mx.edu.utch.melo.nav.Navigator;
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
 * Segregación de Interfaces. DashboardController SÍ recibe AppContext: además
 * de navegar, muestra datos reales (usuario en sesión, pedidos a domicilio
 * activos, ventas del día).
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
    private final ServicioRutas servicioRutas;

    public AppContext(Navigator navigator, SesionActual sesion, Geocodificador geocodificador,
                       UsuarioDAO usuarioDAO, SucursalDAO sucursalDAO, CategoriaDAO categoriaDAO,
                       ClienteDAO clienteDAO, ModificadorDAO modificadorDAO, MesaDAO mesaDAO, TurnoDAO turnoDAO,
                       ProductoDAO productoDAO, OrdenDAO ordenDAO, DetalleOrdenDAO detalleOrdenDAO, PagoDAO pagoDAO,
                       ReporteDAO reporteDAO, ServicioRutas servicioRutas) {
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
        this.servicioRutas = servicioRutas;
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

    public ServicioRutas getServicioRutas() {
        return servicioRutas;
    }
}
