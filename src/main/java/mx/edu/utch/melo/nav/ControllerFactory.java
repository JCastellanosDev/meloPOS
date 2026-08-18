package mx.edu.utch.melo.nav;

import javafx.util.Callback;
import mx.edu.utch.melo.app.AppContext;
import mx.edu.utch.melo.dao.CategoriaDAO;
import mx.edu.utch.melo.dao.ClienteDAO;
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
import mx.edu.utch.melo.geo.Geocodificador;
import mx.edu.utch.melo.geo.ServicioRutas;
import mx.edu.utch.melo.service.CategoriaService;
import mx.edu.utch.melo.service.ClienteService;
import mx.edu.utch.melo.service.DashboardService;
import mx.edu.utch.melo.service.PagoService;
import mx.edu.utch.melo.service.ProductoService;
import mx.edu.utch.melo.service.SucursalService;
import mx.edu.utch.melo.service.TurnoService;
import mx.edu.utch.melo.service.UsuarioService;
import mx.edu.utch.melo.service.VentaService;
import mx.edu.utch.melo.sesion.SesionActual;

import java.lang.reflect.Constructor;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fábrica de controladores para FXMLLoader: resuelve el constructor público de cada
 * controlador por tipo de parámetro contra un registro de instancias ya construidas por
 * {@link AppContext} (Navigator, SesionActual, cada Service, cada DAO, Geocodificador,
 * ServicioRutas, y AppContext mismo). Así cada controlador puede pedir justo las
 * dependencias concretas que usa -- p. ej. {@code Controller(PagoService pagoService,
 * SesionActual sesion)} -- en vez de recibir siempre AppContext completo (ver auditoría
 * de Fase 3: "reducir dependencia de AppContext", melo-architecture).
 *
 * Esto NO es un framework de inyección de dependencias: es un mapa fijo de tipo -> instancia
 * construido una sola vez aquí mismo, sin anotaciones, sin escaneo de classpath y sin ciclo
 * de vida propio -- las instancias ya las construyó AppContext, este registro solo las hace
 * elegibles para inyección por constructor. Un controlador que todavía no se migró a
 * dependencias específicas sigue funcionando igual pidiendo {@code AppContext} completo
 * (también está en el registro) -- la migración es progresiva, no todo-o-nada.
 *
 * Si un controlador declara más de un constructor público resoluble contra el registro, se
 * usa el que tenga más parámetros (más específico). Si no declara ninguno resoluble, se usa
 * el constructor sin argumentos.
 */
public class ControllerFactory implements Callback<Class<?>, Object> {

    private final Map<Class<?>, Object> registro;

    public ControllerFactory(AppContext contexto) {
        this.registro = construirRegistro(contexto);
    }

    @Override
    public Object call(Class<?> tipoControlador) {
        try {
            Constructor<?> constructor = elegirConstructor(tipoControlador);
            if (constructor != null) {
                Class<?>[] tipos = constructor.getParameterTypes();
                Object[] argumentos = new Object[tipos.length];
                for (int i = 0; i < tipos.length; i++) {
                    argumentos[i] = registro.get(tipos[i]);
                }
                return constructor.newInstance(argumentos);
            }
            return tipoControlador.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("No se pudo crear el controlador: " + tipoControlador, e);
        }
    }

    /** El constructor público con más parámetros cuyo tipo esté completo en el registro. */
    private Constructor<?> elegirConstructor(Class<?> tipoControlador) {
        Constructor<?> mejor = null;
        for (Constructor<?> candidato : tipoControlador.getConstructors()) {
            if (candidato.getParameterCount() == 0 || !todosResolubles(candidato.getParameterTypes())) {
                continue;
            }
            if (mejor == null || candidato.getParameterCount() > mejor.getParameterCount()) {
                mejor = candidato;
            }
        }
        return mejor;
    }

    private boolean todosResolubles(Class<?>[] tipos) {
        for (Class<?> tipo : tipos) {
            if (!registro.containsKey(tipo)) {
                return false;
            }
        }
        return true;
    }

    private static Map<Class<?>, Object> construirRegistro(AppContext contexto) {
        Map<Class<?>, Object> registro = new LinkedHashMap<>();

        registro.put(AppContext.class, contexto);
        registro.put(Navigator.class, contexto.getNavigator());
        registro.put(SesionActual.class, contexto.getSesion());
        registro.put(Geocodificador.class, contexto.getGeocodificador());
        registro.put(ServicioRutas.class, contexto.getServicioRutas());

        registro.put(UsuarioDAO.class, contexto.getUsuarioDAO());
        registro.put(SucursalDAO.class, contexto.getSucursalDAO());
        registro.put(CategoriaDAO.class, contexto.getCategoriaDAO());
        registro.put(ClienteDAO.class, contexto.getClienteDAO());
        registro.put(ModificadorDAO.class, contexto.getModificadorDAO());
        registro.put(MesaDAO.class, contexto.getMesaDAO());
        registro.put(TurnoDAO.class, contexto.getTurnoDAO());
        registro.put(ProductoDAO.class, contexto.getProductoDAO());
        registro.put(OrdenDAO.class, contexto.getOrdenDAO());
        registro.put(DetalleOrdenDAO.class, contexto.getDetalleOrdenDAO());
        registro.put(PagoDAO.class, contexto.getPagoDAO());
        registro.put(ReporteDAO.class, contexto.getReporteDAO());
        registro.put(DashboardDAO.class, contexto.getDashboardDAO());

        registro.put(SucursalService.class, contexto.getSucursalService());
        registro.put(CategoriaService.class, contexto.getCategoriaService());
        registro.put(ProductoService.class, contexto.getProductoService());
        registro.put(UsuarioService.class, contexto.getUsuarioService());
        registro.put(VentaService.class, contexto.getVentaService());
        registro.put(PagoService.class, contexto.getPagoService());
        registro.put(ClienteService.class, contexto.getClienteService());
        registro.put(TurnoService.class, contexto.getTurnoService());
        registro.put(DashboardService.class, contexto.getDashboardService());

        return registro;
    }
}
