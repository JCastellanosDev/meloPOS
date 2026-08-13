package mx.edu.utch.melo.dao;

import mx.edu.utch.melo.model.Usuario;

import java.util.List;
import java.util.Optional;

public interface UsuarioDAO extends CrudDAO<Usuario, Integer> {

    /**
     * Para el flujo de acceso por PIN en la terminal; solo considera usuarios
     * activos. El PIN es único por sucursal, no globalmente -- por eso hace
     * falta sucursalId además del PIN.
     */
    Optional<Usuario> obtenerPorPin(int sucursalId, String pinAcceso);

    List<Usuario> obtenerPorSucursal(int sucursalId);
}
