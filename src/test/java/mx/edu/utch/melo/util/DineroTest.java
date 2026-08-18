package mx.edu.utch.melo.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Formatear ahora solo acepta BigDecimal (ver auditoría de Fase 4, melo-java25): sin overload en double. */
class DineroTest {

    @Test
    void formateaConSimboloYDosDecimales() {
        assertEquals("$145.00", Dinero.formatear(new BigDecimal("145.00")));
    }

    @Test
    void formateaConSeparadorDeMiles() {
        assertEquals("$1,240.50", Dinero.formatear(new BigDecimal("1240.50")));
    }

    @Test
    void redondeaADosDecimales() {
        assertEquals("$71.20", Dinero.formatear(new BigDecimal("71.2001")));
    }

    @Test
    void formateaCero() {
        assertEquals("$0.00", Dinero.formatear(BigDecimal.ZERO));
    }

    @Test
    void formateaUnCentavo() {
        assertEquals("$0.01", Dinero.formatear(new BigDecimal("0.01")));
    }

    @Test
    void formateaCantidadesGrandesConVariosSeparadoresDeMiles() {
        assertEquals("$999,999.99", Dinero.formatear(new BigDecimal("999999.99")));
    }
}
