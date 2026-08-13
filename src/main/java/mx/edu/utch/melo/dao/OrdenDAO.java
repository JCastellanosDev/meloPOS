package mx.edu.utch.melo.dao;

import mx.edu.utch.melo.model.EstadoOrden;
import mx.edu.utch.melo.model.Orden;
import mx.edu.utch.melo.model.TipoOrden;

import java.sql.Connection;
import java.util.List;
import java.util.Optional;

public interface OrdenDAO extends CrudDAO<Orden, Integer> {

    List<Orden> obtenerPorEstado(EstadoOrden estado);

    /** Órdenes de una mesa que todavía no están pagadas ni canceladas. */
    List<Orden> obtenerActivasPorMesa(int mesaId);

    /** Órdenes de un canal (p.ej. DOMICILIO) que todavía no están pagadas ni canceladas. */
    List<Orden> obtenerActivasPorTipo(TipoOrden tipoOrden);

    /** id que tendrá la siguiente orden de esta sucursal (MAX(id)+1) -- para mostrarlo antes de crearla. */
    int siguienteNumeroOrden(int sucursalId);

    /**
     * Igual que {@link #crear(Orden)}, pero participa en una transacción ya abierta (ver
     * ConexionDB#ejecutarEnTransaccion) -- no abre ni cierra su propia conexión.
     */
    Orden crear(Orden orden, Connection conexion);

    /** Igual que {@link #actualizar(Orden)}, pero participa en una transacción ya abierta. */
    boolean actualizar(Orden orden, Connection conexion);

    /** Igual que {@link #obtenerPorId(Integer)}, pero participa en una transacción ya abierta. */
    Optional<Orden> obtenerPorId(Integer id, Connection conexion);
}
