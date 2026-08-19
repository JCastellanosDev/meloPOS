package mx.edu.utch.melo.service;

import mx.edu.utch.melo.dao.DescuentoDAO;
import mx.edu.utch.melo.model.Rol;
import mx.edu.utch.melo.model.TipoDescuento;
import mx.edu.utch.melo.security.Auditoria;
import mx.edu.utch.melo.security.ControlAcceso;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Porcentaje configurable de cada categoría de descuento (ver Ajustes, PagoService.aplicarDescuento
 * que lee el valor vigente directo de DescuentoDAO en el momento del cobro -- este Service solo
 * cubre la lectura para mostrar en pantalla y la escritura autorizada, no el cálculo del cobro).
 */
public class DescuentoService {

    private final DescuentoDAO descuentoDAO;

    public DescuentoService(DescuentoDAO descuentoDAO) {
        this.descuentoDAO = descuentoDAO;
    }

    public Map<TipoDescuento, BigDecimal> obtenerPorcentajes() {
        return descuentoDAO.obtenerTodos();
    }

    /**
     * Cambiar el porcentaje de una categoría de descuento cambia cuánto se le cobra a un cliente
     * en cada venta futura donde se autorice esa categoría -- misma sensibilidad que
     * SucursalService.actualizarAplicaIva, mismo requisito de rol.
     */
    public void actualizarPorcentaje(Rol rolSolicitante, TipoDescuento tipo, BigDecimal nuevoPorcentaje) {
        ControlAcceso.exigirRol(rolSolicitante, "modificar el porcentaje de descuento", Rol.ADMINISTRADOR);
        if (nuevoPorcentaje == null || nuevoPorcentaje.signum() < 0 || nuevoPorcentaje.compareTo(BigDecimal.ONE) > 0) {
            throw new PorcentajeInvalidoException(nuevoPorcentaje);
        }
        descuentoDAO.actualizar(tipo, nuevoPorcentaje);
        Auditoria.registrar(rolSolicitante, "cambio de porcentaje de descuento",
                "tipo=" + tipo + " porcentaje=" + nuevoPorcentaje);
    }

    /** El porcentaje es una fracción de 0 a 1 (0% a 100% de descuento) -- fuera de ese rango no tiene sentido de negocio. */
    public static class PorcentajeInvalidoException extends RuntimeException {
        public PorcentajeInvalidoException(BigDecimal porcentaje) {
            super("El porcentaje de descuento debe estar entre 0 y 1 (recibido: " + porcentaje + ").");
        }
    }
}
