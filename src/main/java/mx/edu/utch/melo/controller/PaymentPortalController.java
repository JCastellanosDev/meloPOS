package mx.edu.utch.melo.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import mx.edu.utch.melo.app.AppContext;
import mx.edu.utch.melo.async.Async;
import mx.edu.utch.melo.dao.DetalleOrdenDAO;
import mx.edu.utch.melo.dao.OrdenDAO;
import mx.edu.utch.melo.dao.PagoDAO;
import mx.edu.utch.melo.dao.ProductoDAO;
import mx.edu.utch.melo.dao.UsuarioDAO;
import mx.edu.utch.melo.model.DetalleOrden;
import mx.edu.utch.melo.model.EntradaMonetaria;
import mx.edu.utch.melo.model.EstadoOrden;
import mx.edu.utch.melo.model.MetodoPago;
import mx.edu.utch.melo.model.Orden;
import mx.edu.utch.melo.model.Pago;
import mx.edu.utch.melo.model.Producto;
import mx.edu.utch.melo.model.Usuario;
import mx.edu.utch.melo.nav.Navigator;
import mx.edu.utch.melo.nav.Pantalla;
import mx.edu.utch.melo.sesion.SesionActual;
import mx.edu.utch.melo.util.Dinero;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PaymentPortalController {

    @FXML
    private Label lblNumeroOrden;
    @FXML
    private Label lblFecha;
    @FXML
    private Label lblCajero;
    @FXML
    private VBox listaArticulos;
    @FXML
    private Label lblSubtotalCuenta;
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

    @FXML
    private SidebarController sidebarController;

    private final Navigator navigator;
    private final OrdenDAO ordenDAO;
    private final DetalleOrdenDAO detalleOrdenDAO;
    private final ProductoDAO productoDAO;
    private final PagoDAO pagoDAO;
    private final UsuarioDAO usuarioDAO;
    private final SesionActual sesion;

    private final EntradaMonetaria entradaRecibido = new EntradaMonetaria();
    private Orden ordenActiva;
    private MetodoPago metodoSeleccionado = MetodoPago.EFECTIVO;

    public PaymentPortalController(AppContext contexto) {
        this.navigator = contexto.getNavigator();
        this.ordenDAO = contexto.getOrdenDAO();
        this.detalleOrdenDAO = contexto.getDetalleOrdenDAO();
        this.productoDAO = contexto.getProductoDAO();
        this.pagoDAO = contexto.getPagoDAO();
        this.usuarioDAO = contexto.getUsuarioDAO();
        this.sesion = contexto.getSesion();
    }

    @FXML
    void initialize() {
        sidebarController.activar(Pantalla.ORDENES);
        btnConfirmarPago.setDisable(true);

        Integer ordenId = sesion.getOrdenEnProceso().orElse(null);
        if (ordenId == null) {
            mostrarSinOrden();
            return;
        }
        cargarOrden(ordenId);
    }

    private void mostrarSinOrden() {
        lblNumeroOrden.setText("Sin una orden activa");
        lblFecha.setText("Ve a Menú para tomar un pedido primero.");
    }

    private void cargarOrden(int ordenId) {
        Async.ejecutar(
                () -> construirDatosRecibo(ordenId),
                datos -> {
                    this.ordenActiva = datos.orden();
                    mostrarEncabezado(datos.orden(), datos.cajero());
                    listaArticulos.getChildren().setAll(datos.lineas().stream().map(this::construirFilaRecibo).toList());
                    actualizarRecibido();
                },
                error -> mostrarSinOrden()
        );
    }

    /** Se ejecuta en un hilo aparte (ver Async): todas las consultas a BD juntas, la UI se arma después. */
    private DatosRecibo construirDatosRecibo(int ordenId) {
        Orden orden = ordenDAO.obtenerPorId(ordenId).orElseThrow();
        Usuario cajero = usuarioDAO.obtenerPorId(orden.getUsuarioId()).orElse(null);

        List<LineaRecibo> lineas = new ArrayList<>();
        for (DetalleOrden detalle : detalleOrdenDAO.obtenerPorOrden(ordenId)) {
            Producto producto = productoDAO.obtenerPorId(detalle.getProductoId()).orElse(null);
            String nombre = producto == null ? "Producto #" + detalle.getProductoId() : producto.getNombre();
            lineas.add(new LineaRecibo(detalle.getCantidad() + "x  " + nombre, detalle.getSubtotal()));
        }
        return new DatosRecibo(orden, cajero, lineas);
    }

    private void mostrarEncabezado(Orden orden, Usuario cajero) {
        lblNumeroOrden.setText("Orden #" + orden.getId());
        lblFecha.setText(orden.getFechaCreacion().format(
                DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", new Locale("es", "MX"))));
        lblCajero.setText("Cajero: " + (cajero == null ? "—" : cajero.getNombre()));
        lblSubtotalCuenta.setText(Dinero.formatear(orden.getSubtotal()));
        lblImpuestosCuenta.setText(Dinero.formatear(orden.getImpuestos()));
        lblTotalCuenta.setText(Dinero.formatear(orden.getTotal()));
    }

    private HBox construirFilaRecibo(LineaRecibo linea) {
        Label lblNombre = new Label(linea.texto());
        lblNombre.getStyleClass().add("text-body");
        HBox.setHgrow(lblNombre, Priority.ALWAYS);

        Label lblMonto = new Label(Dinero.formatear(linea.monto()));
        lblMonto.getStyleClass().add("text-body");

        return new HBox(lblNombre, lblMonto);
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
                () -> registrarPago(ordenId, metodo, montoTotal),
                exito -> {
                    sesion.setOrdenEnProceso(null);
                    navigator.navigateTo(Pantalla.DASHBOARD);
                },
                error -> {
                    btnConfirmarPago.setDisable(false);
                    mostrarErrorPago("No se pudo confirmar el pago. Intenta de nuevo.");
                }
        );
    }

    /** Se ejecuta en un hilo aparte (ver Async): registra el pago y avanza el estado de la orden. */
    private Boolean registrarPago(int ordenId, MetodoPago metodo, BigDecimal monto) {
        Pago pago = new Pago();
        pago.setOrdenId(ordenId);
        pago.setMetodoPago(metodo);
        pago.setMonto(monto);
        pago.setFechaPago(LocalDateTime.now());
        pagoDAO.crear(pago);

        // Para COMEDOR/PARA_LLEVAR se cobra antes de preparar (ver CLAUDE.md): al pagar, pasa a cocina.
        Orden orden = ordenDAO.obtenerPorId(ordenId).orElseThrow();
        orden.setEstado(EstadoOrden.EN_PREPARACION);
        ordenDAO.actualizar(orden);
        return Boolean.TRUE;
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

    private record LineaRecibo(String texto, BigDecimal monto) {
    }

    private record DatosRecibo(Orden orden, Usuario cajero, List<LineaRecibo> lineas) {
    }
}
