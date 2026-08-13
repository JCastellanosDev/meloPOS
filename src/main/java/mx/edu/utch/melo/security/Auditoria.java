package mx.edu.utch.melo.security;

import mx.edu.utch.melo.model.Rol;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Estrategia de auditoría para operaciones sensibles (ver auditoría de Fase 5: eliminación de
 * productos, cambios de precios/IVA, movimientos de caja, cambios de usuarios). Un logger nombrado
 * aparte ("mx.edu.utch.melo.auditoria"), no mezclado con los logs normales de warning/error de
 * cada clase -- así se puede enrutar a su propio archivo con un {@code logging.properties}, sin
 * tocar código, si algún día se quiere revisar aparte o alimentar un dashboard.
 *
 * Deliberadamente simple: una línea de texto por operación, no una tabla nueva en la base de
 * datos ("no implementes un sistema excesivamente complejo si no es necesario"). Si más adelante
 * hace falta poder filtrar/reportar auditoría desde la propia app (no solo grep en un archivo de
 * log), el siguiente paso natural es una tabla {@code auditoria(id, fecha, usuario_id, rol,
 * operacion, detalle)} con su DAO -- mismo patrón que el resto del proyecto.
 *
 * Limitación actual, honesta: registra el ROL que hizo el cambio (ver ControlAcceso), no el
 * empleado específico -- los Service que llaman aquí hoy solo reciben el Rol, no el Usuario
 * completo. Identificar al empleado exacto es el siguiente paso natural si se necesita.
 */
public final class Auditoria {

    private static final Logger LOG = Logger.getLogger("mx.edu.utch.melo.auditoria");

    private Auditoria() {
    }

    public static void registrar(Rol rol, String operacion, String detalle) {
        LOG.log(Level.INFO, () -> operacion + " | rol=" + rol + " | " + detalle);
    }
}
