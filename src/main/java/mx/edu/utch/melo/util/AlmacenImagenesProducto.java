package mx.edu.utch.melo.util;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Copia la foto de producto elegida por el usuario a una carpeta local de la
 * app (fuera del classpath, mismo espíritu que Configuracion con .env: no se
 * sube al repo, ver .gitignore) y devuelve la ruta guardada. Es solo
 * almacenamiento local -- no hay backend propio al que subir archivos.
 */
public final class AlmacenImagenesProducto {

    private static final Path CARPETA = Path.of("imagenes-productos");

    private AlmacenImagenesProducto() {
    }

    public static String guardar(File origen) {
        try {
            Files.createDirectories(CARPETA);
            Path destino = CARPETA.resolve(UUID.randomUUID() + extension(origen.getName()));
            Files.copy(origen.toPath(), destino, StandardCopyOption.REPLACE_EXISTING);
            return destino.toString();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar la imagen: " + origen.getName(), e);
        }
    }

    private static String extension(String nombreArchivo) {
        int punto = nombreArchivo.lastIndexOf('.');
        return punto < 0 ? "" : nombreArchivo.substring(punto);
    }
}
