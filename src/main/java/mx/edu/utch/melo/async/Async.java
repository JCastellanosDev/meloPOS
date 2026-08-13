package mx.edu.utch.melo.async;

import javafx.concurrent.Task;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Corre trabajo bloqueante (acceso a BD, llamadas HTTP a Mapbox) fuera
 * del hilo de aplicación de JavaFX, y entrega el resultado de vuelta en
 * ese mismo hilo -- así los controladores pueden tocar la UI directamente
 * dentro de {@code alExito}/{@code alError} sin usar Platform.runLater
 * ellos mismos (javafx.concurrent.Task ya lo garantiza).
 */
public final class Async {

    private Async() {
    }

    public static <T> void ejecutar(Supplier<T> trabajo, Consumer<T> alExito, Consumer<Throwable> alError) {
        Task<T> tarea = new Task<>() {
            @Override
            protected T call() {
                return trabajo.get();
            }
        };
        tarea.setOnSucceeded(evento -> alExito.accept(tarea.getValue()));
        tarea.setOnFailed(evento -> alError.accept(tarea.getException()));

        Thread hilo = new Thread(tarea, "melo-async");
        hilo.setDaemon(true);
        hilo.start();
    }
}
