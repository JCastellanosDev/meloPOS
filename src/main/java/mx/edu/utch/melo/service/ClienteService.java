package mx.edu.utch.melo.service;

import mx.edu.utch.melo.dao.ClienteDAO;
import mx.edu.utch.melo.dao.SucursalDAO;
import mx.edu.utch.melo.geo.Coordenadas;
import mx.edu.utch.melo.geo.Geocodificador;
import mx.edu.utch.melo.geo.Ruta;
import mx.edu.utch.melo.geo.ServicioRutas;
import mx.edu.utch.melo.model.Cliente;
import mx.edu.utch.melo.model.Sucursal;

import java.util.Optional;

/**
 * Alta de clientes y cálculo de ruta a domicilio (ver PedidosController). La geocodificación de
 * direcciones incompletas es poco confiable sin ayuda (confirmado con pruebas reales, ver CLAUDE.md):
 * por eso completarDireccion + pasar la sucursal como "cercaDe" viven juntos aquí, en un solo lugar,
 * en vez de duplicarse entre el cálculo de ruta y el alta de cliente.
 */
public class ClienteService {

    private final ClienteDAO clienteDAO;
    private final SucursalDAO sucursalDAO;
    private final Geocodificador geocodificador;
    private final ServicioRutas servicioRutas;

    public ClienteService(ClienteDAO clienteDAO, SucursalDAO sucursalDAO, Geocodificador geocodificador,
                           ServicioRutas servicioRutas) {
        this.clienteDAO = clienteDAO;
        this.sucursalDAO = sucursalDAO;
        this.geocodificador = geocodificador;
        this.servicioRutas = servicioRutas;
    }

    public Optional<Cliente> buscarPorTelefono(String telefono) {
        return clienteDAO.obtenerPorTelefono(telefono);
    }

    /** Calcula la ruta real (distancia + mapa) de la sucursal activa a una dirección capturada. */
    public Optional<Ruta> calcularRuta(String direccion, int sucursalId) {
        Sucursal sucursal = sucursalDAO.obtenerPorId(sucursalId).orElseThrow();
        if (sucursal.getLatitud() == null || sucursal.getLongitud() == null) {
            throw new SucursalSinCoordenadasException();
        }
        Coordenadas origen = new Coordenadas(sucursal.getLatitud(), sucursal.getLongitud());

        Optional<Coordenadas> destino = geocodificador.geocodificar(completarDireccion(direccion, sucursal), origen);
        if (destino.isEmpty()) {
            return Optional.empty();
        }
        return servicioRutas.calcularRuta(origen, destino.get());
    }

    /**
     * Crea un cliente nuevo, validando que el teléfono no esté duplicado. La geocodificación es
     * "best effort" (ver CLAUDE.md, sección Domicilios): si Mapbox no está configurado, no encuentra
     * la dirección, o falla la red, el cliente se guarda igual -- solo con lat/lng en NULL, nunca
     * bloquea el registro.
     */
    public Cliente crear(String nombre, String telefono, String direccion, int sucursalId) {
        if (clienteDAO.obtenerPorTelefono(telefono).isPresent()) {
            throw new ClienteDuplicadoException(telefono);
        }

        Cliente cliente = new Cliente();
        cliente.setNombre(nombre);
        cliente.setTelefono(telefono);
        cliente.setDireccion(direccion);

        try {
            Sucursal sucursal = sucursalDAO.obtenerPorId(sucursalId).orElse(null);
            String direccionParaGeocodificar = sucursal == null ? direccion : completarDireccion(direccion, sucursal);
            Coordenadas cercaDe = sucursal == null || sucursal.getLatitud() == null || sucursal.getLongitud() == null
                    ? null
                    : new Coordenadas(sucursal.getLatitud(), sucursal.getLongitud());
            geocodificador.geocodificar(direccionParaGeocodificar, cercaDe).ifPresent(coordenadas -> {
                cliente.setLatitud(coordenadas.getLatitud());
                cliente.setLongitud(coordenadas.getLongitud());
            });
        } catch (RuntimeException geocodificacionFallida) {
            // Sin coordenadas por ahora; el cliente se guarda de todas formas.
        }

        return clienteDAO.crear(cliente);
    }

    /** Le pega ciudad/estado/país de la sucursal a la dirección capturada -- mejora mucho la precisión de Mapbox. */
    private String completarDireccion(String direccion, Sucursal sucursal) {
        return direccion + ", " + sucursal.getCiudad() + ", " + sucursal.getEstado() + ", " + sucursal.getPais();
    }

    public static class ClienteDuplicadoException extends RuntimeException {
        public ClienteDuplicadoException(String telefono) {
            super("Cliente duplicado: " + telefono);
        }
    }

    public static class SucursalSinCoordenadasException extends RuntimeException {
        public SucursalSinCoordenadasException() {
            super("La sucursal activa no tiene latitud/longitud configuradas.");
        }
    }
}
