package mx.edu.utch.melo.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** valor() regresa BigDecimal (ver auditoría de Fase 4, melo-java25): monto recibido/cambio es dinero real. */
class EntradaMonetariaTest {

    private static void assertMonto(String esperado, BigDecimal real) {
        assertEquals(0, new BigDecimal(esperado).compareTo(real), () -> "esperado " + esperado + " pero fue " + real);
    }

    @Test
    void empiezaEnCero() {
        assertMonto("0", new EntradaMonetaria().valor());
    }

    @Test
    void acumulaDigitos() {
        EntradaMonetaria entrada = new EntradaMonetaria();
        entrada.procesarTecla("5");
        entrada.procesarTecla("0");

        assertMonto("50", entrada.valor());
    }

    @Test
    void aceptaUnSoloPunto() {
        EntradaMonetaria entrada = new EntradaMonetaria();
        entrada.procesarTecla("1");
        entrada.procesarTecla(".");
        entrada.procesarTecla(".");
        entrada.procesarTecla("5");

        assertMonto("1.5", entrada.valor());
    }

    @Test
    void puntoSinDigitosPreviosAsumeCero() {
        EntradaMonetaria entrada = new EntradaMonetaria();
        entrada.procesarTecla(".");
        entrada.procesarTecla("5");

        assertMonto("0.5", entrada.valor());
    }

    @Test
    void borrarQuitaElUltimoDigito() {
        EntradaMonetaria entrada = new EntradaMonetaria();
        entrada.procesarTecla("1");
        entrada.procesarTecla("2");
        entrada.procesarTecla("⌫");

        assertMonto("1", entrada.valor());
    }

    @Test
    void borrarSobreVacioNoFalla() {
        EntradaMonetaria entrada = new EntradaMonetaria();
        entrada.procesarTecla("⌫");

        assertMonto("0", entrada.valor());
    }

    @Test
    void establecerReemplazaElBuffer() {
        EntradaMonetaria entrada = new EntradaMonetaria();
        entrada.procesarTecla("9");
        entrada.establecer(new BigDecimal("200.00"));

        assertMonto("200.00", entrada.valor());
    }

    @Test
    void reiniciarVacioElBuffer() {
        EntradaMonetaria entrada = new EntradaMonetaria();
        entrada.procesarTecla("9");
        entrada.reiniciar();

        assertMonto("0", entrada.valor());
    }

    @Test
    void establecerConValoresDeMuchosDecimalesNoUsaNotacionCientifica() {
        EntradaMonetaria entrada = new EntradaMonetaria();
        entrada.establecer(new BigDecimal("999999.99"));

        assertMonto("999999.99", entrada.valor());
    }
}
