package mx.edu.utch.melo.service;

import mx.edu.utch.melo.dao.DescuentoDAO;
import mx.edu.utch.melo.dao.DescuentoDAO.ConfiguracionDescuento;
import mx.edu.utch.melo.dao.DetalleOrdenDAO;
import mx.edu.utch.melo.dao.OrdenDAO;
import mx.edu.utch.melo.dao.PagoDAO;
import mx.edu.utch.melo.dao.ProductoDAO;
import mx.edu.utch.melo.dao.SucursalDAO;
import mx.edu.utch.melo.dao.TurnoDAO;
import mx.edu.utch.melo.dao.UsuarioDAO;
import mx.edu.utch.melo.db.TrabajoTransaccional;
import mx.edu.utch.melo.db.Transaccionador;
import mx.edu.utch.melo.model.DetalleOrden;
import mx.edu.utch.melo.model.EstadoOrden;
import mx.edu.utch.melo.model.ItemOrden;
import mx.edu.utch.melo.model.MetodoPago;
import mx.edu.utch.melo.model.Modificador;
import mx.edu.utch.melo.model.ModificadorAplicado;
import mx.edu.utch.melo.model.Orden;
import mx.edu.utch.melo.model.Pago;
import mx.edu.utch.melo.model.Producto;
import mx.edu.utch.melo.model.Rol;
import mx.edu.utch.melo.model.Sucursal;
import mx.edu.utch.melo.model.TipoDescuento;
import mx.edu.utch.melo.model.TipoOrden;
import mx.edu.utch.melo.model.Turno;
import mx.edu.utch.melo.model.Usuario;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de integración a nivel de Service (fakes, sin MySQL real -- ver melo-testing) que cruzan
 * la costura VentaService <-> PagoService, componiendo ambos Service reales sobre los MISMOS fakes
 * (una sola orden viaja de un Service al otro, igual que en producción vía SesionActual.ordenEnProcesoId).
 * VentaServiceTest y PagoServiceTest ya cubren cada Service por separado a fondo; esta clase existe
 * para lo que ninguno de los dos puede probar solo: que crear una venta, cobrarla y (si aplica)
 * intentar cancelarla se comporten de forma consistente entre los dos Service.
 */
class VentaPagoInventarioFlujoTest {

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

    // --- Venta -> Pago: la orden aterriza en el estado correcto según su canal --------------

    @Test
    void ventaComedorSeguidaDePagoDescuentaInventarioYAvanzaAEnPreparacion() {
        OrdenDAOFalso ordenDAO = new OrdenDAOFalso();
        DetalleOrdenDAOFalso detalleDAO = new DetalleOrdenDAOFalso();
        ProductoDAOFalso productoDAO = new ProductoDAOFalso();
        productoDAO.stockDisponible.put(1, 10);
        productoDAO.stockDisponible.put(2, 5);
        PagoDAOFalso pagoDAO = new PagoDAOFalso();
        VentaService ventaService = new VentaService(ordenDAO, detalleDAO, productoDAO, pagoDAO, TRANSACCIONADOR_FALSO);
        PagoService pagoService = new PagoService(ordenDAO, detalleDAO, productoDAO, pagoDAO, new UsuarioDAOFalso(),
                new TurnoDAOFalso(), new SucursalDAOFalso(), new DescuentoDAOFalso(), TRANSACCIONADOR_FALSO);
        List<ItemOrden> items = List.of(
                new ItemOrden(1, "Tacos al Pastor", new BigDecimal("145.00"), 2),
                new ItemOrden(2, "Agua de Jamaica", new BigDecimal("30.00"), 1)
        );

        Orden creada = ventaService.crearOrdenComedor(9, items, true);
        assertEquals(EstadoOrden.PENDIENTE, creada.getEstado(), "comedor nace pendiente de cobro");
        assertEquals(8, productoDAO.stockDisponible.get(1), "la venta ya debe haber descontado el inventario");
        assertEquals(4, productoDAO.stockDisponible.get(2));

        boolean cobrado = pagoService.registrarPago(creada.getId(), MetodoPago.EFECTIVO, creada.getTotal(), 9);

        assertTrue(cobrado);
        assertEquals(EstadoOrden.EN_PREPARACION, ordenDAO.obtenerPorId(creada.getId()).orElseThrow().getEstado(),
                "comedor se cobra antes de preparar -- el pago es lo que la manda a cocina");
        assertEquals(1, pagoDAO.creados.size());
        // el inventario no debe volver a tocarse al pagar -- ya se descontó al vender.
        assertEquals(8, productoDAO.stockDisponible.get(1));
        assertEquals(4, productoDAO.stockDisponible.get(2));
    }

