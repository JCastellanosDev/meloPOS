package mx.edu.utch.melo.model;

/**
 * Buffer de dígitos que el cajero teclea para capturar un monto (p. ej. en el
 * numpad de PaymentPortal). Sin dependencias de JavaFX UI: se puede probar
 * de forma unitaria y reutilizar en cualquier pantalla que necesite captura
 * numérica.
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

    public void establecer(double valor) {
        buffer = String.valueOf(valor);
    }

    public void reiniciar() {
        buffer = "";
    }

    public double valor() {
        if (buffer.isEmpty() || buffer.equals(TECLA_PUNTO)) {
            return 0.0;
        }
        try {
            return Double.parseDouble(buffer);
        } catch (NumberFormatException ex) {
            return 0.0;
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
