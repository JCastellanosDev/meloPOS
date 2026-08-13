package mx.edu.utch.melo.geo;

import java.util.Optional;

/**
 * Abstracción de "convertir una dirección de texto en coordenadas".
 * Los controladores dependen de esta interfaz, no de qué proveedor la
 * resuelve (hoy Mapbox) -- Inversión de Dependencias, igual que Navigator.
 */
public interface Geocodificador {

    /** Vacío si la dirección no se pudo geocodificar (no encontrada, sin conexión, error del proveedor). */
    Optional<Coordenadas> geocodificar(String direccion);

    /**
     * Igual que {@link #geocodificar(String)}, pero ancla la búsqueda cerca de un punto conocido
     * (p. ej. la sucursal activa) -- direcciones parciales o nombres de negocio (calle+número sin
     * ciudad, "Alsúper Arboledas") pueden regresar resultados lejanos/incorrectos sin esta pista.
     * Implementación por defecto: ignora la pista, para no obligar a otros proveedores a soportarla.
     */
    default Optional<Coordenadas> geocodificar(String direccion, Coordenadas cercaDe) {
        return geocodificar(direccion);
    }
}
