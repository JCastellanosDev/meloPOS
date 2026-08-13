package mx.edu.utch.melo.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TurnoTest {

    @Test
    void calcularMontoEsperadoSumaAperturaMasEfectivoVendido() {
        Turno turno = new Turno();
        turno.setMontoApertura(new BigDecimal("500.00"));

        BigDecimal esperado = turno.calcularMontoEsperado(new BigDecimal("1200.00"));

        assertEquals(0, new BigDecimal("1700.00").compareTo(esperado));
    }

    @Test
    void calcularMontoEsperadoSinVentasEsIgualALaApertura() {
        Turno turno = new Turno();
        turno.setMontoApertura(new BigDecimal("500.00"));

        assertEquals(0, new BigDecimal("500.00").compareTo(turno.calcularMontoEsperado(BigDecimal.ZERO)));
    }

    @Test
    void estaAbiertoEsVerdaderoSinFechaDeCierre() {
        Turno turno = new Turno();

        assertTrue(turno.estaAbierto());
    }

    @Test
    void estaAbiertoEsFalsoConFechaDeCierre() {
        Turno turno = new Turno();
        turno.setFechaCierre(java.time.LocalDateTime.now());

        assertFalse(turno.estaAbierto());
    }
}
