package mx.edu.utch.melo.security;

import mx.edu.utch.melo.model.Rol;

/** El rol en sesión no tiene permiso para la pantalla u operación solicitada (ver ControlAcceso). */
public class AccesoDenegadoException extends RuntimeException {

    public AccesoDenegadoException(Rol rol, String operacion) {
        super("El rol " + rol.getEtiqueta() + " no tiene permiso para: " + operacion);
    }
}
