package mx.edu.utch.melo.validation;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Reglas de validación para el registro de clientes. Separado del
 * controlador para poder probarlas de forma unitaria y para que agregar o
 * cambiar una regla no requiera tocar la pantalla (Abierto/Cerrado).
 * Validación de UI solamente: sin verificación de duplicados (requiere base
 * de datos).
 */
public final class ClienteValidator {

    private static final Pattern PATRON_TELEFONO = Pattern.compile("^\\+?\\d{7,15}$");

    private ClienteValidator() {
    }

    /** Devuelve el primer mensaje de error encontrado, o vacío si los datos son válidos. */
    public static Optional<String> validar(String nombre, String telefono, String direccion) {
        if (esVacio(nombre)) {
            return Optional.of("Ingresa el nombre y apellido del cliente.");
        }
        if (!esTelefonoValido(telefono)) {
            return Optional.of("Ingresa un número de celular válido (7 a 15 dígitos).");
        }
        if (esVacio(direccion)) {
            return Optional.of("Ingresa la dirección de entrega.");
        }
        return Optional.empty();
    }

    public static boolean esTelefonoValido(String telefono) {
        if (telefono == null) {
            return false;
        }
        String sinEspacios = telefono.trim().replaceAll("\\s+", "");
        return PATRON_TELEFONO.matcher(sinEspacios).matches();
    }

    private static boolean esVacio(String texto) {
        return texto == null || texto.isBlank();
    }
}
