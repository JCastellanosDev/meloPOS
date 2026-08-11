package mx.edu.utch.melo.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EntradaMonetariaTest {

    @Test
    void empiezaEnCero() {
        assertEquals(0.0, new EntradaMonetaria().valor(), 0.001);
    }

    @Test
    void acumulaDigitos() {
        EntradaMonetaria entrada = new EntradaMonetaria();
        entrada.procesarTecla("5");
        entrada.procesarTecla("0");

        assertEquals(50.0, entrada.valor(), 0.001);
    }

    @Test
    void aceptaUnSoloPunto() {
        EntradaMonetaria entrada = new EntradaMonetaria();
        entrada.procesarTecla("1");
        entrada.procesarTecla(".");
        entrada.procesarTecla(".");
        entrada.procesarTecla("5");

        assertEquals(1.5, entrada.valor(), 0.001);
    }

    @Test
    void puntoSinDigitosPreviosAsumeCero() {
        EntradaMonetaria entrada = new EntradaMonetaria();
        entrada.procesarTecla(".");
        entrada.procesarTecla("5");

        assertEquals(0.5, entrada.valor(), 0.001);
    }

    @Test
    void borrarQuitaElUltimoDigito() {
        EntradaMonetaria entrada = new EntradaMonetaria();
        entrada.procesarTecla("1");
        entrada.procesarTecla("2");
        entrada.procesarTecla("⌫");

        assertEquals(1.0, entrada.valor(), 0.001);
    }

    @Test
    void borrarSobreVacioNoFalla() {
        EntradaMonetaria entrada = new EntradaMonetaria();
        entrada.procesarTecla("⌫");

        assertEquals(0.0, entrada.valor(), 0.001);
    }

    @Test
    void establecerReemplazaElBuffer() {
        EntradaMonetaria entrada = new EntradaMonetaria();
        entrada.procesarTecla("9");
        entrada.establecer(200.0);

        assertEquals(200.0, entrada.valor(), 0.001);
    }

    @Test
    void reiniciarVacioElBuffer() {
        EntradaMonetaria entrada = new EntradaMonetaria();
        entrada.procesarTecla("9");
        entrada.reiniciar();

        assertEquals(0.0, entrada.valor(), 0.001);
    }
}
