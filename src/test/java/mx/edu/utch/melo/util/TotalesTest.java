package mx.edu.utch.melo.util;

import mx.edu.utch.melo.model.ItemOrden;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TotalesTest {

    @Test
    void subtotalSumaPrecioPorCantidadDeCadaArticulo() {
        List<ItemOrden> articulos = List.of(
                new ItemOrden(1, "Tacos al Pastor", 145.00, 1),
                new ItemOrden(2, "Pozole Rojo", 210.00, 1),
                new ItemOrden(3, "Agua de Jamaica", 90.00, 2)
        );

        assertEquals(535.00, Totales.subtotal(articulos), 0.001);
    }

    @Test
    void subtotalDeListaVaciaEsCero() {
        assertEquals(0.0, Totales.subtotal(List.of()), 0.001);
    }

    @Test
    void ivaEsDieciseisPorCientoDelSubtotal() {
        assertEquals(16.00, Totales.iva(100.00), 0.001);
    }

    @Test
    void totalEsSubtotalMasIva() {
        assertEquals(116.00, Totales.total(100.00), 0.001);
    }

    @Test
    void cambiarCantidadAfectaElSubtotalDelArticulo() {
        ItemOrden item = new ItemOrden(3, "Agua de Jamaica", 90.00, 2);
        item.cantidadProperty().set(3);

        assertEquals(270.00, item.getSubtotal(), 0.001);
    }
}