    @Test
    void ventaDomicilioSeguidaDePagoAvanzaAEntregadaNoAEnPreparacion() {
        OrdenDAOFalso ordenDAO = new OrdenDAOFalso();
        DetalleOrdenDAOFalso detalleDAO = new DetalleOrdenDAOFalso();
        ProductoDAOFalso productoDAO = new ProductoDAOFalso();
        productoDAO.stockDisponible.put(1, 10);
        PagoDAOFalso pagoDAO = new PagoDAOFalso();
        VentaService ventaService = new VentaService(ordenDAO, detalleDAO, productoDAO, pagoDAO, TRANSACCIONADOR_FALSO);
        PagoService pagoService = new PagoService(ordenDAO, detalleDAO, productoDAO, pagoDAO, new UsuarioDAOFalso(),
                new TurnoDAOFalso(), new SucursalDAOFalso(), new DescuentoDAOFalso(), TRANSACCIONADOR_FALSO);
        List<ItemOrden> items = List.of(new ItemOrden(1, "Tacos", new BigDecimal("100.00"), 1));

        Orden creada = ventaService.crearOrdenDomicilio(9, 55, items, true, new BigDecimal("4.2"), new BigDecimal("50.00"));
        assertEquals(EstadoOrden.EN_PREPARACION, creada.getEstado(), "domicilio se cobra al entregar, no antes");

        pagoService.registrarPago(creada.getId(), MetodoPago.TARJETA, creada.getTotal(), 9);

        assertEquals(EstadoOrden.ENTREGADA, ordenDAO.obtenerPorId(creada.getId()).orElseThrow().getEstado(),
                "el pago es el último paso del canal domicilio -- no debe resucitarla como EN_PREPARACION");
    }

    @Test
    void pagoConMontoIncorrectoSobreUnaVentaRecienCreadaNoCreaPagoNiAlteraLaOrden() {
        OrdenDAOFalso ordenDAO = new OrdenDAOFalso();
        DetalleOrdenDAOFalso detalleDAO = new DetalleOrdenDAOFalso();
        ProductoDAOFalso productoDAO = new ProductoDAOFalso();
        productoDAO.stockDisponible.put(1, 10);
        PagoDAOFalso pagoDAO = new PagoDAOFalso();
        VentaService ventaService = new VentaService(ordenDAO, detalleDAO, productoDAO, pagoDAO, TRANSACCIONADOR_FALSO);
        PagoService pagoService = new PagoService(ordenDAO, detalleDAO, productoDAO, pagoDAO, new UsuarioDAOFalso(),
                new TurnoDAOFalso(), new SucursalDAOFalso(), new DescuentoDAOFalso(), TRANSACCIONADOR_FALSO);

        Orden creada = ventaService.crearOrdenComedor(9, List.of(new ItemOrden(1, "Tacos", new BigDecimal("100.00"), 1)), true);
        // total real 116.00; el cajero teclea un monto que no cuadra.
        assertThrows(PagoService.MontoPagadoNoCoincideException.class,
                () -> pagoService.registrarPago(creada.getId(), MetodoPago.EFECTIVO, new BigDecimal("100.00"), 9));

        assertTrue(pagoDAO.creados.isEmpty(), "un monto que no cuadra no debe crear ningún Pago");
        assertEquals(EstadoOrden.PENDIENTE, ordenDAO.obtenerPorId(creada.getId()).orElseThrow().getEstado(),
                "la orden debe seguir pendiente de cobro, sin avanzar a cocina");
        assertEquals(9, productoDAO.stockDisponible.get(1), "el inventario ya descontado por la venta no debe tocarse otra vez");
    }

