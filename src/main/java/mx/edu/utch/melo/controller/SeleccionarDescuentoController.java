package mx.edu.utch.melo.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import mx.edu.utch.melo.async.Async;
import mx.edu.utch.melo.dao.DescuentoDAO.ConfiguracionDescuento;
import mx.edu.utch.melo.model.TipoDescuento;
import mx.edu.utch.melo.service.DescuentoService;
import mx.edu.utch.melo.sesion.SesionActual;

import java.util.Map;

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

    @FXML
    private Button btnEmpleado;

    @FXML
    private Button btnCortesia;

    @FXML
    private Button btnPromocion;

    @FXML
    private Button btnAjuste;

    private final DescuentoService descuentoService;
    private final SesionActual sesion;

    public SeleccionarDescuentoController(DescuentoService descuentoService, SesionActual sesion) {
        this.descuentoService = descuentoService;
        this.sesion = sesion;
    }

    @FXML
    void initialize() {
        // Nombres reales de cada categoría (ver DescuentoService, configurable desde Ajustes) --
        // si no se pueden cargar, los botones se quedan con el texto por defecto que ya trae el
        // FXML (el nombre del enum), nunca en blanco.
        Async.ejecutar(
                descuentoService::obtenerConfiguraciones,
                this::aplicarEtiquetas,
                error -> { /* botones ya tienen el nombre por defecto del FXML, nada que hacer */ }
        );
    }

    private void aplicarEtiquetas(Map<TipoDescuento, ConfiguracionDescuento> configuraciones) {
        aplicarEtiqueta(btnEmpleado, configuraciones.get(TipoDescuento.EMPLEADO));
        aplicarEtiqueta(btnCortesia, configuraciones.get(TipoDescuento.CORTESIA));
        aplicarEtiqueta(btnPromocion, configuraciones.get(TipoDescuento.PROMOCION));
        aplicarEtiqueta(btnAjuste, configuraciones.get(TipoDescuento.AJUSTE));
    }

    private void aplicarEtiqueta(Button boton, ConfiguracionDescuento configuracion) {
        if (configuracion != null) {
            boton.setText(configuracion.etiqueta());
        }
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
