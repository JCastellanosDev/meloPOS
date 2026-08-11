package mx.edu.utch.melo.nav;

/** Registro único de las pantallas navegables de la app (fuente única de verdad para sus FXML). */
public enum Pantalla {

    DASHBOARD("Dashboard.fxml"),
    ORDENES("PaymentPortal.fxml"),
    MENU("MenuPOS.fxml"),
    COCINA("KitchenDisplay.fxml"),
    DOMICILIO("DeliveryView.fxml"),
    CLIENTES("RegisterClient.fxml");

    private final String archivoFxml;

    Pantalla(String archivoFxml) {
        this.archivoFxml = archivoFxml;
    }

    public String getArchivoFxml() {
        return archivoFxml;
    }
}
