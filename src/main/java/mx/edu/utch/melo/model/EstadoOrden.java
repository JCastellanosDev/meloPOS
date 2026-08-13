package mx.edu.utch.melo.model;

/** Estados posibles de una orden. La secuencia exacta la decide el servicio de negocio, no esta enumeración. */
public enum EstadoOrden {
    PENDIENTE,
    EN_PREPARACION,
    LISTA,
    ENTREGADA,
    PAGADA,
    CANCELADA
}