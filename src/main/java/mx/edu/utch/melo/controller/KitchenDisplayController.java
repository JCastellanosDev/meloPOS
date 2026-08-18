package mx.edu.utch.melo.controller;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.util.Duration;
import mx.edu.utch.melo.async.Async;
import mx.edu.utch.melo.dao.DetalleOrdenDAO;
import mx.edu.utch.melo.dao.OrdenDAO;
import mx.edu.utch.melo.dao.ProductoDAO;
import mx.edu.utch.melo.model.DetalleOrden;
import mx.edu.utch.melo.model.EstadoOrden;
import mx.edu.utch.melo.model.Orden;
import mx.edu.utch.melo.model.Producto;
import mx.edu.utch.melo.view.TicketFactory;
import mx.edu.utch.melo.view.TicketFactory.LineaTicket;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class KitchenDisplayController {

    private static final DateTimeFormatter FORMATO_RELOJ = DateTimeFormatter.ofPattern("HH:mm");

    private static final Duration INTERVALO_ACTUALIZACION = Duration.seconds(1);

    /** Densidad de cuadrícula por defecto al abrir la pantalla (2 filas x 4 columnas = 8 tickets). */
    private static final int COLUMNAS_INICIAL = 4;

    /** Tiers de tamaño de texto global (ver cambiarNivelTamano): null = tamaño base del CSS, sin
     * clase extra. Mismo patrón que el "ampliado" por ticket, pero aplicado a toda la pantalla a
     * la vez agregando la clase al contenedor (contenedorTickets) -- el selector descendiente de
     * CSS cascada hacia cada ticket hijo sin tocar TicketFactory. */
    private static final String[] CLASES_TAMANO = {null, "kds-size-grande", "kds-size-muy-grande"};
    private static final String[] ETIQUETAS_TAMANO = {"Normal", "Grande", "Muy grande"};

    @FXML
    private Label lblClock;

    @FXML
    private Label lblActivas;

    @FXML
    private Label lblNivelTexto;

    @FXML
    private Button btnDensidad3;

    @FXML
    private Button btnDensidad4;

    @FXML
    private Button btnDensidad6;

    @FXML
    private GridPane contenedorTickets;

    private final OrdenDAO ordenDAO;
    private final DetalleOrdenDAO detalleOrdenDAO;
    private final ProductoDAO productoDAO;

    /** Evita disparar una segunda carga mientras la anterior sigue en vuelo (ver cargarTickets). */
    private boolean cargando;
    /** Si ya se mostró al menos un ticket con éxito, un fallo pasajero de actualización no debe
     * borrar la pantalla -- solo se reintenta en el siguiente ciclo (ver cargarTickets). */
    private boolean cargaInicialCompletada;

    /** Columnas actuales de la cuadrícula (2 filas fijas, ver aplicarColumnas) -- configurable en
     * tiempo de ejecución con los botones de densidad, por eso ya no es un static final. */
    private int columnas = COLUMNAS_INICIAL;
    /** Índice actual en CLASES_TAMANO/ETIQUETAS_TAMANO. */
    private int nivelTamanoTexto = 0;

    /** Últimos datos ya cargados de la base de datos -- ampliar/tachar son puramente visuales
     * (ver más abajo) y no deben esperar al siguiente sondeo de un segundo para reflejarse. */
    private List<DatosTicket> ultimosDatos = List.of();
    /** Id de la orden ocupando ahora mismo las 2 filas de su columna; null si ninguna. Solo una a
     * la vez, para no complicar el acomodo del resto (ver renderizarTickets). */
    private Integer ordenAmpliadaId;
    /** Índices de línea que el cocinero ya tachó como preparados, por orden -- estado visual de
     * esta sesión de KitchenDisplay, no se persiste en base de datos (ver CLAUDE.md: no se pidió
     * un estado de "producto preparado" en el modelo). Se limpia al completar la orden. */
    private final Map<Integer, Set<Integer>> lineasTachadasPorOrden = new HashMap<>();

    public KitchenDisplayController(OrdenDAO ordenDAO, DetalleOrdenDAO detalleOrdenDAO, ProductoDAO productoDAO) {
        this.ordenDAO = ordenDAO;
        this.detalleOrdenDAO = detalleOrdenDAO;
        this.productoDAO = productoDAO;
    }

    @FXML
    void initialize() {
        iniciarReloj();
        actualizarBotonesDensidad();
        cargarTickets();
        iniciarActualizacionAutomatica();
    }

    /** Ventana siempre encendida en cocina: el reloj real ayuda a estimar tiempos a simple vista. */
    private void iniciarReloj() {
        actualizarReloj();
        Timeline reloj = new Timeline(new KeyFrame(Duration.seconds(1), evento -> actualizarReloj()));
        reloj.setCycleCount(Animation.INDEFINITE);
        reloj.play();
    }

    private void actualizarReloj() {
        lblClock.setText(LocalTime.now().format(FORMATO_RELOJ));
    }

    private void iniciarActualizacionAutomatica() {
        Timeline actualizador = new Timeline(new KeyFrame(INTERVALO_ACTUALIZACION, evento -> cargarTickets()));
        actualizador.setCycleCount(Animation.INDEFINITE);
        actualizador.play();
    }

    private void cargarTickets() {
        if (cargando) {
            return;
        }
        cargando = true;
        Async.ejecutar(
                this::construirDatosTickets,
                datos -> {
                    cargando = false;
                    cargaInicialCompletada = true;
                    renderizarTickets(datos);
                },
                error -> {
                    cargando = false;
                    if (!cargaInicialCompletada) {
                        mostrarErrorCarga();
                    }
                }
        );
    }

    /** Se ejecuta en un hilo aparte (ver Async): todas las consultas a BD juntas, la UI se arma después. */
    private List<DatosTicket> construirDatosTickets() {
        List<DatosTicket> resultado = new ArrayList<>();
        for (Orden orden : ordenDAO.obtenerPorEstado(EstadoOrden.EN_PREPARACION)) {
            List<LineaTicket> lineas = new ArrayList<>();
            for (DetalleOrden detalle : detalleOrdenDAO.obtenerPorOrden(orden.getId())) {
                Producto producto = productoDAO.obtenerPorId(detalle.getProductoId()).orElse(null);
                String nombre = producto == null ? "Producto #" + detalle.getProductoId() : producto.getNombre();
                lineas.add(new LineaTicket(detalle.getCantidad() + "x " + nombre, detalle.getNota()));
            }
            resultado.add(new DatosTicket(orden, lineas));
        }
        return resultado;
    }

    /**
     * Acomoda los tickets en la cuadrícula de {@link #columnas} columnas (2 filas fijas). La
     * orden ampliada (ver {@link #ordenAmpliadaId}) reclama las 2 filas de su columna
     * (GridPane.setRowSpan) -- crece hacia abajo si su lugar natural era la fila de arriba, o
     * "hacia arriba" si era la de abajo, ya que en cualquier caso termina ocupando ambas. La
     * orden que antes estaba en la otra fila de esa misma columna no desaparece: se reacomoda en
     * el siguiente lugar libre, igual que cualquier otro ticket (si eso empuja el total más allá
     * de lo visible, las filas de más se ven bajando el scroll vertical, el mismo límite conocido
     * de antes).
     */
    private void renderizarTickets(List<DatosTicket> datos) {
        ultimosDatos = datos;
        contenedorTickets.getChildren().clear();

        List<DatosTicket> pendientes = new ArrayList<>(datos);
        DatosTicket ampliado = null;
        int columnaAmpliado = -1;
        if (ordenAmpliadaId != null) {
            for (int i = 0; i < pendientes.size(); i++) {
                if (pendientes.get(i).orden().getId() == ordenAmpliadaId) {
                    ampliado = pendientes.remove(i);
                    columnaAmpliado = i % columnas;
                    break;
                }
            }
        }

        if (ordenAmpliadaId != null && ampliado == null) {
            // La orden ampliada ya no está activa (se completó desde otra terminal, por ejemplo).
            ordenAmpliadaId = null;
        }

        boolean[][] ocupado = new boolean[datos.size() + 2][columnas];

        if (ampliado != null) {
            colocarTicket(ampliado, columnaAmpliado, 0, 2, ocupado);
        }

        int cursor = 0;
        for (DatosTicket dato : pendientes) {
            while (ocupado[cursor / columnas][cursor % columnas]) {
                cursor++;
            }
            colocarTicket(dato, cursor % columnas, cursor / columnas, 1, ocupado);
            cursor++;
        }

        lblActivas.setText(datos.size() + " Activas");
    }

    private void colocarTicket(DatosTicket datoTicket, int columna, int fila, int filasQueOcupa, boolean[][] ocupado) {
        int ordenId = datoTicket.orden().getId();
        boolean ampliado = filasQueOcupa > 1;
        Set<Integer> lineasTachadas = lineasTachadasPorOrden.getOrDefault(ordenId, Set.of());

        Node ticket = TicketFactory.crear(
                datoTicket.orden(),
                datoTicket.lineas(),
                () -> completarPedido(ordenId),
                () -> alternarAmpliada(ordenId),
                ampliado,
                lineasTachadas,
                indice -> alternarTachado(ordenId, indice));
        GridPane.setHgrow(ticket, Priority.ALWAYS);
        GridPane.setVgrow(ticket, Priority.ALWAYS);
        GridPane.setRowSpan(ticket, filasQueOcupa);
        contenedorTickets.add(ticket, columna, fila);

        for (int f = fila; f < fila + filasQueOcupa; f++) {
            ocupado[f][columna] = true;
        }
    }

    /** Alterna la orden ampliada: si ya era esta, la vuelve a tamaño normal. Solo una a la vez. */
    private void alternarAmpliada(int ordenId) {
        ordenAmpliadaId = Objects.equals(ordenAmpliadaId, ordenId) ? null : ordenId;
        renderizarTickets(ultimosDatos);
    }

    /** Puramente visual (ver lineasTachadasPorOrden) -- no toca la base de datos. */
    private void alternarTachado(int ordenId, int indiceLinea) {
        Set<Integer> lineas = lineasTachadasPorOrden.computeIfAbsent(ordenId, id -> new HashSet<>());
        if (!lineas.add(indiceLinea)) {
            lineas.remove(indiceLinea);
        }
        renderizarTickets(ultimosDatos);
    }

    private void completarPedido(int ordenId) {
        Async.ejecutar(
                () -> marcarEntregada(ordenId),
                exito -> {
                    lineasTachadasPorOrden.remove(ordenId);
                    if (Objects.equals(ordenAmpliadaId, ordenId)) {
                        ordenAmpliadaId = null;
                    }
                    cargarTickets();
                },
                error -> mostrarErrorCarga()
        );
    }

    /** Se ejecuta en un hilo aparte (ver Async): avanza la orden al siguiente estado según su
     * canal (ver CLAUDE.md, "Flujo de un pedido"). COMEDOR/PARA_LLEVAR ya se cobraron antes de
     * preparar, así que "Completar Pedido" aquí sí es el final: ENTREGADA. DOMICILIO/PARA_RECOGER
     * todavía no se cobran -- pasan a LISTA, no ENTREGADA, para que sigan visibles con su botón de
     * cobro en DeliveryView (que ya excluye ENTREGADA de "Pedidos Activos") hasta que de verdad se
     * paguen (ver PagoService, que las manda a ENTREGADA al cobrar). */
    private Boolean marcarEntregada(int ordenId) {
        Orden orden = ordenDAO.obtenerPorId(ordenId).orElseThrow();
        EstadoOrden siguiente = switch (orden.getTipoOrden()) {
            case DOMICILIO, PARA_RECOGER -> EstadoOrden.LISTA;
            case COMEDOR, PARA_LLEVAR -> EstadoOrden.ENTREGADA;
        };
        orden.setEstado(siguiente);
        ordenDAO.actualizar(orden);
        return Boolean.TRUE;
    }

    private void mostrarErrorCarga() {
        Label mensaje = new Label("No se pudo cargar la cocina. Revisa la conexión con la base de datos.");
        mensaje.getStyleClass().add("form-error");
        contenedorTickets.getChildren().clear();
        contenedorTickets.add(mensaje, 0, 0);
        GridPane.setColumnSpan(mensaje, columnas);
    }

    @FXML
    void onDensidad3() {
        aplicarColumnas(3);
    }

    @FXML
    void onDensidad4() {
        aplicarColumnas(4);
    }

    @FXML
    void onDensidad6() {
        aplicarColumnas(6);
    }

    /** Reconstruye columnConstraints (FXML es estático, ver KitchenDisplay.fxml) y vuelve a
     * acomodar los tickets ya cargados con la nueva densidad, sin esperar al siguiente sondeo. */
    private void aplicarColumnas(int nuevasColumnas) {
        if (nuevasColumnas == columnas) {
            return;
        }
        columnas = nuevasColumnas;
        contenedorTickets.getColumnConstraints().clear();
        for (int i = 0; i < columnas; i++) {
            ColumnConstraints columna = new ColumnConstraints();
            columna.setPercentWidth(100.0 / columnas);
            contenedorTickets.getColumnConstraints().add(columna);
        }
        actualizarBotonesDensidad();
        renderizarTickets(ultimosDatos);
    }

    private void actualizarBotonesDensidad() {
        btnDensidad3.getStyleClass().remove("kds-toolbar-btn-active");
        btnDensidad4.getStyleClass().remove("kds-toolbar-btn-active");
        btnDensidad6.getStyleClass().remove("kds-toolbar-btn-active");
        Button activo = switch (columnas) {
            case 3 -> btnDensidad3;
            case 6 -> btnDensidad6;
            default -> btnDensidad4;
        };
        activo.getStyleClass().add("kds-toolbar-btn-active");
    }

    @FXML
    void onDisminuirTexto() {
        cambiarNivelTamano(-1);
    }

    @FXML
    void onAumentarTexto() {
        cambiarNivelTamano(1);
    }

    /** Aplica el tier de tamaño como clase CSS en contenedorTickets (ver CLASES_TAMANO): al ser
     * el padre de todos los tickets, el selector descendiente de styles.css cascada a cada uno
     * sin tener que tocar TicketFactory ni volver a renderizar. */
    private void cambiarNivelTamano(int delta) {
        int nuevoNivel = Math.max(0, Math.min(CLASES_TAMANO.length - 1, nivelTamanoTexto + delta));
        if (nuevoNivel == nivelTamanoTexto) {
            return;
        }
        if (CLASES_TAMANO[nivelTamanoTexto] != null) {
            contenedorTickets.getStyleClass().remove(CLASES_TAMANO[nivelTamanoTexto]);
        }
        nivelTamanoTexto = nuevoNivel;
        if (CLASES_TAMANO[nivelTamanoTexto] != null) {
            contenedorTickets.getStyleClass().add(CLASES_TAMANO[nivelTamanoTexto]);
        }
        lblNivelTexto.setText(ETIQUETAS_TAMANO[nivelTamanoTexto]);
    }

    private record DatosTicket(Orden orden, List<LineaTicket> lineas) {
    }
}
