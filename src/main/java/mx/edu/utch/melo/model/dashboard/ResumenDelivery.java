package mx.edu.utch.melo.model.dashboard;

import java.math.BigDecimal;

/**
 * DTO de solo lectura: desempeño del canal DOMICILIO en un rango de fechas.
 * {@code ingresosTotales} es la venta completa de esas órdenes (no solo el costo de envío) --
 * "ingresos de delivery" en el sentido de negocio: lo que ese canal le generó al restaurante.
 */
public record ResumenDelivery(int numeroPedidos, BigDecimal distanciaPromedioKm, BigDecimal costoEnvioPromedio,
                               BigDecimal ingresosTotales) {

    public static final ResumenDelivery VACIO = new ResumenDelivery(0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
}
