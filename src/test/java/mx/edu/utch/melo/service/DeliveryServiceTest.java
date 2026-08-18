package mx.edu.utch.melo.service;

import mx.edu.utch.melo.dao.ClienteDAO;
import mx.edu.utch.melo.dao.OrdenDAO;
import mx.edu.utch.melo.model.Cliente;
import mx.edu.utch.melo.model.EstadoOrden;
import mx.edu.utch.melo.model.Orden;
import mx.edu.utch.melo.model.TipoOrden;

import java.math.BigDecimal;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeliveryServiceTest {

    @Test
    void obtenerPedidosActivosJuntaCadaOrdenConSuCliente() {
        Orden orden1 = ordenDeEjemplo(1, 10, "150.00");
        Orden orden2 = ordenDeEjemplo(2, 11, "80.00");
        OrdenDAOFalso ordenDAO = new OrdenDAOFalso();
        ordenDAO.activas = List.of(orden1, orden2);
        ClienteDAOFalso clienteDAO = new ClienteDAOFalso();
        clienteDAO.porId.put(10, clienteDeEjemplo(10, "Ana Pérez"));
        clienteDAO.porId.put(11, clienteDeEjemplo(11, "Luis Ruiz"));

        DeliveryService service = new DeliveryService(ordenDAO, clienteDAO);

        List<DeliveryService.PedidoActivo> pedidos = service.obtenerPedidosActivos(TipoOrden.DOMICILIO);

        assertEquals(2, pedidos.size());
        assertEquals("Ana Pérez", pedidos.get(0).cliente().getNombre());
        assertEquals("Luis Ruiz", pedidos.get(1).cliente().getNombre());
        assertEquals(TipoOrden.DOMICILIO, ordenDAO.tipoConsultado);
        // Prueba de la optimización N+1 -> 2 consultas: una sola llamada en lote a
        // ClienteDAO.obtenerPorIds con los ids de ambas órdenes, no una por orden.
        assertEquals(1, clienteDAO.llamadasObtenerPorIds.size(),
                "debe resolver todos los clientes en una sola llamada en lote, no una por orden");
        assertEquals(List.of(10, 11), clienteDAO.llamadasObtenerPorIds.get(0).stream().sorted().toList());
    }

    /**
     * obtenerPedidosActivos ahora recibe el TipoOrden como parámetro (antes estaba fijo a
     * DOMICILIO, ver PedidosController -- que lo reutiliza con PARA_RECOGER para el panel de
     * "Pedidos Para Recoger"). Esta prueba usa un tipo distinto de DOMICILIO justamente para
     * comprobar que el Service reenvía el parámetro real al DAO en vez de seguir hardcodeado.
     */
    @Test
    void obtenerPedidosActivosConsultaElTipoQueSeLePasaNoSiempreDomicilio() {
        Orden orden = ordenDeEjemplo(1, 20, "60.00");
        orden.setTipoOrden(TipoOrden.PARA_RECOGER);
        OrdenDAOFalso ordenDAO = new OrdenDAOFalso();
        ordenDAO.activas = List.of(orden);
        ClienteDAOFalso clienteDAO = new ClienteDAOFalso();
        clienteDAO.porId.put(20, clienteDeEjemplo(20, "Marco Díaz"));

        DeliveryService service = new DeliveryService(ordenDAO, clienteDAO);

        List<DeliveryService.PedidoActivo> pedidos = service.obtenerPedidosActivos(TipoOrden.PARA_RECOGER);

        assertEquals(TipoOrden.PARA_RECOGER, ordenDAO.tipoConsultado,
                "el Service debe reenviar el tipo recibido al DAO, no un DOMICILIO fijo");
        assertEquals(1, pedidos.size());
        assertEquals("Marco Díaz", pedidos.get(0).cliente().getNombre());
    }

    /**
     * MenuPedidoController puede crear una orden DOMICILIO sin clienteId cuando el flujo viene
     * de un lugar sin cliente asociado (ver VentaService.crearOrdenDomicilio, clienteId nullable)
     * -- obtenerPedidosActivos no debe intentar resolverlo contra ClienteDAO, ni la tarjeta debe
     * reventar más adelante en DeliveryController.construirTarjetaPedido (que ya trata cliente
     * null como "Sin registrar"). Con el batch fetch, "no consultar" se traduce en pasar una
     * lista de ids vacía a obtenerPorIds -- que en la implementación JDBC real
     * (ClienteDAOImpl.obtenerPorIds) devuelve una lista vacía sin llegar a tocar la base.
     */
    @Test
    void obtenerPedidosActivosNoConsultaClienteSiLaOrdenNoTieneClienteId() {
        Orden ordenSinCliente = ordenDeEjemplo(1, null, "50.00");
        OrdenDAOFalso ordenDAO = new OrdenDAOFalso();
        ordenDAO.activas = List.of(ordenSinCliente);
        ClienteDAOFalso clienteDAO = new ClienteDAOFalso();

        DeliveryService service = new DeliveryService(ordenDAO, clienteDAO);

        List<DeliveryService.PedidoActivo> pedidos = service.obtenerPedidosActivos(TipoOrden.DOMICILIO);

        assertEquals(1, pedidos.size());
        assertNull(pedidos.get(0).cliente());
        assertEquals(1, clienteDAO.llamadasObtenerPorIds.size());
        assertTrue(clienteDAO.llamadasObtenerPorIds.get(0).isEmpty(),
                "sin clienteId en ninguna orden, la lista de ids a resolver debe quedar vacía");
    }

    @Test
    void obtenerPedidosActivosDevuelveClienteNuloSiYaNoExiste() {
        Orden orden = ordenDeEjemplo(1, 99, "50.00");
        OrdenDAOFalso ordenDAO = new OrdenDAOFalso();
        ordenDAO.activas = List.of(orden);
        ClienteDAOFalso clienteDAO = new ClienteDAOFalso();

        DeliveryService service = new DeliveryService(ordenDAO, clienteDAO);

        List<DeliveryService.PedidoActivo> pedidos = service.obtenerPedidosActivos(TipoOrden.DOMICILIO);

        assertNull(pedidos.get(0).cliente());
    }

    /**
     * ver OrdenDAOImpl.SQL_ACTIVAS_POR_TIPO: PENDIENTE, EN_PREPARACION y LISTA cuentan como
     * "activas" (solo PAGADA/CANCELADA/ENTREGADA quedan fuera) -- todos los demás casos de esta
     * clase usan EN_PREPARACION para todo, lo que dejaba sin probar que el Service (y la tarjeta
     * de DeliveryController que consume esto) también funcionan con una orden DOMICILIO que
     * todavía está PENDIENTE de cobrar o que ya quedó LISTA para entregar.
     */
    @Test
    void obtenerPedidosActivosIncluyeOrdenesEnCualquierEstadoActivoNoSoloEnPreparacion() {
        Orden pendiente = ordenDeEjemploConEstado(1, 10, EstadoOrden.PENDIENTE);
        Orden enPreparacion = ordenDeEjemploConEstado(2, 11, EstadoOrden.EN_PREPARACION);
        Orden lista = ordenDeEjemploConEstado(3, 12, EstadoOrden.LISTA);
        OrdenDAOFalso ordenDAO = new OrdenDAOFalso();
        ordenDAO.activas = List.of(pendiente, enPreparacion, lista);
        ClienteDAOFalso clienteDAO = new ClienteDAOFalso();
        clienteDAO.porId.put(10, clienteDeEjemplo(10, "Ana"));
        clienteDAO.porId.put(11, clienteDeEjemplo(11, "Luis"));
        clienteDAO.porId.put(12, clienteDeEjemplo(12, "Eva"));

        DeliveryService service = new DeliveryService(ordenDAO, clienteDAO);

        List<DeliveryService.PedidoActivo> pedidos = service.obtenerPedidosActivos(TipoOrden.DOMICILIO);

        assertEquals(3, pedidos.size(), "PENDIENTE, EN_PREPARACION y LISTA deben aparecer, ninguna se debe filtrar aquí");
        assertEquals(1, clienteDAO.llamadasObtenerPorIds.size(),
                "3 órdenes, 3 clientes distintos, pero una sola llamada en lote");
    }

    /**
     * El filtro real de "qué es una orden activa" vive en SQL (OrdenDAOImpl.SQL_ACTIVAS_POR_TIPO:
     * {@code WHERE tipo_orden = ? AND estado NOT IN ('PAGADA', 'CANCELADA', 'ENTREGADA')}), no en
     * este Service -- DeliveryService confía en que el DAO ya aplicó ese filtro. Este fake replica
     * esa misma condición exacta (en vez de solo devolver una lista precargada a mano, como el
     * resto de las pruebas de esta clase) para verificar que, dado el universo completo de
     * órdenes de todos los tipos y estados, lo que finalmente llega a la pantalla de Delivery es
     * justo el subconjunto que la SQL real produciría -- no una suposición de que "compila, así
     * que ya filtra bien".
     */
    @Test
    void obtenerPedidosActivosReflejaElMismoFiltroDeLaConsultaSqlReal() {
        List<Orden> universo = List.of(
                ordenDeEjemploConTipoYEstado(1, TipoOrden.DOMICILIO, EstadoOrden.PENDIENTE),
                ordenDeEjemploConTipoYEstado(2, TipoOrden.DOMICILIO, EstadoOrden.EN_PREPARACION),
                ordenDeEjemploConTipoYEstado(3, TipoOrden.DOMICILIO, EstadoOrden.LISTA),
                ordenDeEjemploConTipoYEstado(4, TipoOrden.DOMICILIO, EstadoOrden.ENTREGADA), // debe excluirse
                ordenDeEjemploConTipoYEstado(5, TipoOrden.DOMICILIO, EstadoOrden.PAGADA),    // debe excluirse
                ordenDeEjemploConTipoYEstado(6, TipoOrden.DOMICILIO, EstadoOrden.CANCELADA), // debe excluirse
                ordenDeEjemploConTipoYEstado(7, TipoOrden.PARA_RECOGER, EstadoOrden.EN_PREPARACION), // otro tipo
                ordenDeEjemploConTipoYEstado(8, TipoOrden.COMEDOR, EstadoOrden.PENDIENTE) // otro tipo
        );
        OrdenDAOFalsoConFiltroReal ordenDAO = new OrdenDAOFalsoConFiltroReal(universo);
        DeliveryService service = new DeliveryService(ordenDAO, new ClienteDAOFalso());

        List<DeliveryService.PedidoActivo> pedidos = service.obtenerPedidosActivos(TipoOrden.DOMICILIO);

        List<Integer> idsVisibles = pedidos.stream().map(p -> p.orden().getId()).sorted().toList();
        assertEquals(List.of(1, 2, 3), idsVisibles,
                "solo las órdenes DOMICILIO que no están ENTREGADA/PAGADA/CANCELADA deben llegar a la pantalla");
    }

    private Orden ordenDeEjemploConEstado(int id, Integer clienteId, EstadoOrden estado) {
        Orden orden = ordenDeEjemplo(id, clienteId, "100.00");
        orden.setEstado(estado);
        return orden;
    }

    private static Orden ordenDeEjemploConTipoYEstado(int id, TipoOrden tipo, EstadoOrden estado) {
        Orden orden = new Orden();
        orden.setId(id);
        orden.setTipoOrden(tipo);
        orden.setEstado(estado);
        orden.setClienteId(null);
        orden.setTotal(new BigDecimal("100.00"));
        return orden;
    }

    private Orden ordenDeEjemplo(int id, Integer clienteId, String total) {
        Orden orden = new Orden();
        orden.setId(id);
        orden.setTipoOrden(TipoOrden.DOMICILIO);
        orden.setEstado(EstadoOrden.EN_PREPARACION);
        orden.setClienteId(clienteId);
        orden.setTotal(new BigDecimal(total));
        return orden;
    }

    private Cliente clienteDeEjemplo(int id, String nombre) {
        Cliente cliente = new Cliente();
        cliente.setId(id);
        cliente.setNombre(nombre);
        return cliente;
    }

    private static class OrdenDAOFalso implements OrdenDAO {
        List<Orden> activas = List.of();
        TipoOrden tipoConsultado;

        @Override
        public List<Orden> obtenerActivasPorTipo(TipoOrden tipoOrden) {
            this.tipoConsultado = tipoOrden;
            return activas;
        }

        @Override
        public List<Orden> obtenerPorEstado(EstadoOrden estado) {
            return List.of();
        }

        @Override
        public List<Orden> obtenerActivasPorMesa(int mesaId) {
            return List.of();
        }

        @Override
        public int siguienteNumeroOrden(int sucursalId) {
            return 1;
        }

        @Override
        public Orden crear(Orden orden, Connection conexion) {
            throw new UnsupportedOperationException("no usado en esta prueba");
        }

        @Override
        public boolean actualizar(Orden orden, Connection conexion) {
            throw new UnsupportedOperationException("no usado en esta prueba");
        }

        @Override
        public Optional<Orden> obtenerPorId(Integer id, Connection conexion) {
            throw new UnsupportedOperationException("no usado en esta prueba");
        }

        @Override
        public boolean cancelar(int ordenId, Connection conexion) {
            throw new UnsupportedOperationException("no usado en esta prueba");
        }

        @Override
        public Orden crear(Orden entidad) {
            throw new UnsupportedOperationException("no usado en esta prueba");
        }

        @Override
        public Optional<Orden> obtenerPorId(Integer id) {
            throw new UnsupportedOperationException("no usado en esta prueba");
        }

        @Override
        public List<Orden> obtenerTodos() {
            return List.of();
        }

        @Override
        public boolean actualizar(Orden entidad) {
            throw new UnsupportedOperationException("no usado en esta prueba");
        }

        @Override
        public boolean eliminar(Integer id) {
            throw new UnsupportedOperationException("no usado en esta prueba");
        }
    }

    /**
     * A diferencia de OrdenDAOFalso (que devuelve una lista precargada a mano), este fake
     * replica la condición real de OrdenDAOImpl.SQL_ACTIVAS_POR_TIPO sobre un universo completo
     * de órdenes -- para que la prueba verifique el filtro de verdad, no solo que el Service
     * reenvíe lo que se le haya puesto en el fake.
     */
    private static class OrdenDAOFalsoConFiltroReal extends OrdenDAOFalso {
        private final List<Orden> universo;

        OrdenDAOFalsoConFiltroReal(List<Orden> universo) {
            this.universo = universo;
        }

        @Override
        public List<Orden> obtenerActivasPorTipo(TipoOrden tipoOrden) {
            return universo.stream()
                    .filter(orden -> orden.getTipoOrden() == tipoOrden)
                    .filter(orden -> orden.getEstado() != EstadoOrden.PAGADA
                            && orden.getEstado() != EstadoOrden.CANCELADA
                            && orden.getEstado() != EstadoOrden.ENTREGADA)
                    .toList();
        }
    }

    /**
     * obtenerPorId lanza a propósito (en vez de simplemente no usarse): si DeliveryService
     * regresara a resolver clientes uno por uno (el N+1 original), esta prueba lo detectaría de
     * inmediato con una excepción en vez de solo "pasar por casualidad".
     */
    private static class ClienteDAOFalso implements ClienteDAO {
        java.util.Map<Integer, Cliente> porId = new java.util.HashMap<>();
        List<List<Integer>> llamadasObtenerPorIds = new ArrayList<>();

        @Override
        public Optional<Cliente> obtenerPorTelefono(String telefono) {
            return Optional.empty();
        }

        @Override
        public Cliente crear(Cliente entidad) {
            throw new UnsupportedOperationException("no usado en esta prueba");
        }

        @Override
        public Optional<Cliente> obtenerPorId(Integer id) {
            throw new UnsupportedOperationException(
                    "DeliveryService ya no debe resolver clientes uno por uno -- ver obtenerPorIds");
        }

        @Override
        public List<Cliente> obtenerPorIds(List<Integer> ids) {
            llamadasObtenerPorIds.add(List.copyOf(ids));
            return ids.stream()
                    .map(porId::get)
                    .filter(java.util.Objects::nonNull)
                    .toList();
        }

        @Override
        public List<Cliente> obtenerTodos() {
            return List.of();
        }

        @Override
        public boolean actualizar(Cliente entidad) {
            throw new UnsupportedOperationException("no usado en esta prueba");
        }

        @Override
        public boolean eliminar(Integer id) {
            throw new UnsupportedOperationException("no usado en esta prueba");
        }
    }
}
