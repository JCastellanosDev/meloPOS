package mx.edu.utch.melo.model;

import java.math.BigDecimal;

/**
 * Representa una fila de `detalle_orden_modificador`: un modificador ya
 * elegido para un artículo de una orden, con el precio que tenía en
 * ese momento (snapshot). No es lo mismo que {@link Modificador}: ese
 * es el catálogo (precio actual); este es el histórico de una venta.
 */
public class ModificadorAplicado {

    private int detalleOrdenId;
    private int modificadorId;
    private String nombre;
    private BigDecimal precioExtra;

    public ModificadorAplicado() {
    }

    public ModificadorAplicado(int detalleOrdenId, int modificadorId, String nombre, BigDecimal precioExtra) {
        this.detalleOrdenId = detalleOrdenId;
        this.modificadorId = modificadorId;
        this.nombre = nombre;
        this.precioExtra = precioExtra;
    }

    public int getDetalleOrdenId() {
        return detalleOrdenId;
    }

    public void setDetalleOrdenId(int detalleOrdenId) {
        this.detalleOrdenId = detalleOrdenId;
    }

    public int getModificadorId() {
        return modificadorId;
    }

    public void setModificadorId(int modificadorId) {
        this.modificadorId = modificadorId;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public BigDecimal getPrecioExtra() {
        return precioExtra;
    }

    public void setPrecioExtra(BigDecimal precioExtra) {
        this.precioExtra = precioExtra;
    }
}
