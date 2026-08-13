package mx.edu.utch.melo.model;

/**
 * Canal por el que se tomó la orden. Cada uno tiene su propio flujo de
 * estados documentado en CLAUDE.md (en particular, cuándo se cobra respecto
 * a cuándo se prepara cambia por canal).
 */
public enum TipoOrden {
    COMEDOR,
    PARA_LLEVAR,
    PARA_RECOGER,
    DOMICILIO
}