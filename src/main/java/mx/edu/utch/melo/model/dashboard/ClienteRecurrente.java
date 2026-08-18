package mx.edu.utch.melo.model.dashboard;

import java.math.BigDecimal;

/**
 * DTO de solo lectura: un cliente con más de una orden en el rango (ver auditoría de Fase 8,
 * "clientes recurrentes" -- DashboardDAO#obtenerClientesRecurrentes). Requiere que la orden tenga
 * cliente_id (solo DOMICILIO/PEDIDOS lo captura hoy, ver CLAUDE.md) -- COMEDOR sin cliente
 * registrado nunca puede contar como recurrente con los datos actuales.
 */
public record ClienteRecurrente(String nombreCliente, int numeroOrdenes, BigDecimal totalGastado) {
}
