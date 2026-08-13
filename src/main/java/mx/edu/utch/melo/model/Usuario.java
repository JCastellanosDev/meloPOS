package mx.edu.utch.melo.model;


public class Usuario {

    private int id;
    private int sucursalId;
    private String nombre;
    private String pinAcceso;
    private Rol rol;
    private boolean activo;

    public Usuario() {
    }

    public Usuario(int id, int sucursalId, String nombre, String pinAcceso, Rol rol, boolean activo) {
        this.id = id;
        this.sucursalId = sucursalId;
        this.nombre = nombre;
        this.pinAcceso = pinAcceso;
        this.rol = rol;
        this.activo = activo;
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

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getPinAcceso() {
        return pinAcceso;
    }

    public void setPinAcceso(String pinAcceso) {
        this.pinAcceso = pinAcceso;
    }

    public Rol getRol() {
        return rol;
    }

    public void setRol(Rol rol) {
        this.rol = rol;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }
}
