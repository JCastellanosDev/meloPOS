package mx.edu.utch.melo.util;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * Formato de despliegue de dinero ($#,##0.00). Solo recibe BigDecimal (ver auditoría de Fase 4,
 * melo-java25): ya no existe un overload en double -- el último lugar del proyecto que formateaba
 * un double (PaymentPortalController, cálculo de cambio) se migró a BigDecimal junto con esto,
 * así que no queda ningún llamador legítimo que necesite ese camino.
 */
public final class Dinero {

    private Dinero() {
    }

    public static String formatear(BigDecimal valor) {
        return String.format(Locale.US, "$%,.2f", valor);
    }
}
