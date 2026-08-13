package mx.edu.utch.melo.dao.impl;

import mx.edu.utch.melo.dao.OrdenDAO;
import mx.edu.utch.melo.dao.PersistenciaException;
import mx.edu.utch.melo.db.ConexionDB;
import mx.edu.utch.melo.model.EstadoOrden;
import mx.edu.utch.melo.model.Orden;
import mx.edu.utch.melo.model.TipoOrden;

import java.math.BigDecimal;
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

/** Implementación JDBC de {@link OrdenDAO}. */
public class OrdenDAOImpl implements OrdenDAO {

    private static final String SQL_INSERTAR =
            "INSERT INTO ordenes (tipo_orden, mesa_id, usuario_id, cliente_id, turno_id, estado, subtotal, "
                    + "impuestos, distancia_km, costo_envio, total, fecha_creacion) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String SQL_ACTUALIZAR =
            "UPDATE ordenes SET tipo_orden = ?, mesa_id = ?, usuario_id = ?, cliente_id = ?, turno_id = ?, "
                    + "estado = ?, subtotal = ?, impuestos = ?, distancia_km = ?, costo_envio = ?, total = ? "
                    + "WHERE id = ?";
    private static final String SQL_ELIMINAR = "DELETE FROM ordenes WHERE id = ?";
    private static final String SQL_BASE =
            "SELECT id, tipo_orden, mesa_id, usuario_id, cliente_id, turno_id, estado, subtotal, impuestos, "
                    + "distancia_km, costo_envio, total, fecha_creacion FROM ordenes";
    private static final String SQL_POR_ID = SQL_BASE + " WHERE id = ?";
    private static final String SQL_POR_ESTADO = SQL_BASE + " WHERE estado = ?";
    private static final String SQL_ACTIVAS_POR_MESA =
            SQL_BASE + " WHERE mesa_id = ? AND estado NOT IN ('PAGADA', 'CANCELADA')";
    private static final String SQL_ACTIVAS_POR_TIPO =
            SQL_BASE + " WHERE tipo_orden = ? AND estado NOT IN ('PAGADA', 'CANCELADA')";
    // ordenes no guarda sucursal_id propio (ver CLAUDE.md): se llega a ella vía usuario_id, mismo patrón que ReporteDAOImpl.
    private static final String SQL_SIGUIENTE_NUMERO =
            "SELECT COALESCE(MAX(o.id), 0) + 1 AS siguiente FROM ordenes o "
                    + "INNER JOIN usuarios u ON u.id = o.usuario_id WHERE u.sucursal_id = ?";

    private final ConexionDB conexionDB;

    public OrdenDAOImpl(ConexionDB conexionDB) {
        this.conexionDB = conexionDB;
    }

    @Override
    public Orden crear(Orden orden) {
        try (Connection conexion = conexionDB.obtenerConexion()) {
            return crear(orden, conexion);
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo crear la orden", e);
        }
    }

    @Override
    public Orden crear(Orden orden, Connection conexion) {
        try (PreparedStatement sentencia = conexion.prepareStatement(SQL_INSERTAR, Statement.RETURN_GENERATED_KEYS)) {

            sentencia.setString(1, orden.getTipoOrden().name());
            mapearEnteroNulo(sentencia, 2, orden.getMesaId());
            sentencia.setInt(3, orden.getUsuarioId());
            mapearEnteroNulo(sentencia, 4, orden.getClienteId());
            mapearEnteroNulo(sentencia, 5, orden.getTurnoId());
            sentencia.setString(6, orden.getEstado().name());
            sentencia.setBigDecimal(7, orden.getSubtotal());
            sentencia.setBigDecimal(8, orden.getImpuestos());
            mapearDecimalNulo(sentencia, 9, orden.getDistanciaKm());
            sentencia.setBigDecimal(10, orden.getCostoEnvio());
            sentencia.setBigDecimal(11, orden.getTotal());
            sentencia.setTimestamp(12, Timestamp.valueOf(orden.getFechaCreacion()));
            sentencia.executeUpdate();

            try (ResultSet llaves = sentencia.getGeneratedKeys()) {
                if (llaves.next()) {
                    orden.setId(llaves.getInt(1));
                }
            }
            return orden;
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo crear la orden", e);
        }
    }

    @Override
    public Optional<Orden> obtenerPorId(Integer id) {
        try (Connection conexion = conexionDB.obtenerConexion()) {
            return obtenerPorId(id, conexion);
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo obtener la orden con id " + id, e);
        }
    }

    @Override
    public Optional<Orden> obtenerPorId(Integer id, Connection conexion) {
        try (PreparedStatement sentencia = conexion.prepareStatement(SQL_POR_ID)) {

            sentencia.setInt(1, id);
            try (ResultSet resultado = sentencia.executeQuery()) {
                return resultado.next() ? Optional.of(mapearFila(resultado)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo obtener la orden con id " + id, e);
        }
    }

    @Override
    public List<Orden> obtenerTodos() {
        return ejecutarConsultaLista(SQL_BASE);
    }

    @Override
    public List<Orden> obtenerPorEstado(EstadoOrden estado) {
        List<Orden> ordenes = new ArrayList<>();
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_POR_ESTADO)) {

            sentencia.setString(1, estado.name());
            try (ResultSet resultado = sentencia.executeQuery()) {
                while (resultado.next()) {
                    ordenes.add(mapearFila(resultado));
                }
            }
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo obtener las órdenes en estado " + estado, e);
        }
        return ordenes;
    }

