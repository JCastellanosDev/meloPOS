package mx.edu.utch.melo.model.reporte;

/** DTO de solo lectura: cuántas unidades se vendieron de un producto, para el ranking de más vendidos. */
public record PlatilloVendido(String nombreProducto, int cantidadVendida) {
}
