package mx.edu.utch.melo.util;

import mx.edu.utch.melo.model.ItemOrden;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Cálculo de subtotal/IVA/total de un carrito, en BigDecimal de punta a punta (ver auditoría de
 * Fase 4, melo-java25): antes sumaba en {@code double} y el resultado se envolvía en BigDecimal
 * solo al final (ver VentaService, antes de esta fase) -- ese cruce BigDecimal-double-BigDecimal
 * ya no existe, ni aquí ni en ninguno de sus consumidores (Controllers/Service).
 *
 * Precisión: escala 2 (centavos), redondeo HALF_UP en cada paso -- la misma regla que ya usaba
 * {@code String.format("%.2f", ...)} para mostrar dinero en pantalla, así que el valor final no
 * cambia para ningún caso ya cubierto por las pruebas existentes.
 */
public final class Totales {

    public static final BigDecimal TASA_IVA = new BigDecimal("0.16");
    private static final int ESCALA_DINERO = 2;
    private static final RoundingMode REDONDEO_DINERO = RoundingMode.HALF_UP;

    private Totales() {
    }

    public static BigDecimal subtotal(List<ItemOrden> articulos) {
        return articulos.stream()
                .map(ItemOrden::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(ESCALA_DINERO, REDONDEO_DINERO);
    }

    public static BigDecimal iva(BigDecimal subtotal) {
        return subtotal.multiply(TASA_IVA).setScale(ESCALA_DINERO, REDONDEO_DINERO);
    }

    public static BigDecimal total(BigDecimal subtotal) {
        return subtotal.add(iva(subtotal)).setScale(ESCALA_DINERO, REDONDEO_DINERO);
    }

    /** Algunas sucursales no cobran IVA (ver Sucursal.isAplicaIva/CLAUDE.md): si aplicaIva es false, no hay impuesto. */
    public static BigDecimal iva(BigDecimal subtotal, boolean aplicaIva) {
        return aplicaIva ? iva(subtotal) : BigDecimal.ZERO.setScale(ESCALA_DINERO, REDONDEO_DINERO);
    }

    public static BigDecimal total(BigDecimal subtotal, boolean aplicaIva) {
        return subtotal.add(iva(subtotal, aplicaIva)).setScale(ESCALA_DINERO, REDONDEO_DINERO);
    }
}
