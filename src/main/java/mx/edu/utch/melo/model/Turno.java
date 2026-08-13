package mx.edu.utch.melo.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;


public class Turno {

    private int id;
    private int sucursalId;
    private int usuarioId;
    private BigDecimal montoApertura;
    private BigDecimal montoCierreContado;
    private LocalDateTime fechaApertura;
    private LocalDateTime fechaCierre;

    public Turno() {
    }

    public Turno(int id, int sucursalId, int usuarioId, BigDecimal montoApertura,
                 BigDecimal montoCierreContado, LocalDateTime fechaApertura, LocalDateTime fechaCierre) {
        this.id = id;
        this.sucursalId = sucursalId;
        this.usuarioId = usuarioId;
        this.montoApertura = montoApertura;
        this.montoCierreContado = montoCierreContado;
        this.fechaApertura = fechaApertura;
        this.fechaCierre = fechaCierre;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getSucursalId() {
        return sucursalId;
    }

    public void setSucursalId(int sucursalId) {
        this.sucursalId = sucursalId;
    }

    public int getUsuarioId() {
        return usuarioId;
    }

    public void setUsuarioId(int usuarioId) {
        this.usuarioId = usuarioId;
    }

    public BigDecimal getMontoApertura() {
        return montoApertura;
    }

    public void setMontoApertura(BigDecimal montoApertura) {
        this.montoApertura = montoApertura;
    }

    public BigDecimal getMontoCierreContado() {
        return montoCierreContado;
    }

    public void setMontoCierreContado(BigDecimal montoCierreContado) {
        this.montoCierreContado = montoCierreContado;
    }

    public LocalDateTime getFechaApertura() {
        return fechaApertura;
    }

    public void setFechaApertura(LocalDateTime fechaApertura) {
        this.fechaApertura = fechaApertura;
    }

    public LocalDateTime getFechaCierre() {
        return fechaCierre;
    }

    public void setFechaCierre(LocalDateTime fechaCierre) {
        this.fechaCierre = fechaCierre;
    }

    public boolean estaAbierto() {
        return fechaCierre == null;
    }
}
