package mx.edu.utch.melo.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DineroTest {

    @Test
    void formateaConSimboloYDosDecimales() {
        assertEquals("$145.00", Dinero.formatear(145.0));
    }

    @Test
    void formateaConSeparadorDeMiles() {
        assertEquals("$1,240.50", Dinero.formatear(1240.5));
    }

    @Test
    void redondeaADosDecimales() {
        assertEquals("$71.20", Dinero.formatear(71.2001));
    }
}
