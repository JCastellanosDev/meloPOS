package mx.edu.utch.melo.dao;

import mx.edu.utch.melo.model.EstadoOrden;
import mx.edu.utch.melo.model.Orden;
import mx.edu.utch.melo.model.TipoOrden;

import java.util.List;

public interface OrdenDAO extends CrudDAO<Orden, Integer> {

    List<Orden> obtenerPorEstado(EstadoOrden estado);

    /** Órdenes de una mesa que todavía no están pagadas ni canceladas. */
    List<Orden> obtenerActivasPorMesa(int mesaId);

    /** Órdenes de un canal (p.ej. DOMICILIO) que todavía no están pagadas ni canceladas. */
    List<Orden> obtenerActivasPorTipo(TipoOrden tipoOrden);

    /** id que tendrá la siguiente orden de esta sucursal (MAX(id)+1) -- para mostrarlo antes de crearla. */
    int siguienteNumeroOrden(int sucursalId);
}
