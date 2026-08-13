package mx.edu.utch.melo.dao.impl;

import mx.edu.utch.melo.dao.PagoDAO;
import mx.edu.utch.melo.dao.PersistenciaException;
import mx.edu.utch.melo.db.ConexionDB;
import mx.edu.utch.melo.model.MetodoPago;
import mx.edu.utch.melo.model.Pago;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Implementación JDBC de {@link PagoDAO}. */
public class PagoDAOImpl implements PagoDAO {

    private static final String SQL_INSERTAR =
            "INSERT INTO pagos (orden_id, metodo_pago, monto, fecha_pago) VALUES (?, ?, ?, ?)";
    private static final String SQL_ACTUALIZAR =
            "UPDATE pagos SET orden_id = ?, metodo_pago = ?, monto = ?, fecha_pago = ? WHERE id = ?";
    private static final String SQL_ELIMINAR = "DELETE FROM pagos WHERE id = ?";
    private static final String SQL_BASE = "SELECT id, orden_id, metodo_pago, monto, fecha_pago FROM pagos";
    private static final String SQL_POR_ID = SQL_BASE + " WHERE id = ?";
    private static final String SQL_POR_ORDEN = SQL_BASE + " WHERE orden_id = ?";

    private final ConexionDB conexionDB;

    public PagoDAOImpl(ConexionDB conexionDB) {
        this.conexionDB = conexionDB;
    }

    @Override
    public Pago crear(Pago pago) {
        try (Connection conexion = conexionDB.obtenerConexion()) {
            return crear(pago, conexion);
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo crear el pago de la orden " + pago.getOrdenId(), e);
        }
    }

    @Override
    public Pago crear(Pago pago, Connection conexion) {
        try (PreparedStatement sentencia = conexion.prepareStatement(SQL_INSERTAR, Statement.RETURN_GENERATED_KEYS)) {

            mapearParametros(sentencia, pago);
            sentencia.executeUpdate();

            try (ResultSet llaves = sentencia.getGeneratedKeys()) {
                if (llaves.next()) {
                    pago.setId(llaves.getInt(1));
                }
            }
            return pago;
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo crear el pago de la orden " + pago.getOrdenId(), e);
        }
    }

    @Override
    public Optional<Pago> obtenerPorId(Integer id) {
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_POR_ID)) {

            sentencia.setInt(1, id);
            try (ResultSet resultado = sentencia.executeQuery()) {
                return resultado.next() ? Optional.of(mapearFila(resultado)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo obtener el pago con id " + id, e);
        }
    }

    @Override
    public List<Pago> obtenerTodos() {
        List<Pago> pagos = new ArrayList<>();
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_BASE);
             ResultSet resultado = sentencia.executeQuery()) {

            while (resultado.next()) {
                pagos.add(mapearFila(resultado));
            }
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo obtener la lista de pagos", e);
        }
        return pagos;
    }

    @Override
    public List<Pago> obtenerPorOrden(int ordenId) {
        List<Pago> pagos = new ArrayList<>();
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_POR_ORDEN)) {

            sentencia.setInt(1, ordenId);
            try (ResultSet resultado = sentencia.executeQuery()) {
                while (resultado.next()) {
                    pagos.add(mapearFila(resultado));
                }
            }
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo obtener los pagos de la orden " + ordenId, e);
        }
        return pagos;
    }

    @Override
    public boolean actualizar(Pago pago) {
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_ACTUALIZAR)) {

            mapearParametros(sentencia, pago);
            sentencia.setInt(5, pago.getId());
            return sentencia.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo actualizar el pago con id " + pago.getId(), e);
        }
    }

    @Override
    public boolean eliminar(Integer id) {
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_ELIMINAR)) {

            sentencia.setInt(1, id);
            return sentencia.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo eliminar el pago con id " + id, e);
        }
    }

    private void mapearParametros(PreparedStatement sentencia, Pago pago) throws SQLException {
        sentencia.setInt(1, pago.getOrdenId());
        sentencia.setString(2, pago.getMetodoPago().name());
        sentencia.setBigDecimal(3, pago.getMonto());
        sentencia.setTimestamp(4, Timestamp.valueOf(pago.getFechaPago()));
    }

    private Pago mapearFila(ResultSet resultado) throws SQLException {
        Pago pago = new Pago();
        pago.setId(resultado.getInt("id"));
        pago.setOrdenId(resultado.getInt("orden_id"));
        pago.setMetodoPago(MetodoPago.valueOf(resultado.getString("metodo_pago")));
        pago.setMonto(resultado.getBigDecimal("monto"));
        pago.setFechaPago(resultado.getTimestamp("fecha_pago").toLocalDateTime());
        return pago;
    }
}
