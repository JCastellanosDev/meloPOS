package mx.edu.utch.melo.dao;

import mx.edu.utch.melo.model.Cliente;

import java.util.Optional;

public interface ClienteDAO extends CrudDAO<Cliente, Integer> {

    /** Para PedidosController: verificar si el cliente ya existe antes de crear uno nuevo. */
    Optional<Cliente> obtenerPorTelefono(String telefono);
}
