package mx.edu.utch.melo.dao;

import mx.edu.utch.melo.model.TipoDescuento;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Porcentaje y nombre visible de cada categoría de descuento (tabla descuentos, ver
 * DescuentoService/PagoService.aplicarDescuento). No extiende CrudDAO: es un catálogo de
 * exactamente una fila por valor de TipoDescuento, sembrado por schema.sql -- no se crean ni
 * eliminan filas en tiempo de ejecución, solo se lee y se actualiza cada fila existente.
 */
public interface DescuentoDAO {

    /** Porcentaje vigente de una sola categoría (ej. 0.20 = 20%), usado al calcular un cobro real. */
    BigDecimal obtenerPorcentaje(TipoDescuento tipo);

    /** Las 4 categorías con su configuración vigente (etiqueta + porcentaje), para mostrarlas juntas. */
    Map<TipoDescuento, ConfiguracionDescuento> obtenerTodos();

    boolean actualizar(TipoDescuento tipo, String etiqueta, BigDecimal porcentaje);

    /** Una fila de la tabla descuentos -- etiqueta es lo que ve el cajero, tipo_descuento (la llave) no cambia. */
    record ConfiguracionDescuento(String etiqueta, BigDecimal porcentaje) {
    }
}
