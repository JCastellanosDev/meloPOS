package mx.edu.utch.melo.dao.impl;

import mx.edu.utch.melo.dao.DescuentoDAO;
import mx.edu.utch.melo.dao.PersistenciaException;
import mx.edu.utch.melo.db.ConexionDB;
import mx.edu.utch.melo.model.TipoDescuento;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.EnumMap;
import java.util.Map;
import java.util.NoSuchElementException;

/** Implementación JDBC de {@link DescuentoDAO}. */
public class DescuentoDAOImpl implements DescuentoDAO {

    private static final String SQL_POR_TIPO = "SELECT porcentaje FROM descuentos WHERE tipo_descuento = ?";
    private static final String SQL_TODOS = "SELECT tipo_descuento, etiqueta, porcentaje FROM descuentos";
    private static final String SQL_ACTUALIZAR = "UPDATE descuentos SET etiqueta = ?, porcentaje = ? WHERE tipo_descuento = ?";

    private final ConexionDB conexionDB;

    public DescuentoDAOImpl(ConexionDB conexionDB) {
        this.conexionDB = conexionDB;
    }

    @Override
    public BigDecimal obtenerPorcentaje(TipoDescuento tipo) {
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_POR_TIPO)) {

            sentencia.setString(1, tipo.name());
            try (ResultSet resultado = sentencia.executeQuery()) {
                if (!resultado.next()) {
                    throw new NoSuchElementException("No hay porcentaje configurado para " + tipo);
                }
                return resultado.getBigDecimal("porcentaje");
            }
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo obtener el porcentaje de descuento para " + tipo, e);
        }
    }

    @Override
    public Map<TipoDescuento, ConfiguracionDescuento> obtenerTodos() {
        Map<TipoDescuento, ConfiguracionDescuento> resultado = new EnumMap<>(TipoDescuento.class);
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_TODOS);
             ResultSet filas = sentencia.executeQuery()) {

            while (filas.next()) {
                TipoDescuento tipo = TipoDescuento.valueOf(filas.getString("tipo_descuento"));
                resultado.put(tipo, new ConfiguracionDescuento(filas.getString("etiqueta"), filas.getBigDecimal("porcentaje")));
            }
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo obtener la lista de configuración de descuentos", e);
        }
        return resultado;
    }

    @Override
    public boolean actualizar(TipoDescuento tipo, String etiqueta, BigDecimal porcentaje) {
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_ACTUALIZAR)) {

            sentencia.setString(1, etiqueta);
            sentencia.setBigDecimal(2, porcentaje);
            sentencia.setString(3, tipo.name());
            return sentencia.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo actualizar la configuración de descuento para " + tipo, e);
        }
    }
}
