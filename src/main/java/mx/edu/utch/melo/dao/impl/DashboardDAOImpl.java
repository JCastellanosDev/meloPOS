package mx.edu.utch.melo.dao.impl;

import mx.edu.utch.melo.dao.DashboardDAO;
import mx.edu.utch.melo.dao.PersistenciaException;
import mx.edu.utch.melo.db.ConexionDB;
import mx.edu.utch.melo.model.MetodoPago;
import mx.edu.utch.melo.model.dashboard.CategoriaVendida;
import mx.edu.utch.melo.model.dashboard.ProductoSinVentas;
import mx.edu.utch.melo.model.dashboard.ResumenDelivery;
import mx.edu.utch.melo.model.dashboard.ResumenVentas;
import mx.edu.utch.melo.model.dashboard.VentaPorEmpleado;
import mx.edu.utch.melo.model.dashboard.VentaPorHora;
import mx.edu.utch.melo.model.dashboard.VentaPorMetodoPago;
import mx.edu.utch.melo.model.reporte.PlatilloVendido;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementación JDBC de {@link DashboardDAO}. `ordenes` no guarda `sucursal_id` propio (ver
 * CLAUDE.md): se deriva uniendo con `usuarios`, mismo patrón que {@link ReporteDAOImpl}.
 */
public class DashboardDAOImpl implements DashboardDAO {

    private static final String SQL_RESUMEN_VENTAS =
            "SELECT COALESCE(SUM(o.total), 0) AS total, COUNT(*) AS numero_ordenes, "
                    + "COALESCE(AVG(o.total), 0) AS ticket_promedio "
                    + "FROM ordenes o INNER JOIN usuarios u ON u.id = o.usuario_id "
                    + "WHERE u.sucursal_id = ? AND o.estado <> 'CANCELADA' "
                    + "AND DATE(o.fecha_creacion) BETWEEN ? AND ?";

    private static final String SQL_VENTAS_POR_HORA =
            "SELECT HOUR(o.fecha_creacion) AS hora, SUM(o.total) AS total "
                    + "FROM ordenes o INNER JOIN usuarios u ON u.id = o.usuario_id "
                    + "WHERE u.sucursal_id = ? AND o.estado <> 'CANCELADA' "
                    + "AND DATE(o.fecha_creacion) BETWEEN ? AND ? "
                    + "GROUP BY HOUR(o.fecha_creacion) ORDER BY hora";

    private static final String SQL_PRODUCTOS_BASE =
            "SELECT p.nombre AS nombre, SUM(d.cantidad) AS cantidad "
                    + "FROM detalle_orden d "
                    + "INNER JOIN ordenes o ON o.id = d.orden_id "
                    + "INNER JOIN usuarios u ON u.id = o.usuario_id "
                    + "INNER JOIN productos p ON p.id = d.producto_id "
                    + "WHERE u.sucursal_id = ? AND o.estado <> 'CANCELADA' "
                    + "AND DATE(o.fecha_creacion) BETWEEN ? AND ? "
                    + "GROUP BY p.id, p.nombre ";
    private static final String SQL_PRODUCTOS_MAS_VENDIDOS = SQL_PRODUCTOS_BASE + "ORDER BY cantidad DESC LIMIT ?";
    private static final String SQL_PRODUCTOS_MENOS_VENDIDOS = SQL_PRODUCTOS_BASE + "ORDER BY cantidad ASC LIMIT ?";

    private static final String SQL_CATEGORIAS_PRINCIPALES =
            "SELECT c.nombre AS categoria, SUM(d.cantidad) AS unidades, SUM(d.cantidad * d.precio_unitario) AS ingresos "
                    + "FROM detalle_orden d "
                    + "INNER JOIN ordenes o ON o.id = d.orden_id "
                    + "INNER JOIN usuarios u ON u.id = o.usuario_id "
                    + "INNER JOIN productos p ON p.id = d.producto_id "
                    + "INNER JOIN categorias c ON c.id = p.categoria_id "
                    + "WHERE u.sucursal_id = ? AND o.estado <> 'CANCELADA' "
                    + "AND DATE(o.fecha_creacion) BETWEEN ? AND ? "
                    + "GROUP BY c.id, c.nombre ORDER BY unidades DESC LIMIT ?";

