package mx.edu.utch.melo.service;

import mx.edu.utch.melo.dao.ClienteDAO;
import mx.edu.utch.melo.dao.SucursalDAO;
import mx.edu.utch.melo.geo.Coordenadas;
import mx.edu.utch.melo.geo.Geocodificador;
import mx.edu.utch.melo.geo.Ruta;
import mx.edu.utch.melo.geo.ServicioRutas;
import mx.edu.utch.melo.model.Cliente;
import mx.edu.utch.melo.model.Sucursal;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClienteServiceTest {

    @Test
    void crearLanzaSiElTelefonoYaEstaRegistrado() {
        ClienteDAOFalso clienteDAO = new ClienteDAOFalso();
        clienteDAO.existentePorTelefono = new Cliente();
        ClienteService service = new ClienteService(clienteDAO, new SucursalDAOFalso(),
                direccion -> Optional.empty(), (origen, destino) -> Optional.empty());

        assertThrows(ClienteService.ClienteDuplicadoException.class, () ->
                service.crear("Juan", "3001234567", "Calle 1", 1));
        assertNull(clienteDAO.ultimoCreado, "no debería llegar a crear nada si el teléfono ya existe");
    }

    @Test
    void crearGuardaElClienteAunSiLaGeocodificacionNoEncuentraNada() {
        ClienteDAOFalso clienteDAO = new ClienteDAOFalso();
        SucursalDAOFalso sucursalDAO = new SucursalDAOFalso();
        sucursalDAO.sucursal = sucursalConCoordenadas();
        ClienteService service = new ClienteService(clienteDAO, sucursalDAO,
                direccion -> Optional.empty(), (origen, destino) -> Optional.empty());

        Cliente creado = service.crear("Juan", "3001234567", "Dirección rarísima", 1);

        assertEquals("Juan", creado.getNombre());
        assertNull(creado.getLatitud(), "sin geocodificación exitosa, no debería bloquear el registro");
        assertNull(creado.getLongitud());
    }

    @Test
    void crearGuardaElClienteAunSiElGeocodificadorLanzaUnaExcepcion() {
        // "best effort" (ver CLAUDE.md, Domicilios): sin internet, sin token de Mapbox, etc. -- el
        // registro del cliente nunca debe bloquearse por esto.
        ClienteDAOFalso clienteDAO = new ClienteDAOFalso();
        SucursalDAOFalso sucursalDAO = new SucursalDAOFalso();
        sucursalDAO.sucursal = sucursalConCoordenadas();
        Geocodificador geocodificadorQueFalla = new Geocodificador() {
            @Override
            public Optional<Coordenadas> geocodificar(String direccion) {
                throw new IllegalStateException("sin token de Mapbox configurado");
            }
        };
        ClienteService service = new ClienteService(clienteDAO, sucursalDAO,
                geocodificadorQueFalla, (origen, destino) -> Optional.empty());

        Cliente creado = service.crear("Juan", "3001234567", "Calle 1", 1);

        assertEquals("Juan", creado.getNombre());
        assertEquals(clienteDAO.ultimoCreado, creado);
    }

    @Test
    void crearGuardaLasCoordenadasCuandoLaGeocodificacionTieneExito() {
        ClienteDAOFalso clienteDAO = new ClienteDAOFalso();
        SucursalDAOFalso sucursalDAO = new SucursalDAOFalso();
        sucursalDAO.sucursal = sucursalConCoordenadas();
        Coordenadas coordenadasEncontradas = new Coordenadas(new BigDecimal("28.65"), new BigDecimal("-106.12"));
        ClienteService service = new ClienteService(clienteDAO, sucursalDAO,
                direccion -> Optional.of(coordenadasEncontradas), (origen, destino) -> Optional.empty());

        Cliente creado = service.crear("Juan", "3001234567", "Calle 1", 1);

        assertEquals(new BigDecimal("28.65"), creado.getLatitud());
        assertEquals(new BigDecimal("-106.12"), creado.getLongitud());
    }

    @Test
    void calcularRutaLanzaSiLaSucursalNoTieneCoordenadas() {
        SucursalDAOFalso sucursalDAO = new SucursalDAOFalso();
        sucursalDAO.sucursal = new Sucursal();
        sucursalDAO.sucursal.setLatitud(null);
        sucursalDAO.sucursal.setLongitud(null);
        ClienteService service = new ClienteService(new ClienteDAOFalso(), sucursalDAO,
                direccion -> Optional.empty(), (origen, destino) -> Optional.empty());

        assertThrows(ClienteService.SucursalSinCoordenadasException.class, () ->
                service.calcularRuta("Calle 1", 1));
    }

    @Test
    void calcularRutaRegresaVacioSiNoSePudoGeocodificarLaDireccion() {
        SucursalDAOFalso sucursalDAO = new SucursalDAOFalso();
        sucursalDAO.sucursal = sucursalConCoordenadas();
        ClienteService service = new ClienteService(new ClienteDAOFalso(), sucursalDAO,
                direccion -> Optional.empty(), (origen, destino) -> Optional.empty());

        assertTrue(service.calcularRuta("Dirección inexistente", 1).isEmpty());
    }

    @Test
    void calcularRutaRegresaLaRutaCuandoTodoSaleBien() {
        SucursalDAOFalso sucursalDAO = new SucursalDAOFalso();
        sucursalDAO.sucursal = sucursalConCoordenadas();
        Ruta rutaEsperada = new Ruta(new BigDecimal("4.2"), "https://ejemplo/mapa.png");
        Coordenadas destino = new Coordenadas(new BigDecimal("28.70"), new BigDecimal("-106.10"));
        ClienteService service = new ClienteService(new ClienteDAOFalso(), sucursalDAO,
                direccion -> Optional.of(destino), (origen, destinoParam) -> Optional.of(rutaEsperada));

        Optional<Ruta> resultado = service.calcularRuta("Calle 1", 1);

        assertTrue(resultado.isPresent());
        assertEquals(new BigDecimal("4.2"), resultado.get().distanciaKm());
    }

    private static Sucursal sucursalConCoordenadas() {
        Sucursal sucursal = new Sucursal();
        sucursal.setLatitud(new BigDecimal("28.6580350"));
        sucursal.setLongitud(new BigDecimal("-106.1239400"));
        sucursal.setCiudad("Chihuahua");
        sucursal.setEstado("Chihuahua");
        sucursal.setPais("México");
        return sucursal;
    }

    private static class ClienteDAOFalso implements ClienteDAO {
        Cliente existentePorTelefono;
        Cliente ultimoCreado;

        @Override
        public Cliente crear(Cliente cliente) {
            this.ultimoCreado = cliente;
            return cliente;
        }

        @Override
        public Optional<Cliente> obtenerPorId(Integer id) {
            return Optional.empty();
        }

        @Override
        public List<Cliente> obtenerTodos() {
            return List.of();
        }

        @Override
        public boolean actualizar(Cliente cliente) {
            return true;
        }

        @Override
        public boolean eliminar(Integer id) {
            return true;
        }

        @Override
        public Optional<Cliente> obtenerPorTelefono(String telefono) {
            return Optional.ofNullable(existentePorTelefono);
        }
    }

    private static class SucursalDAOFalso implements SucursalDAO {
        Sucursal sucursal;

        @Override
        public Sucursal crear(Sucursal sucursal) {
            return sucursal;
        }

        @Override
        public Optional<Sucursal> obtenerPorId(Integer id) {
            return Optional.ofNullable(sucursal);
        }

        @Override
        public List<Sucursal> obtenerTodos() {
            return List.of();
        }

        @Override
        public boolean actualizar(Sucursal sucursal) {
            return true;
        }

        @Override
        public boolean eliminar(Integer id) {
            return true;
        }

        @Override
        public List<Sucursal> obtenerActivas() {
            return List.of();
        }
    }
}
