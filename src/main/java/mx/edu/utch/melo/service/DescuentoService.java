package mx.edu.utch.melo.service;

import mx.edu.utch.melo.dao.DescuentoDAO;
import mx.edu.utch.melo.dao.DescuentoDAO.ConfiguracionDescuento;
import mx.edu.utch.melo.model.Rol;
import mx.edu.utch.melo.model.TipoDescuento;
import mx.edu.utch.melo.security.Auditoria;
import mx.edu.utch.melo.security.ControlAcceso;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Porcentaje y nombre visible de cada categoría de descuento (ver Ajustes, PagoService.aplicarDescuento
 * que lee el porcentaje vigente directo de DescuentoDAO en el momento del cobro -- este Service solo
 * cubre la lectura para mostrar en pantalla y la escritura autorizada, no el cálculo del cobro).
 */
public class DescuentoService {

    private final DescuentoDAO descuentoDAO;

    public DescuentoService(DescuentoDAO descuentoDAO) {
        this.descuentoDAO = descuentoDAO;
    }

    public Map<TipoDescuento, ConfiguracionDescuento> obtenerConfiguraciones() {
        return descuentoDAO.obtenerTodos();
    }

    /**
     * Cambiar la etiqueta o el porcentaje de una categoría de descuento afecta cómo se ve y cuánto
     * se le cobra a un cliente en cada venta futura donde se autorice esa categoría -- misma
     * sensibilidad que SucursalService.actualizarAplicaIva, mismo requisito de rol. tipo_descuento
     * (la llave interna) nunca se edita, solo etiqueta/porcentaje.
     */
    public void actualizarConfiguracion(Rol rolSolicitante, TipoDescuento tipo, String etiqueta, BigDecimal nuevoPorcentaje) {
        ControlAcceso.exigirRol(rolSolicitante, "modificar la configuración de descuento", Rol.ADMINISTRADOR);
        if (etiqueta == null || etiqueta.isBlank()) {
            throw new EtiquetaInvalidaException();
        }
        if (nuevoPorcentaje == null || nuevoPorcentaje.signum() < 0 || nuevoPorcentaje.compareTo(BigDecimal.ONE) > 0) {
            throw new PorcentajeInvalidoException(nuevoPorcentaje);
        }
        descuentoDAO.actualizar(tipo, etiqueta.trim(), nuevoPorcentaje);
        Auditoria.registrar(rolSolicitante, "cambio de configuración de descuento",
                "tipo=" + tipo + " etiqueta=" + etiqueta.trim() + " porcentaje=" + nuevoPorcentaje);
    }

    /** El porcentaje es una fracción de 0 a 1 (0% a 100% de descuento) -- fuera de ese rango no tiene sentido de negocio. */
    public static class PorcentajeInvalidoException extends RuntimeException {
        public PorcentajeInvalidoException(BigDecimal porcentaje) {
            super("El porcentaje de descuento debe estar entre 0 y 1 (recibido: " + porcentaje + ").");
        }
    }

    /** El cajero ve esta etiqueta en pantalla -- vacía no comunica nada. */
    public static class EtiquetaInvalidaException extends RuntimeException {
        public EtiquetaInvalidaException() {
            super("El nombre de la categoría de descuento no puede quedar vacío.");
        }
    }
}
