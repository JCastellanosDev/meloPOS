package mx.edu.utch.melo.dao.impl;

import mx.edu.utch.melo.dao.DetalleOrdenDAO;
import mx.edu.utch.melo.dao.PersistenciaException;
import mx.edu.utch.melo.db.ConexionDB;
import mx.edu.utch.melo.model.DetalleOrden;
import mx.edu.utch.melo.model.ModificadorAplicado;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Implementación JDBC de {@link DetalleOrdenDAO}. */
public class DetalleOrdenDAOImpl implements DetalleOrdenDAO {

    private static final String SQL_INSERTAR =
            "INSERT INTO detalle_orden (orden_id, producto_id, cantidad, precio_unitario, nota) VALUES (?, ?, ?, ?, ?)";
    private static final String SQL_ACTUALIZAR =
            "UPDATE detalle_orden SET orden_id = ?, producto_id = ?, cantidad = ?, precio_unitario = ?, nota = ? WHERE id = ?";
    private static final String SQL_ELIMINAR = "DELETE FROM detalle_orden WHERE id = ?";
    private static final String SQL_BASE =
            "SELECT id, orden_id, producto_id, cantidad, precio_unitario, nota FROM detalle_orden";
    private static final String SQL_POR_ID = SQL_BASE + " WHERE id = ?";
    private static final String SQL_POR_ORDEN = SQL_BASE + " WHERE orden_id = ?";

    private static final String SQL_MODIFICADORES_DEL_DETALLE =
            "SELECT dom.detalle_orden_id, dom.modificador_id, m.nombre, dom.precio_extra "
                    + "FROM detalle_orden_modificador dom "
                    + "INNER JOIN modificadores m ON m.id = dom.modificador_id "
                    + "WHERE dom.detalle_orden_id = ?";
    private static final String SQL_AGREGAR_MODIFICADOR =
            "INSERT INTO detalle_orden_modificador (detalle_orden_id, modificador_id, precio_extra) VALUES (?, ?, ?)";

    private final ConexionDB conexionDB;

    public DetalleOrdenDAOImpl(ConexionDB conexionDB) {
        this.conexionDB = conexionDB;
    }

    @Override
    public DetalleOrden crear(DetalleOrden detalle) {
        try (Connection conexion = conexionDB.obtenerConexion()) {
            return crear(detalle, conexion);
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo crear el detalle de la orden " + detalle.getOrdenId(), e);
        }
    }

    @Override
    public DetalleOrden crear(DetalleOrden detalle, Connection conexion) {
        try (PreparedStatement sentencia = conexion.prepareStatement(SQL_INSERTAR, Statement.RETURN_GENERATED_KEYS)) {

            mapearParametros(sentencia, detalle);
            sentencia.executeUpdate();

            try (ResultSet llaves = sentencia.getGeneratedKeys()) {
                if (llaves.next()) {
                    detalle.setId(llaves.getInt(1));
                }
            }
            return detalle;
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo crear el detalle de la orden " + detalle.getOrdenId(), e);
        }
    }

