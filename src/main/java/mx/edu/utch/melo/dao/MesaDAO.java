package mx.edu.utch.melo.dao;

import mx.edu.utch.melo.model.Mesa;

import java.util.List;

public interface MesaDAO extends CrudDAO<Mesa, Integer> {

    List<Mesa> obtenerPorSucursal(int sucursalId);
}