    @Override
    public List<Orden> obtenerActivasPorMesa(int mesaId) {
        List<Orden> ordenes = new ArrayList<>();
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_ACTIVAS_POR_MESA)) {

            sentencia.setInt(1, mesaId);
            try (ResultSet resultado = sentencia.executeQuery()) {
                while (resultado.next()) {
                    ordenes.add(mapearFila(resultado));
                }
            }
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo obtener las órdenes activas de la mesa " + mesaId, e);
        }
        return ordenes;
    }

    @Override
    public List<Orden> obtenerActivasPorTipo(TipoOrden tipoOrden) {
        List<Orden> ordenes = new ArrayList<>();
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_ACTIVAS_POR_TIPO)) {

            sentencia.setString(1, tipoOrden.name());
            try (ResultSet resultado = sentencia.executeQuery()) {
                while (resultado.next()) {
                    ordenes.add(mapearFila(resultado));
                }
            }
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo obtener las órdenes activas del tipo " + tipoOrden, e);
        }
        return ordenes;
    }

    @Override
    public int siguienteNumeroOrden(int sucursalId) {
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_SIGUIENTE_NUMERO)) {

            sentencia.setInt(1, sucursalId);
            try (ResultSet resultado = sentencia.executeQuery()) {
                resultado.next();
                return resultado.getInt("siguiente");
            }
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo calcular el siguiente número de orden de la sucursal " + sucursalId, e);
        }
    }

    @Override
    public boolean actualizar(Orden orden) {
        try (Connection conexion = conexionDB.obtenerConexion()) {
            return actualizar(orden, conexion);
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo actualizar la orden con id " + orden.getId(), e);
        }
    }

    @Override
    public boolean actualizar(Orden orden, Connection conexion) {
        try (PreparedStatement sentencia = conexion.prepareStatement(SQL_ACTUALIZAR)) {

            sentencia.setString(1, orden.getTipoOrden().name());
            mapearEnteroNulo(sentencia, 2, orden.getMesaId());
            sentencia.setInt(3, orden.getUsuarioId());
            mapearEnteroNulo(sentencia, 4, orden.getClienteId());
            mapearEnteroNulo(sentencia, 5, orden.getTurnoId());
            sentencia.setString(6, orden.getEstado().name());
            sentencia.setBigDecimal(7, orden.getSubtotal());
            sentencia.setBigDecimal(8, orden.getImpuestos());
            mapearDecimalNulo(sentencia, 9, orden.getDistanciaKm());
            sentencia.setBigDecimal(10, orden.getCostoEnvio());
            sentencia.setBigDecimal(11, orden.getTotal());
            sentencia.setInt(12, orden.getId());
            return sentencia.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo actualizar la orden con id " + orden.getId(), e);
        }
    }

    @Override
    public boolean eliminar(Integer id) {
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_ELIMINAR)) {

            sentencia.setInt(1, id);
            return sentencia.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo eliminar la orden con id " + id, e);
        }
    }

    private List<Orden> ejecutarConsultaLista(String sql) {
        List<Orden> ordenes = new ArrayList<>();
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(sql);
             ResultSet resultado = sentencia.executeQuery()) {

            while (resultado.next()) {
                ordenes.add(mapearFila(resultado));
            }
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo obtener la lista de órdenes", e);
        }
        return ordenes;
    }

    private void mapearEnteroNulo(PreparedStatement sentencia, int indice, Integer valor) throws SQLException {
        if (valor == null) {
            sentencia.setNull(indice, Types.INTEGER);
        } else {
            sentencia.setInt(indice, valor);
        }
    }

    private void mapearDecimalNulo(PreparedStatement sentencia, int indice, BigDecimal valor) throws SQLException {
        if (valor == null) {
            sentencia.setNull(indice, Types.DECIMAL);
        } else {
            sentencia.setBigDecimal(indice, valor);
        }
    }

    private Integer leerEnteroNulo(ResultSet resultado, String columna) throws SQLException {
        int valor = resultado.getInt(columna);
        return resultado.wasNull() ? null : valor;
    }

    private Orden mapearFila(ResultSet resultado) throws SQLException {
        Orden orden = new Orden();
        orden.setId(resultado.getInt("id"));
        orden.setTipoOrden(TipoOrden.valueOf(resultado.getString("tipo_orden")));
        orden.setMesaId(leerEnteroNulo(resultado, "mesa_id"));
        orden.setUsuarioId(resultado.getInt("usuario_id"));
        orden.setClienteId(leerEnteroNulo(resultado, "cliente_id"));
        orden.setTurnoId(leerEnteroNulo(resultado, "turno_id"));
        orden.setEstado(EstadoOrden.valueOf(resultado.getString("estado")));
        orden.setSubtotal(resultado.getBigDecimal("subtotal"));
        orden.setImpuestos(resultado.getBigDecimal("impuestos"));
        orden.setDistanciaKm(resultado.getBigDecimal("distancia_km"));
        orden.setCostoEnvio(resultado.getBigDecimal("costo_envio"));
        orden.setTotal(resultado.getBigDecimal("total"));
        orden.setFechaCreacion(resultado.getTimestamp("fecha_creacion").toLocalDateTime());
        return orden;
    }
}
