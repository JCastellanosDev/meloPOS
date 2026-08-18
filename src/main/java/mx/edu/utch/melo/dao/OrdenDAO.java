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

    /**
     * Marca la orden como CANCELADA, solo si su estado actual es uno de los que permiten
     * cancelar (PENDIENTE, EN_PREPARACION, LISTA -- ver VentaService.cancelarOrden). Un único
     * UPDATE condicionado por estado resuelve dos cosas de forma atómica, sin leer-y-decidir en
     * Java: rechaza cancelar una orden ya PAGADA/ENTREGADA (eso necesitaría un flujo de reembolso
     * aparte, fuera de alcance aquí) y evita doble cancelación en carrera (si dos llamados
     * concurrentes intentan cancelar la misma orden, como mucho uno de los dos UPDATE afecta una
     * fila, porque el primero ya la dejó fuera de la lista de estados permitidos). Regresa
     * {@code false} si la orden no existe o su estado actual no permite cancelar -- el llamador
     * decide cuál de los dos casos fue, leyendo la orden aparte. Participa siempre en una
     * transacción ya abierta (mismo patrón que {@link ProductoDAO#descontarStock}).
     */
    boolean cancelar(int ordenId, Connection conexion);
}
