package mx.edu.utch.melo.dao;

import mx.edu.utch.melo.model.TipoDescuento;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Porcentaje configurable de cada categoría de descuento (tabla descuentos, ver
 * DescuentoService/PagoService.aplicarDescuento). No extiende CrudDAO: es un catálogo de
 * exactamente una fila por valor de TipoDescuento, sembrado por schema.sql -- no se crean ni
 * eliminan filas en tiempo de ejecución, solo se lee y se actualiza el porcentaje.
 */
public interface DescuentoDAO {

    /** Porcentaje vigente de una sola categoría (ej. 0.20 = 20%), usado al calcular un cobro real. */
    BigDecimal obtenerPorcentaje(TipoDescuento tipo);

    /** Las 4 categorías con su porcentaje vigente, para mostrarlas juntas (ver Ajustes). */
    Map<TipoDescuento, BigDecimal> obtenerTodos();

    boolean actualizar(TipoDescuento tipo, BigDecimal porcentaje);
}
