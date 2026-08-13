package mx.edu.utch.melo.dao;

import mx.edu.utch.melo.model.Sucursal;

import java.util.List;

public interface SucursalDAO extends CrudDAO<Sucursal, Integer> {

    List<Sucursal> obtenerActivas();
}
