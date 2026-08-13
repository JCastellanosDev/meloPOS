package mx.edu.utch.melo.model.dashboard;

import java.math.BigDecimal;

/** DTO de solo lectura: unidades e ingresos de una categoría en un rango de fechas. */
public record CategoriaVendida(String nombreCategoria, int unidadesVendidas, BigDecimal ingresos) {
}
