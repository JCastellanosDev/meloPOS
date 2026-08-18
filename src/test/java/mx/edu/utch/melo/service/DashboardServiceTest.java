package mx.edu.utch.melo.service;

import mx.edu.utch.melo.dao.DashboardDAO;
import mx.edu.utch.melo.dao.OrdenDAO;
import mx.edu.utch.melo.dao.ProductoDAO;
import mx.edu.utch.melo.dao.ReporteDAO;
import mx.edu.utch.melo.dao.TurnoDAO;
import mx.edu.utch.melo.model.EstadoOrden;
import mx.edu.utch.melo.model.Modificador;
import mx.edu.utch.melo.model.Orden;
import mx.edu.utch.melo.model.Producto;
import mx.edu.utch.melo.model.TipoOrden;
import mx.edu.utch.melo.model.Turno;
import mx.edu.utch.melo.model.dashboard.CategoriaVendida;
import mx.edu.utch.melo.model.dashboard.ClienteRecurrente;
import mx.edu.utch.melo.model.dashboard.ComparacionVentas;
import mx.edu.utch.melo.model.dashboard.ProductoSinVentas;
import mx.edu.utch.melo.model.dashboard.ResumenDelivery;
import mx.edu.utch.melo.model.dashboard.ResumenVentas;
import mx.edu.utch.melo.model.dashboard.VentaPorEmpleado;
import mx.edu.utch.melo.model.dashboard.VentaPorHora;
import mx.edu.utch.melo.model.dashboard.VentaPorMetodoPago;
import mx.edu.utch.melo.model.reporte.PlatilloVendido;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DashboardServiceTest {

    @Test
    void calculaVariacionPositivaCuandoLasVentasSubieron() {
        DashboardDAOFalso dao = new DashboardDAOFalso();
        dao.colaResumenVentas.add(new ResumenVentas(new BigDecimal("1000.00"), 10, new BigDecimal("100.00"))); // actual
        dao.colaResumenVentas.add(new ResumenVentas(new BigDecimal("800.00"), 8, new BigDecimal("100.00")));   // anterior
        DashboardService service = construirService(dao);

        ComparacionVentas resultado = service.obtenerVentasDeHoy(1);

        // (1000 - 800) / 800 * 100 = 25.0000
        assertEquals(0, new BigDecimal("25.0000").compareTo(resultado.variacionPorcentual()));
    }

    @Test
    void calculaVariacionNegativaCuandoLasVentasBajaron() {
        DashboardDAOFalso dao = new DashboardDAOFalso();
        dao.colaResumenVentas.add(new ResumenVentas(new BigDecimal("600.00"), 6, BigDecimal.ZERO)); // actual
        dao.colaResumenVentas.add(new ResumenVentas(new BigDecimal("800.00"), 8, BigDecimal.ZERO)); // anterior
        DashboardService service = construirService(dao);

        ComparacionVentas resultado = service.obtenerVentasDeHoy(1);

        // (600 - 800) / 800 * 100 = -25.0000
        assertEquals(0, new BigDecimal("-25.0000").compareTo(resultado.variacionPorcentual()));
    }

    @Test
    void variacionEsCienPorCientoCuandoAntesNoHuboVentasYAhoraSi() {
        DashboardDAOFalso dao = new DashboardDAOFalso();
        dao.colaResumenVentas.add(new ResumenVentas(new BigDecimal("500.00"), 5, BigDecimal.ZERO)); // actual
        dao.colaResumenVentas.add(ResumenVentas.VACIO); // anterior: cero ventas
        DashboardService service = construirService(dao);

        ComparacionVentas resultado = service.obtenerVentasDeHoy(1);

        assertEquals(0, new BigDecimal("100").compareTo(resultado.variacionPorcentual()));
    }

    @Test
    void variacionEsCeroCuandoNiAntesNiAhoraHuboVentas() {
        // No debe intentar dividir entre cero (0/0) -- ver DashboardService.calcularVariacionPorcentual.
        DashboardDAOFalso dao = new DashboardDAOFalso();
        dao.colaResumenVentas.add(ResumenVentas.VACIO);
        dao.colaResumenVentas.add(ResumenVentas.VACIO);
        DashboardService service = construirService(dao);

        ComparacionVentas resultado = service.obtenerVentasDeHoy(1);

        assertEquals(0, BigDecimal.ZERO.compareTo(resultado.variacionPorcentual()));
    }

    @Test
    void obtenerHoraPicoRegresaLaHoraConMasVentas() {
        DashboardDAOFalso dao = new DashboardDAOFalso();
        dao.ventasPorHora = List.of(
                new VentaPorHora(9, new BigDecimal("200.00")),
                new VentaPorHora(14, new BigDecimal("900.00")),
                new VentaPorHora(20, new BigDecimal("500.00")));
        DashboardService service = construirService(dao);

        Integer horaPico = service.obtenerHoraPico(1, LocalDate.now(), LocalDate.now());

        assertEquals(14, horaPico);
    }

    @Test
    void obtenerHoraPicoRegresaNullSinVentas() {
        DashboardDAOFalso dao = new DashboardDAOFalso();
        dao.ventasPorHora = List.of();
        DashboardService service = construirService(dao);

        assertNull(service.obtenerHoraPico(1, LocalDate.now(), LocalDate.now()));
    }

    @Test
    void obtenerProductosBajoStockReutilizaLaReglaDeDominioDeProducto() {
        // ver auditoría de Fase 7: Producto.tieneStockBajo() es la fuente de verdad, no se reimplementa aquí.
        ProductoDAOFalso productoDAO = new ProductoDAOFalso();
        productoDAO.productos = List.of(
                productoConStock("Bajo", 2, 5),
                productoConStock("Normal", 50, 5));
        DashboardService service = construirService(new DashboardDAOFalso(), productoDAO, new OrdenDAOFalso());

        List<Producto> resultado = service.obtenerProductosBajoStock(1);

        assertEquals(1, resultado.size());
        assertEquals("Bajo", resultado.get(0).getNombre());
    }

    @Test
    void obtenerClientesRecurrentesDelegaAlDashboardDAO() {
        // ver auditoría de Fase 8: DashboardService no reimplementa la agregación, solo la expone.
        DashboardDAOFalso dashboardDAO = new DashboardDAOFalso();
        dashboardDAO.clientesRecurrentes = List.of(new ClienteRecurrente("Ana", 3, new BigDecimal("450.00")));
        DashboardService service = construirService(dashboardDAO, new ProductoDAOFalso(), new OrdenDAOFalso());

        List<ClienteRecurrente> resultado = service.obtenerClientesRecurrentes(1, LocalDate.now(), LocalDate.now());

        assertEquals(1, resultado.size());
        assertEquals("Ana", resultado.get(0).nombreCliente());
    }

    @Test
    void obtenerPedidosActivosDomicilioDelegaAlOrdenDAO() {
        OrdenDAOFalso ordenDAO = new OrdenDAOFalso();
        ordenDAO.activasDomicilio = List.of(new Orden(), new Orden());
        DashboardService service = construirService(new DashboardDAOFalso(), new ProductoDAOFalso(), ordenDAO);

        assertEquals(2, service.obtenerPedidosActivosDomicilio());
    }

    @Test
    void construirDatosDashboardArmaTodoElPanelSinLanzar() {
        DashboardDAOFalso dao = new DashboardDAOFalso();
        for (int i = 0; i < 6; i++) {
            dao.colaResumenVentas.add(ResumenVentas.VACIO);
        }
        DashboardService service = construirService(dao);

        DashboardService.DatosDashboard datos = service.construirDatosDashboard(1, 9);

        assertTrue(datos.masVendidos().isEmpty());
        assertEquals(0, datos.pedidosActivosDomicilio());
    }

    private static Producto productoConStock(String nombre, int cantidadDisponible, int stockMinimo) {
        Producto producto = new Producto();
        producto.setNombre(nombre);
        producto.setCantidadDisponible(cantidadDisponible);
        producto.setStockMinimo(stockMinimo);
        return producto;
    }

    private static DashboardService construirService(DashboardDAOFalso dashboardDAO) {
        return construirService(dashboardDAO, new ProductoDAOFalso(), new OrdenDAOFalso());
    }

    private static DashboardService construirService(DashboardDAOFalso dashboardDAO, ProductoDAOFalso productoDAO,
                                                       OrdenDAOFalso ordenDAO) {
        TurnoService turnoService = new TurnoService(new TurnoDAOFalso(), new ReporteDAOFalso());
        return new DashboardService(dashboardDAO, productoDAO, ordenDAO, turnoService);
    }

    private static class DashboardDAOFalso implements DashboardDAO {
        Deque<ResumenVentas> colaResumenVentas = new ArrayDeque<>();
        List<VentaPorHora> ventasPorHora = List.of();
        List<ClienteRecurrente> clientesRecurrentes = List.of();

        @Override
        public ResumenVentas obtenerResumenVentas(int sucursalId, LocalDate desde, LocalDate hasta) {
            return colaResumenVentas.isEmpty() ? ResumenVentas.VACIO : colaResumenVentas.poll();
        }

        @Override
        public List<VentaPorHora> obtenerVentasPorHora(int sucursalId, LocalDate desde, LocalDate hasta) {
            return ventasPorHora;
        }

        @Override
        public List<PlatilloVendido> obtenerProductosMasVendidos(int sucursalId, LocalDate desde, LocalDate hasta, int limite) {
            return List.of();
        }

        @Override
        public List<PlatilloVendido> obtenerProductosMenosVendidos(int sucursalId, LocalDate desde, LocalDate hasta, int limite) {
            return List.of();
        }

        @Override
        public List<CategoriaVendida> obtenerCategoriasPrincipales(int sucursalId, LocalDate desde, LocalDate hasta, int limite) {
            return List.of();
        }

        @Override
        public List<VentaPorMetodoPago> obtenerVentasPorMetodoPago(int sucursalId, LocalDate desde, LocalDate hasta) {
            return List.of();
        }

        @Override
        public List<VentaPorEmpleado> obtenerVentasPorEmpleado(int sucursalId, LocalDate desde, LocalDate hasta) {
            return List.of();
        }

        @Override
        public List<ProductoSinVentas> obtenerProductosSinVentas(int sucursalId, LocalDate desde, LocalDate hasta) {
            return List.of();
        }

        @Override
        public ResumenDelivery obtenerResumenDelivery(int sucursalId, LocalDate desde, LocalDate hasta) {
            return ResumenDelivery.VACIO;
        }

        @Override
        public List<ClienteRecurrente> obtenerClientesRecurrentes(int sucursalId, LocalDate desde, LocalDate hasta) {
            return clientesRecurrentes;
        }
    }

    private static class ProductoDAOFalso implements ProductoDAO {
        List<Producto> productos = List.of();

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
            return productos;
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
        public boolean descontarStock(int productoId, int cantidad, java.sql.Connection conexion) {
            return true;
        }

        @Override
        public void restaurarStock(int productoId, int cantidad, java.sql.Connection conexion) {
        }
    }

    private static class OrdenDAOFalso implements OrdenDAO {
        List<Orden> activasDomicilio = List.of();

        @Override
        public Orden crear(Orden orden) {
            return orden;
        }

        @Override
        public Orden crear(Orden orden, java.sql.Connection conexion) {
            return orden;
        }

        @Override
        public Optional<Orden> obtenerPorId(Integer id) {
            return Optional.empty();
        }

        @Override
        public Optional<Orden> obtenerPorId(Integer id, java.sql.Connection conexion) {
            return Optional.empty();
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
        public boolean actualizar(Orden orden, java.sql.Connection conexion) {
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
            return tipoOrden == TipoOrden.DOMICILIO ? activasDomicilio : List.of();
        }

        @Override
        public int siguienteNumeroOrden(int sucursalId) {
            return 1;
        }

        @Override
        public boolean cancelar(int ordenId, java.sql.Connection conexion) {
            return true;
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

    private static class ReporteDAOFalso implements ReporteDAO {
        @Override
        public List<mx.edu.utch.melo.model.reporte.VentaDiaria> obtenerVentasPorPeriodo(int sucursalId, LocalDate desde, LocalDate hasta) {
            return List.of();
        }

        @Override
        public List<mx.edu.utch.melo.model.reporte.PlatilloVendido> obtenerPlatillosMasVendidos(int sucursalId, LocalDate desde, LocalDate hasta, int limite) {
            return List.of();
        }

        @Override
        public mx.edu.utch.melo.model.reporte.ResumenTurno obtenerResumenTurno(int turnoId) {
            return null;
        }
    }
}
