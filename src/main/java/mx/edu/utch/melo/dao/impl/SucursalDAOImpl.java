package mx.edu.utch.melo.dao.impl;

import mx.edu.utch.melo.dao.PersistenciaException;
import mx.edu.utch.melo.dao.SucursalDAO;
import mx.edu.utch.melo.db.ConexionDB;
import mx.edu.utch.melo.model.Sucursal;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Implementación JDBC de {@link SucursalDAO}. */
public class SucursalDAOImpl implements SucursalDAO {

    private static final String SQL_INSERTAR =
            "INSERT INTO sucursales (nombre, calle, numero, colonia, codigo_postal, ciudad, estado, pais, "
                    + "latitud, longitud, telefono, tarifa_base_envio, tarifa_por_km, activa, aplica_iva) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String SQL_ACTUALIZAR =
            "UPDATE sucursales SET nombre = ?, calle = ?, numero = ?, colonia = ?, codigo_postal = ?, ciudad = ?, "
                    + "estado = ?, pais = ?, latitud = ?, longitud = ?, telefono = ?, tarifa_base_envio = ?, "
                    + "tarifa_por_km = ?, activa = ?, aplica_iva = ? WHERE id = ?";
    private static final String SQL_ELIMINAR = "DELETE FROM sucursales WHERE id = ?";
    private static final String SQL_BASE =
            "SELECT id, nombre, calle, numero, colonia, codigo_postal, ciudad, estado, pais, latitud, longitud, "
                    + "telefono, tarifa_base_envio, tarifa_por_km, activa, aplica_iva FROM sucursales";
    private static final String SQL_POR_ID = SQL_BASE + " WHERE id = ?";
    private static final String SQL_ACTIVAS = SQL_BASE + " WHERE activa = TRUE";

    private final ConexionDB conexionDB;

    public SucursalDAOImpl(ConexionDB conexionDB) {
        this.conexionDB = conexionDB;
    }

    @Override
    public Sucursal crear(Sucursal sucursal) {
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_INSERTAR, Statement.RETURN_GENERATED_KEYS)) {

            mapearParametros(sentencia, sucursal);
            sentencia.executeUpdate();

            try (ResultSet llaves = sentencia.getGeneratedKeys()) {
                if (llaves.next()) {
                    sucursal.setId(llaves.getInt(1));
                }
            }
            return sucursal;
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo crear la sucursal: " + sucursal.getNombre(), e);
        }
    }

    @Override
    public Optional<Sucursal> obtenerPorId(Integer id) {
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_POR_ID)) {

            sentencia.setInt(1, id);
            try (ResultSet resultado = sentencia.executeQuery()) {
                return resultado.next() ? Optional.of(mapearFila(resultado)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo obtener la sucursal con id " + id, e);
        }
    }

    @Override
    public List<Sucursal> obtenerTodos() {
        return ejecutarConsultaLista(SQL_BASE);
    }

    @Override
    public List<Sucursal> obtenerActivas() {
        return ejecutarConsultaLista(SQL_ACTIVAS);
    }

    @Override
    public boolean actualizar(Sucursal sucursal) {
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_ACTUALIZAR)) {

            mapearParametros(sentencia, sucursal);
            sentencia.setInt(16, sucursal.getId());
            return sentencia.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo actualizar la sucursal con id " + sucursal.getId(), e);
        }
    }

    @Override
    public boolean eliminar(Integer id) {
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_ELIMINAR)) {

            sentencia.setInt(1, id);
            return sentencia.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo eliminar la sucursal con id " + id, e);
        }
    }

    private List<Sucursal> ejecutarConsultaLista(String sql) {
        List<Sucursal> sucursales = new ArrayList<>();
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(sql);
             ResultSet resultado = sentencia.executeQuery()) {

            while (resultado.next()) {
                sucursales.add(mapearFila(resultado));
            }
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo obtener la lista de sucursales", e);
        }
        return sucursales;
    }

    private void mapearParametros(PreparedStatement sentencia, Sucursal sucursal) throws SQLException {
        sentencia.setString(1, sucursal.getNombre());
        sentencia.setString(2, sucursal.getCalle());
        sentencia.setString(3, sucursal.getNumero());
        sentencia.setString(4, sucursal.getColonia());
        sentencia.setString(5, sucursal.getCodigoPostal());
        sentencia.setString(6, sucursal.getCiudad());
        sentencia.setString(7, sucursal.getEstado());
        sentencia.setString(8, sucursal.getPais());
        sentencia.setBigDecimal(9, sucursal.getLatitud());
        sentencia.setBigDecimal(10, sucursal.getLongitud());
        sentencia.setString(11, sucursal.getTelefono());
        sentencia.setBigDecimal(12, sucursal.getTarifaBaseEnvio());
        sentencia.setBigDecimal(13, sucursal.getTarifaPorKm());
        sentencia.setBoolean(14, sucursal.isActiva());
        sentencia.setBoolean(15, sucursal.isAplicaIva());
    }

    private Sucursal mapearFila(ResultSet resultado) throws SQLException {
        Sucursal sucursal = new Sucursal();
        sucursal.setId(resultado.getInt("id"));
        sucursal.setNombre(resultado.getString("nombre"));
        sucursal.setCalle(resultado.getString("calle"));
        sucursal.setNumero(resultado.getString("numero"));
        sucursal.setColonia(resultado.getString("colonia"));
        sucursal.setCodigoPostal(resultado.getString("codigo_postal"));
        sucursal.setCiudad(resultado.getString("ciudad"));
        sucursal.setEstado(resultado.getString("estado"));
        sucursal.setPais(resultado.getString("pais"));
        sucursal.setLatitud(resultado.getBigDecimal("latitud"));
        sucursal.setLongitud(resultado.getBigDecimal("longitud"));
        sucursal.setTelefono(resultado.getString("telefono"));
        sucursal.setTarifaBaseEnvio(resultado.getBigDecimal("tarifa_base_envio"));
        sucursal.setTarifaPorKm(resultado.getBigDecimal("tarifa_por_km"));
        sucursal.setActiva(resultado.getBoolean("activa"));
        sucursal.setAplicaIva(resultado.getBoolean("aplica_iva"));
        return sucursal;
    }
}
