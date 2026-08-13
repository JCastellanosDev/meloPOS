package mx.edu.utch.melo.geo;

/** Envuelve cualquier falla de red/formato al llamar al proveedor de geocodificación (hoy Mapbox). */
public class GeocodificacionException extends RuntimeException {

    public GeocodificacionException(String mensaje) {
        super(mensaje);
    }

    public GeocodificacionException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
