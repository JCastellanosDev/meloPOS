package mx.edu.utch.melo.model.dashboard;

import java.math.BigDecimal;

/** DTO de solo lectura: cuánto vendió y cuántas órdenes atendió un empleado en un rango de fechas. */
public record VentaPorEmpleado(String nombreEmpleado, int numeroOrdenes, BigDecimal totalVendido) {
}