    private static final String SQL_VENTAS_POR_METODO_PAGO =
            "SELECT p.metodo_pago AS metodo, SUM(p.monto) AS total "
                    + "FROM pagos p "
                    + "INNER JOIN ordenes o ON o.id = p.orden_id "
                    + "INNER JOIN usuarios u ON u.id = o.usuario_id "
                    + "WHERE u.sucursal_id = ? AND o.estado <> 'CANCELADA' "
                    + "AND DATE(o.fecha_creacion) BETWEEN ? AND ? "
                    + "GROUP BY p.metodo_pago";

    private static final String SQL_VENTAS_POR_EMPLEADO =
            "SELECT u.nombre AS empleado, COUNT(*) AS numero_ordenes, SUM(o.total) AS total "
                    + "FROM ordenes o INNER JOIN usuarios u ON u.id = o.usuario_id "
                    + "WHERE u.sucursal_id = ? AND o.estado <> 'CANCELADA' "
                    + "AND DATE(o.fecha_creacion) BETWEEN ? AND ? "
                    + "GROUP BY u.id, u.nombre ORDER BY total DESC";

    // LEFT JOIN con la condición de fecha/estado en el propio JOIN (no en el WHERE) para que un
    // producto sin ninguna venta en el rango siga apareciendo (con o.id nulo) en vez de desaparecer.
    private static final String SQL_PRODUCTOS_SIN_VENTAS =
            "SELECT p.id AS id, p.nombre AS nombre, p.precio AS precio "
                    + "FROM productos p "
                    + "LEFT JOIN detalle_orden d ON d.producto_id = p.id "
                    + "LEFT JOIN ordenes o ON o.id = d.orden_id AND o.estado <> 'CANCELADA' "
                    + "AND DATE(o.fecha_creacion) BETWEEN ? AND ? "
                    + "WHERE p.sucursal_id = ? AND p.disponible = TRUE "
                    + "GROUP BY p.id, p.nombre, p.precio "
                    + "HAVING COUNT(o.id) = 0 "
                    + "ORDER BY p.nombre";

    private static final String SQL_RESUMEN_DELIVERY =
            "SELECT COUNT(*) AS numero_pedidos, "
                    + "COALESCE(AVG(o.distancia_km), 0) AS distancia_promedio, "
                    + "COALESCE(AVG(o.costo_envio), 0) AS costo_envio_promedio, "
                    + "COALESCE(SUM(o.total), 0) AS ingresos_totales "
                    + "FROM ordenes o INNER JOIN usuarios u ON u.id = o.usuario_id "
                    + "WHERE u.sucursal_id = ? AND o.tipo_orden = 'DOMICILIO' AND o.estado <> 'CANCELADA' "
                    + "AND DATE(o.fecha_creacion) BETWEEN ? AND ?";

    private final ConexionDB conexionDB;

    public DashboardDAOImpl(ConexionDB conexionDB) {
        this.conexionDB = conexionDB;
    }

