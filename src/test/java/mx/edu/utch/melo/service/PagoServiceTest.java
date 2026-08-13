package mx.edu.utch.melo.service;

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
import mx.edu.utch.melo.model.MetodoPago;
import mx.edu.utch.melo.model.Modificador;
import mx.edu.utch.melo.model.ModificadorAplicado;
import mx.edu.utch.melo.model.Orden;
import mx.edu.utch.melo.model.Pago;
import mx.edu.utch.melo.model.Producto;
import mx.edu.utch.melo.model.Sucursal;
import mx.edu.utch.melo.model.TipoOrden;
import mx.edu.utch.melo.model.Turno;
import mx.edu.utch.melo.model.Usuario;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PagoServiceTest {

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
    void obtenerReciboJuntaOrdenCajeroSucursalYLineas() {
        OrdenDAOFalso ordenDAO = new OrdenDAOFalso();
        ordenDAO.orden = ordenDeEjemplo();
        UsuarioDAOFalso usuarioDAO = new UsuarioDAOFalso();
        usuarioDAO.usuario = usuarioDeEjemplo();
        SucursalDAOFalso sucursalDAO = new SucursalDAOFalso();
        sucursalDAO.sucursal = new Sucursal();
        sucursalDAO.sucursal.setNombre("Crepas de Oro");
        DetalleOrdenDAOFalso detalleDAO = new DetalleOrdenDAOFalso();
        detalleDAO.detalles = List.of(detalleDeEjemplo(1, 10, 2, "50.00"));
        ProductoDAOFalso productoDAO = new ProductoDAOFalso();
        productoDAO.producto = productoDeEjemplo(10, "Tacos al Pastor");

        PagoService service = new PagoService(ordenDAO, detalleDAO, productoDAO, new PagoDAOFalso(),
                usuarioDAO, new TurnoDAOFalso(), sucursalDAO, TRANSACCIONADOR_FALSO);

        PagoService.DatosRecibo recibo = service.obtenerRecibo(1, 1);

        assertEquals("Crepas de Oro", recibo.sucursal().getNombre());
        assertEquals("Cajero Demo", recibo.cajero().getNombre());
        assertEquals(1, recibo.lineas().size());
        assertEquals("Tacos al Pastor", recibo.lineas().get(0).nombre());
        assertEquals(0, new BigDecimal("100.00").compareTo(recibo.lineas().get(0).monto()));
    }

    @Test
    void obtenerReciboUsaUnNombreDeRespaldoSiElProductoYaNoExiste() {
        OrdenDAOFalso ordenDAO = new OrdenDAOFalso();
        ordenDAO.orden = ordenDeEjemplo();
        DetalleOrdenDAOFalso detalleDAO = new DetalleOrdenDAOFalso();
        detalleDAO.detalles = List.of(detalleDeEjemplo(1, 999, 1, "50.00"));
        ProductoDAOFalso productoDAO = new ProductoDAOFalso();
        productoDAO.producto = null; // el producto fue eliminado después de la venta

        PagoService service = new PagoService(ordenDAO, detalleDAO, productoDAO, new PagoDAOFalso(),
                new UsuarioDAOFalso(), new TurnoDAOFalso(), new SucursalDAOFalso(), TRANSACCIONADOR_FALSO);

        PagoService.DatosRecibo recibo = service.obtenerRecibo(1, 1);

        assertEquals("Producto #999", recibo.lineas().get(0).nombre());
    }

    @Test
    void registrarPagoCreaElPagoYAvanzaLaOrdenAEnPreparacion() {
        OrdenDAOFalso ordenDAO = new OrdenDAOFalso();
        ordenDAO.orden = ordenDeEjemplo();
        PagoDAOFalso pagoDAO = new PagoDAOFalso();
        PagoService service = new PagoService(ordenDAO, new DetalleOrdenDAOFalso(), new ProductoDAOFalso(), pagoDAO,
                new UsuarioDAOFalso(), new TurnoDAOFalso(), new SucursalDAOFalso(), TRANSACCIONADOR_FALSO);

        boolean resultado = service.registrarPago(1, MetodoPago.EFECTIVO, new BigDecimal("371.20"), 9);

        assertTrue(resultado);
        assertEquals(MetodoPago.EFECTIVO, pagoDAO.ultimoCreado.getMetodoPago());
        assertEquals(0, new BigDecimal("371.20").compareTo(pagoDAO.ultimoCreado.getMonto()));
        assertEquals(EstadoOrden.EN_PREPARACION, ordenDAO.orden.getEstado());
    }

    @Test
    void registrarPagoAsignaElTurnoAbiertoDelCajero() {
        OrdenDAOFalso ordenDAO = new OrdenDAOFalso();
        ordenDAO.orden = ordenDeEjemplo();
        TurnoDAOFalso turnoDAO = new TurnoDAOFalso();
        Turno turnoAbierto = new Turno();
        turnoAbierto.setId(77);
        turnoDAO.turnoAbierto = turnoAbierto;
        PagoService service = new PagoService(ordenDAO, new DetalleOrdenDAOFalso(), new ProductoDAOFalso(),
                new PagoDAOFalso(), new UsuarioDAOFalso(), turnoDAO, new SucursalDAOFalso(), TRANSACCIONADOR_FALSO);

        service.registrarPago(1, MetodoPago.EFECTIVO, new BigDecimal("100.00"), 9);

        assertEquals(77, ordenDAO.orden.getTurnoId());
    }

    @Test
    void registrarPagoNoBloqueaElCobroSiElCajeroNoTieneTurnoAbierto() {
        // ver CLAUDE.md: si el cajero olvidó abrir turno, el cobro no se bloquea -- queda con
        // turno_id null, igual que hoy.
        OrdenDAOFalso ordenDAO = new OrdenDAOFalso();
        ordenDAO.orden = ordenDeEjemplo();
        TurnoDAOFalso turnoDAO = new TurnoDAOFalso();
        turnoDAO.turnoAbierto = null;
        PagoService service = new PagoService(ordenDAO, new DetalleOrdenDAOFalso(), new ProductoDAOFalso(),
                new PagoDAOFalso(), new UsuarioDAOFalso(), turnoDAO, new SucursalDAOFalso(), TRANSACCIONADOR_FALSO);

        boolean resultado = service.registrarPago(1, MetodoPago.TARJETA, new BigDecimal("100.00"), 9);

        assertTrue(resultado);
        assertNull(ordenDAO.orden.getTurnoId());
    }

    private static Orden ordenDeEjemplo() {
        Orden orden = new Orden();
        orden.setId(1);
        orden.setTipoOrden(TipoOrden.COMEDOR);
        orden.setUsuarioId(9);
        orden.setEstado(EstadoOrden.PENDIENTE);
        orden.setSubtotal(new BigDecimal("320.00"));
        orden.setImpuestos(new BigDecimal("51.20"));
        orden.setCostoEnvio(BigDecimal.ZERO);
        orden.setTotal(new BigDecimal("371.20"));
        return orden;
    }

    private static Usuario usuarioDeEjemplo() {
        Usuario usuario = new Usuario();
        usuario.setId(9);
        usuario.setNombre("Cajero Demo");
        return usuario;
    }

    private static DetalleOrden detalleDeEjemplo(int ordenId, int productoId, int cantidad, String precioUnitario) {
        DetalleOrden detalle = new DetalleOrden();
        detalle.setOrdenId(ordenId);
        detalle.setProductoId(productoId);
        detalle.setCantidad(cantidad);
        detalle.setPrecioUnitario(new BigDecimal(precioUnitario));
        return detalle;
    }

    private static Producto productoDeEjemplo(int id, String nombre) {
        Producto producto = new Producto();
        producto.setId(id);
        producto.setNombre(nombre);
        return producto;
    }

    private static class OrdenDAOFalso implements OrdenDAO {
        Orden orden;

        @Override
        public Orden crear(Orden orden) {
            return orden;
        }

        @Override
        public Orden crear(Orden orden, Connection conexion) {
            return orden;
        }

        @Override
        public Optional<Orden> obtenerPorId(Integer id) {
            return Optional.ofNullable(orden);
        }

        @Override
        public Optional<Orden> obtenerPorId(Integer id, Connection conexion) {
            return Optional.ofNullable(orden);
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
            this.orden = orden;
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
            return 1;
        }
    }

    private static class DetalleOrdenDAOFalso implements DetalleOrdenDAO {
        List<DetalleOrden> detalles = List.of();

        @Override
        public DetalleOrden crear(DetalleOrden detalle) {
            return detalle;
        }

        @Override
        public DetalleOrden crear(DetalleOrden detalle, Connection conexion) {
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
            return detalles;
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
        Producto producto;

        @Override
        public Producto crear(Producto producto) {
            return producto;
        }

        @Override
        public Optional<Producto> obtenerPorId(Integer id) {
            return Optional.ofNullable(producto);
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
    }

    private static class PagoDAOFalso implements PagoDAO {
        Pago ultimoCreado;

        @Override
        public Pago crear(Pago pago) {
            return crear(pago, null);
        }

        @Override
        public Pago crear(Pago pago, Connection conexion) {
            this.ultimoCreado = pago;
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
            return List.of();
        }
    }

    private static class UsuarioDAOFalso implements UsuarioDAO {
        Usuario usuario;

        @Override
        public Usuario crear(Usuario usuario) {
            return usuario;
        }

        @Override
        public Optional<Usuario> obtenerPorId(Integer id) {
            return Optional.ofNullable(usuario);
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
        Turno turnoAbierto;

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
            return Optional.ofNullable(turnoAbierto);
        }
    }

    private static class SucursalDAOFalso implements SucursalDAO {
        Sucursal sucursal = new Sucursal();

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
