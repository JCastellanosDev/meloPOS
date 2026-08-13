package mx.edu.utch.melo.util;

import mx.edu.utch.melo.model.ItemOrden;

import java.util.List;


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

    /** Algunas sucursales no cobran IVA (ver Sucursal.isAplicaIva/CLAUDE.md): si aplicaIva es false, no hay impuesto. */
    public static double iva(double subtotal, boolean aplicaIva) {
        return aplicaIva ? iva(subtotal) : 0.0;
    }

    public static double total(double subtotal, boolean aplicaIva) {
        return subtotal + iva(subtotal, aplicaIva);
    }
}
