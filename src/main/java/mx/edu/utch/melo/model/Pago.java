package mx.edu.utch.melo.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Entidad de persistencia: una fila de la tabla `pagos`. Una orden puede tener varios (división de cuenta). */
public class Pago {

    private int id;
    private int ordenId;
    private MetodoPago metodoPago;
    private BigDecimal monto;
    private LocalDateTime fechaPago;

    public Pago() {
    }

    public Pago(int id, int ordenId, MetodoPago metodoPago, BigDecimal monto, LocalDateTime fechaPago) {
        this.id = id;
        this.ordenId = ordenId;
        this.metodoPago = metodoPago;
        this.monto = monto;
        this.fechaPago = fechaPago;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getOrdenId() {
        return ordenId;
    }

    public void setOrdenId(int ordenId) {
        this.ordenId = ordenId;
    }

    public MetodoPago getMetodoPago() {
        return metodoPago;
    }

    public void setMetodoPago(MetodoPago metodoPago) {
        this.metodoPago = metodoPago;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public void setMonto(BigDecimal monto) {
        this.monto = monto;
    }

    public LocalDateTime getFechaPago() {
        return fechaPago;
    }

    public void setFechaPago(LocalDateTime fechaPago) {
        this.fechaPago = fechaPago;
    }
}
