package mx.edu.utch.melo.nav;

/**
 * Abstracción de navegación. Los controladores dependen de esta interfaz,
 * no de la implementación concreta (Principio de Inversión de Dependencias).
 */
public interface Navigator {

    void navigateTo(Pantalla pantalla);

    /**
     * Abre una pantalla en una ventana (Stage) independiente, en vez de
     * reemplazar la raíz de la ventana principal -- para pantallas pensadas
     * para operar fuera del flujo normal de la app (p. ej. COCINA, que corre
     * como un monitor/terminal aparte). La ventana principal sigue intacta.
     */
    void abrirVentana(Pantalla pantalla, String titulo);
}
