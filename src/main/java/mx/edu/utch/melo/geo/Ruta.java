package mx.edu.utch.melo.geo;

import java.math.BigDecimal;

/** Resultado de calcular una ruta real: la distancia y una imagen estática del mapa con el trazo dibujado. */
public record Ruta(BigDecimal distanciaKm, String urlMapaEstatico) {
}
