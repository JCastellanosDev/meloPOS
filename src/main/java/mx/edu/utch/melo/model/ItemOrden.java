package mx.edu.utch.melo.model;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;

import java.util.List;

/**
 * Artículo del carrito en memoria de MenuPOS (sin persistencia hasta que
 * se cobra la cuenta). productoId enlaza con el {@link Producto} real en
 * base de datos -- lo necesita MenuPOSController para crear el
 * {@link DetalleOrden} correspondiente al momento de cobrar.
 */
public class ItemOrden {

    private final int productoId;
    private final String nombre;
    private final double precioUnitario;
    private final IntegerProperty cantidad;
    private final List<String> modificadores;
    private String nota = "";

    public ItemOrden(int productoId, String nombre, double precioUnitario, int cantidadInicial, List<String> modificadores) {
        this.productoId = productoId;
        this.nombre = nombre;
        this.precioUnitario = precioUnitario;
        this.cantidad = new SimpleIntegerProperty(cantidadInicial);
        this.modificadores = modificadores;
    }

    public ItemOrden(int productoId, String nombre, double precioUnitario, int cantidadInicial) {
        this(productoId, nombre, precioUnitario, cantidadInicial, List.of());
    }

    public int getProductoId() {
        return productoId;
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

    /** Instrucción para cocina sobre este artículo (ej. "sin cebolla"); cadena vacía si no se capturó ninguna. */
    public String getNota() {
        return nota;
    }

    public void setNota(String nota) {
        this.nota = nota == null ? "" : nota;
    }

    public double getSubtotal() {
        return precioUnitario * getCantidad();
    }
}
