package mx.edu.utch.melo.dao;

import mx.edu.utch.melo.model.Cliente;

import java.util.List;
import java.util.Optional;

public interface ClienteDAO extends CrudDAO<Cliente, Integer> {

    /** Para PedidosController: verificar si el cliente ya existe antes de crear uno nuevo. */
    Optional<Cliente> obtenerPorTelefono(String telefono);

    /**
     * Trae varios clientes en una sola consulta ({@code WHERE id IN (...)}) -- para evitar el
     * patrón N+1 de resolver un cliente por orden en un bucle (ver DeliveryService). IDs que no
     * existan simplemente no aparecen en el resultado; una lista vacía devuelve una lista vacía
     * sin tocar la base de datos.
     */
    List<Cliente> obtenerPorIds(List<Integer> ids);
}
