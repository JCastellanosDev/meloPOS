package mx.edu.utch.melo.dao;

import mx.edu.utch.melo.model.EstadoBloqueoPin;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Persistencia del bloqueo temporal por intentos fallidos de PIN (ver auditoría de Fase 6,
 * UsuarioService). No extiende {@link CrudDAO}: es una sola fila por sucursal que se
 * actualiza con upsert, no una colección de entidades con alta/baja/listado -- mismo criterio
 * que ReporteDAO/DashboardDAO (agregaciones/estado, no CRUD de una entidad de negocio).
 */
public interface ControlIntentosPinDAO {

    Optional<EstadoBloqueoPin> obtenerEstado(int sucursalId);

    /**
     * Incrementa intentosFallidos en un solo UPDATE atómico (upsert con {@code = intentos_fallidos + 1})
     * y regresa el valor resultante -- a diferencia de leer el estado en Java y volver a escribirlo,
     * dos incrementos concurrentes de la misma sucursal nunca se pisan entre sí (ver auditoría de
     * Fase 7). Regresa 1 si la sucursal no tenía fila todavía.
     */
    int incrementarIntentosFallidos(int sucursalId);

    /** Crea o actualiza el estado de la sucursal (upsert) -- nunca hay historial, solo el estado actual. */
    void guardarEstado(int sucursalId, int intentosFallidos, LocalDateTime bloqueadoHasta);

    /** Borra el estado (equivalente a 0 intentos, sin bloqueo) -- ver UsuarioService, login correcto. */
    void reiniciar(int sucursalId);
}
