package mx.edu.utch.melo.dao.impl;

import mx.edu.utch.melo.dao.ControlIntentosPinDAO;
import mx.edu.utch.melo.dao.PersistenciaException;
import mx.edu.utch.melo.db.ConexionDB;
import mx.edu.utch.melo.model.EstadoBloqueoPin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.Optional;

/** Implementación JDBC de {@link ControlIntentosPinDAO}. */
public class ControlIntentosPinDAOImpl implements ControlIntentosPinDAO {

    private static final String SQL_POR_SUCURSAL =
            "SELECT sucursal_id, intentos_fallidos, bloqueado_hasta FROM bloqueo_pin_sucursal WHERE sucursal_id = ?";
    // Una fila por sucursal: si ya existe, se actualiza en vez de duplicar (ver ControlIntentosPinDAO).
    private static final String SQL_UPSERT =
            "INSERT INTO bloqueo_pin_sucursal (sucursal_id, intentos_fallidos, bloqueado_hasta) VALUES (?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE intentos_fallidos = VALUES(intentos_fallidos), "
                    + "bloqueado_hasta = VALUES(bloqueado_hasta)";
    // "intentos_fallidos = intentos_fallidos + 1" en el propio UPDATE lo hace atómico a nivel de
    // fila (bloqueo de InnoDB) -- dos incrementos concurrentes de la misma sucursal nunca se
    // pisan (ver ControlIntentosPinDAO#incrementarIntentosFallidos, auditoría de Fase 7).
    private static final String SQL_INCREMENTAR =
            "INSERT INTO bloqueo_pin_sucursal (sucursal_id, intentos_fallidos, bloqueado_hasta) VALUES (?, 1, NULL) "
                    + "ON DUPLICATE KEY UPDATE intentos_fallidos = intentos_fallidos + 1";
    private static final String SQL_INTENTOS_ACTUALES =
            "SELECT intentos_fallidos FROM bloqueo_pin_sucursal WHERE sucursal_id = ?";
    private static final String SQL_ELIMINAR = "DELETE FROM bloqueo_pin_sucursal WHERE sucursal_id = ?";

    private final ConexionDB conexionDB;

    public ControlIntentosPinDAOImpl(ConexionDB conexionDB) {
        this.conexionDB = conexionDB;
    }

    @Override
    public Optional<EstadoBloqueoPin> obtenerEstado(int sucursalId) {
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_POR_SUCURSAL)) {

            sentencia.setInt(1, sucursalId);
            try (ResultSet resultado = sentencia.executeQuery()) {
                return resultado.next() ? Optional.of(mapearFila(resultado)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo obtener el estado de bloqueo de PIN de la sucursal " + sucursalId, e);
        }
    }

    @Override
    public int incrementarIntentosFallidos(int sucursalId) {
        try (Connection conexion = conexionDB.obtenerConexion()) {
            try (PreparedStatement incrementar = conexion.prepareStatement(SQL_INCREMENTAR)) {
                incrementar.setInt(1, sucursalId);
                incrementar.executeUpdate();
            }
            // Lectura aparte: el UPDATE en sí ya es atómico y nunca pierde un incremento
            // concurrente -- esta lectura solo puede quedar desfasada si otro intento incrementa
            // justo entre el UPDATE y este SELECT, nunca duplicada ni perdida.
            try (PreparedStatement leer = conexion.prepareStatement(SQL_INTENTOS_ACTUALES)) {
                leer.setInt(1, sucursalId);
                try (ResultSet resultado = leer.executeQuery()) {
                    resultado.next();
                    return resultado.getInt("intentos_fallidos");
                }
            }
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo incrementar los intentos fallidos de la sucursal " + sucursalId, e);
        }
    }

    @Override
    public void guardarEstado(int sucursalId, int intentosFallidos, LocalDateTime bloqueadoHasta) {
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_UPSERT)) {

            sentencia.setInt(1, sucursalId);
            sentencia.setInt(2, intentosFallidos);
            if (bloqueadoHasta == null) {
                sentencia.setNull(3, Types.TIMESTAMP);
            } else {
                sentencia.setTimestamp(3, Timestamp.valueOf(bloqueadoHasta));
            }
            sentencia.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo guardar el estado de bloqueo de PIN de la sucursal " + sucursalId, e);
        }
    }

    @Override
    public void reiniciar(int sucursalId) {
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_ELIMINAR)) {

            sentencia.setInt(1, sucursalId);
            sentencia.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo reiniciar el estado de bloqueo de PIN de la sucursal " + sucursalId, e);
        }
    }

    private EstadoBloqueoPin mapearFila(ResultSet resultado) throws SQLException {
        Timestamp bloqueadoHasta = resultado.getTimestamp("bloqueado_hasta");
        return new EstadoBloqueoPin(
                resultado.getInt("sucursal_id"),
                resultado.getInt("intentos_fallidos"),
                bloqueadoHasta == null ? null : bloqueadoHasta.toLocalDateTime());
    }
}
