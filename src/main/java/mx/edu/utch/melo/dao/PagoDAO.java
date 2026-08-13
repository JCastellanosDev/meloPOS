package mx.edu.utch.melo.dao;

import mx.edu.utch.melo.model.Pago;

import java.util.List;

public interface PagoDAO extends CrudDAO<Pago, Integer> {

    /** Todos los pagos de una orden -- para verificar que la suma cubra el total (división de cuenta). */
    List<Pago> obtenerPorOrden(int ordenId);
}
