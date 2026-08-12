package mx.edu.utch.melo.nav;

import javafx.util.Callback;
import mx.edu.utch.melo.app.AppContext;

import java.lang.reflect.Constructor;

/**
 * Fábrica de controladores para FXMLLoader: si el controlador declara
 * un constructor que recibe {@link AppContext}, se lo inyecta; si
 * declara uno que recibe solo {@link Navigator} (controladores que
 * únicamente navegan, sin tocar la base de datos), le da eso; si no
 * declara ninguno de los dos, usa el constructor sin argumentos. Así
 * cada controlador pide justo lo que necesita, no más.
 */
public class ControllerFactory implements Callback<Class<?>, Object> {

    private final AppContext contexto;

    public ControllerFactory(AppContext contexto) {
        this.contexto = contexto;
    }

    @Override
    public Object call(Class<?> tipoControlador) {
        try {
            for (Constructor<?> constructor : tipoControlador.getConstructors()) {
                Class<?>[] parametros = constructor.getParameterTypes();
                if (parametros.length == 1 && parametros[0] == AppContext.class) {
                    return constructor.newInstance(contexto);
                }
                if (parametros.length == 1 && parametros[0] == Navigator.class) {
                    return constructor.newInstance(contexto.getNavigator());
                }
            }
            return tipoControlador.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("No se pudo crear el controlador: " + tipoControlador, e);
        }
    }
}
