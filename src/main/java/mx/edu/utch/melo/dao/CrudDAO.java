package mx.edu.utch.melo.dao;

import java.util.List;
import java.util.Optional;

/**
 * Operaciones CRUD comunes a cualquier entidad persistida. Las interfaces
 * específicas (UsuarioDAO, ProductoDAO, OrdenDAO) extienden esta base y
 * agregan solo las consultas propias de su dominio -- Segregación de
 * Interfaces: nadie se ve obligado a implementar un método que no le
 * corresponde a su entidad.
 *
 * @param <T>  tipo de la entidad
 * @param <ID> tipo de la llave primaria
 */
public interface CrudDAO<T, ID> {

    T crear(T entidad);

    Optional<T> obtenerPorId(ID id);

    List<T> obtenerTodos();

    boolean actualizar(T entidad);

    boolean eliminar(ID id);
}
