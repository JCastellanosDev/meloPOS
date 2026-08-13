package mx.edu.utch.melo.dao.impl;

import mx.edu.utch.melo.dao.MesaDAO;
import mx.edu.utch.melo.dao.PersistenciaException;
import mx.edu.utch.melo.db.ConexionDB;
import mx.edu.utch.melo.model.Mesa;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Implementación JDBC de {@link MesaDAO}. */
public class MesaDAOImpl implements MesaDAO {

    private static final String SQL_INSERTAR = "INSERT INTO mesas (sucursal_id, numero, capacidad) VALUES (?, ?, ?)";
    private static final String SQL_ACTUALIZAR =
            "UPDATE mesas SET sucursal_id = ?, numero = ?, capacidad = ? WHERE id = ?";
    private static final String SQL_ELIMINAR = "DELETE FROM mesas WHERE id = ?";
    private static final String SQL_BASE = "SELECT id, sucursal_id, numero, capacidad FROM mesas";
    private static final String SQL_POR_ID = SQL_BASE + " WHERE id = ?";
    private static final String SQL_POR_SUCURSAL = SQL_BASE + " WHERE sucursal_id = ?";

    private final ConexionDB conexionDB;

    public MesaDAOImpl(ConexionDB conexionDB) {
        this.conexionDB = conexionDB;
    }

    @Override
    public Mesa crear(Mesa mesa) {
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_INSERTAR, Statement.RETURN_GENERATED_KEYS)) {

            sentencia.setInt(1, mesa.getSucursalId());
            sentencia.setInt(2, mesa.getNumero());
            mapearCapacidad(sentencia, 3, mesa.getCapacidad());
            sentencia.executeUpdate();

            try (ResultSet llaves = sentencia.getGeneratedKeys()) {
                if (llaves.next()) {
                    mesa.setId(llaves.getInt(1));
                }
            }
            return mesa;
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo crear la mesa " + mesa.getNumero(), e);
        }
    }

    @Override
    public Optional<Mesa> obtenerPorId(Integer id) {
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_POR_ID)) {

            sentencia.setInt(1, id);
            try (ResultSet resultado = sentencia.executeQuery()) {
                return resultado.next() ? Optional.of(mapearFila(resultado)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo obtener la mesa con id " + id, e);
        }
    }

    @Override
    public List<Mesa> obtenerTodos() {
        List<Mesa> mesas = new ArrayList<>();
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_BASE);
             ResultSet resultado = sentencia.executeQuery()) {

            while (resultado.next()) {
                mesas.add(mapearFila(resultado));
            }
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo obtener la lista de mesas", e);
        }
        return mesas;
    }

    @Override
    public List<Mesa> obtenerPorSucursal(int sucursalId) {
        List<Mesa> mesas = new ArrayList<>();
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_POR_SUCURSAL)) {

            sentencia.setInt(1, sucursalId);
            try (ResultSet resultado = sentencia.executeQuery()) {
                while (resultado.next()) {
                    mesas.add(mapearFila(resultado));
                }
            }
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo obtener las mesas de la sucursal " + sucursalId, e);
        }
        return mesas;
    }

    @Override
    public boolean actualizar(Mesa mesa) {
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_ACTUALIZAR)) {

            sentencia.setInt(1, mesa.getSucursalId());
            sentencia.setInt(2, mesa.getNumero());
            mapearCapacidad(sentencia, 3, mesa.getCapacidad());
            sentencia.setInt(4, mesa.getId());
            return sentencia.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo actualizar la mesa con id " + mesa.getId(), e);
        }
    }

    @Override
    public boolean eliminar(Integer id) {
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_ELIMINAR)) {

            sentencia.setInt(1, id);
            return sentencia.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo eliminar la mesa con id " + id, e);
        }
    }

    private void mapearCapacidad(PreparedStatement sentencia, int indice, Integer capacidad) throws SQLException {
        if (capacidad == null) {
            sentencia.setNull(indice, Types.INTEGER);
        } else {
            sentencia.setInt(indice, capacidad);
        }
    }

    private Mesa mapearFila(ResultSet resultado) throws SQLException {
        Mesa mesa = new Mesa();
        mesa.setId(resultado.getInt("id"));
        mesa.setSucursalId(resultado.getInt("sucursal_id"));
        mesa.setNumero(resultado.getInt("numero"));
        int capacidad = resultado.getInt("capacidad");
        mesa.setCapacidad(resultado.wasNull() ? null : capacidad);
        return mesa;
    }
}