    // --- La costura venta/pago/cancelación: guardias mutuamente consistentes -----------------

    /**
     * Bug real encontrado y corregido en esta sesión (ver Javadoc de VentaService.cancelarOrden):
     * COMEDOR/PARA_LLEVAR quedan en EN_PREPARACION al pagar (PagoService.estadoTrasPago) -- el
     * MISMO estado en el que una orden DOMICILIO/PARA_RECOGER nace sin pagar, y EN_PREPARACION
     * está en la lista de "estados cancelables" de VentaService. Antes del fix, este flujo completo
     * (crear -> pagar -> cancelar) tenía éxito en el tercer paso: la cancelación revertía el stock
     * de una venta ya cobrada sin tocar el Pago ya registrado. Este test recorre el flujo real de
     * punta a punta (los dos Service reales sobre los mismos fakes) para probar que ahora se
     * rechaza.
     */
    @Test
    void unaOrdenComedorYaPagadaNoPuedeCancelarseAunqueQuedeEnPreparacion() {
        OrdenDAOFalso ordenDAO = new OrdenDAOFalso();
        DetalleOrdenDAOFalso detalleDAO = new DetalleOrdenDAOFalso();
        ProductoDAOFalso productoDAO = new ProductoDAOFalso();
        productoDAO.stockDisponible.put(1, 10);
        PagoDAOFalso pagoDAO = new PagoDAOFalso();
        VentaService ventaService = new VentaService(ordenDAO, detalleDAO, productoDAO, pagoDAO, TRANSACCIONADOR_FALSO);
        PagoService pagoService = new PagoService(ordenDAO, detalleDAO, productoDAO, pagoDAO, new UsuarioDAOFalso(),
                new TurnoDAOFalso(), new SucursalDAOFalso(), new DescuentoDAOFalso(), TRANSACCIONADOR_FALSO);

        Orden creada = ventaService.crearOrdenComedor(9, List.of(new ItemOrden(1, "Tacos", new BigDecimal("100.00"), 3)), true);
        pagoService.registrarPago(creada.getId(), MetodoPago.EFECTIVO, creada.getTotal(), 9);
        assertEquals(EstadoOrden.EN_PREPARACION, ordenDAO.obtenerPorId(creada.getId()).orElseThrow().getEstado());
        assertEquals(7, productoDAO.stockDisponible.get(1), "la venta descontó 3 unidades");

        assertThrows(VentaService.OrdenYaPagadaException.class,
                () -> ventaService.cancelarOrden(Rol.CAJERO, creada.getId()));

        assertEquals(EstadoOrden.EN_PREPARACION, ordenDAO.obtenerPorId(creada.getId()).orElseThrow().getEstado(),
                "la orden ya pagada no debe quedar CANCELADA");
        assertEquals(7, productoDAO.stockDisponible.get(1),
                "no debe restaurarse stock de una venta ya cobrada sin revertir el pago");
        assertEquals(1, pagoDAO.creados.size(), "el pago ya registrado debe seguir intacto");
    }

