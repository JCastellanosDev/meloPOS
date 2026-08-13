package mx.edu.utch.melo.dao;

import mx.edu.utch.melo.model.Modificador;
import mx.edu.utch.melo.model.Producto;

import java.util.List;

public interface ProductoDAO extends CrudDAO<Producto, Integer> {

    List<Producto> obtenerTodosActivos();

    List<Producto> obtenerPorCategoria(int categoriaId);

    /** Todos los productos de una sucursal (activos e inactivos) -- para inventario, a diferencia de obtenerTodosActivos(). */
    List<Producto> obtenerPorSucursal(int sucursalId);

    /** Modificadores que este producto ofrece (tabla producto_modificador). */
    List<Modificador> obtenerModificadores(int productoId);

    void asignarModificador(int productoId, int modificadorId);

    void quitarModificador(int productoId, int modificadorId);
}
