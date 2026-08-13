package mx.edu.utch.melo.model;

import java.math.BigDecimal;

/** Entidad de persistencia: una fila de la tabla `modificadores`. Catálogo compartido por toda la cadena. */
public class Modificador {

    private int id;
    private String nombre;
    private BigDecimal precioExtra;

    public Modificador() {
    }

    public Modificador(int id, String nombre, BigDecimal precioExtra) {
        this.id = id;
        this.nombre = nombre;
        this.precioExtra = precioExtra;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
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
