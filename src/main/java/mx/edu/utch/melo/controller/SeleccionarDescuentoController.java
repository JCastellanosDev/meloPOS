package mx.edu.utch.melo.controller;

import javafx.fxml.FXML;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import mx.edu.utch.melo.model.TipoDescuento;
import mx.edu.utch.melo.sesion.SesionActual;

/**
 * Ventana emergente chica para elegir la categoría de descuento (ver Pantalla.SELECCIONAR_DESCUENTO,
 * abierta desde PaymentPortalController). Solo guarda la elección en SesionActual y se cierra --
 * la autorización con PIN de Administrador y la llamada a PagoService.aplicarDescuento siguen
 * viviendo en PaymentPortalController, que recoge la elección cuando la ventana de Cobrar
 * recupera el foco (mismo patrón que clienteEnProceso/distanciaKmEnProceso, ver SesionActual).
 */
public class SeleccionarDescuentoController {

    @FXML
    private VBox raiz;

    private final SesionActual sesion;

    public SeleccionarDescuentoController(SesionActual sesion) {
        this.sesion = sesion;
    }

    @FXML
    void onDescuentoEmpleado() {
        seleccionar(TipoDescuento.EMPLEADO);
    }

    @FXML
    void onDescuentoCortesia() {
        seleccionar(TipoDescuento.CORTESIA);
    }

    @FXML
    void onDescuentoPromocion() {
        seleccionar(TipoDescuento.PROMOCION);
    }

    @FXML
    void onDescuentoAjuste() {
        seleccionar(TipoDescuento.AJUSTE);
    }

    private void seleccionar(TipoDescuento tipo) {
        sesion.setDescuentoSeleccionado(tipo);
        cerrarVentana();
    }

    @FXML
    void onCancelar() {
        cerrarVentana();
    }

    private void cerrarVentana() {
        ((Stage) raiz.getScene().getWindow()).close();
    }
}
