package mx.edu.utch.melo.dao;

/**
 * Envuelve cualquier SQLException ocurrida dentro de un DAO. Quien consume
 * UsuarioDAO/ProductoDAO/OrdenDAO depende solo de la interfaz y de esta
 * excepción de dominio, sin acoplarse a que la implementación use JDBC
 * (Inversión de Dependencias) -- si el día de mañana cambia la tecnología de
 * acceso a datos, el contrato que ven los controladores no cambia.
 */
public class PersistenciaException extends RuntimeException {

    public PersistenciaException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
