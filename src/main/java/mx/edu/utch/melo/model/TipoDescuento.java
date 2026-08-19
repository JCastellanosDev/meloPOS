package mx.edu.utch.melo.model;

/**
 * Categorías de descuento (ver PagoService.aplicarDescuento). El cajero solo elige la
 * categoría -- no captura ningún valor -- y requiere autorización con PIN de Administrador
 * (ver UsuarioService.autenticarAdministrador, ControlAcceso).
 *
 * El porcentaje de cada categoría ya NO vive aquí como constante -- se volvió configurable
 * desde Ajustes (ver DescuentoService, tabla descuentos) para que el negocio pueda ajustarlo
 * sin recompilar. Este enum solo identifica la categoría y su etiqueta para mostrar en pantalla.
 */
public enum TipoDescuento {
    EMPLEADO("Empleado"),
    CORTESIA("Cortesía"),
    PROMOCION("Promoción"),
    AJUSTE("Ajuste");

    private final String etiqueta;

    TipoDescuento(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    /** Nombre en español para mostrar en pantalla. */
    public String getEtiqueta() {
        return etiqueta;
    }
}
