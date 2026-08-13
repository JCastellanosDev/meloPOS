package mx.edu.utch.melo.model;

import java.math.BigDecimal;

/**
 * Entidad de persistencia: una fila de la tabla `sucursales`.
 * latitud/longitud son el origen para calcular la distancia de los
 * envíos a domicilio; tarifaBaseEnvio/tarifaPorKm son la fórmula de
 * cobro de esa sucursal (ver CLAUDE.md, sección Domicilios).
 * La dirección va estructurada (calle/numero/colonia/codigoPostal/
 * ciudad/estado/pais) en vez de un solo campo de texto libre -- ver
 * {@link #getDireccionCompleta()} para la versión de una sola línea,
 * usada para mostrar en pantalla o para geocodificar.
 */
public class Sucursal {

    private int id;
    private String nombre;
    private String calle;
    private String numero;
    private String colonia;
    private String codigoPostal;
    private String ciudad;
    private String estado;
    private String pais;
    private BigDecimal latitud;
    private BigDecimal longitud;
    private String telefono;
    private BigDecimal tarifaBaseEnvio;
    private BigDecimal tarifaPorKm;
    private boolean activa;
    private boolean aplicaIva;

    public Sucursal() {
    }

    public Sucursal(int id, String nombre, String calle, String numero, String colonia, String codigoPostal,
                     String ciudad, String estado, String pais, BigDecimal latitud, BigDecimal longitud,
                     String telefono, BigDecimal tarifaBaseEnvio, BigDecimal tarifaPorKm, boolean activa,
                     boolean aplicaIva) {
        this.id = id;
        this.nombre = nombre;
        this.calle = calle;
        this.numero = numero;
        this.colonia = colonia;
        this.codigoPostal = codigoPostal;
        this.ciudad = ciudad;
        this.estado = estado;
        this.pais = pais;
        this.latitud = latitud;
        this.longitud = longitud;
        this.telefono = telefono;
        this.tarifaBaseEnvio = tarifaBaseEnvio;
        this.tarifaPorKm = tarifaPorKm;
        this.activa = activa;
        this.aplicaIva = aplicaIva;
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

    public String getCalle() {
        return calle;
    }

    public void setCalle(String calle) {
        this.calle = calle;
    }

    public String getNumero() {
        return numero;
    }

    public void setNumero(String numero) {
        this.numero = numero;
    }

    public String getColonia() {
        return colonia;
    }

    public void setColonia(String colonia) {
        this.colonia = colonia;
    }

    public String getCodigoPostal() {
        return codigoPostal;
    }

    public void setCodigoPostal(String codigoPostal) {
        this.codigoPostal = codigoPostal;
    }

    public String getCiudad() {
        return ciudad;
    }

    public void setCiudad(String ciudad) {
        this.ciudad = ciudad;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public String getPais() {
        return pais;
    }

    public void setPais(String pais) {
        this.pais = pais;
    }

    /** Dirección completa en una sola línea, para mostrar en pantalla o para geocodificar. */
    public String getDireccionCompleta() {
        return calle + " " + numero + ", " + colonia + ", " + codigoPostal + " " + ciudad + ", " + estado + ", " + pais;
    }

    public BigDecimal getLatitud() {
        return latitud;
    }

    public void setLatitud(BigDecimal latitud) {
        this.latitud = latitud;
    }

    public BigDecimal getLongitud() {
        return longitud;
    }

    public void setLongitud(BigDecimal longitud) {
        this.longitud = longitud;
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }

    public BigDecimal getTarifaBaseEnvio() {
        return tarifaBaseEnvio;
    }

    public void setTarifaBaseEnvio(BigDecimal tarifaBaseEnvio) {
        this.tarifaBaseEnvio = tarifaBaseEnvio;
    }

    public BigDecimal getTarifaPorKm() {
        return tarifaPorKm;
    }

    public void setTarifaPorKm(BigDecimal tarifaPorKm) {
        this.tarifaPorKm = tarifaPorKm;
    }

    public boolean isActiva() {
        return activa;
    }

    public void setActiva(boolean activa) {
        this.activa = activa;
    }

    /** Si esta sucursal cobra IVA o no -- cada restaurante decide (ver CLAUDE.md, sección Precios e IVA). */
    public boolean isAplicaIva() {
        return aplicaIva;
    }

    public void setAplicaIva(boolean aplicaIva) {
        this.aplicaIva = aplicaIva;
    }
}
