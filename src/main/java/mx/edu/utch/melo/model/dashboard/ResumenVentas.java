package mx.edu.utch.melo.model.dashboard;

import java.math.BigDecimal;

/** DTO de solo lectura: ventas totales, número de órdenes y ticket promedio en un rango de fechas. */
public record ResumenVentas(BigDecimal total, int numeroOrdenes, BigDecimal ticketPromedio) {

    public static final ResumenVentas VACIO = new ResumenVentas(BigDecimal.ZERO, 0, BigDecimal.ZERO);
}
