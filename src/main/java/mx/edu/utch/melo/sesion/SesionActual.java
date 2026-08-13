package mx.edu.utch.melo.sesion;

import mx.edu.utch.melo.model.Usuario;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Estado de la terminal mientras la app está abierta: quién la está
 * usando, y (si aplica) qué orden o qué cliente se está armando ahora
 * mismo en una pantalla antes de continuar en otra -- Navigator solo
 * sabe cambiar de pantalla, no pasar datos entre ellas, así que ese
 * estado en proceso viaja aquí en vez de como parámetro de navegación.
 *
 * Objeto normal (no singleton estático): se construye una vez en
 * HelloApplication y se inyecta a través de AppContext, igual que los DAO.
 *
 * Quién está usando la terminal: hoy no existe pantalla de login por
 * PIN, así que HelloApplication inicia sesión automáticamente con un
 * usuario semilla al arrancar (ver db/schema.sql) -- cuando exista
 * login real, {@link #iniciarSesion} se llama desde ahí en vez de
 * desde el arranque de la app.
 */
public class SesionActual {

    private Usuario usuarioActivo;
    private Integer ordenEnProcesoId;
    private Integer clienteEnProcesoId;
    private BigDecimal distanciaKmEnProceso;

    public void iniciarSesion(Usuario usuario) {
        this.usuarioActivo = usuario;
    }

    public Usuario getUsuarioActivo() {
        if (usuarioActivo == null) {
            throw new IllegalStateException("No hay una sesión iniciada todavía.");
        }
        return usuarioActivo;
    }

    public int getSucursalActivaId() {
        return getUsuarioActivo().getSucursalId();
    }

    public void setOrdenEnProceso(Integer ordenId) {
        this.ordenEnProcesoId = ordenId;
    }

    public Optional<Integer> getOrdenEnProceso() {
        return Optional.ofNullable(ordenEnProcesoId);
    }

    /** Cliente para el que PedidosController está armando un pedido, antes de abrir MenuPedido (ver Pantalla.MENU_PEDIDO). */
    public void setClienteEnProceso(Integer clienteId) {
        this.clienteEnProcesoId = clienteId;
    }

    public Optional<Integer> getClienteEnProceso() {
        return Optional.ofNullable(clienteEnProcesoId);
    }

    /** Distancia real calculada en Pedidos (ver PedidosController.onUbicar), null si no se calculó ninguna ruta. */
    public void setDistanciaKmEnProceso(BigDecimal distanciaKm) {
        this.distanciaKmEnProceso = distanciaKm;
    }

    public Optional<BigDecimal> getDistanciaKmEnProceso() {
        return Optional.ofNullable(distanciaKmEnProceso);
    }
}
