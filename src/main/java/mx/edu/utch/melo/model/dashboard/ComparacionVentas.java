package mx.edu.utch.melo.model.dashboard;

import java.math.BigDecimal;

/**
 * Ventas del periodo actual contra el periodo anterior equivalente (p. ej. esta semana vs. la
 * pasada) -- ver DashboardService, que decide qué es "el periodo anterior" para cada caso
 * (día/semana/mes) y calcula {@code variacionPorcentual}; el DAO solo agrega, no compara.
 */
public record ComparacionVentas(ResumenVentas actual, ResumenVentas anterior, BigDecimal variacionPorcentual) {
}
