package mx.edu.utch.melo.model.dashboard;

import java.math.BigDecimal;

/**
 * DTO de solo lectura: un producto del catálogo que no tuvo ninguna venta en el rango de fechas
 * consultado. No existe una tabla de movimientos de inventario (ver auditoría de Fase 8) -- esta
 * es la aproximación honesta a "sin movimiento" que sí se puede calcular hoy: sin ventas, no sin
 * entradas/salidas de almacén.
 */
public record ProductoSinVentas(int productoId, String nombre, BigDecimal precio) {
}
