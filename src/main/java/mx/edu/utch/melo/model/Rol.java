package mx.edu.utch.melo.model;

/** Roles de usuario definidos en CLAUDE.md. Evita strings mágicos como "Cajero" repetidos en el código. */
public enum Rol {
    MESERO("Mesero"),
    CAJERO("Cajero"),
    COCINA("Cocina"),
    ADMINISTRADOR("Administrador");

    private final String etiqueta;

    Rol(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    /** Nombre en español para mostrar en pantalla (ej. Personal, Ajustes). */
    public String getEtiqueta() {
        return etiqueta;
    }
}
