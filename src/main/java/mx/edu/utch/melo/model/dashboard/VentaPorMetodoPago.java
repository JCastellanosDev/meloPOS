package mx.edu.utch.melo.model.dashboard;

import mx.edu.utch.melo.model.MetodoPago;

import java.math.BigDecimal;

/** DTO de solo lectura: total cobrado con un método de pago en un rango de fechas. */
public record VentaPorMetodoPago(MetodoPago metodoPago, BigDecimal total) {
}
