package mx.edu.utch.melo.dao;

import mx.edu.utch.melo.model.Turno;

import java.util.Optional;

public interface TurnoDAO extends CrudDAO<Turno, Integer> {

    /** El turno sin cerrar de este usuario, si tiene uno abierto ahora mismo. */
    Optional<Turno> obtenerTurnoAbierto(int usuarioId);
}
