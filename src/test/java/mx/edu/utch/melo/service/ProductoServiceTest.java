package mx.edu.utch.melo.service;

import mx.edu.utch.melo.dao.ProductoDAO;
import mx.edu.utch.melo.model.Modificador;
import mx.edu.utch.melo.model.Producto;
import mx.edu.utch.melo.model.Rol;
import mx.edu.utch.melo.security.AccesoDenegadoException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductoServiceTest {

    @Test
    void crearAplicaValoresPorDefectoSensatos() {
        ProductoDAOFalso dao = new ProductoDAOFalso();
        ProductoService service = new ProductoService(dao);

        service.crear(Rol.ADMINISTRADOR, 1, 5, "Tacos al Pastor", "3 piezas",
                new BigDecimal("65.00"), 20, null);

        assertEquals(1, dao.ultimoCreado.getSucursalId());
        assertEquals(5, dao.ultimoCreado.getCategoriaId());
        assertEquals("Tacos al Pastor", dao.ultimoCreado.getNombre());
        assertEquals(new BigDecimal("65.00"), dao.ultimoCreado.getPrecio());
        assertEquals(new BigDecimal("0.16"), dao.ultimoCreado.getTasaIva());
        assertTrue(dao.ultimoCreado.isDisponible());
        assertEquals(20, dao.ultimoCreado.getCantidadDisponible());
        assertEquals(0, dao.ultimoCreado.getStockMinimo());
    }

    @Test
    void crearConvierteDescripcionEnBlancoANull() {
        ProductoDAOFalso dao = new ProductoDAOFalso();
        ProductoService service = new ProductoService(dao);

        service.crear(Rol.ADMINISTRADOR, 1, 5, "Agua", "   ", new BigDecimal("20.00"), 10, null);

        assertNull(dao.ultimoCreado.getDescripcion());
    }

    @Test
    void crearRechazaARolesQueNoSonAdministrador() {
        ProductoDAOFalso dao = new ProductoDAOFalso();
        ProductoService service = new ProductoService(dao);

        assertThrows(AccesoDenegadoException.class, () ->
                service.crear(Rol.MESERO, 1, 5, "Tacos", null, new BigDecimal("65.00"), 20, null));
        assertThrows(AccesoDenegadoException.class, () ->
                service.crear(Rol.CAJERO, 1, 5, "Tacos", null, new BigDecimal("65.00"), 20, null));
        assertThrows(AccesoDenegadoException.class, () ->
                service.crear(Rol.COCINA, 1, 5, "Tacos", null, new BigDecimal("65.00"), 20, null));

        assertNull(dao.ultimoCreado, "un rol sin permiso no debería llegar a persistir nada");
    }

    @Test
    void eliminarFuncionaParaAdministrador() {
        ProductoDAOFalso dao = new ProductoDAOFalso();
        ProductoService service = new ProductoService(dao);

        boolean resultado = service.eliminar(Rol.ADMINISTRADOR, 42);

        assertTrue(resultado);
        assertEquals(42, dao.idEliminado);
    }

    @Test
    void eliminarRechazaARolesQueNoSonAdministrador() {
        ProductoDAOFalso dao = new ProductoDAOFalso();
        ProductoService service = new ProductoService(dao);

        assertThrows(AccesoDenegadoException.class, () -> service.eliminar(Rol.MESERO, 42));
        assertEquals(0, dao.idEliminado, "un rol sin permiso no debería llegar a eliminar nada");
    }

    /** Fake sin librerías de mocking (ver auditoría de Fase 6): captura lo que le pasó ProductoService. */
    private static class ProductoDAOFalso implements ProductoDAO {
        Producto ultimoCreado;
        int idEliminado;

        @Override
        public Producto crear(Producto producto) {
            producto.setId(99);
            this.ultimoCreado = producto;
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
            this.idEliminado = id;
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
        public boolean descontarStock(int productoId, int cantidad, java.sql.Connection conexion) {
            return true;
        }
    }
}
