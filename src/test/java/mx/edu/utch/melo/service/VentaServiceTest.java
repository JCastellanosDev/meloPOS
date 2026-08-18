package mx.edu.utch.melo.service;

import mx.edu.utch.melo.dao.DetalleOrdenDAO;
import mx.edu.utch.melo.dao.OrdenDAO;
import mx.edu.utch.melo.db.TrabajoTransaccional;
import mx.edu.utch.melo.db.Transaccionador;
import mx.edu.utch.melo.model.DetalleOrden;
import mx.edu.utch.melo.model.EstadoOrden;
import mx.edu.utch.melo.model.ItemOrden;
import mx.edu.utch.melo.model.ModificadorAplicado;
import mx.edu.utch.melo.model.Orden;
import mx.edu.utch.melo.model.TipoOrden;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VentaServiceTest {

    private static final Transaccionador TRANSACCIONADOR_FALSO = new Transaccionador() {
        @Override
        public <T> T ejecutarEnTransaccion(TrabajoTransaccional<T> trabajo) {
            try {
                return trabajo.ejecutar(null);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    };

    @Test
    void crearOrdenComedorCalculaSubtotalIvaYTotal() {
        OrdenDAOFalso ordenDAO = new OrdenDAOFalso();
        DetalleOrdenDAOFalso detalleDAO = new DetalleOrdenDAOFalso();
        VentaService service = new VentaService(ordenDAO, detalleDAO, TRANSACCIONADOR_FALSO);

        List<ItemOrden> items = List.of(
                new ItemOrden(1, "Tacos al Pastor", new BigDecimal("145.00"), 2),
                new ItemOrden(2, "Agua de Jamaica", new BigDecimal("30.00"), 1)
        );

        Orden creada = service.crearOrdenComedor(9, items, true);

        // subtotal = 145*2 + 30 = 320; iva = 320*0.16 = 51.20; total = 371.20
        assertEquals(0, new BigDecimal("320.00").compareTo(creada.getSubtotal()));
        assertEquals(0, new BigDecimal("51.20").compareTo(creada.getImpuestos()));
        assertEquals(0, new BigDecimal("371.20").compareTo(creada.getTotal()));
    }

    @Test
    void crearOrdenComedorSinIvaNoAgregaImpuesto() {
        OrdenDAOFalso ordenDAO = new OrdenDAOFalso();
        VentaService service = new VentaService(ordenDAO, new DetalleOrdenDAOFalso(), TRANSACCIONADOR_FALSO);
        List<ItemOrden> items = List.of(new ItemOrden(1, "Tacos", new BigDecimal("100.00"), 1));

        Orden creada = service.crearOrdenComedor(9, items, false);

        assertEquals(0, BigDecimal.ZERO.compareTo(creada.getImpuestos()));
        assertEquals(0, new BigDecimal("100.00").compareTo(creada.getTotal()));
    }

    @Test
    void crearOrdenComedorArmaLaOrdenSegunElCanalComedor() {
        OrdenDAOFalso ordenDAO = new OrdenDAOFalso();
        VentaService service = new VentaService(ordenDAO, new DetalleOrdenDAOFalso(), TRANSACCIONADOR_FALSO);

        Orden creada = service.crearOrdenComedor(9, List.of(new ItemOrden(1, "Tacos", new BigDecimal("100.00"), 1)), true);

        assertEquals(TipoOrden.COMEDOR, creada.getTipoOrden());
        assertEquals(EstadoOrden.PENDIENTE, creada.getEstado(), "comedor se cobra antes de preparar (ver CLAUDE.md)");
        assertNull(creada.getMesaId());
        assertNull(creada.getClienteId());
        assertNull(creada.getTurnoId());
        assertNull(creada.getDistanciaKm());
        assertEquals(0, BigDecimal.ZERO.compareTo(creada.getCostoEnvio()));
    }

    @Test
    void crearOrdenDomicilioEntraDirectoAEnPreparacionYSumaElCostoDeEnvio() {
        OrdenDAOFalso ordenDAO = new OrdenDAOFalso();
        VentaService service = new VentaService(ordenDAO, new DetalleOrdenDAOFalso(), TRANSACCIONADOR_FALSO);
        List<ItemOrden> items = List.of(new ItemOrden(1, "Tacos", new BigDecimal("100.00"), 1));

        Orden creada = service.crearOrdenDomicilio(9, 55, items, true,
                new BigDecimal("4.2"), new BigDecimal("50.00"));

        assertEquals(TipoOrden.DOMICILIO, creada.getTipoOrden());
        assertEquals(EstadoOrden.EN_PREPARACION, creada.getEstado(),
                "domicilio se cobra al entregar, no antes de preparar (ver CLAUDE.md)");
        assertEquals(55, creada.getClienteId());
        assertEquals(0, new BigDecimal("4.2").compareTo(creada.getDistanciaKm()));
        assertEquals(0, new BigDecimal("50.00").compareTo(creada.getCostoEnvio()));
        // subtotal 100 + iva 16 = 116, + envío 50 = 166
        assertEquals(0, new BigDecimal("166.00").compareTo(creada.getTotal()));
    }

    @Test
    void persistirCreaUnDetallePorCadaArticuloConSuNota() {
        OrdenDAOFalso ordenDAO = new OrdenDAOFalso();
        DetalleOrdenDAOFalso detalleDAO = new DetalleOrdenDAOFalso();
        VentaService service = new VentaService(ordenDAO, detalleDAO, TRANSACCIONADOR_FALSO);
        ItemOrden conNota = new ItemOrden(1, "Tacos", new BigDecimal("100.00"), 2);
        conNota.setNota("sin cebolla");
        ItemOrden sinNota = new ItemOrden(2, "Agua", new BigDecimal("30.00"), 1);
        sinNota.setNota("   ");

        service.crearOrdenComedor(9, List.of(conNota, sinNota), true);

        assertEquals(2, detalleDAO.creados.size());
        assertEquals("sin cebolla", detalleDAO.creados.get(0).getNota());
        assertNull(detalleDAO.creados.get(1).getNota(), "una nota en blanco debe guardarse como null, no como texto vacío");
        assertEquals(2, detalleDAO.creados.get(0).getCantidad());
        assertEquals(0, new BigDecimal("100.00").compareTo(detalleDAO.creados.get(0).getPrecioUnitario()));
    }

    @Test
    void siguienteNumeroOrdenDelegaAlDao() {
        OrdenDAOFalso ordenDAO = new OrdenDAOFalso();
        ordenDAO.siguienteNumero = 42;
        VentaService service = new VentaService(ordenDAO, new DetalleOrdenDAOFalso(), TRANSACCIONADOR_FALSO);

        assertEquals(42, service.siguienteNumeroOrden(1));
    }

    @Test
    void siFallaLaInsercionDeUnDetalleLaExcepcionSePropaga() {
        OrdenDAOFalso ordenDAO = new OrdenDAOFalso();
        DetalleOrdenDAOFalso detalleDAO = new DetalleOrdenDAOFalso();
        detalleDAO.lanzarEnElSegundo = true;
        VentaService service = new VentaService(ordenDAO, detalleDAO, TRANSACCIONADOR_FALSO);
        List<ItemOrden> items = List.of(
                new ItemOrden(1, "Tacos", new BigDecimal("100.00"), 1),
                new ItemOrden(2, "Agua", new BigDecimal("30.00"), 1)
        );

        assertThrows(RuntimeException.class, () -> service.crearOrdenComedor(9, items, true));
    }

    @Test
    void crearOrdenComedorRechazaUnaListaDeArticulosVacia() {
        // ver auditoría de Fase 7: la guarda de "carrito vacío" hoy solo vive en los Controllers
        // (MenuPOSController/MenuPedidoController) -- el Service no debe confiar en que la repitan.
        OrdenDAOFalso ordenDAO = new OrdenDAOFalso();
        VentaService service = new VentaService(ordenDAO, new DetalleOrdenDAOFalso(), TRANSACCIONADOR_FALSO);

        assertThrows(IllegalArgumentException.class, () -> service.crearOrdenComedor(9, List.of(), true));
        assertNull(ordenDAO.ultimoCreado, "no debe llegar a persistir nada si la lista está vacía");
    }

    @Test
    void crearOrdenDomicilioRechazaUnaListaDeArticulosVacia() {
        OrdenDAOFalso ordenDAO = new OrdenDAOFalso();
        VentaService service = new VentaService(ordenDAO, new DetalleOrdenDAOFalso(), TRANSACCIONADOR_FALSO);

        assertThrows(IllegalArgumentException.class, () ->
                service.crearOrdenDomicilio(9, 55, List.of(), true, new BigDecimal("4.2"), new BigDecimal("50.00")));
        assertNull(ordenDAO.ultimoCreado);
    }

    private static class OrdenDAOFalso implements OrdenDAO {
        int siguienteNumero;
        Orden ultimoCreado;

        @Override
        public Orden crear(Orden orden) {
            return crear(orden, null);
        }

        @Override
        public Orden crear(Orden orden, Connection conexion) {
            orden.setId(1);
            this.ultimoCreado = orden;
            return orden;
        }

        @Override
        public Optional<Orden> obtenerPorId(Integer id) {
            return Optional.ofNullable(ultimoCreado);
        }

        @Override
        public Optional<Orden> obtenerPorId(Integer id, Connection conexion) {
            return obtenerPorId(id);
        }

        @Override
        public List<Orden> obtenerTodos() {
            return List.of();
        }

        @Override
        public boolean actualizar(Orden orden) {
            return true;
        }

        @Override
        public boolean actualizar(Orden orden, Connection conexion) {
            return true;
        }

        @Override
        public boolean eliminar(Integer id) {
            return true;
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
        public List<Orden> obtenerActivasPorTipo(TipoOrden tipoOrden) {
            return List.of();
        }

        @Override
        public int siguienteNumeroOrden(int sucursalId) {
            return siguienteNumero;
        }
    }

    private static class DetalleOrdenDAOFalso implements DetalleOrdenDAO {
        final List<DetalleOrden> creados = new ArrayList<>();
        boolean lanzarEnElSegundo;

        @Override
        public DetalleOrden crear(DetalleOrden detalle) {
            return crear(detalle, null);
        }

        @Override
        public DetalleOrden crear(DetalleOrden detalle, Connection conexion) {
            if (lanzarEnElSegundo && creados.size() == 1) {
                throw new RuntimeException("fallo simulado al insertar el segundo detalle");
            }
            detalle.setId(creados.size() + 1);
            creados.add(detalle);
            return detalle;
        }

        @Override
        public Optional<DetalleOrden> obtenerPorId(Integer id) {
            return Optional.empty();
        }

        @Override
        public List<DetalleOrden> obtenerTodos() {
            return List.of();
        }

        @Override
        public boolean actualizar(DetalleOrden detalle) {
            return true;
        }

        @Override
        public boolean eliminar(Integer id) {
            return true;
        }

        @Override
        public List<DetalleOrden> obtenerPorOrden(int ordenId) {
            return List.copyOf(creados);
        }

        @Override
        public List<ModificadorAplicado> obtenerModificadores(int detalleOrdenId) {
            return List.of();
        }

        @Override
        public void agregarModificador(int detalleOrdenId, int modificadorId, BigDecimal precioExtra) {
        }
    }
}
