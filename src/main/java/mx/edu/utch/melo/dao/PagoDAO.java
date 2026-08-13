package mx.edu.utch.melo.dao;

import mx.edu.utch.melo.model.Pago;

import java.sql.Connection;
import java.util.List;

public interface PagoDAO extends CrudDAO<Pago, Integer> {

    /** Todos los pagos de una orden -- para verificar que la suma cubra el total (división de cuenta). */
    List<Pago> obtenerPorOrden(int ordenId);

    /**
     * Igual que {@link #crear(Pago)}, pero participa en una transacción ya abierta (ver
     * ConexionDB#ejecutarEnTransaccion) -- no abre ni cierra su propia conexión.
     */
    Pago crear(Pago pago, Connection conexion);
}
