package mx.edu.utch.melo.model.dashboard;

import java.math.BigDecimal;

/** DTO de solo lectura: total vendido en una hora del día (0-23), para detectar la hora pico. */
public record VentaPorHora(int hora, BigDecimal total) {
}
