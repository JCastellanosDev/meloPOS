package mx.edu.utch.melo.dao;

import mx.edu.utch.melo.model.Modificador;
import mx.edu.utch.melo.model.Producto;

import java.sql.Connection;
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

    /**
     * Descuenta {@code cantidad} unidades del inventario de un producto de forma atómica (ver
     * VentaService.persistir/agregarArticulos, "Registrar venta" en la auditoría de Fase 4): un
     * único {@code UPDATE ... WHERE id = ? AND cantidad_disponible >= ?} hace la comprobación de
     * "hay suficiente stock" y el descuento en el mismo viaje a la base de datos -- evita la
     * condición de carrera de leer el stock, decidir en Java, y actualizar por separado (dos
     * cajeros vendiendo el último artículo al mismo tiempo podrían ambos leer stock suficiente
     * antes de que el otro actualice). Regresa {@code false} sin lanzar si no había stock
     * suficiente (0 filas afectadas) -- el Service decide si eso debe revertir la transacción
     * completa. Participa siempre en una transacción ya abierta (ver Transaccionador); no tiene
     * sentido llamarla fuera de una.
     */
    boolean descontarStock(int productoId, int cantidad, Connection conexion);

    /**
     * Restaura {@code cantidad} unidades al inventario de un producto (ver
     * VentaService.cancelarOrden) -- operación simétrica a {@link #descontarStock}, pero sin
     * necesidad de la guarda de "hay suficiente": sumar stock nunca puede dejarlo en negativo, así
     * que no hace falta la cláusula {@code WHERE cantidad_disponible >= ?} que sí necesita el
     * descuento. Participa siempre en una transacción ya abierta (mismo patrón que
     * {@link #descontarStock}).
     */
    void restaurarStock(int productoId, int cantidad, Connection conexion);
}
