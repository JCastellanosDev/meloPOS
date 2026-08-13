package mx.edu.utch.melo.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entidad de persistencia: una fila de la tabla `ordenes`.
 *
 * No confundir con {@code ItemOrden} (paquete model, usado por MenuPOS): esa
 * clase es el estado en memoria del carrito de una pantalla, con propiedades
 * JavaFX observables y sin id de base de datos. Esta clase es el registro
 * persistido de la orden ya tomada.
 *
 * mesaId, clienteId y turnoId son nullable: no toda orden tiene mesa
 * (solo COMEDOR), ni cliente registrado, ni turno abierto asociado
 * todavía (la apertura de turno no está implementada en la UI).
 * distanciaKm es nullable: solo aplica a DOMICILIO. subtotal/impuestos/
 * costoEnvio/total son snapshot de lo realmente cobrado -- ver el
 * comentario en db/schema.sql sobre por qué esto no es la duplicidad
 * que se evitó en el resto del esquema.
 *
 * Esta clase NO lleva sucursalId: se deriva de usuarioId -> Usuario.sucursalId.
 */
public class Orden {

    private int id;
    private TipoOrden tipoOrden;
    private Integer mesaId;
    private int usuarioId;
    private Integer clienteId;
    private Integer turnoId;
    private EstadoOrden estado;
    private BigDecimal subtotal;
    private BigDecimal impuestos;
    private BigDecimal distanciaKm;
    private BigDecimal costoEnvio;
    private BigDecimal total;
    private LocalDateTime fechaCreacion;

    public Orden() {
    }

    public Orden(int id, TipoOrden tipoOrden, Integer mesaId, int usuarioId, Integer clienteId, Integer turnoId,
                 EstadoOrden estado, BigDecimal subtotal, BigDecimal impuestos, BigDecimal distanciaKm,
                 BigDecimal costoEnvio, BigDecimal total, LocalDateTime fechaCreacion) {
        this.id = id;
        this.tipoOrden = tipoOrden;
        this.mesaId = mesaId;
        this.usuarioId = usuarioId;
        this.clienteId = clienteId;
        this.turnoId = turnoId;
        this.estado = estado;
        this.subtotal = subtotal;
        this.impuestos = impuestos;
        this.distanciaKm = distanciaKm;
        this.costoEnvio = costoEnvio;
        this.total = total;
        this.fechaCreacion = fechaCreacion;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public TipoOrden getTipoOrden() {
        return tipoOrden;
    }

    public void setTipoOrden(TipoOrden tipoOrden) {
        this.tipoOrden = tipoOrden;
    }

    public Integer getMesaId() {
        return mesaId;
    }

    public void setMesaId(Integer mesaId) {
        this.mesaId = mesaId;
    }

    public int getUsuarioId() {
        return usuarioId;
    }

    public void setUsuarioId(int usuarioId) {
        this.usuarioId = usuarioId;
    }

    public Integer getClienteId() {
        return clienteId;
    }

    public void setClienteId(Integer clienteId) {
        this.clienteId = clienteId;
    }

    public Integer getTurnoId() {
        return turnoId;
    }

    public void setTurnoId(Integer turnoId) {
        this.turnoId = turnoId;
    }

    public EstadoOrden getEstado() {
        return estado;
    }

    public void setEstado(EstadoOrden estado) {
        this.estado = estado;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }

    public BigDecimal getImpuestos() {
        return impuestos;
    }

    public void setImpuestos(BigDecimal impuestos) {
        this.impuestos = impuestos;
    }

    public BigDecimal getDistanciaKm() {
        return distanciaKm;
    }

    public void setDistanciaKm(BigDecimal distanciaKm) {
        this.distanciaKm = distanciaKm;
    }

    public BigDecimal getCostoEnvio() {
        return costoEnvio;
    }

    public void setCostoEnvio(BigDecimal costoEnvio) {
        this.costoEnvio = costoEnvio;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(LocalDateTime fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }
}
