package mx.edu.utch.melo.service;

import mx.edu.utch.melo.dao.DetalleOrdenDAO;
import mx.edu.utch.melo.dao.OrdenDAO;
import mx.edu.utch.melo.dao.ProductoDAO;
import mx.edu.utch.melo.db.TrabajoTransaccional;
import mx.edu.utch.melo.db.Transaccionador;
import mx.edu.utch.melo.model.DetalleOrden;
import mx.edu.utch.melo.model.EstadoOrden;
import mx.edu.utch.melo.model.ItemOrden;
import mx.edu.utch.melo.model.Modificador;
import mx.edu.utch.melo.model.ModificadorAplicado;
import mx.edu.utch.melo.model.Orden;
import mx.edu.utch.melo.model.Producto;
import mx.edu.utch.melo.model.TipoOrden;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    void crearOrdenDomicilioSinDistanciaSeRegistraComoParaRecogerSinCobrarEnvio() {
        // El mesero no presionó "Ubicar" en Pedidos (ver PedidosController.onUbicar) -- sin ruta
        // calculada no hay forma de cobrar un envío real, así que el canal correcto es "para
        // recoger" (el cliente pasa por su pedido), no domicilio.
        OrdenDAOFalso ordenDAO = new OrdenDAOFalso();
        VentaService service = new VentaService(ordenDAO, new DetalleOrdenDAOFalso(), TRANSACCIONADOR_FALSO);
        List<ItemOrden> items = List.of(new ItemOrden(1, "Tacos", new BigDecimal("100.00"), 1));

        Orden creada = service.crearOrdenDomicilio(9, 55, items, true, null, BigDecimal.ZERO);

        assertEquals(TipoOrden.PARA_RECOGER, creada.getTipoOrden());
        assertNull(creada.getDistanciaKm());
        assertEquals(0, BigDecimal.ZERO.compareTo(creada.getCostoEnvio()));
        // subtotal 100 + iva 16 = 116, sin envío
        assertEquals(0, new BigDecimal("116.00").compareTo(creada.getTotal()));
    }

    @Test
    void crearOrdenDomicilioRechazaUnaListaDeArticulosVacia() {
        OrdenDAOFalso ordenDAO = new OrdenDAOFalso();
        VentaService service = new VentaService(ordenDAO, new DetalleOrdenDAOFalso(), TRANSACCIONADOR_FALSO);

        assertThrows(IllegalArgumentException.class, () ->
                service.crearOrdenDomicilio(9, 55, List.of(), true, new BigDecimal("4.2"), new BigDecimal("50.00")));
        assertNull(ordenDAO.ultimoCreado);
    }

    @Test
    void agregarArticulosRecalculaElTotalSobreLosArticulosExistentesYLosNuevosJuntos() {
        OrdenDAOFalso ordenDAO = new OrdenDAOFalso();
        ordenDAO.ultimoCreado = ordenDomicilioExistente();
        DetalleOrdenDAOFalso detalleDAO = new DetalleOrdenDAOFalso();
        detalleDAO.creados.add(detalleExistente(1, "100.00")); // ya en la orden antes de abrir MenuPedido
        VentaService service = new VentaService(ordenDAO, detalleDAO, TRANSACCIONADOR_FALSO);

        Orden actualizada = service.agregarArticulos(1,
                List.of(new ItemOrden(2, "Agua de Jamaica", new BigDecimal("30.00"), 1)), true);

        // subtotal = 100 (ya existía) + 30 (nuevo) = 130; iva = 130*0.16 = 20.80; total = 130+20.80+50 (envío) = 200.80
        assertEquals(2, detalleDAO.creados.size(), "no debe re-insertar el artículo que ya existía, solo el nuevo");
        assertEquals(0, new BigDecimal("130.00").compareTo(actualizada.getSubtotal()));
        assertEquals(0, new BigDecimal("20.80").compareTo(actualizada.getImpuestos()));
        assertEquals(0, new BigDecimal("200.80").compareTo(actualizada.getTotal()));
    }

    @Test
    void agregarArticulosPreservaElCostoDeEnvioYLaDistanciaYaCalculados() {
        // ver CLAUDE.md: agregar comida a un pedido a domicilio no cambia la ruta -- el envío no
        // se debe recalcular aquí, solo al crear la orden (ver crearOrdenDomicilio).
        OrdenDAOFalso ordenDAO = new OrdenDAOFalso();
        ordenDAO.ultimoCreado = ordenDomicilioExistente();
        VentaService service = new VentaService(ordenDAO, new DetalleOrdenDAOFalso(), TRANSACCIONADOR_FALSO);

        Orden actualizada = service.agregarArticulos(1,
                List.of(new ItemOrden(2, "Agua de Jamaica", new BigDecimal("30.00"), 1)), true);

        assertEquals(0, new BigDecimal("50.00").compareTo(actualizada.getCostoEnvio()));
        assertEquals(0, new BigDecimal("4.2").compareTo(actualizada.getDistanciaKm()));
    }

    @Test
    void agregarArticulosRechazaUnaListaDeArticulosNuevosVacia() {
        // decisión deliberada: agregar "nada" no es una operación válida -- si el Controller
        // llegara a permitirlo (no debería, ver MenuPedidoController), el Service no debe abrir
        // una transacción ni tocar la orden por una lista vacía.
        OrdenDAOFalso ordenDAO = new OrdenDAOFalso();
        ordenDAO.ultimoCreado = ordenDomicilioExistente();
        VentaService service = new VentaService(ordenDAO, new DetalleOrdenDAOFalso(), TRANSACCIONADOR_FALSO);

        assertThrows(IllegalArgumentException.class, () -> service.agregarArticulos(1, List.of(), true));
    }

    @Test
    void persistirDescuentaElStockDeCadaArticuloVendidoEnLaMismaTransaccion() {
        OrdenDAOFalso ordenDAO = new OrdenDAOFalso();
        DetalleOrdenDAOFalso detalleDAO = new DetalleOrdenDAOFalso();
        ProductoDAOFalso productoDAO = new ProductoDAOFalso();
        productoDAO.stockDisponible.put(1, 10);
        productoDAO.stockDisponible.put(2, 5);
        VentaService service = new VentaService(ordenDAO, detalleDAO, productoDAO, TRANSACCIONADOR_FALSO);
        List<ItemOrden> items = List.of(
                new ItemOrden(1, "Tacos al Pastor", new BigDecimal("145.00"), 2),
                new ItemOrden(2, "Agua de Jamaica", new BigDecimal("30.00"), 1)
        );

        service.crearOrdenComedor(9, items, true);

        assertEquals(8, productoDAO.stockDisponible.get(1), "debe descontar la cantidad vendida, no solo marcar 'vendido'");
        assertEquals(4, productoDAO.stockDisponible.get(2));
    }

    @Test
    void persistirRevierteTodaLaVentaSiUnArticuloNoTieneStockSuficiente() {
        // Postura elegida (ver Javadoc de VentaService.descontarStock, no había regla de negocio
        // previa en CLAUDE.md/código): rechazar la venta completa en vez de dejar vender en
        // negativo -- el UPDATE atómico de ProductoDAO.descontarStock regresa 0 filas cuando no
        // alcanza el stock, y el Service lo convierte en una excepción que revierte la
        // transacción completa.
        OrdenDAOFalso ordenDAO = new OrdenDAOFalso();
        DetalleOrdenDAOFalso detalleDAO = new DetalleOrdenDAOFalso();
        ProductoDAOFalso productoDAO = new ProductoDAOFalso();
        productoDAO.stockDisponible.put(1, 1); // solo queda 1, pero se piden 2
        VentaService service = new VentaService(ordenDAO, detalleDAO, productoDAO, TRANSACCIONADOR_FALSO);
        List<ItemOrden> items = List.of(new ItemOrden(1, "Tacos", new BigDecimal("145.00"), 2));

        assertThrows(VentaService.StockInsuficienteException.class,
                () -> service.crearOrdenComedor(9, items, true));
    }

    @Test
    void persistirNoDescuentaStockDeArticulosPosterioresAlQueFalloElStock() {
        OrdenDAOFalso ordenDAO = new OrdenDAOFalso();
        DetalleOrdenDAOFalso detalleDAO = new DetalleOrdenDAOFalso();
        ProductoDAOFalso productoDAO = new ProductoDAOFalso();
        productoDAO.stockDisponible.put(1, 0); // el primer artículo del carrito ya falla
        productoDAO.stockDisponible.put(2, 5);
        VentaService service = new VentaService(ordenDAO, detalleDAO, productoDAO, TRANSACCIONADOR_FALSO);
        List<ItemOrden> items = List.of(
                new ItemOrden(1, "Tacos", new BigDecimal("145.00"), 1),
                new ItemOrden(2, "Agua", new BigDecimal("30.00"), 1)
        );

        assertThrows(VentaService.StockInsuficienteException.class, () -> service.crearOrdenComedor(9, items, true));

        assertEquals(5, productoDAO.stockDisponible.get(2),
                "no debe descontar artículos que nunca se procesaron tras la falla (aunque aquí, con fakes, "
                        + "el rollback real de la orden/detalle solo lo garantiza ConexionDB -- ver melo-testing)");
    }

    @Test
    void persistirPermiteVenderElUltimoArticuloDejandoElStockEnExactamenteCero() {
        // límite exacto del UPDATE atómico (actual >= cantidad, no actual > cantidad, ver
        // ProductoDAOFalso.descontarStock): vender justo lo que queda debe tener éxito y dejar 0
        // disponible, no rechazarse como si fuera insuficiente ni caer en negativo.
        OrdenDAOFalso ordenDAO = new OrdenDAOFalso();
        DetalleOrdenDAOFalso detalleDAO = new DetalleOrdenDAOFalso();
        ProductoDAOFalso productoDAO = new ProductoDAOFalso();
        productoDAO.stockDisponible.put(1, 2); // quedan exactamente 2, se piden 2
        VentaService service = new VentaService(ordenDAO, detalleDAO, productoDAO, TRANSACCIONADOR_FALSO);
        List<ItemOrden> items = List.of(new ItemOrden(1, "Tacos", new BigDecimal("145.00"), 2));

        Orden creada = service.crearOrdenComedor(9, items, true);

        assertEquals(TipoOrden.COMEDOR, creada.getTipoOrden(), "la venta no debe rechazarse en el límite exacto");
        assertEquals(0, productoDAO.stockDisponible.get(1), "debe quedar en exactamente cero, no en negativo");
    }

    @Test
    void elConstructorDeprecadoSinProductoDAONoIntentaDescontarStockNiLanzaNullPointer() {
        // ver DEPENDENCIA CRUZADA en el reporte final: AppContext hoy sigue construyendo
        // VentaService sin ProductoDAO -- debe seguir funcionando exactamente igual que antes
        // (mismo gap de inventario, nunca un NullPointerException nuevo) hasta que se actualice
        // esa wiring.
        OrdenDAOFalso ordenDAO = new OrdenDAOFalso();
        VentaService service = new VentaService(ordenDAO, new DetalleOrdenDAOFalso(), TRANSACCIONADOR_FALSO);

        Orden creada = service.crearOrdenComedor(9,
                List.of(new ItemOrden(1, "Tacos", new BigDecimal("100.00"), 1)), true);

        assertEquals(TipoOrden.COMEDOR, creada.getTipoOrden());
    }

    @Test
    void agregarArticulosDescuentaStockSoloDeLosArticulosNuevos() {
        OrdenDAOFalso ordenDAO = new OrdenDAOFalso();
        ordenDAO.ultimoCreado = ordenDomicilioExistente();
        DetalleOrdenDAOFalso detalleDAO = new DetalleOrdenDAOFalso();
        detalleDAO.creados.add(detalleExistente(1, "100.00")); // ya en la orden, su stock ya se había descontado
        ProductoDAOFalso productoDAO = new ProductoDAOFalso();
        productoDAO.stockDisponible.put(1, 3); // NO debe tocarse -- ya se descontó al crear la orden
        productoDAO.stockDisponible.put(2, 5);
        VentaService service = new VentaService(ordenDAO, detalleDAO, productoDAO, TRANSACCIONADOR_FALSO);

        service.agregarArticulos(1, List.of(new ItemOrden(2, "Agua de Jamaica", new BigDecimal("30.00"), 1)), true);

        assertEquals(3, productoDAO.stockDisponible.get(1), "el artículo que ya existía en la orden no debe volver a descontarse");
        assertEquals(4, productoDAO.stockDisponible.get(2), "el artículo nuevo sí debe descontar su stock");
    }

    @Test
    void agregarArticulosRevierteSiElArticuloNuevoNoTieneStockSuficiente() {
        OrdenDAOFalso ordenDAO = new OrdenDAOFalso();
        ordenDAO.ultimoCreado = ordenDomicilioExistente();
        DetalleOrdenDAOFalso detalleDAO = new DetalleOrdenDAOFalso();
        detalleDAO.creados.add(detalleExistente(1, "100.00"));
        ProductoDAOFalso productoDAO = new ProductoDAOFalso();
        productoDAO.stockDisponible.put(2, 0);
        VentaService service = new VentaService(ordenDAO, detalleDAO, productoDAO, TRANSACCIONADOR_FALSO);

        assertThrows(VentaService.StockInsuficienteException.class, () -> service.agregarArticulos(1,
                List.of(new ItemOrden(2, "Agua de Jamaica", new BigDecimal("30.00"), 1)), true));
    }

    private static Orden ordenDomicilioExistente() {
        Orden orden = new Orden();
        orden.setId(1);
        orden.setTipoOrden(TipoOrden.DOMICILIO);
        orden.setUsuarioId(9);
        orden.setClienteId(55);
        orden.setEstado(EstadoOrden.EN_PREPARACION);
        orden.setSubtotal(new BigDecimal("100.00"));
        orden.setImpuestos(new BigDecimal("16.00"));
        orden.setDistanciaKm(new BigDecimal("4.2"));
        orden.setCostoEnvio(new BigDecimal("50.00"));
        orden.setTotal(new BigDecimal("166.00"));
        return orden;
    }

    private static DetalleOrden detalleExistente(int productoId, String precioUnitario) {
        DetalleOrden detalle = new DetalleOrden();
        detalle.setOrdenId(1);
        detalle.setProductoId(productoId);
        detalle.setCantidad(1);
        detalle.setPrecioUnitario(new BigDecimal(precioUnitario));
        return detalle;
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

        /** Simula el UPDATE atómico real: descuenta solo si alcanza, regresa false sin lanzar si no. */
        @Override
        public boolean descontarStock(int productoId, int cantidad, Connection conexion) {
            int actual = stockDisponible.getOrDefault(productoId, 0);
            if (actual < cantidad) {
                return false;
            }
            stockDisponible.put(productoId, actual - cantidad);
            return true;
        }
    }
}
