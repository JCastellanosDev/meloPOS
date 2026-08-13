package mx.edu.utch.melo.dao.impl;

import mx.edu.utch.melo.dao.PersistenciaException;
import mx.edu.utch.melo.dao.TurnoDAO;
import mx.edu.utch.melo.db.ConexionDB;
import mx.edu.utch.melo.model.Turno;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Implementación JDBC de {@link TurnoDAO}. */
public class TurnoDAOImpl implements TurnoDAO {

    private static final String SQL_INSERTAR =
            "INSERT INTO turnos (sucursal_id, usuario_id, monto_apertura, monto_cierre_contado, fecha_apertura, "
                    + "fecha_cierre) VALUES (?, ?, ?, ?, ?, ?)";
    private static final String SQL_ACTUALIZAR =
            "UPDATE turnos SET sucursal_id = ?, usuario_id = ?, monto_apertura = ?, monto_cierre_contado = ?, "
                    + "fecha_apertura = ?, fecha_cierre = ? WHERE id = ?";
    private static final String SQL_ELIMINAR = "DELETE FROM turnos WHERE id = ?";
    private static final String SQL_BASE =
            "SELECT id, sucursal_id, usuario_id, monto_apertura, monto_cierre_contado, fecha_apertura, fecha_cierre "
                    + "FROM turnos";
    private static final String SQL_POR_ID = SQL_BASE + " WHERE id = ?";
    private static final String SQL_TURNO_ABIERTO = SQL_BASE + " WHERE usuario_id = ? AND fecha_cierre IS NULL";

    private final ConexionDB conexionDB;

    public TurnoDAOImpl(ConexionDB conexionDB) {
        this.conexionDB = conexionDB;
    }

    @Override
    public Turno crear(Turno turno) {
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_INSERTAR, Statement.RETURN_GENERATED_KEYS)) {

            sentencia.setInt(1, turno.getSucursalId());
            sentencia.setInt(2, turno.getUsuarioId());
            sentencia.setBigDecimal(3, turno.getMontoApertura());
            sentencia.setBigDecimal(4, turno.getMontoCierreContado());
            sentencia.setTimestamp(5, Timestamp.valueOf(turno.getFechaApertura()));
            mapearFechaCierre(sentencia, 6, turno);
            sentencia.executeUpdate();

            try (ResultSet llaves = sentencia.getGeneratedKeys()) {
                if (llaves.next()) {
                    turno.setId(llaves.getInt(1));
                }
            }
            return turno;
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo crear el turno", e);
        }
    }

    @Override
    public Optional<Turno> obtenerPorId(Integer id) {
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_POR_ID)) {

            sentencia.setInt(1, id);
            try (ResultSet resultado = sentencia.executeQuery()) {
                return resultado.next() ? Optional.of(mapearFila(resultado)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo obtener el turno con id " + id, e);
        }
    }

    @Override
    public Optional<Turno> obtenerTurnoAbierto(int usuarioId) {
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_TURNO_ABIERTO)) {

            sentencia.setInt(1, usuarioId);
            try (ResultSet resultado = sentencia.executeQuery()) {
                return resultado.next() ? Optional.of(mapearFila(resultado)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo buscar el turno abierto del usuario " + usuarioId, e);
        }
    }

    @Override
    public List<Turno> obtenerTodos() {
        List<Turno> turnos = new ArrayList<>();
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_BASE);
             ResultSet resultado = sentencia.executeQuery()) {

            while (resultado.next()) {
                turnos.add(mapearFila(resultado));
            }
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo obtener la lista de turnos", e);
        }
        return turnos;
    }

    @Override
    public boolean actualizar(Turno turno) {
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_ACTUALIZAR)) {

            sentencia.setInt(1, turno.getSucursalId());
            sentencia.setInt(2, turno.getUsuarioId());
            sentencia.setBigDecimal(3, turno.getMontoApertura());
            sentencia.setBigDecimal(4, turno.getMontoCierreContado());
            sentencia.setTimestamp(5, Timestamp.valueOf(turno.getFechaApertura()));
            mapearFechaCierre(sentencia, 6, turno);
            sentencia.setInt(7, turno.getId());
            return sentencia.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo actualizar el turno con id " + turno.getId(), e);
        }
    }

    @Override
    public boolean eliminar(Integer id) {
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_ELIMINAR)) {

            sentencia.setInt(1, id);
            return sentencia.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo eliminar el turno con id " + id, e);
        }
    }

    private void mapearFechaCierre(PreparedStatement sentencia, int indice, Turno turno) throws SQLException {
        if (turno.getFechaCierre() == null) {
            sentencia.setNull(indice, Types.TIMESTAMP);
        } else {
            sentencia.setTimestamp(indice, Timestamp.valueOf(turno.getFechaCierre()));
        }
    }

    private Turno mapearFila(ResultSet resultado) throws SQLException {
        Turno turno = new Turno();
        turno.setId(resultado.getInt("id"));
        turno.setSucursalId(resultado.getInt("sucursal_id"));
        turno.setUsuarioId(resultado.getInt("usuario_id"));
        turno.setMontoApertura(resultado.getBigDecimal("monto_apertura"));
        turno.setMontoCierreContado(resultado.getBigDecimal("monto_cierre_contado"));
        turno.setFechaApertura(resultado.getTimestamp("fecha_apertura").toLocalDateTime());
        Timestamp fechaCierre = resultado.getTimestamp("fecha_cierre");
        turno.setFechaCierre(fechaCierre == null ? null : fechaCierre.toLocalDateTime());
        return turno;
    }
}
