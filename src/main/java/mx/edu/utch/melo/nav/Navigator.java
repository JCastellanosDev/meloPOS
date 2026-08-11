package mx.edu.utch.melo.nav;

/**
 * Abstracción de navegación. Los controladores dependen de esta interfaz,
 * no de la implementación concreta (Principio de Inversión de Dependencias).
 */
public interface Navigator {

    void navigateTo(Pantalla pantalla);
}
