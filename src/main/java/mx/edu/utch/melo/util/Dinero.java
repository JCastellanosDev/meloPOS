package mx.edu.utch.melo.util;

import java.util.Locale;

/** Formato de moneda consistente para toda la app (MXN, sin símbolo de moneda regional). */
public final class Dinero {

    private Dinero() {
    }

    public static String formatear(double valor) {
        return String.format(Locale.US, "$%,.2f", valor);
    }
}
