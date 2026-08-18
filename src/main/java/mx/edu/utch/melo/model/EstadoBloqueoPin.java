package mx.edu.utch.melo.model;

import java.time.LocalDateTime;

/**
 * Estado del bloqueo temporal por intentos fallidos de PIN de una sucursal (ver auditoría de
 * Fase 6, ControlIntentosPinDAO/UsuarioService). No es una entidad de negocio con vida propia --
 * es un DTO de solo lectura sobre la fila de {@code bloqueo_pin_sucursal}, por eso es record
 * (mismo criterio que model/reporte y model/dashboard, ver melo-java25).
 */
public record EstadoBloqueoPin(int sucursalId, int intentosFallidos, LocalDateTime bloqueadoHasta) {

    /** true si el bloqueo sigue vigente (bloqueadoHasta capturado y todavía no pasó). */
    public boolean estaBloqueado() {
        return bloqueadoHasta != null && bloqueadoHasta.isAfter(LocalDateTime.now());
    }
}
