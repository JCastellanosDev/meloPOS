package mx.edu.utch.melo.model;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;

import java.util.List;

/** Artículo de una orden en memoria (sin persistencia). */
public class ItemOrden {

    private final String nombre;
    private final double precioUnitario;
    private final IntegerProperty cantidad;
    private final List<String> modificadores;

    public ItemOrden(String nombre, double precioUnitario, int cantidadInicial, List<String> modificadores) {
        this.nombre = nombre;
        this.precioUnitario = precioUnitario;
        this.cantidad = new SimpleIntegerProperty(cantidadInicial);
        this.modificadores = modificadores;
    }

    public ItemOrden(String nombre, double precioUnitario, int cantidadInicial) {
        this(nombre, precioUnitario, cantidadInicial, List.of());
    }

    public String getNombre() {
        return nombre;
    }

    public double getPrecioUnitario() {
        return precioUnitario;
    }

    public int getCantidad() {
        return cantidad.get();
    }

    public IntegerProperty cantidadProperty() {
        return cantidad;
    }

    public List<String> getModificadores() {
        return modificadores;
    }

    public double getSubtotal() {
        return precioUnitario * getCantidad();
    }
}
