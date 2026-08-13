package mx.edu.utch.melo.dao.impl;

import mx.edu.utch.melo.dao.PersistenciaException;
import mx.edu.utch.melo.dao.ReporteDAO;
import mx.edu.utch.melo.db.ConexionDB;
import mx.edu.utch.melo.model.reporte.PlatilloVendido;
import mx.edu.utch.melo.model.reporte.ResumenTurno;
import mx.edu.utch.melo.model.reporte.VentaDiaria;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementación JDBC de {@link ReporteDAO}. `ordenes` no guarda
 * `sucursal_id` propio (ver CLAUDE.md): se deriva uniendo con `usuarios`.
 */
public class ReporteDAOImpl implements ReporteDAO {

    private static final String SQL_VENTAS_POR_PERIODO =
            "SELECT DATE(o.fecha_creacion) AS dia, SUM(o.total) AS total "
                    + "FROM ordenes o INNER JOIN usuarios u ON u.id = o.usuario_id "
                    + "WHERE u.sucursal_id = ? AND o.estado <> 'CANCELADA' "
                    + "AND DATE(o.fecha_creacion) BETWEEN ? AND ? "
                    + "GROUP BY DATE(o.fecha_creacion) ORDER BY dia";

    private static final String SQL_PLATILLOS_MAS_VENDIDOS =
            "SELECT p.nombre AS nombre, SUM(d.cantidad) AS cantidad "
                    + "FROM detalle_orden d "
                    + "INNER JOIN ordenes o ON o.id = d.orden_id "
                    + "INNER JOIN usuarios u ON u.id = o.usuario_id "
                    + "INNER JOIN productos p ON p.id = d.producto_id "
                    + "WHERE u.sucursal_id = ? AND o.estado <> 'CANCELADA' "
                    + "AND DATE(o.fecha_creacion) BETWEEN ? AND ? "
                    + "GROUP BY p.id, p.nombre ORDER BY cantidad DESC LIMIT ?";

    // pagos no tiene turno_id propio -- se llega a él vía ordenes.turno_id (ver CLAUDE.md,
    // mismo principio de "no duplicar lo que ya se puede derivar" que usan otras tablas).
    private static final String SQL_RESUMEN_TURNO =
            "SELECT "
                    + "COALESCE(SUM(CASE WHEN p.metodo_pago = 'EFECTIVO' THEN p.monto ELSE 0 END), 0) AS efectivo, "
                    + "COALESCE(SUM(CASE WHEN p.metodo_pago = 'TARJETA' THEN p.monto ELSE 0 END), 0) AS tarjeta, "
                    + "COALESCE(SUM(CASE WHEN p.metodo_pago = 'TRANSFERENCIA' THEN p.monto ELSE 0 END), 0) AS transferencia, "
                    + "COALESCE(SUM(p.monto), 0) AS total, "
                    + "COUNT(DISTINCT o.id) AS numero_ordenes "
                    + "FROM ordenes o INNER JOIN pagos p ON p.orden_id = o.id "
                    + "WHERE o.turno_id = ? AND o.estado <> 'CANCELADA'";

    private final ConexionDB conexionDB;

    public ReporteDAOImpl(ConexionDB conexionDB) {
        this.conexionDB = conexionDB;
    }

    @Override
    public List<VentaDiaria> obtenerVentasPorPeriodo(int sucursalId, LocalDate desde, LocalDate hasta) {
        List<VentaDiaria> ventas = new ArrayList<>();
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_VENTAS_POR_PERIODO)) {

            sentencia.setInt(1, sucursalId);
            sentencia.setObject(2, desde);
            sentencia.setObject(3, hasta);
            try (ResultSet resultado = sentencia.executeQuery()) {
                while (resultado.next()) {
                    ventas.add(new VentaDiaria(resultado.getDate("dia").toLocalDate(), resultado.getBigDecimal("total")));
                }
            }
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudieron obtener las ventas por periodo de la sucursal " + sucursalId, e);
        }
        return ventas;
    }

    @Override
    public List<PlatilloVendido> obtenerPlatillosMasVendidos(int sucursalId, LocalDate desde, LocalDate hasta, int limite) {
        List<PlatilloVendido> platillos = new ArrayList<>();
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_PLATILLOS_MAS_VENDIDOS)) {

            sentencia.setInt(1, sucursalId);
            sentencia.setObject(2, desde);
            sentencia.setObject(3, hasta);
            sentencia.setInt(4, limite);
            try (ResultSet resultado = sentencia.executeQuery()) {
                while (resultado.next()) {
                    platillos.add(new PlatilloVendido(resultado.getString("nombre"), resultado.getInt("cantidad")));
                }
            }
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudieron obtener los platillos más vendidos de la sucursal " + sucursalId, e);
        }
        return platillos;
    }

    @Override
    public ResumenTurno obtenerResumenTurno(int turnoId) {
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_RESUMEN_TURNO)) {

            sentencia.setInt(1, turnoId);
            try (ResultSet resultado = sentencia.executeQuery()) {
                resultado.next();
                return new ResumenTurno(
                        turnoId,
                        resultado.getBigDecimal("efectivo"),
                        resultado.getBigDecimal("tarjeta"),
                        resultado.getBigDecimal("transferencia"),
                        resultado.getBigDecimal("total"),
                        resultado.getInt("numero_ordenes"));
            }
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo obtener el resumen del turno " + turnoId, e);
        }
    }
}
