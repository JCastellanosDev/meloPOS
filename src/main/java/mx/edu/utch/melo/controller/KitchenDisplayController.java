package mx.edu.utch.melo.controller;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
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

    /** Cuadrícula fija de 4 columnas (ver KitchenDisplay.fxml, columnConstraints) -- cada ticket
     * se ubica por columna/fila según su posición en la lista (ver renderizarTickets), para que
     * quepan 8 a la vista (2 filas x 4 columnas) antes de necesitar scroll. */
    private static final int COLUMNAS = 4;

    @FXML
    private Label lblClock;

    @FXML
    private Label lblActivas;

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
     * Acomoda los tickets en la cuadrícula de 4 columnas. La orden ampliada (ver
     * {@link #ordenAmpliadaId}) reclama las 2 filas de su columna (GridPane.setRowSpan) -- crece
     * hacia abajo si su lugar natural era la fila de arriba, o "hacia arriba" si era la de abajo,
     * ya que en cualquier caso termina ocupando ambas. La orden que antes estaba en la otra fila
     * de esa misma columna no desaparece: se reacomoda en el siguiente lugar libre, igual que
     * cualquier otro ticket (si eso empuja el total más allá de 8, las filas de más se ven bajando
     * el scroll vertical, el mismo límite conocido de antes).
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
                    columnaAmpliado = i % COLUMNAS;
                    break;
                }
            }
        }

        if (ordenAmpliadaId != null && ampliado == null) {
            // La orden ampliada ya no está activa (se completó desde otra terminal, por ejemplo).
            ordenAmpliadaId = null;
        }

        boolean[][] ocupado = new boolean[datos.size() + 2][COLUMNAS];

        if (ampliado != null) {
            colocarTicket(ampliado, columnaAmpliado, 0, 2, ocupado);
        }

        int cursor = 0;
        for (DatosTicket dato : pendientes) {
            while (ocupado[cursor / COLUMNAS][cursor % COLUMNAS]) {
                cursor++;
            }
            colocarTicket(dato, cursor % COLUMNAS, cursor / COLUMNAS, 1, ocupado);
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

    /** Se ejecuta en un hilo aparte (ver Async): avanza la orden a ENTREGADA. */
    private Boolean marcarEntregada(int ordenId) {
        Orden orden = ordenDAO.obtenerPorId(ordenId).orElseThrow();
        orden.setEstado(EstadoOrden.ENTREGADA);
        ordenDAO.actualizar(orden);
        return Boolean.TRUE;
    }

    private void mostrarErrorCarga() {
        Label mensaje = new Label("No se pudo cargar la cocina. Revisa la conexión con la base de datos.");
        mensaje.getStyleClass().add("form-error");
        contenedorTickets.getChildren().clear();
        contenedorTickets.add(mensaje, 0, 0);
        GridPane.setColumnSpan(mensaje, COLUMNAS);
    }

    private record DatosTicket(Orden orden, List<LineaTicket> lineas) {
    }
}
