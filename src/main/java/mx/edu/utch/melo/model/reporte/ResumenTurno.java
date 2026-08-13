package mx.edu.utch.melo.model.reporte;

import java.math.BigDecimal;

/** DTO de solo lectura: ventas de un turno agrupadas por método de pago, para el corte de caja. */
public record ResumenTurno(int turnoId, BigDecimal totalEfectivo, BigDecimal totalTarjeta,
                            BigDecimal totalTransferencia, BigDecimal ventasTotales, int numeroOrdenes) {
}
