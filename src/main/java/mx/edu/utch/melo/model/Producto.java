package mx.edu.utch.melo.model;

import java.math.BigDecimal;

/**
 * Entidad de persistencia: una fila de la tabla `productos`.
 * El precio usa BigDecimal (no double) porque es dinero -- evita errores de
 * redondeo por representación binaria de punto flotante.
 *
 * Es propio de una sucursal: el mismo platillo conceptual en dos
 * sucursales distintas es una fila por cada una, con su propio precio,
 * disponibilidad e inventario (ver CLAUDE.md, sección multi-sucursal).
 * tasaIva es por producto, no global. cantidadDisponible/stockMinimo
 * son el inventario real con alerta de stock bajo.
 */
public class Producto {

    private int id;
    private int sucursalId;
    private int categoriaId;
    private String nombre;
    private String descripcion;
    private BigDecimal precio;
    private BigDecimal tasaIva;
    private boolean disponible;
    private int cantidadDisponible;
    private int stockMinimo;
    private String imagenPath;

    public Producto() {
    }

    public Producto(int id, int sucursalId, int categoriaId, String nombre, String descripcion, BigDecimal precio,
                     BigDecimal tasaIva, boolean disponible, int cantidadDisponible, int stockMinimo,
                     String imagenPath) {
        this.id = id;
        this.sucursalId = sucursalId;
        this.categoriaId = categoriaId;
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.precio = precio;
        this.tasaIva = tasaIva;
        this.disponible = disponible;
        this.cantidadDisponible = cantidadDisponible;
        this.stockMinimo = stockMinimo;
        this.imagenPath = imagenPath;
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

    public int getCategoriaId() {
        return categoriaId;
    }

    public void setCategoriaId(int categoriaId) {
        this.categoriaId = categoriaId;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public BigDecimal getPrecio() {
        return precio;
    }

    public void setPrecio(BigDecimal precio) {
        this.precio = precio;
    }

    public BigDecimal getTasaIva() {
        return tasaIva;
    }

    public void setTasaIva(BigDecimal tasaIva) {
        this.tasaIva = tasaIva;
    }

    public boolean isDisponible() {
        return disponible;
    }

    public void setDisponible(boolean disponible) {
        this.disponible = disponible;
    }

    public int getCantidadDisponible() {
        return cantidadDisponible;
    }

    public void setCantidadDisponible(int cantidadDisponible) {
        this.cantidadDisponible = cantidadDisponible;
    }

    public int getStockMinimo() {
        return stockMinimo;
    }

    public void setStockMinimo(int stockMinimo) {
        this.stockMinimo = stockMinimo;
    }

    /** Ruta local del archivo de imagen (ver AlmacenImagenesProducto); null si el producto no tiene foto. */
    public String getImagenPath() {
        return imagenPath;
    }

    public void setImagenPath(String imagenPath) {
        this.imagenPath = imagenPath;
    }

    /** Para las alertas de stock bajo que pide CLAUDE.md. */
    public boolean tieneStockBajo() {
        return cantidadDisponible <= stockMinimo;
    }
}
