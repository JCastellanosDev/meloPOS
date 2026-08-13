package mx.edu.utch.melo.dao.impl;

import mx.edu.utch.melo.dao.PersistenciaException;
import mx.edu.utch.melo.dao.ProductoDAO;
import mx.edu.utch.melo.db.ConexionDB;
import mx.edu.utch.melo.model.Modificador;
import mx.edu.utch.melo.model.Producto;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Implementación JDBC de {@link ProductoDAO}. Cada método abre su propia
 * conexión y la cierra con try-with-resources -- ninguna conexión,
 * PreparedStatement o ResultSet queda abierto más allá del método que lo usa.
 */
public class ProductoDAOImpl implements ProductoDAO {

    private static final String SQL_INSERTAR =
            "INSERT INTO productos (sucursal_id, categoria_id, nombre, descripcion, precio, tasa_iva, disponible, "
                    + "cantidad_disponible, stock_minimo, imagen_path) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String SQL_ACTUALIZAR =
            "UPDATE productos SET sucursal_id = ?, categoria_id = ?, nombre = ?, descripcion = ?, precio = ?, "
                    + "tasa_iva = ?, disponible = ?, cantidad_disponible = ?, stock_minimo = ?, imagen_path = ? WHERE id = ?";
    private static final String SQL_ELIMINAR = "DELETE FROM productos WHERE id = ?";
    private static final String SQL_BASE =
            "SELECT id, sucursal_id, categoria_id, nombre, descripcion, precio, tasa_iva, disponible, "
                    + "cantidad_disponible, stock_minimo, imagen_path FROM productos";
    private static final String SQL_POR_ID = SQL_BASE + " WHERE id = ?";
    private static final String SQL_TODOS_ACTIVOS = SQL_BASE + " WHERE disponible = TRUE";
    private static final String SQL_POR_CATEGORIA = SQL_BASE + " WHERE categoria_id = ?";
    private static final String SQL_POR_SUCURSAL = SQL_BASE + " WHERE sucursal_id = ?";

    private static final String SQL_MODIFICADORES_DEL_PRODUCTO =
            "SELECT m.id, m.nombre, m.precio_extra FROM modificadores m "
                    + "INNER JOIN producto_modificador pm ON pm.modificador_id = m.id "
                    + "WHERE pm.producto_id = ?";
    private static final String SQL_ASIGNAR_MODIFICADOR =
            "INSERT INTO producto_modificador (producto_id, modificador_id) VALUES (?, ?)";
    private static final String SQL_QUITAR_MODIFICADOR =
            "DELETE FROM producto_modificador WHERE producto_id = ? AND modificador_id = ?";

    private final ConexionDB conexionDB;

    public ProductoDAOImpl(ConexionDB conexionDB) {
        this.conexionDB = conexionDB;
    }