    /**
     * Contraparte "bien portada" del test anterior (domicilio/para_recoger, cuyo pago es el ÚLTIMO
     * paso del canal): una vez pagada, la orden queda ENTREGADA -- ya rechazada por
     * estadoEsperadoAntesDePagar (segundo cobro) y ahora también por el chequeo nuevo de Pago ya
     * registrado (cancelación) -- ambas guardias, de Service distintos, coinciden en que esta
     * orden ya no es tocable por ninguna de las dos operaciones.
     */
    @Test
    void unaOrdenDomicilioYaPagadaRechazaUnSegundoCobroYUnaCancelacion() {
        OrdenDAOFalso ordenDAO = new OrdenDAOFalso();
        DetalleOrdenDAOFalso detalleDAO = new DetalleOrdenDAOFalso();
        ProductoDAOFalso productoDAO = new ProductoDAOFalso();
        productoDAO.stockDisponible.put(1, 10);
        PagoDAOFalso pagoDAO = new PagoDAOFalso();
        VentaService ventaService = new VentaService(ordenDAO, detalleDAO, productoDAO, pagoDAO, TRANSACCIONADOR_FALSO);
        PagoService pagoService = new PagoService(ordenDAO, detalleDAO, productoDAO, pagoDAO, new UsuarioDAOFalso(),
                new TurnoDAOFalso(), new SucursalDAOFalso(), new DescuentoDAOFalso(), TRANSACCIONADOR_FALSO);

        Orden creada = ventaService.crearOrdenDomicilio(9, 55,
                List.of(new ItemOrden(1, "Tacos", new BigDecimal("100.00"), 1)), true,
                new BigDecimal("4.2"), new BigDecimal("50.00"));
        pagoService.registrarPago(creada.getId(), MetodoPago.EFECTIVO, creada.getTotal(), 9);
        assertEquals(EstadoOrden.ENTREGADA, ordenDAO.obtenerPorId(creada.getId()).orElseThrow().getEstado());

        assertThrows(PagoService.OrdenYaFueCobradaException.class,
                () -> pagoService.registrarPago(creada.getId(), MetodoPago.EFECTIVO, creada.getTotal(), 9));
        assertThrows(VentaService.OrdenYaPagadaException.class,
                () -> ventaService.cancelarOrden(Rol.CAJERO, creada.getId()));

        assertEquals(1, pagoDAO.creados.size(), "ningún intento posterior debe agregar un segundo pago");
        assertEquals(EstadoOrden.ENTREGADA, ordenDAO.obtenerPorId(creada.getId()).orElseThrow().getEstado());
    }

    @Test
    void unaOrdenDomicilioSinPagarSiguePudiendoseCancelarConNormalidad() {
        // contraste con los dos tests anteriores: el chequeo nuevo de VentaService.cancelarOrden
        // se fija en si HAY un Pago, no en el EstadoOrden -- una orden domicilio recién creada
        // (EN_PREPARACION, sin pagar todavía) debe poder cancelarse igual que antes del fix.
        OrdenDAOFalso ordenDAO = new OrdenDAOFalso();
        DetalleOrdenDAOFalso detalleDAO = new DetalleOrdenDAOFalso();
        ProductoDAOFalso productoDAO = new ProductoDAOFalso();
        productoDAO.stockDisponible.put(1, 10);
        PagoDAOFalso pagoDAO = new PagoDAOFalso();
        VentaService ventaService = new VentaService(ordenDAO, detalleDAO, productoDAO, pagoDAO, TRANSACCIONADOR_FALSO);

        Orden creada = ventaService.crearOrdenDomicilio(9, 55,
                List.of(new ItemOrden(1, "Tacos", new BigDecimal("100.00"), 1)), true,
                new BigDecimal("4.2"), new BigDecimal("50.00"));
        assertEquals(9, productoDAO.stockDisponible.get(1));

        Orden cancelada = ventaService.cancelarOrden(Rol.CAJERO, creada.getId());

        assertEquals(EstadoOrden.CANCELADA, cancelada.getEstado());
        assertEquals(10, productoDAO.stockDisponible.get(1), "sin ningún pago registrado, el stock sigue restaurándose con normalidad");
    }

    // --- Rollback real (Transaccionador que sí revierte, no solo propaga la excepción) -------

