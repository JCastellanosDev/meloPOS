package mx.edu.utch.melo.nav;

import javafx.util.Callback;

import java.lang.reflect.Constructor;

/**
 * Fábrica de controladores para FXMLLoader: si el controlador declara un
 * constructor que recibe un {@link Navigator}, se lo inyecta; si no,
 * usa el constructor sin argumentos. Esto permite que los controladores
 * dependan de la abstracción Navigator en vez de buscarla ellos mismos.
 */
public class ControllerFactory implements Callback<Class<?>, Object> {

    private final Navigator navigator;

    public ControllerFactory(Navigator navigator) {
        this.navigator = navigator;
    }

    @Override
    public Object call(Class<?> tipoControlador) {
        try {
            for (Constructor<?> constructor : tipoControlador.getConstructors()) {
                Class<?>[] parametros = constructor.getParameterTypes();
                if (parametros.length == 1 && parametros[0] == Navigator.class) {
                    return constructor.newInstance(navigator);
                }
            }
            return tipoControlador.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("No se pudo crear el controlador: " + tipoControlador, e);
        }
    }
}
