package mx.edu.utch.melo.dao;

import mx.edu.utch.melo.model.DetalleOrden;
import mx.edu.utch.melo.model.ModificadorAplicado;

import java.math.BigDecimal;
import java.sql.Connection;
import java.util.List;

public interface DetalleOrdenDAO extends CrudDAO<DetalleOrden, Integer> {

    List<DetalleOrden> obtenerPorOrden(int ordenId);

    /**
     * Igual que {@link #obtenerPorOrden(int)}, pero participa en una transacción ya abierta (ver
     * ConexionDB#ejecutarEnTransaccion) -- la usa VentaService.agregarArticulos para recalcular el
     * total sobre TODOS los artículos de la orden (existentes + recién insertados) dentro de la
     * misma transacción, sin abrir una segunda conexión.
     */
    List<DetalleOrden> obtenerPorOrden(int ordenId, Connection conexion);

    /** Modificadores elegidos para este artículo, con su precio al momento de la venta. */
    List<ModificadorAplicado> obtenerModificadores(int detalleOrdenId);

    void agregarModificador(int detalleOrdenId, int modificadorId, BigDecimal precioExtra);

    /**
     * Igual que {@link #crear(DetalleOrden)}, pero participa en una transacción ya abierta (ver
     * ConexionDB#ejecutarEnTransaccion) -- no abre ni cierra su propia conexión.
     */
    DetalleOrden crear(DetalleOrden detalle, Connection conexion);
}
