package mx.edu.utch.melo.validation;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClienteValidatorTest {

    @Test
    void aceptaTelefonoConEspacios() {
        assertTrue(ClienteValidator.esTelefonoValido("300 123 4567"));
    }

    @Test
    void aceptaTelefonoConLadaInternacional() {
        assertTrue(ClienteValidator.esTelefonoValido("+52 300 123 4567"));
    }

    @Test
    void aceptaTelefonoSinEspacios() {
        assertTrue(ClienteValidator.esTelefonoValido("3001234567"));
    }

    @Test
    void rechazaTelefonoVacio() {
        assertFalse(ClienteValidator.esTelefonoValido(""));
    }

    @Test
    void rechazaTelefonoMuyCorto() {
        assertFalse(ClienteValidator.esTelefonoValido("123"));
    }

    @Test
    void rechazaTelefonoConLetras() {
        assertFalse(ClienteValidator.esTelefonoValido("abcdefgh"));
    }

    @Test
    void rechazaTelefonoNulo() {
        assertFalse(ClienteValidator.esTelefonoValido(null));
    }

    @Test
    void validarDevuelveVacioCuandoTodoEsCorrecto() {
        Optional<String> resultado = ClienteValidator.validar("Juan Pérez", "3001234567", "Calle 12 #34");
        assertTrue(resultado.isEmpty());
    }

    @Test
    void validarReportaNombreFaltante() {
        Optional<String> resultado = ClienteValidator.validar("", "3001234567", "Calle 12 #34");
        assertTrue(resultado.isPresent());
    }

    @Test
    void validarReportaTelefonoInvalido() {
        Optional<String> resultado = ClienteValidator.validar("Juan Pérez", "123", "Calle 12 #34");
        assertTrue(resultado.isPresent());
    }

    @Test
    void validarReportaDireccionFaltante() {
        Optional<String> resultado = ClienteValidator.validar("Juan Pérez", "3001234567", "  ");
        assertTrue(resultado.isPresent());
    }
}
