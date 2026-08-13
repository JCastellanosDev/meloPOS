package mx.edu.utch.melo.security;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

/**
 * Hash de PIN de acceso con PBKDF2WithHmacSHA256 (parte del JDK, sin dependencias nuevas -- ver
 * auditoría de Fase 5). Antes {@code usuarios.pin_acceso} se guardaba y comparaba en texto plano
 * (ver UsuarioService#autenticarPorPin para la migración gradual de los PIN existentes).
 *
 * Formato guardado: {@code iteraciones:sal_base64:hash_base64}. Guardar las iteraciones junto con
 * el hash permite subir el costo en el futuro sin invalidar los hashes ya guardados con un valor
 * menor -- {@link #verificar} siempre usa las iteraciones que trae el propio hash, no la constante
 * actual.
 *
 * Nota honesta: un PIN de 4-6 dígitos tiene muy poca entropía (10,000 a 1,000,000 combinaciones).
 * PBKDF2 con sal evita que un volcado de la base exponga el PIN directamente y evita ataques de
 * diccionario precalculado (rainbow tables), pero no lo vuelve "fuerte" contra fuerza bruta dirigida
 * si alguien ya tiene el hash -- la misma limitación que un PIN de tarjeta de débito, que depende
 * también del acceso físico a la terminal y (a futuro) de bloqueo por intentos fallidos.
 */
public final class PinHasher {

    private static final String ALGORITMO = "PBKDF2WithHmacSHA256";
    private static final int ITERACIONES_ACTUALES = 120_000;
    private static final int LONGITUD_LLAVE_BITS = 256;
    private static final int LONGITUD_SAL_BYTES = 16;
    private static final String SEPARADOR = ":";

    private PinHasher() {
    }

    /** true si el valor guardado ya está en el formato "iteraciones:sal:hash" (no es un PIN legado en texto plano). */
    public static boolean esHash(String valorAlmacenado) {
        if (valorAlmacenado == null) {
            return false;
        }
        String[] partes = valorAlmacenado.split(SEPARADOR, -1);
        return partes.length == 3 && !partes[0].isEmpty() && partes[0].chars().allMatch(Character::isDigit);
    }

    public static String hash(String pin) {
        byte[] sal = generarSal();
        byte[] hash = derivar(pin, sal, ITERACIONES_ACTUALES);
        return ITERACIONES_ACTUALES + SEPARADOR + codificar(sal) + SEPARADOR + codificar(hash);
    }

    public static boolean verificar(String pin, String valorAlmacenado) {
        if (!esHash(valorAlmacenado)) {
            return false;
        }
        // Un valor con forma de hash pero corrupto (base64 inválido, iteraciones fuera de rango) no
        // debe tumbar el login -- solo significa que ese PIN nunca puede coincidir (bug encontrado
        // en la Fase 6 al escribir estas mismas pruebas, ver PinHasherTest).
        try {
            String[] partes = valorAlmacenado.split(SEPARADOR, -1);
            int iteraciones = Integer.parseInt(partes[0]);
            byte[] sal = Base64.getDecoder().decode(partes[1]);
            byte[] hashEsperado = Base64.getDecoder().decode(partes[2]);
            byte[] hashCalculado = derivar(pin, sal, iteraciones);
            return MessageDigest.isEqual(hashCalculado, hashEsperado);
        } catch (IllegalArgumentException valorCorrupto) {
            return false;
        }
    }

    private static byte[] derivar(String pin, byte[] sal, int iteraciones) {
        PBEKeySpec especificacion = new PBEKeySpec(pin.toCharArray(), sal, iteraciones, LONGITUD_LLAVE_BITS);
        try {
            SecretKeyFactory fabrica = SecretKeyFactory.getInstance(ALGORITMO);
            return fabrica.generateSecret(especificacion).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("El JDK no soporta " + ALGORITMO, e);
        } finally {
            especificacion.clearPassword();
        }
    }

    private static byte[] generarSal() {
        byte[] sal = new byte[LONGITUD_SAL_BYTES];
        new SecureRandom().nextBytes(sal);
        return sal;
    }

    private static String codificar(byte[] datos) {
        return Base64.getEncoder().encodeToString(datos);
    }
}
