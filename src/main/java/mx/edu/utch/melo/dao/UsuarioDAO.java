package mx.edu.utch.melo.dao;

import mx.edu.utch.melo.model.Usuario;

import java.util.List;

public interface UsuarioDAO extends CrudDAO<Usuario, Integer> {

    List<Usuario> obtenerPorSucursal(int sucursalId);
}
