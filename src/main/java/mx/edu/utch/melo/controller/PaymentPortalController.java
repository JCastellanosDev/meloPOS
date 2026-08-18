package mx.edu.utch.melo.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import mx.edu.utch.melo.async.Async;
import mx.edu.utch.melo.model.EntradaMonetaria;
import mx.edu.utch.melo.model.MetodoPago;
import mx.edu.utch.melo.model.Orden;
import mx.edu.utch.melo.model.Rol;
import mx.edu.utch.melo.model.TipoDescuento;
import mx.edu.utch.melo.nav.Navigator;
import mx.edu.utch.melo.nav.Pantalla;
import mx.edu.utch.melo.security.PinBloqueadoException;
import mx.edu.utch.melo.service.PagoService;
import mx.edu.utch.melo.service.UsuarioService;
import mx.edu.utch.melo.sesion.SesionActual;
import mx.edu.utch.melo.util.Dinero;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Ventana emergente de cobro (ver MenuPOSController.onCobrarCuenta). Muestra el desglose de
 * artículos (ver contenedorTicket) además del total a pagar, descuento, método de pago y teclado
 * numérico (ver PaymentPortal.fxml) -- el carrito en Menú ya se vació al crear la orden (ver
 * MenuPOSController.onCobrarCuenta), así que aquí es la única pantalla que lo sigue mostrando.
 */
public class PaymentPortalController {

    @FXML
    private BorderPane raiz;
    @FXML
    private Label lblNumeroOrden;
    @FXML
    private VBox contenedorTicket;

    @FXML
    private HBox filaTotalOriginal;
    @FXML
    private Label lblTotalOriginal;
    @FXML
    private HBox filaDescuentoInfo;
    @FXML
    private Label lblDescuentoEtiqueta;
    @FXML
    private Label lblDescuentoMonto;
    @FXML
    private Label lblTotalAPagar;

    @FXML
    private Button btnElegirDescuento;
    @FXML
    private HBox filaDescuentoAplicado;
    @FXML
    private Label lblDescuentoAplicadoTexto;

    @FXML
    private VBox panelAutorizacion;
    @FXML
    private Label lblAutorizacionTitulo;
    @FXML
    private PasswordField txtPinAutorizacion;
    @FXML
    private Label lblErrorDescuento;
    @FXML
    private Button btnAutorizarDescuento;

    @FXML
    private Button tabPagoUnico;
    @FXML
    private Button tabPagoDividido;

    @FXML
    private VBox panelPagoUnico;
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
    private Label lblRecibido;
    @FXML
    private Label lblCambio;

    @FXML
    private VBox panelPagoDividido;
    @FXML
    private VBox cajaEfectivoDividido;
    @FXML
    private Label lblEfectivoDividido;
    @FXML
    private VBox cajaTarjetaDividido;
    @FXML
    private Label lblTarjetaDividido;
    @FXML
    private Label lblFaltanteDividido;

    @FXML
    private Label lblErrorPago;
    @FXML
    private Button btnConfirmarPago;

    private final PagoService pagoService;
    private final UsuarioService usuarioService;
    private final SesionActual sesion;
    private final Navigator navigator;

    private final EntradaMonetaria entradaRecibido = new EntradaMonetaria();
    private final EntradaMonetaria entradaEfectivoDividido = new EntradaMonetaria();
    private final EntradaMonetaria entradaTarjetaDividido = new EntradaMonetaria();

    private Orden ordenActiva;
    private MetodoPago metodoSeleccionado = MetodoPago.EFECTIVO;
    private ModoPago modoPago = ModoPago.UNICO;
    private CampoDividido campoDivididoActivo = CampoDividido.EFECTIVO;
    /** Categoría que se está autorizando ahora mismo (ver panelAutorizacion); null si no hay ninguna en curso. */
    private TipoDescuento descuentoPendienteAutorizar;

    public PaymentPortalController(PagoService pagoService, UsuarioService usuarioService, SesionActual sesion,
                                    Navigator navigator) {
        this.pagoService = pagoService;
        this.usuarioService = usuarioService;
        this.sesion = sesion;
        this.navigator = navigator;
    }

