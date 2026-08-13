package mx.edu.utch.melo.geo;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Distancia en línea recta entre dos coordenadas (fórmula de Haversine).
 * Sin dependencias de red ni de JavaFX: se puede probar de forma unitaria.
 * Es una aproximación en línea recta, no una ruta real por calles -- para
 * eso haría falta una API de rutas (Mapbox Directions), fuera de alcance.
 */
public final class CalculadorDistancia {

    private static final double RADIO_TIERRA_KM = 6371.0;

    private CalculadorDistancia() {
    }

    public static BigDecimal enKilometros(BigDecimal latitud1, BigDecimal longitud1,
                                           BigDecimal latitud2, BigDecimal longitud2) {
        double lat1Rad = Math.toRadians(latitud1.doubleValue());
        double lat2Rad = Math.toRadians(latitud2.doubleValue());
        double deltaLat = Math.toRadians(latitud2.doubleValue() - latitud1.doubleValue());
        double deltaLon = Math.toRadians(longitud2.doubleValue() - longitud1.doubleValue());

        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(lat1Rad) * Math.cos(lat2Rad)
                * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distanciaKm = RADIO_TIERRA_KM * c;

        return BigDecimal.valueOf(distanciaKm).setScale(2, RoundingMode.HALF_UP);
    }
}
