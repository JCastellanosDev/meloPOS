package mx.edu.utch.melo.model;

import java.math.BigDecimal;

/**
 * Categorías de descuento con porcentaje predefinido (ver PagoService.aplicarDescuento). El
 * cajero solo elige la categoría -- no captura ningún valor -- y requiere autorización con PIN
 * de Administrador (ver UsuarioService.autenticarAdministrador, ControlAcceso).
 *
 * Los porcentajes son valores por defecto sensatos, iguales en espíritu a
 * ProductoService.TASA_IVA_DEFECTO: editables aquí mismo si el negocio pide otros valores, no
 * hay una pantalla de configuración para esto todavía.
 */
public enum TipoDescuento {
    EMPLEADO("Empleado", new BigDecimal("0.20")),
    CORTESIA("Cortesía", new BigDecimal("1.00")),
    PROMOCION("Promoción", new BigDecimal("0.10")),
    AJUSTE("Ajuste", new BigDecimal("0.05"));

    private final String etiqueta;
    private final BigDecimal porcentaje;

    TipoDescuento(String etiqueta, BigDecimal porcentaje) {
        this.etiqueta = etiqueta;
        this.porcentaje = porcentaje;
    }

    /** Nombre en español para mostrar en pantalla. */
    public String getEtiqueta() {
        return etiqueta;
    }

    /** Fracción de 0 a 1 (ej. 0.20 = 20%) que se resta del total -- ver PagoService.aplicarDescuento. */
    public BigDecimal getPorcentaje() {
        return porcentaje;
    }
}
