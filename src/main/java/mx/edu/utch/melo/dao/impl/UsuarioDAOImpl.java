package mx.edu.utch.melo.dao.impl;

import mx.edu.utch.melo.dao.PersistenciaException;
import mx.edu.utch.melo.dao.UsuarioDAO;
import mx.edu.utch.melo.db.ConexionDB;
import mx.edu.utch.melo.model.Rol;
import mx.edu.utch.melo.model.Usuario;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Implementación JDBC de {@link UsuarioDAO}. */
public class UsuarioDAOImpl implements UsuarioDAO {

    private static final String SQL_INSERTAR =
            "INSERT INTO usuarios (sucursal_id, nombre, pin_acceso, rol, activo) VALUES (?, ?, ?, ?, ?)";
    private static final String SQL_ACTUALIZAR =
            "UPDATE usuarios SET sucursal_id = ?, nombre = ?, pin_acceso = ?, rol = ?, activo = ? WHERE id = ?";
    private static final String SQL_ELIMINAR = "DELETE FROM usuarios WHERE id = ?";
    private static final String SQL_BASE = "SELECT id, sucursal_id, nombre, pin_acceso, rol, activo FROM usuarios";
    private static final String SQL_POR_ID = SQL_BASE + " WHERE id = ?";
    private static final String SQL_POR_PIN =
            SQL_BASE + " WHERE sucursal_id = ? AND pin_acceso = ? AND activo = TRUE";
    private static final String SQL_POR_SUCURSAL = SQL_BASE + " WHERE sucursal_id = ?";

    private final ConexionDB conexionDB;

    public UsuarioDAOImpl(ConexionDB conexionDB) {
        this.conexionDB = conexionDB;
    }

    @Override
    public Usuario crear(Usuario usuario) {
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_INSERTAR, Statement.RETURN_GENERATED_KEYS)) {

            mapearParametros(sentencia, usuario);
            sentencia.executeUpdate();

            try (ResultSet llaves = sentencia.getGeneratedKeys()) {
                if (llaves.next()) {
                    usuario.setId(llaves.getInt(1));
                }
            }
            return usuario;
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo crear el usuario: " + usuario.getNombre(), e);
        }
    }

    @Override
    public Optional<Usuario> obtenerPorId(Integer id) {
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_POR_ID)) {

            sentencia.setInt(1, id);
            try (ResultSet resultado = sentencia.executeQuery()) {
                return resultado.next() ? Optional.of(mapearFila(resultado)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo obtener el usuario con id " + id, e);
        }
    }

    @Override
    public Optional<Usuario> obtenerPorPin(int sucursalId, String pinAcceso) {
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_POR_PIN)) {

            sentencia.setInt(1, sucursalId);
            sentencia.setString(2, pinAcceso);
            try (ResultSet resultado = sentencia.executeQuery()) {
                return resultado.next() ? Optional.of(mapearFila(resultado)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo validar el PIN de acceso", e);
        }
    }

    @Override
    public List<Usuario> obtenerPorSucursal(int sucursalId) {
        List<Usuario> usuarios = new ArrayList<>();
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_POR_SUCURSAL)) {

            sentencia.setInt(1, sucursalId);
            try (ResultSet resultado = sentencia.executeQuery()) {
                while (resultado.next()) {
                    usuarios.add(mapearFila(resultado));
                }
            }
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo obtener los usuarios de la sucursal " + sucursalId, e);
        }
        return usuarios;
    }

    @Override
    public List<Usuario> obtenerTodos() {
        List<Usuario> usuarios = new ArrayList<>();
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_BASE);
             ResultSet resultado = sentencia.executeQuery()) {

            while (resultado.next()) {
                usuarios.add(mapearFila(resultado));
            }
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo obtener la lista de usuarios", e);
        }
        return usuarios;
    }

    @Override
    public boolean actualizar(Usuario usuario) {
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_ACTUALIZAR)) {

            mapearParametros(sentencia, usuario);
            sentencia.setInt(6, usuario.getId());
            return sentencia.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo actualizar el usuario con id " + usuario.getId(), e);
        }
    }

    @Override
    public boolean eliminar(Integer id) {
        try (Connection conexion = conexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_ELIMINAR)) {

            sentencia.setInt(1, id);
            return sentencia.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo eliminar el usuario con id " + id, e);
        }
    }

    private void mapearParametros(PreparedStatement sentencia, Usuario usuario) throws SQLException {
        sentencia.setInt(1, usuario.getSucursalId());
        sentencia.setString(2, usuario.getNombre());
        sentencia.setString(3, usuario.getPinAcceso());
        sentencia.setString(4, usuario.getRol().name());
        sentencia.setBoolean(5, usuario.isActivo());
    }

    private Usuario mapearFila(ResultSet resultado) throws SQLException {
        Usuario usuario = new Usuario();
        usuario.setId(resultado.getInt("id"));
        usuario.setSucursalId(resultado.getInt("sucursal_id"));
        usuario.setNombre(resultado.getString("nombre"));
        usuario.setPinAcceso(resultado.getString("pin_acceso"));
        usuario.setRol(Rol.valueOf(resultado.getString("rol")));
        usuario.setActivo(resultado.getBoolean("activo"));
        return usuario;
    }
}
