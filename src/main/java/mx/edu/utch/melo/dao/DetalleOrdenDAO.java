package mx.edu.utch.melo.dao;

import mx.edu.utch.melo.model.DetalleOrden;
import mx.edu.utch.melo.model.ModificadorAplicado;

import java.math.BigDecimal;
import java.util.List;

public interface DetalleOrdenDAO extends CrudDAO<DetalleOrden, Integer> {

    List<DetalleOrden> obtenerPorOrden(int ordenId);

    /** Modificadores elegidos para este artículo, con su precio al momento de la venta. */
    List<ModificadorAplicado> obtenerModificadores(int detalleOrdenId);

    void agregarModificador(int detalleOrdenId, int modificadorId, BigDecimal precioExtra);
}