    @Override
    public ResumenVentas obtenerResumenVentas(int sucursalId, LocalDate desde, LocalDate hasta) {
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_RESUMEN_VENTAS)) {

            sentencia.setInt(1, sucursalId);
            sentencia.setObject(2, desde);
            sentencia.setObject(3, hasta);
            try (ResultSet resultado = sentencia.executeQuery()) {
                resultado.next();
                return new ResumenVentas(resultado.getBigDecimal("total"), resultado.getInt("numero_ordenes"),
                        resultado.getBigDecimal("ticket_promedio"));
            }
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo obtener el resumen de ventas de la sucursal " + sucursalId, e);
        }
    }

    @Override
    public List<VentaPorHora> obtenerVentasPorHora(int sucursalId, LocalDate desde, LocalDate hasta) {
        List<VentaPorHora> ventas = new ArrayList<>();
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_VENTAS_POR_HORA)) {

            sentencia.setInt(1, sucursalId);
            sentencia.setObject(2, desde);
            sentencia.setObject(3, hasta);
            try (ResultSet resultado = sentencia.executeQuery()) {
                while (resultado.next()) {
                    ventas.add(new VentaPorHora(resultado.getInt("hora"), resultado.getBigDecimal("total")));
                }
            }
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudieron obtener las ventas por hora de la sucursal " + sucursalId, e);
        }
        return ventas;
    }

    @Override
    public List<PlatilloVendido> obtenerProductosMasVendidos(int sucursalId, LocalDate desde, LocalDate hasta, int limite) {
        return ejecutarRankingProductos(SQL_PRODUCTOS_MAS_VENDIDOS, sucursalId, desde, hasta, limite);
    }

    @Override
    public List<PlatilloVendido> obtenerProductosMenosVendidos(int sucursalId, LocalDate desde, LocalDate hasta, int limite) {
        return ejecutarRankingProductos(SQL_PRODUCTOS_MENOS_VENDIDOS, sucursalId, desde, hasta, limite);
    }

    private List<PlatilloVendido> ejecutarRankingProductos(String sql, int sucursalId, LocalDate desde, LocalDate hasta, int limite) {
        List<PlatilloVendido> platillos = new ArrayList<>();
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(sql)) {

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
            throw new PersistenciaException("No se pudo obtener el ranking de productos de la sucursal " + sucursalId, e);
        }
        return platillos;
    }

    @Override
    public List<CategoriaVendida> obtenerCategoriasPrincipales(int sucursalId, LocalDate desde, LocalDate hasta, int limite) {
        List<CategoriaVendida> categorias = new ArrayList<>();
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_CATEGORIAS_PRINCIPALES)) {

            sentencia.setInt(1, sucursalId);
            sentencia.setObject(2, desde);
            sentencia.setObject(3, hasta);
            sentencia.setInt(4, limite);
            try (ResultSet resultado = sentencia.executeQuery()) {
                while (resultado.next()) {
                    categorias.add(new CategoriaVendida(resultado.getString("categoria"), resultado.getInt("unidades"),
                            resultado.getBigDecimal("ingresos")));
                }
            }
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudieron obtener las categorías principales de la sucursal " + sucursalId, e);
        }
        return categorias;
    }

    @Override
    public List<VentaPorMetodoPago> obtenerVentasPorMetodoPago(int sucursalId, LocalDate desde, LocalDate hasta) {
        List<VentaPorMetodoPago> ventas = new ArrayList<>();
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_VENTAS_POR_METODO_PAGO)) {

            sentencia.setInt(1, sucursalId);
            sentencia.setObject(2, desde);
            sentencia.setObject(3, hasta);
            try (ResultSet resultado = sentencia.executeQuery()) {
                while (resultado.next()) {
                    ventas.add(new VentaPorMetodoPago(MetodoPago.valueOf(resultado.getString("metodo")),
                            resultado.getBigDecimal("total")));
                }
            }
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudieron obtener las ventas por método de pago de la sucursal " + sucursalId, e);
        }
        return ventas;
    }

    @Override
    public List<VentaPorEmpleado> obtenerVentasPorEmpleado(int sucursalId, LocalDate desde, LocalDate hasta) {
        List<VentaPorEmpleado> ventas = new ArrayList<>();
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_VENTAS_POR_EMPLEADO)) {

            sentencia.setInt(1, sucursalId);
            sentencia.setObject(2, desde);
            sentencia.setObject(3, hasta);
            try (ResultSet resultado = sentencia.executeQuery()) {
                while (resultado.next()) {
                    ventas.add(new VentaPorEmpleado(resultado.getString("empleado"), resultado.getInt("numero_ordenes"),
                            resultado.getBigDecimal("total")));
                }
            }
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudieron obtener las ventas por empleado de la sucursal " + sucursalId, e);
        }
        return ventas;
    }

    @Override
    public List<ProductoSinVentas> obtenerProductosSinVentas(int sucursalId, LocalDate desde, LocalDate hasta) {
        List<ProductoSinVentas> productos = new ArrayList<>();
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_PRODUCTOS_SIN_VENTAS)) {

            sentencia.setObject(1, desde);
            sentencia.setObject(2, hasta);
            sentencia.setInt(3, sucursalId);
            try (ResultSet resultado = sentencia.executeQuery()) {
                while (resultado.next()) {
                    productos.add(new ProductoSinVentas(resultado.getInt("id"), resultado.getString("nombre"),
                            resultado.getBigDecimal("precio")));
                }
            }
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudieron obtener los productos sin ventas de la sucursal " + sucursalId, e);
        }
        return productos;
    }

    @Override
    public ResumenDelivery obtenerResumenDelivery(int sucursalId, LocalDate desde, LocalDate hasta) {
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_RESUMEN_DELIVERY)) {

            sentencia.setInt(1, sucursalId);
            sentencia.setObject(2, desde);
            sentencia.setObject(3, hasta);
            try (ResultSet resultado = sentencia.executeQuery()) {
                resultado.next();
                return new ResumenDelivery(resultado.getInt("numero_pedidos"), resultado.getBigDecimal("distancia_promedio"),
                        resultado.getBigDecimal("costo_envio_promedio"), resultado.getBigDecimal("ingresos_totales"));
            }
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo obtener el resumen de delivery de la sucursal " + sucursalId, e);
        }
    }
}