    @FXML
    void initialize() {
        btnConfirmarPago.setDisable(true);
        habilitarCapturaPorTeclado();
        habilitarRecogerDescuentoAlRegresarElFoco();

        Integer ordenId = sesion.getOrdenEnProceso().orElse(null);
        if (ordenId == null) {
            mostrarSinOrden();
            return;
        }
        cargarOrden(ordenId);
    }

    /**
     * Las categorías de descuento viven en su propia ventana emergente (ver
     * Pantalla.SELECCIONAR_DESCUENTO) -- cuando esta ventana recupera el foco (la de descuento se
     * cerró), revisa si dejó una elección pendiente en SesionActual y, si la hay, arranca la
     * autorización con PIN de Administrador aquí mismo.
     */
    private void habilitarRecogerDescuentoAlRegresarElFoco() {
        raiz.sceneProperty().addListener((obsScene, escenaAnterior, escenaNueva) -> {
            if (escenaNueva == null) {
                return;
            }
            escenaNueva.windowProperty().addListener((obsWindow, ventanaAnterior, ventanaNueva) -> {
                if (ventanaNueva == null) {
                    return;
                }
                ventanaNueva.focusedProperty().addListener((obsFoco, antes, tieneFoco) -> {
                    if (tieneFoco) {
                        recogerDescuentoSeleccionado();
                    }
                });
            });
        });
    }

    private void recogerDescuentoSeleccionado() {
        sesion.consumirDescuentoSeleccionado().ifPresent(this::iniciarAutorizacionDescuento);
    }

    @FXML
    void onAbrirSeleccionDescuento() {
        navigator.abrirVentana(Pantalla.SELECCIONAR_DESCUENTO, "melo - Descuento");
    }

