package mx.edu.utch.melo.db;

import java.sql.Connection;
import java.sql.SQLException;

/** Trabajo que corre dentro de una transacción abierta por {@link ConexionDB#ejecutarEnTransaccion}. */
@FunctionalInterface
public interface TrabajoTransaccional<T> {

    T ejecutar(Connection conexion) throws SQLException;
}
