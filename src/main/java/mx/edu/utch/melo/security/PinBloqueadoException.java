package mx.edu.utch.melo.security;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * La sucursal tiene el login por PIN bloqueado temporalmente por demasiados intentos fallidos
 * (ver auditoría de Fase 6, UsuarioService#autenticarPorPin). No indica qué PIN se probó ni a
 * quién pertenecía -- solo hasta cuándo dura el bloqueo (ver melo-security, "no revelar
 * información innecesaria durante el login").
 */
public class PinBloqueadoException extends RuntimeException {

    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final LocalDateTime bloqueadoHasta;

    public PinBloqueadoException(LocalDateTime bloqueadoHasta) {
        super("Demasiados intentos fallidos. Intenta de nuevo después de las " + bloqueadoHasta.format(FORMATO) + ".");
        this.bloqueadoHasta = bloqueadoHasta;
    }

    public LocalDateTime getBloqueadoHasta() {
        return bloqueadoHasta;
    }
}
