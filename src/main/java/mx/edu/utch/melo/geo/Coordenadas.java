package mx.edu.utch.melo.geo;

import java.math.BigDecimal;

/** Un par latitud/longitud, resultado de geocodificar una dirección. */
public class Coordenadas {

    private final BigDecimal latitud;
    private final BigDecimal longitud;

    public Coordenadas(BigDecimal latitud, BigDecimal longitud) {
        this.latitud = latitud;
        this.longitud = longitud;
    }

    public BigDecimal getLatitud() {
        return latitud;
    }

    public BigDecimal getLongitud() {
        return longitud;
    }
}