    @Override
    public Optional<DetalleOrden> obtenerPorId(Integer id) {
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_POR_ID)) {

            sentencia.setInt(1, id);
            try (ResultSet resultado = sentencia.executeQuery()) {
                return resultado.next() ? Optional.of(mapearFila(resultado)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo obtener el detalle de orden con id " + id, e);
        }
    }

    @Override
    public List<DetalleOrden> obtenerTodos() {
        List<DetalleOrden> detalles = new ArrayList<>();
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_BASE);
             ResultSet resultado = sentencia.executeQuery()) {

            while (resultado.next()) {
                detalles.add(mapearFila(resultado));
            }
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo obtener la lista de detalles de orden", e);
        }
        return detalles;
    }

    @Override
    public List<DetalleOrden> obtenerPorOrden(int ordenId) {
        List<DetalleOrden> detalles = new ArrayList<>();
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_POR_ORDEN)) {

            sentencia.setInt(1, ordenId);
            try (ResultSet resultado = sentencia.executeQuery()) {
                while (resultado.next()) {
                    detalles.add(mapearFila(resultado));
                }
            }
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo obtener los artículos de la orden " + ordenId, e);
        }
        return detalles;
    }

    @Override
    public List<DetalleOrden> obtenerPorOrden(int ordenId, Connection conexion) {
        List<DetalleOrden> detalles = new ArrayList<>();
        try (PreparedStatement sentencia = conexion.prepareStatement(SQL_POR_ORDEN)) {
            sentencia.setInt(1, ordenId);
            try (ResultSet resultado = sentencia.executeQuery()) {
                while (resultado.next()) {
                    detalles.add(mapearFila(resultado));
                }
            }
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo obtener los artículos de la orden " + ordenId, e);
        }
        return detalles;
    }

    @Override
    public List<ModificadorAplicado> obtenerModificadores(int detalleOrdenId) {
        List<ModificadorAplicado> modificadores = new ArrayList<>();
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_MODIFICADORES_DEL_DETALLE)) {

            sentencia.setInt(1, detalleOrdenId);
            try (ResultSet resultado = sentencia.executeQuery()) {
                while (resultado.next()) {
                    ModificadorAplicado aplicado = new ModificadorAplicado();
                    aplicado.setDetalleOrdenId(resultado.getInt("detalle_orden_id"));
                    aplicado.setModificadorId(resultado.getInt("modificador_id"));
                    aplicado.setNombre(resultado.getString("nombre"));
                    aplicado.setPrecioExtra(resultado.getBigDecimal("precio_extra"));
                    modificadores.add(aplicado);
                }
            }
        } catch (SQLException e) {
            throw new PersistenciaException(
                    "No se pudo obtener los modificadores del detalle de orden " + detalleOrdenId, e);
        }
        return modificadores;
    }

    @Override
    public void agregarModificador(int detalleOrdenId, int modificadorId, BigDecimal precioExtra) {
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_AGREGAR_MODIFICADOR)) {

            sentencia.setInt(1, detalleOrdenId);
            sentencia.setInt(2, modificadorId);
            sentencia.setBigDecimal(3, precioExtra);
            sentencia.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenciaException(
                    "No se pudo agregar el modificador " + modificadorId + " al detalle " + detalleOrdenId, e);
        }
    }

    @Override
    public boolean actualizar(DetalleOrden detalle) {
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_ACTUALIZAR)) {

            mapearParametros(sentencia, detalle);
            sentencia.setInt(6, detalle.getId());
            return sentencia.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo actualizar el detalle de orden con id " + detalle.getId(), e);
        }
    }

    @Override
    public boolean eliminar(Integer id) {
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_ELIMINAR)) {

            sentencia.setInt(1, id);
            return sentencia.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo eliminar el detalle de orden con id " + id, e);
        }
    }

    private void mapearParametros(PreparedStatement sentencia, DetalleOrden detalle) throws SQLException {
        sentencia.setInt(1, detalle.getOrdenId());
        sentencia.setInt(2, detalle.getProductoId());
        sentencia.setInt(3, detalle.getCantidad());
        sentencia.setBigDecimal(4, detalle.getPrecioUnitario());
        sentencia.setString(5, detalle.getNota());
    }

    private DetalleOrden mapearFila(ResultSet resultado) throws SQLException {
        DetalleOrden detalle = new DetalleOrden();
        detalle.setId(resultado.getInt("id"));
        detalle.setOrdenId(resultado.getInt("orden_id"));
        detalle.setProductoId(resultado.getInt("producto_id"));
        detalle.setCantidad(resultado.getInt("cantidad"));
        detalle.setPrecioUnitario(resultado.getBigDecimal("precio_unitario"));
        detalle.setNota(resultado.getString("nota"));
        return detalle;
    }
}
