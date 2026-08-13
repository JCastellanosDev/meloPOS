package mx.edu.utch.melo.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PinHasherTest {

    @Test
    void unPinCorrectoVerificaContraSuPropioHash() {
        String hash = PinHasher.hash("1234");

        assertTrue(PinHasher.verificar("1234", hash));
    }

    @Test
    void unPinIncorrectoNoVerifica() {
        String hash = PinHasher.hash("1234");

        assertFalse(PinHasher.verificar("4321", hash));
    }

    @Test
    void elMismoPinProduceHashesDistintosPorLaSalAleatoria() {
        String hashUno = PinHasher.hash("1234");
        String hashDos = PinHasher.hash("1234");

        assertNotEquals(hashUno, hashDos);
        // pero ambos deben seguir verificando el mismo PIN original:
        assertTrue(PinHasher.verificar("1234", hashUno));
        assertTrue(PinHasher.verificar("1234", hashDos));
    }

    @Test
    void reconoceUnHashComoHash() {
        assertTrue(PinHasher.esHash(PinHasher.hash("0000")));
    }

    @Test
    void noConfundeUnPinLegadoEnTextoPlanoConUnHash() {
        assertFalse(PinHasher.esHash("0000"));
        assertFalse(PinHasher.esHash("1234"));
    }

    @Test
    void esHashEsFalsoParaValoresNulosOVacios() {
        assertFalse(PinHasher.esHash(null));
        assertFalse(PinHasher.esHash(""));
    }

    @Test
    void verificarContraUnPinLegadoEnTextoPlanoSiempreEsFalso() {
        // verificar() no debe caer de vuelta a comparación en texto plano -- esa migración vive
        // en UsuarioService.autenticarPorPin, no aquí (ver auditoría de Fase 5).
        assertFalse(PinHasher.verificar("0000", "0000"));
    }

    @Test
    void verificarContraUnValorConFormaDeHashPeroNoEsHashValidoNoLanza() {
        // "no-es-un-hash-valido" no tiene forma de hash (esHash ya lo descarta) -- este caso es
        // distinto y más peligroso: SÍ tiene la forma "iteraciones:sal:hash" pero con base64
        // corrupto. Bug real encontrado al escribir esta prueba (ver PinHasher.verificar): antes
        // de la Fase 6, esto lanzaba IllegalArgumentException en vez de simplemente no coincidir.
        assertFalse(PinHasher.verificar("1234", "120000:###:###"));
    }

    @Test
    void verificarContraTextoSinFormaDeHashNoLanza() {
        assertFalse(PinHasher.verificar("1234", "no-es-un-hash-valido"));
    }
}
