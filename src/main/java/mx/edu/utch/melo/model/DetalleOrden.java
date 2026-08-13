package mx.edu.utch.melo.model;

import java.math.BigDecimal;


public class DetalleOrden {

    private int id;
    private int ordenId;
    private int productoId;
    private int cantidad;
    private BigDecimal precioUnitario;
    private String nota;

    public DetalleOrden() {
    }

    public DetalleOrden(int id, int ordenId, int productoId, int cantidad, BigDecimal precioUnitario, String nota) {
        this.id = id;
        this.ordenId = ordenId;
        this.productoId = productoId;
        this.cantidad = cantidad;
        this.precioUnitario = precioUnitario;
        this.nota = nota;
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

    public int getProductoId() {
        return productoId;
    }

    public void setProductoId(int productoId) {
        this.productoId = productoId;
    }

    public int getCantidad() {
        return cantidad;
    }

    public void setCantidad(int cantidad) {
        this.cantidad = cantidad;
    }

    public BigDecimal getPrecioUnitario() {
        return precioUnitario;
    }

    public void setPrecioUnitario(BigDecimal precioUnitario) {
        this.precioUnitario = precioUnitario;
    }

    /** Instrucción para cocina sobre este artículo (ej. "sin cebolla"); null si no se capturó ninguna. */
    public String getNota() {
        return nota;
    }

    public void setNota(String nota) {
        this.nota = nota;
    }

    /** cantidad * precioUnitario -- no se guarda en base de datos porque ya es calculable de esta misma fila. */
    public BigDecimal getSubtotal() {
        return precioUnitario.multiply(BigDecimal.valueOf(cantidad));
    }
}
