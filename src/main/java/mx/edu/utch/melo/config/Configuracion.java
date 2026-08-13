package mx.edu.utch.melo.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Lectura de configuración compartida por toda la app (conexión a
 * MySQL, token de Mapbox, etc.), resuelta en dos capas, cada una
 * opcional:
 * 1. {@code database.properties} en el classpath -- valores por
 *    defecto, seguros de subir al repo (sin credenciales reales).
 * 2. {@code .env} en la raíz del proyecto -- credenciales locales
 *    reales, NUNCA se sube al repo (ver .gitignore). Si existe, sus
 *    valores sobrescriben a los de database.properties.
 * Si ninguno de los dos define una clave, se usa el valor por defecto
 * que pase quien la pida.
 */
public final class Configuracion {

    private static final String ARCHIVO_PROPIEDADES = "database.properties";
    private static final String ARCHIVO_ENV = ".env";

    private static final Properties PROPIEDADES = cargarPropiedades();

    private Configuracion() {
    }

    public static String obtener(String clave, String porDefecto) {
        return PROPIEDADES.getProperty(clave, porDefecto);
    }

    private static Properties cargarPropiedades() {
        Properties propiedades = new Properties();
        cargarDesdeClasspath(propiedades);
        cargarDesdeEnv(propiedades);
        return propiedades;
    }

    private static void cargarDesdeClasspath(Properties propiedades) {
        try (InputStream entrada = Configuracion.class.getClassLoader().getResourceAsStream(ARCHIVO_PROPIEDADES)) {
            if (entrada != null) {
                propiedades.load(entrada);
            }
            // Si el archivo no existe todavía, se usan los valores por defecto que pase el llamador.
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer " + ARCHIVO_PROPIEDADES, e);
        }
    }

    private static void cargarDesdeEnv(Properties propiedades) {
        Path archivoEnv = Path.of(ARCHIVO_ENV);
        if (!Files.exists(archivoEnv)) {
            return;
        }
        try (InputStream entrada = Files.newInputStream(archivoEnv)) {
            propiedades.load(entrada);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer " + ARCHIVO_ENV, e);
        }
    }
}