    /**
     * VentaServiceTest ya prueba que un artículo SIN stock no descuenta nada del artículo que
     * nunca llegó a procesarse (ver persistirNoDescuentaStockDeArticulosPosterioresAlQueFalloElStock),
     * pero ese test usa el Transaccionador de "solo propagar" (ver melo-testing) -- no puede probar
     * que el descuento de un artículo que SÍ tuvo éxito ANTES del que falla también se revierte,
     * porque ese fake nunca deshace nada (el comentario del propio test lo reconoce). Aquí se usa
     * un Transaccionador que sí simula el contrato real de ConexionDB (revierte todo lo escrito en
     * la transacción si algo lanza) para poder probar esa parte más fuerte del escenario: "no queda
     * orden persistida, stock intacto" de verdad, no solo el artículo que falló.
     */
    @Test
    void ventaMultiArticuloRevierteElStockDelArticuloQueSiTuvoExitoSiOtroFallaDespues() {
        OrdenDAOFalso ordenDAO = new OrdenDAOFalso();
        DetalleOrdenDAOFalso detalleDAO = new DetalleOrdenDAOFalso();
        ProductoDAOFalso productoDAO = new ProductoDAOFalso();
        productoDAO.stockDisponible.put(1, 5); // artículo 1: hay de sobra, se descuenta primero
        productoDAO.stockDisponible.put(2, 1); // artículo 2: no alcanza para las 2 unidades pedidas
        PagoDAOFalso pagoDAO = new PagoDAOFalso();
        Transaccionador transaccionadorConRollback =
                new TransaccionadorConRollbackSimulado(productoDAO, ordenDAO, detalleDAO);
        VentaService service = new VentaService(ordenDAO, detalleDAO, productoDAO, pagoDAO, transaccionadorConRollback);
        List<ItemOrden> items = List.of(
                new ItemOrden(1, "Tacos al Pastor", new BigDecimal("145.00"), 2), // se procesa y descuenta bien
                new ItemOrden(2, "Agua de Jamaica", new BigDecimal("30.00"), 2)   // truena aquí
        );

        assertThrows(VentaService.StockInsuficienteException.class, () -> service.crearOrdenComedor(9, items, true));

        assertEquals(5, productoDAO.stockDisponible.get(1),
                "el artículo que sí tuvo éxito antes del que falló debe revertirse también, no solo quedarse descontado");
        assertEquals(1, productoDAO.stockDisponible.get(2), "el artículo que falló nunca llega a descontar nada");
        assertNull(ordenDAO.ultimoCreado, "la orden nunca debe quedar persistida si la transacción completa se revierte");
        assertTrue(detalleDAO.creados.isEmpty(), "tampoco debe quedar ninguna línea de detalle huérfana");
    }

    private static class TransaccionadorConRollbackSimulado implements Transaccionador {
        private final ProductoDAOFalso productoDAO;
        private final OrdenDAOFalso ordenDAO;
        private final DetalleOrdenDAOFalso detalleDAO;

        TransaccionadorConRollbackSimulado(ProductoDAOFalso productoDAO, OrdenDAOFalso ordenDAO, DetalleOrdenDAOFalso detalleDAO) {
            this.productoDAO = productoDAO;
            this.ordenDAO = ordenDAO;
            this.detalleDAO = detalleDAO;
        }

        @Override
        public <T> T ejecutarEnTransaccion(TrabajoTransaccional<T> trabajo) {
            Map<Integer, Integer> stockAntes = new HashMap<>(productoDAO.stockDisponible);
            Orden ordenAntes = ordenDAO.ultimoCreado;
            List<DetalleOrden> detalleAntes = new ArrayList<>(detalleDAO.creados);
            try {
                return trabajo.ejecutar(null);
            } catch (SQLException e) {
                revertir(stockAntes, ordenAntes, detalleAntes);
                throw new RuntimeException(e);
            } catch (RuntimeException e) {
                revertir(stockAntes, ordenAntes, detalleAntes);
                throw e;
            }
        }