    /**
     * Además de los botones en pantalla, se puede teclear el monto con el teclado físico -- pero
     * no mientras panelAutorizacion está abierto (ahí el foco es del PasswordField del PIN, que ya
     * maneja su propia escritura; interceptar aquí encima le robaría los dígitos).
     */
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
        if (panelAutorizacion.isVisible() || evento.getCharacter().length() != 1) {
            return;
        }
        char c = evento.getCharacter().charAt(0);
        if (Character.isDigit(c) || c == '.') {
            entradaActiva().procesarTecla(String.valueOf(c));
            actualizarSegunModo();
        }
    }

    private void onTeclaFisicaControl(KeyEvent evento) {
        if (panelAutorizacion.isVisible()) {
            return;
        }
        if (evento.getCode() == KeyCode.BACK_SPACE) {
            entradaActiva().procesarTecla("⌫");
            actualizarSegunModo();
        } else if (evento.getCode() == KeyCode.ENTER && !btnConfirmarPago.isDisable()) {
            onConfirmarPago();
        }
    }

    private void mostrarSinOrden() {
        lblNumeroOrden.setText("Sin una orden activa");
    }

    private void cargarOrden(int ordenId) {
        Async.ejecutar(
                () -> pagoService.obtenerRecibo(ordenId, sesion.getSucursalActivaId()),
                datos -> {
                    this.ordenActiva = datos.orden();
                    lblNumeroOrden.setText("Orden #" + datos.orden().getId());
                    renderizarTicket(datos.lineas());
                    // Precarga "Recibido" con el total para el caso común de pago exacto en efectivo
                    // (mismo mecanismo que el botón "Exacto", ver onMontoRapido); solo afecta el
                    // campo de pago único -- el dividido no se puede adivinar, se captura a mano.
                    entradaRecibido.establecer(datos.orden().getTotal());
                    actualizarTotales();
                },
                error -> mostrarSinOrden()
        );
    }

    private void renderizarTicket(List<PagoService.LineaRecibo> lineas) {
        contenedorTicket.getChildren().clear();
        for (PagoService.LineaRecibo linea : lineas) {
            Label lblNombre = new Label(linea.cantidad() + "×  " + linea.nombre());
            lblNombre.getStyleClass().add("text-body");
            lblNombre.setWrapText(true);
            HBox.setHgrow(lblNombre, Priority.ALWAYS);

            Label lblMonto = new Label(Dinero.formatear(linea.monto()));
            lblMonto.getStyleClass().add("text-price");

            HBox fila = new HBox(8, lblNombre, lblMonto);
            fila.setAlignment(Pos.TOP_LEFT);

            if (linea.nota() == null || linea.nota().isBlank()) {
                contenedorTicket.getChildren().add(fila);
            } else {
                Label lblNota = new Label(linea.nota());
                lblNota.getStyleClass().add("text-muted");
                lblNota.setWrapText(true);
                contenedorTicket.getChildren().add(new VBox(2, fila, lblNota));
            }
        }
    }

    // ===================== TOTAL Y DESCUENTO =====================

    private void actualizarTotales() {
        boolean tieneDescuento = ordenActiva.getTipoDescuento() != null;
        filaTotalOriginal.setVisible(tieneDescuento);
        filaTotalOriginal.setManaged(tieneDescuento);
        filaDescuentoInfo.setVisible(tieneDescuento);
        filaDescuentoInfo.setManaged(tieneDescuento);

        if (tieneDescuento) {
            BigDecimal totalOriginal = ordenActiva.getTotal().add(ordenActiva.getMontoDescuento());
            lblTotalOriginal.setText(Dinero.formatear(totalOriginal));
            lblDescuentoEtiqueta.setText("Descuento (" + ordenActiva.getTipoDescuento().getEtiqueta() + ")");
            lblDescuentoMonto.setText("-" + Dinero.formatear(ordenActiva.getMontoDescuento()));
        }
        lblTotalAPagar.setText(Dinero.formatear(ordenActiva.getTotal()));

        actualizarPanelDescuento();
        actualizarSegunModo();
    }

    private void actualizarPanelDescuento() {
        boolean tieneDescuento = ordenActiva.getTipoDescuento() != null;
        btnElegirDescuento.setVisible(!tieneDescuento);
        btnElegirDescuento.setManaged(!tieneDescuento);
        filaDescuentoAplicado.setVisible(tieneDescuento);
        filaDescuentoAplicado.setManaged(tieneDescuento);
        if (tieneDescuento) {
            lblDescuentoAplicadoTexto.setText(
                    ordenActiva.getTipoDescuento().getEtiqueta() + ": -" + Dinero.formatear(ordenActiva.getMontoDescuento()));
        }
    }

    private void iniciarAutorizacionDescuento(TipoDescuento tipo) {
        descuentoPendienteAutorizar = tipo;
        int porcentaje = tipo.getPorcentaje().multiply(BigDecimal.valueOf(100)).intValue();
        lblAutorizacionTitulo.setText("Autoriza \"" + tipo.getEtiqueta() + "\" (" + porcentaje + "%)");
        txtPinAutorizacion.clear();
        ocultarErrorDescuento();
        btnElegirDescuento.setVisible(false);
        btnElegirDescuento.setManaged(false);
        panelAutorizacion.setVisible(true);
        panelAutorizacion.setManaged(true);
        txtPinAutorizacion.requestFocus();
    }

    @FXML
    void onCancelarAutorizacion() {
        descuentoPendienteAutorizar = null;
        panelAutorizacion.setVisible(false);
        panelAutorizacion.setManaged(false);
        actualizarPanelDescuento();
    }

    @FXML
    void onAutorizarDescuento() {
        if (descuentoPendienteAutorizar == null || txtPinAutorizacion.getText().isBlank()) {
            return;
        }
        ocultarErrorDescuento();
        btnAutorizarDescuento.setDisable(true);
        String pin = txtPinAutorizacion.getText();
        TipoDescuento tipo = descuentoPendienteAutorizar;

        Async.ejecutar(
                () -> usuarioService.autenticarAdministrador(sesion.getSucursalActivaId(), pin),
                resultado -> {
                    btnAutorizarDescuento.setDisable(false);
                    if (resultado.isEmpty()) {
                        mostrarErrorDescuento("PIN inválido o sin autorización de Administrador.");
                        txtPinAutorizacion.clear();
                        return;
                    }
                    aplicarDescuentoAutorizado(tipo, resultado.get().getRol());
                },
                error -> {
                    btnAutorizarDescuento.setDisable(false);
                    mostrarErrorDescuento(error instanceof PinBloqueadoException
                            ? error.getMessage()
                            : "No se pudo verificar el PIN. Intenta de nuevo.");
                }
        );
    }

    private void aplicarDescuentoAutorizado(TipoDescuento tipo, Rol rolAutorizante) {
        int ordenId = ordenActiva.getId();
        Async.ejecutar(
                () -> pagoService.aplicarDescuento(rolAutorizante, ordenId, tipo),
                ordenActualizada -> {
                    this.ordenActiva = ordenActualizada;
                    descuentoPendienteAutorizar = null;
                    panelAutorizacion.setVisible(false);
                    panelAutorizacion.setManaged(false);
                    // El total cambió -- re-sincroniza "Recibido" (ver cargarOrden) para que no se
                    // quede apuntando al monto de antes del descuento.
                    entradaRecibido.establecer(ordenActualizada.getTotal());
                    actualizarTotales();
                },
                error -> mostrarErrorDescuento("No se pudo aplicar el descuento. Intenta de nuevo.")
        );
    }

    @FXML
    void onQuitarDescuento() {
        Async.ejecutar(
                () -> pagoService.quitarDescuento(ordenActiva.getId()),
                ordenActualizada -> {
                    this.ordenActiva = ordenActualizada;
                    entradaRecibido.establecer(ordenActualizada.getTotal());
                    actualizarTotales();
                },
                error -> mostrarErrorPago("No se pudo quitar el descuento. Intenta de nuevo.")
        );
    }

    // ===================== MODO DE PAGO =====================

    @FXML
    void onModoPagoUnico() {
        cambiarModoPago(ModoPago.UNICO);
    }

    @FXML
    void onModoPagoDividido() {
        cambiarModoPago(ModoPago.DIVIDIDO);
    }

    private void cambiarModoPago(ModoPago modo) {
        this.modoPago = modo;
        boolean unico = modo == ModoPago.UNICO;
        tabPagoUnico.getStyleClass().setAll(unico ? "tab-item-active" : "tab-item");
        tabPagoDividido.getStyleClass().setAll(unico ? "tab-item" : "tab-item-active");
        panelPagoUnico.setVisible(unico);
        panelPagoUnico.setManaged(unico);
        panelPagoDividido.setVisible(!unico);
        panelPagoDividido.setManaged(!unico);
        ocultarErrorPago();
        actualizarSegunModo();
    }

    @FXML
    void onSeleccionarCampoEfectivo() {
        campoDivididoActivo = CampoDividido.EFECTIVO;
        resaltarCampoDivididoActivo();
    }

    @FXML
    void onSeleccionarCampoTarjeta() {
        campoDivididoActivo = CampoDividido.TARJETA;
        resaltarCampoDivididoActivo();
    }

    private void resaltarCampoDivididoActivo() {
        boolean efectivoActivo = campoDivididoActivo == CampoDividido.EFECTIVO;
        cajaEfectivoDividido.getStyleClass().setAll(efectivoActivo ? "amount-box-active" : "amount-box");
        cajaTarjetaDividido.getStyleClass().setAll(efectivoActivo ? "amount-box" : "amount-box-active");
    }

    @FXML
    void onLimpiarPagoDividido() {
        entradaEfectivoDividido.reiniciar();
        entradaTarjetaDividido.reiniciar();
        actualizarPagoDividido();
    }

    private EntradaMonetaria entradaActiva() {
        if (modoPago == ModoPago.UNICO) {
            return entradaRecibido;
        }
        return campoDivididoActivo == CampoDividido.EFECTIVO ? entradaEfectivoDividido : entradaTarjetaDividido;
    }

    private void actualizarSegunModo() {
        if (modoPago == ModoPago.UNICO) {
            actualizarRecibido();
        } else {
            actualizarPagoDividido();
        }
    }

    // ===================== NUMPAD / MONTOS =====================

    @FXML
    void onTeclaNumpad(ActionEvent evento) {
        String tecla = ((Button) evento.getSource()).getText();
        entradaActiva().procesarTecla(tecla);
        actualizarSegunModo();
    }

    @FXML
    private void onMontoRapido(ActionEvent evento) {
        if (ordenActiva == null) {
            return;
        }
        String texto = ((Button) evento.getSource()).getText();
        BigDecimal monto = texto.equals("Exacto") ? ordenActiva.getTotal() : parsearMoneda(texto);
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
            tile.getKey().getStyleClass().setAll(activo ? "pay-method-active" : "pay-method", "gap-6");
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
        int usuarioId = sesion.getUsuarioActivo().getId();

        if (modoPago == ModoPago.UNICO) {
            MetodoPago metodo = metodoSeleccionado;
            BigDecimal montoTotal = ordenActiva.getTotal();
            Async.ejecutar(
                    () -> pagoService.registrarPago(ordenId, metodo, montoTotal, usuarioId),
                    exito -> confirmarYCerrar(),
                    error -> fallarConfirmacion()
            );
        } else {
            BigDecimal montoEfectivo = entradaEfectivoDividido.valor();
            BigDecimal montoTarjeta = entradaTarjetaDividido.valor();
            Async.ejecutar(
                    () -> pagoService.registrarPagoDividido(ordenId, montoEfectivo, montoTarjeta, usuarioId),
                    exito -> confirmarYCerrar(),
                    error -> fallarConfirmacion()
            );
        }
    }

    private void confirmarYCerrar() {
        sesion.setOrdenEnProceso(null);
        cerrarVentana();
    }

    private void fallarConfirmacion() {
        btnConfirmarPago.setDisable(false);
        mostrarErrorPago("No se pudo confirmar el pago. Intenta de nuevo.");
    }

    /** Esta pantalla es una ventana emergente (ver MenuPOSController.onCobrarCuenta), no una pestaña: al terminar, se cierra sola. */
    private void cerrarVentana() {
        ((Stage) btnConfirmarPago.getScene().getWindow()).close();
    }

    private BigDecimal parsearMoneda(String texto) {
        String limpio = texto.replace("$", "").replace(",", "").trim();
        try {
            return new BigDecimal(limpio);
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }

    private void actualizarRecibido() {
        BigDecimal recibido = entradaRecibido.valor();
        lblRecibido.setText(String.format(Locale.US, "%,.2f", recibido));
        if (ordenActiva == null) {
            lblCambio.setText(Dinero.formatear(BigDecimal.ZERO));
            btnConfirmarPago.setDisable(true);
            return;
        }
        BigDecimal cambio = recibido.subtract(ordenActiva.getTotal());
        lblCambio.setText(Dinero.formatear(cambio.max(BigDecimal.ZERO)));
        if (modoPago == ModoPago.UNICO) {
            btnConfirmarPago.setDisable(cambio.signum() < 0);
        }
    }

    private void actualizarPagoDividido() {
        lblEfectivoDividido.setText(String.format(Locale.US, "%,.2f", entradaEfectivoDividido.valor()));
        lblTarjetaDividido.setText(String.format(Locale.US, "%,.2f", entradaTarjetaDividido.valor()));
        if (ordenActiva == null) {
            return;
        }
        BigDecimal suma = entradaEfectivoDividido.valor().add(entradaTarjetaDividido.valor());
        BigDecimal falta = ordenActiva.getTotal().subtract(suma);
        if (falta.signum() == 0) {
            lblFaltanteDividido.setText("Coincide con el total ✓");
        } else if (falta.signum() > 0) {
            lblFaltanteDividido.setText("Falta " + Dinero.formatear(falta));
        } else {
            lblFaltanteDividido.setText("Sobran " + Dinero.formatear(falta.abs()));
        }
        if (modoPago == ModoPago.DIVIDIDO) {
            btnConfirmarPago.setDisable(falta.signum() != 0);
        }
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

    private void mostrarErrorDescuento(String mensaje) {
        lblErrorDescuento.setText("⚠ " + mensaje);
        lblErrorDescuento.setVisible(true);
        lblErrorDescuento.setManaged(true);
    }

    private void ocultarErrorDescuento() {
        lblErrorDescuento.setVisible(false);
        lblErrorDescuento.setManaged(false);
    }

    private enum ModoPago {
        UNICO, DIVIDIDO
    }

    private enum CampoDividido {
        EFECTIVO, TARJETA
    }
}
