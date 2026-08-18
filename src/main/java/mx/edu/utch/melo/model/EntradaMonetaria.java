package mx.edu.utch.melo.model;

import java.math.BigDecimal;

/**
 * Buffer de dígitos que el cajero teclea para capturar un monto (p. ej. en el
 * numpad de PaymentPortal). Sin dependencias de JavaFX UI: se puede probar
 * de forma unitaria y reutilizar en cualquier pantalla que necesite captura
 * numérica.
 *
 * {@link #valor()} regresa BigDecimal (ver auditoría de Fase 4, melo-java25): el monto recibido
 * y el cambio calculado a partir de él son dinero real, no un valor de UI de solo lectura -- antes
 * pasaba por double y PaymentPortalController volvía a convertir a BigDecimal para comparar contra
 * el total de la orden.
 */
public class EntradaMonetaria {

    private static final String TECLA_BORRAR = "⌫";
    private static final String TECLA_PUNTO = ".";

    private String buffer = "";

    /** Interpreta una tecla del numpad (dígito, "." o "⌫") y actualiza el buffer. */
    public void procesarTecla(String tecla) {
        if (TECLA_BORRAR.equals(tecla)) {
            borrar();
        } else if (TECLA_PUNTO.equals(tecla)) {
            agregarPunto();
        } else {
            buffer += tecla;
        }
    }

    public void establecer(BigDecimal valor) {
        buffer = valor.toPlainString();
    }

    public void reiniciar() {
        buffer = "";
    }

    public BigDecimal valor() {
        if (buffer.isEmpty() || buffer.equals(TECLA_PUNTO)) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(buffer);
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }

    private void agregarPunto() {
        if (!buffer.contains(TECLA_PUNTO)) {
            buffer += buffer.isEmpty() ? "0." : TECLA_PUNTO;
        }
    }

    private void borrar() {
        if (!buffer.isEmpty()) {
            buffer = buffer.substring(0, buffer.length() - 1);
        }
    }
}
