package mx.edu.utch.melo.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Producto.tieneStockBajo() es la regla de alerta de inventario (ver CLAUDE.md, sección
 * Inventario; InventarioController/DashboardService la consumen para la alerta de stock bajo) --
 * no tenía ninguna prueba directa antes de esta fase, solo cobertura transitiva vía
 * DashboardServiceTest con un único escenario fijo (ver auditoría de Fase 5, "riesgo de negocio
 * antes que cobertura artificial").
 */
class ProductoTest {

    private static Producto productoConStock(int cantidadDisponible, int stockMinimo) {
        Producto producto = new Producto();
        producto.setCantidadDisponible(cantidadDisponible);
        producto.setStockMinimo(stockMinimo);
        return producto;
    }

    @Test
    void tieneStockBajoCuandoLaCantidadEsMenorQueElMinimo() {
        assertTrue(productoConStock(2, 5).tieneStockBajo());
    }

    @Test
    void tieneStockBajoCuandoLaCantidadEsExactamenteIgualAlMinimo() {
        // la implementación usa "<=": igualar el mínimo ya cuenta como alerta, no solo bajarlo.
        assertTrue(productoConStock(5, 5).tieneStockBajo());
    }

    @Test
    void noTieneStockBajoCuandoLaCantidadSuperaElMinimo() {
        assertFalse(productoConStock(6, 5).tieneStockBajo());
    }

    @Test
    void tieneStockBajoCuandoLaCantidadEsCeroYElMinimoTambienEsCero() {
        // producto sin stockMinimo configurado (0 por defecto, ver ProductoService.crear) que ya
        // se agotó -- debe seguir marcándose como alerta, no asumir "sin mínimo = nunca hay alerta".
        assertTrue(productoConStock(0, 0).tieneStockBajo());
    }

    @Test
    void noTieneStockBajoConExistenciasYSinMinimoConfigurado() {
        assertFalse(productoConStock(20, 0).tieneStockBajo());
    }

    @Test
    void tieneStockBajoConCantidadNegativaEsVerdadero() {
        // no debería ocurrir en operación normal (nada descuenta stock todavía, ver CLAUDE.md),
        // pero si llegara a pasar (ajuste manual erróneo en la base), la alerta debe seguir
        // disparando -- no lanzar ni tratarlo como caso especial.
        assertTrue(productoConStock(-1, 0).tieneStockBajo());
    }
}
