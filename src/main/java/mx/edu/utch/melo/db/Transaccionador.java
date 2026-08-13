package mx.edu.utch.melo.db;

/**
 * Abstracción de "correr esto como una transacción" (ver ConexionDB#ejecutarEnTransaccion),
 * separada de la conexión concreta para que VentaService/PagoService se puedan probar con un
 * Transaccionador falso -- sin base de datos real (ver auditoría de Fase 6, "no dependas de una
 * base de datos real para cada unit test").
 */
public interface Transaccionador {

    <T> T ejecutarEnTransaccion(TrabajoTransaccional<T> trabajo);
}
