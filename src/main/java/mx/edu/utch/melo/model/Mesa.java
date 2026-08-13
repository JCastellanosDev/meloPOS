package mx.edu.utch.melo.model;

/** Entidad de persistencia: una fila de la tabla `mesas`. Propia de una sucursal (mismo número puede repetirse en otra). */
public class Mesa {

    private int id;
    private int sucursalId;
    private int numero;
    private Integer capacidad;

    public Mesa() {
    }

    public Mesa(int id, int sucursalId, int numero, Integer capacidad) {
        this.id = id;
        this.sucursalId = sucursalId;
        this.numero = numero;
        this.capacidad = capacidad;
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

    public int getNumero() {
        return numero;
    }

    public void setNumero(int numero) {
        this.numero = numero;
    }

    public Integer getCapacidad() {
        return capacidad;
    }

    public void setCapacidad(Integer capacidad) {
        this.capacidad = capacidad;
    }
}