    @Override
    public Producto crear(Producto producto) {
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_INSERTAR, Statement.RETURN_GENERATED_KEYS)) {

            mapearParametros(sentencia, producto);
            sentencia.executeUpdate();

            try (ResultSet llaves = sentencia.getGeneratedKeys()) {
                if (llaves.next()) {
                    producto.setId(llaves.getInt(1));
                }
            }
            return producto;
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo crear el producto: " + producto.getNombre(), e);
        }
    }

    @Override
    public Optional<Producto> obtenerPorId(Integer id) {
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_POR_ID)) {

            sentencia.setInt(1, id);
            try (ResultSet resultado = sentencia.executeQuery()) {
                return resultado.next() ? Optional.of(mapearFila(resultado)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo obtener el producto con id " + id, e);
        }
    }

    @Override
    public List<Producto> obtenerTodos() {
        return ejecutarConsultaLista(SQL_BASE);
    }

    @Override
    public List<Producto> obtenerTodosActivos() {
        return ejecutarConsultaLista(SQL_TODOS_ACTIVOS);
    }

    @Override
    public List<Producto> obtenerPorCategoria(int categoriaId) {
        List<Producto> productos = new ArrayList<>();
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_POR_CATEGORIA)) {

            sentencia.setInt(1, categoriaId);
            try (ResultSet resultado = sentencia.executeQuery()) {
                while (resultado.next()) {
                    productos.add(mapearFila(resultado));
                }
            }
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo obtener los productos de la categoría " + categoriaId, e);
        }
        return productos;
    }

    @Override
    public List<Producto> obtenerPorSucursal(int sucursalId) {
        List<Producto> productos = new ArrayList<>();
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_POR_SUCURSAL)) {

            sentencia.setInt(1, sucursalId);
            try (ResultSet resultado = sentencia.executeQuery()) {
                while (resultado.next()) {
                    productos.add(mapearFila(resultado));
                }
            }
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo obtener los productos de la sucursal " + sucursalId, e);
        }
        return productos;
    }

    @Override
    public boolean actualizar(Producto producto) {
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_ACTUALIZAR)) {

            mapearParametros(sentencia, producto);
            sentencia.setInt(11, producto.getId());
            return sentencia.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo actualizar el producto con id " + producto.getId(), e);
        }
    }

    @Override
    public boolean eliminar(Integer id) {
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_ELIMINAR)) {

            sentencia.setInt(1, id);
            return sentencia.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo eliminar el producto con id " + id, e);
        }
    }

    @Override
    public List<Modificador> obtenerModificadores(int productoId) {
        List<Modificador> modificadores = new ArrayList<>();
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_MODIFICADORES_DEL_PRODUCTO)) {

            sentencia.setInt(1, productoId);
            try (ResultSet resultado = sentencia.executeQuery()) {
                while (resultado.next()) {
                    Modificador modificador = new Modificador();
                    modificador.setId(resultado.getInt("id"));
                    modificador.setNombre(resultado.getString("nombre"));
                    modificador.setPrecioExtra(resultado.getBigDecimal("precio_extra"));
                    modificadores.add(modificador);
                }
            }
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo obtener los modificadores del producto " + productoId, e);
        }
        return modificadores;
    }

    @Override
    public void asignarModificador(int productoId, int modificadorId) {
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_ASIGNAR_MODIFICADOR)) {

            sentencia.setInt(1, productoId);
            sentencia.setInt(2, modificadorId);
            sentencia.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenciaException(
                    "No se pudo asignar el modificador " + modificadorId + " al producto " + productoId, e);
        }
    }

    @Override
    public void quitarModificador(int productoId, int modificadorId) {
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_QUITAR_MODIFICADOR)) {

            sentencia.setInt(1, productoId);
            sentencia.setInt(2, modificadorId);
            sentencia.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenciaException(
                    "No se pudo quitar el modificador " + modificadorId + " del producto " + productoId, e);
        }
    }

    private List<Producto> ejecutarConsultaLista(String sql) {
        List<Producto> productos = new ArrayList<>();
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(sql);
             ResultSet resultado = sentencia.executeQuery()) {

            while (resultado.next()) {
                productos.add(mapearFila(resultado));
            }
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo obtener la lista de productos", e);
        }
        return productos;
    }

    private void mapearParametros(PreparedStatement sentencia, Producto producto) throws SQLException {
        sentencia.setInt(1, producto.getSucursalId());
        sentencia.setInt(2, producto.getCategoriaId());
        sentencia.setString(3, producto.getNombre());
        sentencia.setString(4, producto.getDescripcion());
        sentencia.setBigDecimal(5, producto.getPrecio());
        sentencia.setBigDecimal(6, producto.getTasaIva());
        sentencia.setBoolean(7, producto.isDisponible());
        sentencia.setInt(8, producto.getCantidadDisponible());
        sentencia.setInt(9, producto.getStockMinimo());
        sentencia.setString(10, producto.getImagenPath());
    }

    private Producto mapearFila(ResultSet resultado) throws SQLException {
        Producto producto = new Producto();
        producto.setId(resultado.getInt("id"));
        producto.setSucursalId(resultado.getInt("sucursal_id"));
        producto.setCategoriaId(resultado.getInt("categoria_id"));
        producto.setNombre(resultado.getString("nombre"));
        producto.setDescripcion(resultado.getString("descripcion"));
        producto.setPrecio(resultado.getBigDecimal("precio"));
        producto.setTasaIva(resultado.getBigDecimal("tasa_iva"));
        producto.setDisponible(resultado.getBoolean("disponible"));
        producto.setCantidadDisponible(resultado.getInt("cantidad_disponible"));
        producto.setStockMinimo(resultado.getInt("stock_minimo"));
        producto.setImagenPath(resultado.getString("imagen_path"));
        return producto;
    }
}
