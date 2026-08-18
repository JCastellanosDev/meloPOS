package mx.edu.utch.melo.util;

import mx.edu.utch.melo.model.ItemOrden;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * BigDecimal de punta a punta (ver auditoría de Fase 4, melo-java25): ya no hay ningún cruce a
 * double en ningún paso del cálculo. Las aserciones comparan con {@code compareTo(...) == 0}
 * (no {@code equals}, que también compara escala) siguiendo el patrón ya usado en VentaServiceTest.
 */
class TotalesTest {

    private static ItemOrden item(int productoId, String nombre, String precioUnitario, int cantidad) {
        return new ItemOrden(productoId, nombre, new BigDecimal(precioUnitario), cantidad);
    }

    private static void assertDinero(String esperado, BigDecimal real) {
        assertEquals(0, new BigDecimal(esperado).compareTo(real),
                () -> "esperado " + esperado + " pero fue " + real);
    }

    @Test
    void subtotalSumaPrecioPorCantidadDeCadaArticulo() {
        List<ItemOrden> articulos = List.of(
                item(1, "Tacos al Pastor", "145.00", 1),
                item(2, "Pozole Rojo", "210.00", 1),
                item(3, "Agua de Jamaica", "90.00", 2)
        );

        assertDinero("535.00", Totales.subtotal(articulos));
    }

    @Test
    void subtotalDeListaVaciaEsCero() {
        assertDinero("0.00", Totales.subtotal(List.of()));
    }

    @Test
    void ivaEsDieciseisPorCientoDelSubtotal() {
        assertDinero("16.00", Totales.iva(new BigDecimal("100.00")));
    }

    @Test
    void totalEsSubtotalMasIva() {
        assertDinero("116.00", Totales.total(new BigDecimal("100.00")));
    }

    @Test
    void cambiarCantidadAfectaElSubtotalDelArticulo() {
        ItemOrden articulo = item(3, "Agua de Jamaica", "90.00", 2);
        articulo.cantidadProperty().set(3);

        assertDinero("270.00", articulo.getSubtotal());
    }

    /**
     * ver CLAUDE.md, sección "Precios e IVA": algunas sucursales no cobran IVA
     * (Sucursal.isAplicaIva). Estas dos ramas (iva/total con aplicaIva=false) no tenían ninguna
     * prueba pese a ser lógica de negocio real, usada directamente en VentaService -- encontrado
     * al auditar la Fase 6.
     */
    @Test
    void ivaEsCeroCuandoLaSucursalNoAplicaIva() {
        assertDinero("0.00", Totales.iva(new BigDecimal("100.00"), false));
    }

    @Test
    void ivaSigueSiendoDieciseisPorCientoCuandoLaSucursalSiAplicaIva() {
        assertDinero("16.00", Totales.iva(new BigDecimal("100.00"), true));
    }

    @Test
    void totalEsIgualAlSubtotalCuandoLaSucursalNoAplicaIva() {
        assertDinero("100.00", Totales.total(new BigDecimal("100.00"), false));
    }

    @Test
    void totalIncluyeIvaCuandoLaSucursalSiAplicaIva() {
        assertDinero("116.00", Totales.total(new BigDecimal("100.00"), true));
    }

    /**
     * Casos de precisión (ver auditoría de Fase 4): valores límite y con más de dos decimales de
     * entrada, para confirmar que el redondeo a centavos (HALF_UP) es consistente en todo el
     * camino BigDecimal, sin pasar nunca por double.
     */
    @Test
    void subtotalDeUnSoloArticuloEnCero() {
        assertDinero("0.00", Totales.subtotal(List.of(item(1, "Cortesía", "0.00", 1))));
    }

    @Test
    void manejaMontosDeUnCentavo() {
        assertDinero("0.01", Totales.subtotal(List.of(item(1, "Extra", "0.01", 1))));
        // 0.01 * 0.16 = 0.0016 -> redondeado HALF_UP a centavos, 0.00
        assertDinero("0.00", Totales.iva(new BigDecimal("0.01")));
    }

    @Test
    void manejaDiezCentavos() {
        assertDinero("0.10", Totales.subtotal(List.of(item(1, "Extra", "0.10", 1))));
        assertDinero("0.02", Totales.iva(new BigDecimal("0.10")));
    }

    @Test
    void manejaUnNoventaYNueve() {
        assertDinero("1.99", Totales.subtotal(List.of(item(1, "Refresco chico", "1.99", 1))));
    }

    @Test
    void manejaDiez() {
        assertDinero("10.00", Totales.subtotal(List.of(item(1, "Postre", "10.00", 1))));
        assertDinero("11.60", Totales.total(new BigDecimal("10.00")));
    }

    @Test
    void manejaNovecientosNoventaYNueveConNoventaYNueve() {
        assertDinero("999.99", Totales.subtotal(List.of(item(1, "Banquete", "999.99", 1))));
    }

    @Test
    void manejaCantidadesGrandesDeArticulos() {
        List<ItemOrden> articulos = List.of(item(1, "Taco", "15.50", 250));

        assertDinero("3875.00", Totales.subtotal(articulos));
        assertDinero("4495.00", Totales.total(new BigDecimal("3875.00")));
    }

    @Test
    void redondeaAlCalcularIvaSobreUnSubtotalConMasDeDosDecimales() {
        // 33.33 * 0.16 = 5.3328 -> redondeado HALF_UP a 5.33
        assertDinero("5.33", Totales.iva(new BigDecimal("33.33")));
    }

    @Test
    void sumaVariosArticulosConDecimalesDistintos() {
        List<ItemOrden> articulos = List.of(
                item(1, "A", "12.345", 1),
                item(2, "B", "7.655", 1)
        );

        // 12.345 + 7.655 = 20.000 -> redondeado a escala 2
        assertDinero("20.00", Totales.subtotal(articulos));
    }
}
