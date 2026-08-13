package mx.edu.utch.melo.dao.impl;

import mx.edu.utch.melo.dao.ModificadorDAO;
import mx.edu.utch.melo.dao.PersistenciaException;
import mx.edu.utch.melo.db.ConexionDB;
import mx.edu.utch.melo.model.Modificador;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Implementación JDBC de {@link ModificadorDAO}. */
public class ModificadorDAOImpl implements ModificadorDAO {

    private static final String SQL_INSERTAR = "INSERT INTO modificadores (nombre, precio_extra) VALUES (?, ?)";
    private static final String SQL_ACTUALIZAR = "UPDATE modificadores SET nombre = ?, precio_extra = ? WHERE id = ?";
    private static final String SQL_ELIMINAR = "DELETE FROM modificadores WHERE id = ?";
    private static final String SQL_BASE = "SELECT id, nombre, precio_extra FROM modificadores";
    private static final String SQL_POR_ID = SQL_BASE + " WHERE id = ?";

    private final ConexionDB conexionDB;

    public ModificadorDAOImpl(ConexionDB conexionDB) {
        this.conexionDB = conexionDB;
    }

    @Override
    public Modificador crear(Modificador modificador) {
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_INSERTAR, Statement.RETURN_GENERATED_KEYS)) {

            sentencia.setString(1, modificador.getNombre());
            sentencia.setBigDecimal(2, modificador.getPrecioExtra());
            sentencia.executeUpdate();

            try (ResultSet llaves = sentencia.getGeneratedKeys()) {
                if (llaves.next()) {
                    modificador.setId(llaves.getInt(1));
                }
            }
            return modificador;
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo crear el modificador: " + modificador.getNombre(), e);
        }
    }

    @Override
    public Optional<Modificador> obtenerPorId(Integer id) {
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_POR_ID)) {

            sentencia.setInt(1, id);
            try (ResultSet resultado = sentencia.executeQuery()) {
                return resultado.next() ? Optional.of(mapearFila(resultado)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo obtener el modificador con id " + id, e);
        }
    }

    @Override
    public List<Modificador> obtenerTodos() {
        List<Modificador> modificadores = new ArrayList<>();
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_BASE);
             ResultSet resultado = sentencia.executeQuery()) {

            while (resultado.next()) {
                modificadores.add(mapearFila(resultado));
            }
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo obtener la lista de modificadores", e);
        }
        return modificadores;
    }

    @Override
    public boolean actualizar(Modificador modificador) {
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_ACTUALIZAR)) {

            sentencia.setString(1, modificador.getNombre());
            sentencia.setBigDecimal(2, modificador.getPrecioExtra());
            sentencia.setInt(3, modificador.getId());
            return sentencia.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo actualizar el modificador con id " + modificador.getId(), e);
        }
    }

    @Override
    public boolean eliminar(Integer id) {
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_ELIMINAR)) {

            sentencia.setInt(1, id);
            return sentencia.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo eliminar el modificador con id " + id, e);
        }
    }

    private Modificador mapearFila(ResultSet resultado) throws SQLException {
        Modificador modificador = new Modificador();
        modificador.setId(resultado.getInt("id"));
        modificador.setNombre(resultado.getString("nombre"));
        modificador.setPrecioExtra(resultado.getBigDecimal("precio_extra"));
        return modificador;
    }
}
