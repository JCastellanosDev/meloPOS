package mx.edu.utch.melo.util;

import mx.edu.utch.melo.model.ItemOrden;

import java.util.List;

/** Cálculo de subtotal/IVA/total. Sin dependencias de JavaFX: se puede probar de forma unitaria. */
public final class Totales {

    public static final double TASA_IVA = 0.16;

    private Totales() {
    }

    public static double subtotal(List<ItemOrden> articulos) {
        return articulos.stream().mapToDouble(ItemOrden::getSubtotal).sum();
    }

    public static double iva(double subtotal) {
        return subtotal * TASA_IVA;
    }

    public static double total(double subtotal) {
        return subtotal + iva(subtotal);
    }
}
