package mx.edu.utch.melo.dao.impl;

import mx.edu.utch.melo.dao.ClienteDAO;
import mx.edu.utch.melo.dao.PersistenciaException;
import mx.edu.utch.melo.db.ConexionDB;
import mx.edu.utch.melo.model.Cliente;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Implementación JDBC de {@link ClienteDAO}. */
public class ClienteDAOImpl implements ClienteDAO {

    private static final String SQL_INSERTAR =
            "INSERT INTO clientes (nombre, telefono, direccion, latitud, longitud, notas) VALUES (?, ?, ?, ?, ?, ?)";
    private static final String SQL_ACTUALIZAR =
            "UPDATE clientes SET nombre = ?, telefono = ?, direccion = ?, latitud = ?, longitud = ?, notas = ? WHERE id = ?";
    private static final String SQL_ELIMINAR = "DELETE FROM clientes WHERE id = ?";
    private static final String SQL_BASE =
            "SELECT id, nombre, telefono, direccion, latitud, longitud, notas FROM clientes";
    private static final String SQL_POR_ID = SQL_BASE + " WHERE id = ?";
    private static final String SQL_POR_TELEFONO = SQL_BASE + " WHERE telefono = ?";
    // SQL_BASE + " WHERE id IN (...)" con tantos "?" como ids se pidan -- se arma en obtenerPorIds,
    // nunca por concatenación del valor en sí (los ids van como parámetros de PreparedStatement).
    private static final String SQL_POR_IDS_PREFIJO = SQL_BASE + " WHERE id IN (";

    private final ConexionDB conexionDB;

    public ClienteDAOImpl(ConexionDB conexionDB) {
        this.conexionDB = conexionDB;
    }

    @Override
    public Cliente crear(Cliente cliente) {
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_INSERTAR, Statement.RETURN_GENERATED_KEYS)) {

            mapearParametros(sentencia, cliente);
            sentencia.executeUpdate();

            try (ResultSet llaves = sentencia.getGeneratedKeys()) {
                if (llaves.next()) {
                    cliente.setId(llaves.getInt(1));
                }
            }
            return cliente;
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo crear el cliente: " + cliente.getNombre(), e);
        }
    }

    @Override
    public Optional<Cliente> obtenerPorId(Integer id) {
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_POR_ID)) {

            sentencia.setInt(1, id);
            try (ResultSet resultado = sentencia.executeQuery()) {
                return resultado.next() ? Optional.of(mapearFila(resultado)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo obtener el cliente con id " + id, e);
        }
    }

    @Override
    public Optional<Cliente> obtenerPorTelefono(String telefono) {
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_POR_TELEFONO)) {

            sentencia.setString(1, telefono);
            try (ResultSet resultado = sentencia.executeQuery()) {
                return resultado.next() ? Optional.of(mapearFila(resultado)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo buscar el cliente con teléfono " + telefono, e);
        }
    }

    @Override
    public List<Cliente> obtenerPorIds(List<Integer> ids) {
        List<Cliente> clientes = new ArrayList<>();
        if (ids == null || ids.isEmpty()) {
            return clientes;
        }
        String marcadores = String.join(", ", java.util.Collections.nCopies(ids.size(), "?"));
        String sql = SQL_POR_IDS_PREFIJO + marcadores + ")";
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(sql)) {

            for (int i = 0; i < ids.size(); i++) {
                sentencia.setInt(i + 1, ids.get(i));
            }
            try (ResultSet resultado = sentencia.executeQuery()) {
                while (resultado.next()) {
                    clientes.add(mapearFila(resultado));
                }
            }
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo obtener los clientes con ids " + ids, e);
        }
        return clientes;
    }

    @Override
    public List<Cliente> obtenerTodos() {
        List<Cliente> clientes = new ArrayList<>();
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_BASE);
             ResultSet resultado = sentencia.executeQuery()) {

            while (resultado.next()) {
                clientes.add(mapearFila(resultado));
            }
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo obtener la lista de clientes", e);
        }
        return clientes;
    }

    @Override
    public boolean actualizar(Cliente cliente) {
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_ACTUALIZAR)) {

            mapearParametros(sentencia, cliente);
            sentencia.setInt(7, cliente.getId());
            return sentencia.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo actualizar el cliente con id " + cliente.getId(), e);
        }
    }

    @Override
    public boolean eliminar(Integer id) {
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_ELIMINAR)) {

            sentencia.setInt(1, id);
            return sentencia.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo eliminar el cliente con id " + id, e);
        }
    }

    private void mapearParametros(PreparedStatement sentencia, Cliente cliente) throws SQLException {
        sentencia.setString(1, cliente.getNombre());
        sentencia.setString(2, cliente.getTelefono());
        sentencia.setString(3, cliente.getDireccion());
        sentencia.setBigDecimal(4, cliente.getLatitud());
        sentencia.setBigDecimal(5, cliente.getLongitud());
        sentencia.setString(6, cliente.getNotas());
    }

    private Cliente mapearFila(ResultSet resultado) throws SQLException {
        Cliente cliente = new Cliente();
        cliente.setId(resultado.getInt("id"));
        cliente.setNombre(resultado.getString("nombre"));
        cliente.setTelefono(resultado.getString("telefono"));
        cliente.setDireccion(resultado.getString("direccion"));
        cliente.setLatitud(resultado.getBigDecimal("latitud"));
        cliente.setLongitud(resultado.getBigDecimal("longitud"));
        cliente.setNotas(resultado.getString("notas"));
        return cliente;
    }
}