        private void revertir(Map<Integer, Integer> stockAntes, Orden ordenAntes, List<DetalleOrden> detalleAntes) {
            productoDAO.stockDisponible.clear();
            productoDAO.stockDisponible.putAll(stockAntes);
            ordenDAO.ultimoCreado = ordenAntes;
            detalleDAO.creados.clear();
            detalleDAO.creados.addAll(detalleAntes);
        }
    }

    // --- Fakes compartidos por VentaService y PagoService en esta clase ----------------------

    private static class OrdenDAOFalso implements OrdenDAO {
        int siguienteId = 1;
        Orden ultimoCreado;

        @Override
        public Orden crear(Orden orden) {
            return crear(orden, null);
        }

        @Override
        public Orden crear(Orden orden, Connection conexion) {
            orden.setId(siguienteId++);
            this.ultimoCreado = orden;
            return orden;
        }

        @Override
        public Optional<Orden> obtenerPorId(Integer id) {
            return Optional.ofNullable(ultimoCreado).filter(o -> o.getId() == id);
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
            return actualizar(orden, null);
        }

        @Override
        public boolean actualizar(Orden orden, Connection conexion) {
            this.ultimoCreado = orden;
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
            return siguienteId;
        }

        /** Simula el UPDATE atómico real (ver OrdenDAO.cancelar): solo cancela desde un estado válido. */
        @Override
        public boolean cancelar(int ordenId, Connection conexion) {
            if (ultimoCreado == null || ultimoCreado.getId() != ordenId) {
                return false;
            }
            EstadoOrden estado = ultimoCreado.getEstado();
            boolean permiteCancelar = estado == EstadoOrden.PENDIENTE || estado == EstadoOrden.EN_PREPARACION
                    || estado == EstadoOrden.LISTA;
            if (!permiteCancelar) {
                return false;
            }
            ultimoCreado.setEstado(EstadoOrden.CANCELADA);
            return true;
        }
    }

    private static class DetalleOrdenDAOFalso implements DetalleOrdenDAO {
        final List<DetalleOrden> creados = new ArrayList<>();

        @Override
        public DetalleOrden crear(DetalleOrden detalle) {
            return crear(detalle, null);
        }

