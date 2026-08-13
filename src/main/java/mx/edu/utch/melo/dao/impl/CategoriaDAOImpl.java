package mx.edu.utch.melo.dao.impl;

import mx.edu.utch.melo.dao.CategoriaDAO;
import mx.edu.utch.melo.dao.PersistenciaException;
import mx.edu.utch.melo.db.ConexionDB;
import mx.edu.utch.melo.model.Categoria;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Implementación JDBC de {@link CategoriaDAO}. */
public class CategoriaDAOImpl implements CategoriaDAO {

    private static final String SQL_INSERTAR = "INSERT INTO categorias (nombre) VALUES (?)";
    private static final String SQL_ACTUALIZAR = "UPDATE categorias SET nombre = ? WHERE id = ?";
    private static final String SQL_ELIMINAR = "DELETE FROM categorias WHERE id = ?";
    private static final String SQL_BASE = "SELECT id, nombre FROM categorias";
    private static final String SQL_POR_ID = SQL_BASE + " WHERE id = ?";

    private final ConexionDB conexionDB;

    public CategoriaDAOImpl(ConexionDB conexionDB) {
        this.conexionDB = conexionDB;
    }

    @Override
    public Categoria crear(Categoria categoria) {
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_INSERTAR, Statement.RETURN_GENERATED_KEYS)) {

            sentencia.setString(1, categoria.getNombre());
            sentencia.executeUpdate();

            try (ResultSet llaves = sentencia.getGeneratedKeys()) {
                if (llaves.next()) {
                    categoria.setId(llaves.getInt(1));
                }
            }
            return categoria;
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo crear la categoría: " + categoria.getNombre(), e);
        }
    }

    @Override
    public Optional<Categoria> obtenerPorId(Integer id) {
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_POR_ID)) {

            sentencia.setInt(1, id);
            try (ResultSet resultado = sentencia.executeQuery()) {
                return resultado.next() ? Optional.of(mapearFila(resultado)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo obtener la categoría con id " + id, e);
        }
    }

    @Override
    public List<Categoria> obtenerTodos() {
        List<Categoria> categorias = new ArrayList<>();
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_BASE);
             ResultSet resultado = sentencia.executeQuery()) {

            while (resultado.next()) {
                categorias.add(mapearFila(resultado));
            }
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo obtener la lista de categorías", e);
        }
        return categorias;
    }

    @Override
    public boolean actualizar(Categoria categoria) {
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_ACTUALIZAR)) {

            sentencia.setString(1, categoria.getNombre());
            sentencia.setInt(2, categoria.getId());
            return sentencia.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo actualizar la categoría con id " + categoria.getId(), e);
        }
    }

    @Override
    public boolean eliminar(Integer id) {
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_ELIMINAR)) {

            sentencia.setInt(1, id);
            return sentencia.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo eliminar la categoría con id " + id, e);
        }
    }

    private Categoria mapearFila(ResultSet resultado) throws SQLException {
        Categoria categoria = new Categoria();
        categoria.setId(resultado.getInt("id"));
        categoria.setNombre(resultado.getString("nombre"));
        return categoria;
    }
}
