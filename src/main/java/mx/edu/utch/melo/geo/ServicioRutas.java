package mx.edu.utch.melo.geo;

import java.util.Optional;

/**
 * Abstracción de "calcular la ruta real entre dos puntos". Los controladores
 * dependen de esta interfaz, no de qué proveedor la resuelve (hoy Mapbox) --
 * Inversión de Dependencias, igual que Geocodificador.
 */
public interface ServicioRutas {

    /** Vacío si no se pudo calcular la ruta (coordenadas inválidas, sin conexión, error del proveedor). */
    Optional<Ruta> calcularRuta(Coordenadas origen, Coordenadas destino);
}