        @Override
        public DetalleOrden crear(DetalleOrden detalle, Connection conexion) {
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
        public List<DetalleOrden> obtenerPorOrden(int ordenId, Connection conexion) {
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

    private static class ProductoDAOFalso implements ProductoDAO {
        final Map<Integer, Integer> stockDisponible = new HashMap<>();

        @Override
        public Producto crear(Producto producto) {
            return producto;
        }

        @Override
        public Optional<Producto> obtenerPorId(Integer id) {
            return Optional.empty();
        }

        @Override
        public List<Producto> obtenerTodos() {
            return List.of();
        }

        @Override
        public boolean actualizar(Producto producto) {
            return true;
        }

        @Override
        public boolean eliminar(Integer id) {
            return true;
        }

        @Override
        public List<Producto> obtenerTodosActivos() {
            return List.of();
        }

        @Override
        public List<Producto> obtenerPorCategoria(int categoriaId) {
            return List.of();
        }

        @Override
        public List<Producto> obtenerPorSucursal(int sucursalId) {
            return List.of();
        }

        @Override
        public List<Modificador> obtenerModificadores(int productoId) {
            return List.of();
        }

        @Override
        public void asignarModificador(int productoId, int modificadorId) {
        }

        @Override
        public void quitarModificador(int productoId, int modificadorId) {
        }

        @Override
        public boolean descontarStock(int productoId, int cantidad, Connection conexion) {
            int actual = stockDisponible.getOrDefault(productoId, 0);
            if (actual < cantidad) {
                return false;
            }
            stockDisponible.put(productoId, actual - cantidad);
            return true;
        }

        @Override
        public void restaurarStock(int productoId, int cantidad, Connection conexion) {
            int actual = stockDisponible.getOrDefault(productoId, 0);
            stockDisponible.put(productoId, actual + cantidad);
        }
    }

    private static class PagoDAOFalso implements PagoDAO {
        final List<Pago> creados = new ArrayList<>();

        @Override
        public Pago crear(Pago pago) {
            return crear(pago, null);
        }

        @Override
        public Pago crear(Pago pago, Connection conexion) {
            pago.setId(creados.size() + 1);
            creados.add(pago);
            return pago;
        }

        @Override
        public Optional<Pago> obtenerPorId(Integer id) {
            return Optional.empty();
        }

        @Override
        public List<Pago> obtenerTodos() {
            return List.of();
        }

        @Override
        public boolean actualizar(Pago pago) {
            return true;
        }

        @Override
        public boolean eliminar(Integer id) {
            return true;
        }

        @Override
        public List<Pago> obtenerPorOrden(int ordenId) {
            return creados.stream().filter(p -> p.getOrdenId() == ordenId).toList();
        }
    }

    private static class UsuarioDAOFalso implements UsuarioDAO {
        @Override
        public Usuario crear(Usuario usuario) {
            return usuario;
        }

        @Override
        public Optional<Usuario> obtenerPorId(Integer id) {
            return Optional.empty();
        }

        @Override
        public List<Usuario> obtenerTodos() {
            return List.of();
        }

        @Override
        public boolean actualizar(Usuario usuario) {
            return true;
        }

        @Override
        public boolean eliminar(Integer id) {
            return true;
        }

        @Override
        public List<Usuario> obtenerPorSucursal(int sucursalId) {
            return List.of();
        }
    }

    private static class TurnoDAOFalso implements TurnoDAO {
        @Override
        public Turno crear(Turno turno) {
            return turno;
        }

        @Override
        public Optional<Turno> obtenerPorId(Integer id) {
            return Optional.empty();
        }

        @Override
        public List<Turno> obtenerTodos() {
            return List.of();
        }

        @Override
        public boolean actualizar(Turno turno) {
            return true;
        }

        @Override
        public boolean eliminar(Integer id) {
            return true;
        }

        @Override
        public Optional<Turno> obtenerTurnoAbierto(int usuarioId) {
            return Optional.empty();
        }
    }

    private static class SucursalDAOFalso implements SucursalDAO {
        @Override
        public Sucursal crear(Sucursal sucursal) {
            return sucursal;
        }

        @Override
        public Optional<Sucursal> obtenerPorId(Integer id) {
            return Optional.empty();
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

    private static class DescuentoDAOFalso implements DescuentoDAO {
        Map<TipoDescuento, ConfiguracionDescuento> configuraciones = new EnumMap<>(Map.of(
                TipoDescuento.EMPLEADO, new ConfiguracionDescuento("Empleado", new BigDecimal("0.20")),
                TipoDescuento.CORTESIA, new ConfiguracionDescuento("Cortesía", new BigDecimal("1.00")),
                TipoDescuento.PROMOCION, new ConfiguracionDescuento("Promoción", new BigDecimal("0.10")),
                TipoDescuento.AJUSTE, new ConfiguracionDescuento("Ajuste", new BigDecimal("0.05"))
        ));

        @Override
        public BigDecimal obtenerPorcentaje(TipoDescuento tipo) {
            return configuraciones.get(tipo).porcentaje();
        }

        @Override
        public Map<TipoDescuento, ConfiguracionDescuento> obtenerTodos() {
            return configuraciones;
        }

        @Override
        public boolean actualizar(TipoDescuento tipo, String etiqueta, BigDecimal porcentaje) {
            configuraciones.put(tipo, new ConfiguracionDescuento(etiqueta, porcentaje));
            return true;
        }
    }
}
