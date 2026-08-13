package mx.edu.utch.melo.model.reporte;

import java.math.BigDecimal;
import java.time.LocalDate;

/** DTO de solo lectura: total vendido en un día, para el reporte de ventas por periodo. */
public record VentaDiaria(LocalDate fecha, BigDecimal total) {
}
