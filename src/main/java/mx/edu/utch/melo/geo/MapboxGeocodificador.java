package mx.edu.utch.melo.geo;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;


public class MapboxGeocodificador implements Geocodificador {

    private static final String URL_BASE = "https://api.mapbox.com/geocoding/v5/mapbox.places/";

    private final String token;
    private final HttpClient httpClient;

    public MapboxGeocodificador(String token) {
        this.token = token;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public Optional<Coordenadas> geocodificar(String direccion) {
        return geocodificar(direccion, null);
    }

    @Override
    public Optional<Coordenadas> geocodificar(String direccion, Coordenadas cercaDe) {
        if (token == null || token.isBlank()) {
            throw new IllegalStateException(
                    "Falta configurar mapbox.token en .env -- ver CLAUDE.md, sección Domicilios.");
        }
        if (direccion == null || direccion.isBlank()) {
            return Optional.empty();
        }

        String direccionCodificada = URLEncoder.encode(direccion, StandardCharsets.UTF_8);
        StringBuilder url = new StringBuilder(URL_BASE)
                .append(direccionCodificada)
                .append(".json?limit=1&country=mx&access_token=")
                .append(token);
        if (cercaDe != null) {
            url.append("&proximity=")
                    .append(String.format(Locale.US, "%s,%s", cercaDe.getLongitud().toPlainString(), cercaDe.getLatitud().toPlainString()));
        }
        URI uri = URI.create(url.toString());
        HttpRequest peticion = HttpRequest.newBuilder(uri).GET().timeout(Duration.ofSeconds(10)).build();

        try {
            HttpResponse<String> respuesta = httpClient.send(peticion, HttpResponse.BodyHandlers.ofString());
            if (respuesta.statusCode() != 200) {
                throw new GeocodificacionException(
                        "Mapbox respondió " + respuesta.statusCode() + " al geocodificar: " + direccion);
            }
            return extraerCoordenadas(respuesta.body());
        } catch (IOException e) {
            throw new GeocodificacionException("No se pudo conectar con Mapbox para geocodificar: " + direccion, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GeocodificacionException("Geocodificación interrumpida: " + direccion, e);
        }
    }

    private Optional<Coordenadas> extraerCoordenadas(String cuerpoJson) {
        JsonObject raiz = JsonParser.parseString(cuerpoJson).getAsJsonObject();
        JsonArray features = raiz.getAsJsonArray("features");
        if (features == null || features.isEmpty()) {
            return Optional.empty();
        }
        JsonArray coordenadas = features.get(0).getAsJsonObject()
                .getAsJsonObject("geometry")
                .getAsJsonArray("coordinates");
        // Mapbox devuelve [longitud, latitud], en ese orden -- al revés de lo intuitivo.
        BigDecimal longitud = coordenadas.get(0).getAsBigDecimal();
        BigDecimal latitud = coordenadas.get(1).getAsBigDecimal();
        return Optional.of(new Coordenadas(latitud, longitud));
    }
}
