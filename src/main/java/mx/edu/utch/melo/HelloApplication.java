package mx.edu.utch.melo;

import javafx.application.Application;
import javafx.stage.Stage;
import mx.edu.utch.melo.nav.Navigator;
import mx.edu.utch.melo.nav.Pantalla;
import mx.edu.utch.melo.nav.SceneManager;

public class HelloApplication extends Application {
    @Override
    public void start(Stage stage) {
        Navigator navigator = new SceneManager(stage);
        stage.setTitle("melo - Terminal de Ventas");
        navigator.navigateTo(Pantalla.DASHBOARD);
        stage.show();
    }
}
