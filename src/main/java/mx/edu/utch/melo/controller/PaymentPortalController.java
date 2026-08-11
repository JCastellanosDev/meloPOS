package mx.edu.utch.melo.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import mx.edu.utch.melo.model.EntradaMonetaria;
import mx.edu.utch.melo.nav.Pantalla;
import mx.edu.utch.melo.util.Dinero;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PaymentPortalController {

    @FXML
    private Label lblRecibido;

    @FXML
    private Label lblCambio;

    @FXML
    private Label lblTotalCuenta;

    @FXML
    private Button btnConfirmarPago;

    @FXML
    private VBox tileEfectivo;
    @FXML
    private Label lblEfectivo;
    @FXML
    private VBox tileTarjeta;
    @FXML
    private Label lblTarjeta;
    @FXML
    private VBox tileTransferencia;
    @FXML
    private Label lblTransferencia;

    @FXML
    private SidebarController sidebarController;

    /** Total de la cuenta activa, tomado del recibo mostrado (sin persistencia todavía). */
    private double totalCuenta;

    private final EntradaMonetaria entradaRecibido = new EntradaMonetaria();

    @FXML
    void initialize() {
        sidebarController.activar(Pantalla.ORDENES);
        totalCuenta = parsearMoneda(lblTotalCuenta.getText());
        actualizarRecibido();
    }

    @FXML
    void onTeclaNumpad(ActionEvent evento) {
        String tecla = ((Button) evento.getSource()).getText();
        entradaRecibido.procesarTecla(tecla);
        actualizarRecibido();
    }

    @FXML
    private void onMontoRapido(ActionEvent evento) {
        String texto = ((Button) evento.getSource()).getText();
        double monto = texto.equals("Exacto") ? totalCuenta : parsearMoneda(texto);
        entradaRecibido.establecer(monto);
        actualizarRecibido();
    }

    @FXML
    private void onReiniciarRecibido() {
        entradaRecibido.reiniciar();
        actualizarRecibido();
    }

    @FXML
    private void onEfectivoClick() {
        activarMetodoPago(tileEfectivo);
    }

    @FXML
    private void onTarjetaClick() {
        activarMetodoPago(tileTarjeta);
    }

    @FXML
    private void onTransferenciaClick() {
        activarMetodoPago(tileTransferencia);
    }

    private void activarMetodoPago(VBox tileSeleccionado) {
        for (Map.Entry<VBox, Label> tile : List.of(
                Map.entry(tileEfectivo, lblEfectivo),
                Map.entry(tileTarjeta, lblTarjeta),
                Map.entry(tileTransferencia, lblTransferencia))) {
            boolean activo = tile.getKey() == tileSeleccionado;
            tile.getKey().getStyleClass().setAll(activo ? "pay-method-active" : "pay-method", "gap-8");
            tile.getValue().getStyleClass().setAll(activo ? "pay-method-label-active" : "pay-method-label");
        }
    }

    private double parsearMoneda(String texto) {
        String limpio = texto.replace("$", "").replace(",", "").trim();
        try {
            return Double.parseDouble(limpio);
        } catch (NumberFormatException ex) {
            return 0.0;
        }
    }

    private void actualizarRecibido() {
        double recibido = entradaRecibido.valor();
        lblRecibido.setText(String.format(Locale.US, "%,.2f", recibido));
        double cambio = recibido - totalCuenta;
        lblCambio.setText(Dinero.formatear(Math.max(cambio, 0)));
        btnConfirmarPago.setDisable(cambio < 0);
    }
}
