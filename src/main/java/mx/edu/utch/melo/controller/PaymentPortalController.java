package mx.edu.utch.melo.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import mx.edu.utch.melo.app.AppContext;
import mx.edu.utch.melo.async.Async;
import mx.edu.utch.melo.model.EntradaMonetaria;
import mx.edu.utch.melo.model.MetodoPago;
import mx.edu.utch.melo.model.Orden;
import mx.edu.utch.melo.model.Sucursal;
import mx.edu.utch.melo.model.Usuario;
import mx.edu.utch.melo.service.PagoService;
import mx.edu.utch.melo.service.PagoService.LineaRecibo;
import mx.edu.utch.melo.sesion.SesionActual;
import mx.edu.utch.melo.util.Dinero;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PaymentPortalController {

    private static final DateTimeFormatter FORMATO_FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy · HH:mm");

    @FXML
    private BorderPane raiz;
    @FXML
    private Label lblNumeroOrden;
    @FXML
    private Label lblSucursal;
    @FXML
    private Label lblTelefonoSucursal;
    @FXML
    private Label lblFechaHora;
    @FXML
    private Label lblCajero;
    @FXML
    private VBox listaArticulos;
    @FXML
    private Label lblSubtotalCuenta;
    @FXML
    private HBox filaImpuestos;
    @FXML
    private Label lblImpuestosCuenta;
    @FXML
    private Label lblTotalCuenta;

    @FXML
    private Label lblRecibido;
    @FXML
    private Label lblCambio;
    @FXML
    private Label lblErrorPago;
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

    private final PagoService pagoService;
    private final SesionActual sesion;

    private final EntradaMonetaria entradaRecibido = new EntradaMonetaria();
    private Orden ordenActiva;
    private MetodoPago metodoSeleccionado = MetodoPago.EFECTIVO;

    public PaymentPortalController(AppContext contexto) {
        this.pagoService = contexto.getPagoService();
        this.sesion = contexto.getSesion();
    }

    @FXML
    void initialize() {
        btnConfirmarPago.setDisable(true);
        habilitarCapturaPorTeclado();

        Integer ordenId = sesion.getOrdenEnProceso().orElse(null);
        if (ordenId == null) {
            mostrarSinOrden();
            return;
        }
        cargarOrden(ordenId);
    }

    /** Además de los botones en pantalla, el cajero puede teclear el monto recibido con el teclado físico. */
    private void habilitarCapturaPorTeclado() {
        raiz.sceneProperty().addListener((obs, escenaAnterior, escenaNueva) -> {
            if (escenaNueva == null) {
                return;
            }
            escenaNueva.addEventFilter(KeyEvent.KEY_TYPED, this::onTeclaFisicaCaracter);
            escenaNueva.addEventFilter(KeyEvent.KEY_PRESSED, this::onTeclaFisicaControl);
        });
    }

    private void onTeclaFisicaCaracter(KeyEvent evento) {
        String caracter = evento.getCharacter();
        if (caracter.length() != 1) {
            return;
        }
        char c = caracter.charAt(0);
        if (Character.isDigit(c)) {
            entradaRecibido.procesarTecla(String.valueOf(c));
            actualizarRecibido();
        } else if (c == '.') {
            entradaRecibido.procesarTecla(".");
            actualizarRecibido();
        }
    }

    private void onTeclaFisicaControl(KeyEvent evento) {
        if (evento.getCode() == KeyCode.BACK_SPACE) {
            entradaRecibido.procesarTecla("⌫");
            actualizarRecibido();
        } else if (evento.getCode() == KeyCode.ENTER && !btnConfirmarPago.isDisable()) {
            onConfirmarPago();
        }
    }

    private void mostrarSinOrden() {
        lblNumeroOrden.setText("Sin una orden activa");
        lblFechaHora.setText("Ve a Menú para tomar un pedido primero.");
    }

    private void cargarOrden(int ordenId) {
        Async.ejecutar(
                () -> pagoService.obtenerRecibo(ordenId, sesion.getSucursalActivaId()),
                datos -> {
                    this.ordenActiva = datos.orden();
                    mostrarEncabezado(datos.orden(), datos.cajero(), datos.sucursal());
                    listaArticulos.getChildren().setAll(datos.lineas().stream().map(this::construirFilaRecibo).toList());
                    actualizarRecibido();
                },
                error -> mostrarSinOrden()
        );
    }

    /** Mismo encabezado que el ticket de Menú (ver MenuPOSController), para que el recibo se vea igual. */
    private void mostrarEncabezado(Orden orden, Usuario cajero, Sucursal sucursal) {
        lblNumeroOrden.setText("Orden #" + orden.getId());
        lblSucursal.setText(sucursal == null ? "—" : sucursal.getNombre());
        lblTelefonoSucursal.setText(sucursal == null || sucursal.getTelefono() == null || sucursal.getTelefono().isBlank()
                ? "—" : sucursal.getTelefono());
        lblFechaHora.setText(orden.getFechaCreacion().format(FORMATO_FECHA_HORA));
        lblCajero.setText("Cajero: " + (cajero == null ? "—" : cajero.getNombre()));
        lblSubtotalCuenta.setText(Dinero.formatear(orden.getSubtotal()));
        lblImpuestosCuenta.setText(Dinero.formatear(orden.getImpuestos()));
        lblTotalCuenta.setText(Dinero.formatear(orden.getTotal()));
        boolean cobraIva = orden.getImpuestos().signum() > 0;
        filaImpuestos.setVisible(cobraIva);
        filaImpuestos.setManaged(cobraIva);
    }

    /** Misma tipografía que un artículo del ticket de Menú (ver FilaArticuloFactory), pero de solo lectura. */
    private VBox construirFilaRecibo(LineaRecibo linea) {
        Label lblNombre = new Label(linea.cantidad() + "×  " + linea.nombre());
        lblNombre.getStyleClass().add("cart-item-name");
        lblNombre.setWrapText(true);
        HBox.setHgrow(lblNombre, Priority.ALWAYS);

        Label lblMonto = new Label(Dinero.formatear(linea.monto()));
        lblMonto.getStyleClass().add("text-price");

        HBox filaSuperior = new HBox(8, lblNombre, lblMonto);

        VBox fila = new VBox(4, filaSuperior);
        fila.getStyleClass().add("cart-item-row");

        if (linea.nota() != null && !linea.nota().isBlank()) {
            Label lblNota = new Label("↳ " + linea.nota());
            lblNota.getStyleClass().add("receipt-item-note");
            lblNota.setWrapText(true);
            fila.getChildren().add(lblNota);
        }

        return fila;
    }

    @FXML
    void onTeclaNumpad(ActionEvent evento) {
        String tecla = ((Button) evento.getSource()).getText();
        entradaRecibido.procesarTecla(tecla);
        actualizarRecibido();
    }

    @FXML
    private void onMontoRapido(ActionEvent evento) {
        if (ordenActiva == null) {
            return;
        }
        String texto = ((Button) evento.getSource()).getText();
        double monto = texto.equals("Exacto") ? ordenActiva.getTotal().doubleValue() : parsearMoneda(texto);
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
        activarMetodoPago(tileEfectivo, MetodoPago.EFECTIVO);
    }

    @FXML
    private void onTarjetaClick() {
        activarMetodoPago(tileTarjeta, MetodoPago.TARJETA);
    }

    @FXML
    private void onTransferenciaClick() {
        activarMetodoPago(tileTransferencia, MetodoPago.TRANSFERENCIA);
    }

    private void activarMetodoPago(VBox tileSeleccionado, MetodoPago metodo) {
        this.metodoSeleccionado = metodo;
        for (Map.Entry<VBox, Label> tile : List.of(
                Map.entry(tileEfectivo, lblEfectivo),
                Map.entry(tileTarjeta, lblTarjeta),
                Map.entry(tileTransferencia, lblTransferencia))) {
            boolean activo = tile.getKey() == tileSeleccionado;
            tile.getKey().getStyleClass().setAll(activo ? "pay-method-active" : "pay-method", "gap-8");
            tile.getValue().getStyleClass().setAll(activo ? "pay-method-label-active" : "pay-method-label");
        }
    }

    @FXML
    void onConfirmarPago() {
        if (ordenActiva == null) {
            return;
        }
        ocultarErrorPago();
        btnConfirmarPago.setDisable(true);

        int ordenId = ordenActiva.getId();
        MetodoPago metodo = metodoSeleccionado;
        BigDecimal montoTotal = ordenActiva.getTotal();

        Async.ejecutar(
                () -> pagoService.registrarPago(ordenId, metodo, montoTotal, sesion.getUsuarioActivo().getId()),
                exito -> {
                    sesion.setOrdenEnProceso(null);
                    cerrarVentana();
                },
                error -> {
                    btnConfirmarPago.setDisable(false);
                    mostrarErrorPago("No se pudo confirmar el pago. Intenta de nuevo.");
                }
        );
    }

    /** Esta pantalla es una ventana emergente (ver MenuPOSController.onCobrarCuenta), no una pestaña: al terminar, se cierra sola. */
    private void cerrarVentana() {
        ((Stage) btnConfirmarPago.getScene().getWindow()).close();
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
        if (ordenActiva == null) {
            lblCambio.setText(Dinero.formatear(0));
            btnConfirmarPago.setDisable(true);
            return;
        }
        double cambio = recibido - ordenActiva.getTotal().doubleValue();
        lblCambio.setText(Dinero.formatear(Math.max(cambio, 0)));
        btnConfirmarPago.setDisable(cambio < 0);
    }

    private void mostrarErrorPago(String mensaje) {
        lblErrorPago.setText("⚠ " + mensaje);
        lblErrorPago.setVisible(true);
        lblErrorPago.setManaged(true);
    }

    private void ocultarErrorPago() {
        lblErrorPago.setVisible(false);
        lblErrorPago.setManaged(false);
    }
}
