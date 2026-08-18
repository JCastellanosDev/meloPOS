package mx.edu.utch.melo.async;

import javafx.concurrent.Task;

import java.util.function.Consumer;
import java.util.function.Supplier;

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
