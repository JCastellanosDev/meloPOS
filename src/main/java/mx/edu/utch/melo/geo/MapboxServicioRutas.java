package mx.edu.utch.melo.geo;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

/**
 * Calcula rutas reales (distancia + una imagen estática con el trazo dibujado)
 * vía las APIs de Directions y Static Images de Mapbox. Llamada de red
 * BLOQUEANTE a propósito: quien la use desde un controlador de JavaFX debe
 * correrla en un hilo aparte (ver async.Async) para no congelar la UI.
 */
public class MapboxServicioRutas implements ServicioRutas {

    private static final String URL_DIRECTIONS = "https://api.mapbox.com/directions/v5/mapbox/driving/";
    private static final String URL_STATIC = "https://api.mapbox.com/styles/v1/mapbox/streets-v11/static/";
    private static final String COLOR_RUTA = "C8552A";

    private final String token;
    private final HttpClient httpClient;

    public MapboxServicioRutas(String token) {
        this.token = token;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public Optional<Ruta> calcularRuta(Coordenadas origen, Coordenadas destino) {
        if (token == null || token.isBlank()) {
            throw new IllegalStateException(
                    "Falta configurar mapbox.token en .env -- ver CLAUDE.md, sección Domicilios.");
        }

        String coordenadasRuta = formatearCoordenada(origen) + ";" + formatearCoordenada(destino);
        URI uri = URI.create(URL_DIRECTIONS + coordenadasRuta
                + "?geometries=polyline&overview=simplified&access_token=" + token);
        HttpRequest peticion = HttpRequest.newBuilder(uri).GET().timeout(Duration.ofSeconds(10)).build();

        try {
            HttpResponse<String> respuesta = httpClient.send(peticion, HttpResponse.BodyHandlers.ofString());
            if (respuesta.statusCode() != 200) {
                return Optional.empty();
            }
            return extraerRuta(respuesta.body(), origen, destino);
        } catch (IOException e) {
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    private Optional<Ruta> extraerRuta(String cuerpoJson, Coordenadas origen, Coordenadas destino) {
        JsonObject raiz = JsonParser.parseString(cuerpoJson).getAsJsonObject();
        JsonArray rutas = raiz.getAsJsonArray("routes");
        if (rutas == null || rutas.isEmpty()) {
            return Optional.empty();
        }
        JsonObject primeraRuta = rutas.get(0).getAsJsonObject();
        double distanciaMetros = primeraRuta.get("distance").getAsDouble();
        String polilineaCodificada = primeraRuta.get("geometry").getAsString();

        BigDecimal distanciaKm = BigDecimal.valueOf(distanciaMetros / 1000.0).setScale(2, RoundingMode.HALF_UP);
        String urlMapa = construirUrlMapaEstatico(origen, destino, polilineaCodificada);
        return Optional.of(new Ruta(distanciaKm, urlMapa));
    }

    private String construirUrlMapaEstatico(Coordenadas origen, Coordenadas destino, String polilineaCodificada) {
        String trazo = "path-4+" + COLOR_RUTA + "-0.8(" + URLEncoder.encode(polilineaCodificada, StandardCharsets.UTF_8) + ")";
        String pinOrigen = "pin-s-a+" + COLOR_RUTA + "(" + formatearCoordenada(origen) + ")";
        String pinDestino = "pin-s-b+" + COLOR_RUTA + "(" + formatearCoordenada(destino) + ")";
        return URL_STATIC + trazo + "," + pinOrigen + "," + pinDestino + "/auto/560x220@2x?access_token=" + token;
    }

    /** Mapbox espera "longitud,latitud", al revés de lo intuitivo -- igual que en MapboxGeocodificador. */
    private String formatearCoordenada(Coordenadas coordenadas) {
        return String.format(Locale.US, "%s,%s", coordenadas.getLongitud().toPlainString(), coordenadas.getLatitud().toPlainString());
    }
}
