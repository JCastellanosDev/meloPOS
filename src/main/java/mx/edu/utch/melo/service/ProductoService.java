package mx.edu.utch.melo.service;

import mx.edu.utch.melo.dao.ProductoDAO;
import mx.edu.utch.melo.model.Producto;
import mx.edu.utch.melo.model.Rol;
import mx.edu.utch.melo.security.Auditoria;
import mx.edu.utch.melo.security.ControlAcceso;
import mx.edu.utch.melo.util.AlmacenImagenesProducto;

import java.io.File;
import java.math.BigDecimal;
import java.util.List;

/**
 * Reglas de negocio sobre productos del menú, detrás de {@link ProductoDAO}. Dar de alta o
 * eliminar productos son "cambios de precios/productos" (ver auditoría de Fase 5) -- solo
 * ADMINISTRADOR, aunque quien llegue a la pantalla ya haya pasado el filtro de navegación.
 */
public class ProductoService {

    /** Tasa de IVA por defecto para productos dados de alta desde el formulario básico de Ajustes (ver CLAUDE.md). */
    private static final BigDecimal TASA_IVA_DEFECTO = new BigDecimal("0.16");

    private final ProductoDAO productoDAO;

    public ProductoService(ProductoDAO productoDAO) {
        this.productoDAO = productoDAO;
    }

    public List<Producto> obtenerPorSucursal(int sucursalId) {
        return productoDAO.obtenerPorSucursal(sucursalId);
    }

    /**
     * Da de alta un producto básico: nombre, descripción, precio y categoría capturados por el
     * usuario; el resto de columnas ({@code tasaIva}, {@code disponible}, {@code stockMinimo}) quedan
     * con valores por defecto sensatos, editables después desde la base de datos o Inventario.
     */
    public Producto crear(Rol rolSolicitante, int sucursalId, int categoriaId, String nombre, String descripcion,
                           BigDecimal precio, int cantidadInicial, File imagen) {
        ControlAcceso.exigirRol(rolSolicitante, "dar de alta un producto", Rol.ADMINISTRADOR);
        Producto producto = new Producto();
        producto.setSucursalId(sucursalId);
        producto.setCategoriaId(categoriaId);
        producto.setNombre(nombre);
        producto.setDescripcion(descripcion == null || descripcion.isBlank() ? null : descripcion);
        producto.setPrecio(precio);
        producto.setTasaIva(TASA_IVA_DEFECTO);
        producto.setDisponible(true);
        producto.setCantidadDisponible(cantidadInicial);
        producto.setStockMinimo(0);
        producto.setImagenPath(imagen == null ? null : AlmacenImagenesProducto.guardar(imagen));
        Producto creado = productoDAO.crear(producto);
        Auditoria.registrar(rolSolicitante, "alta de producto", "producto=\"" + nombre + "\" precio=" + precio);
        return creado;
    }

    public boolean eliminar(Rol rolSolicitante, int productoId) {
        ControlAcceso.exigirRol(rolSolicitante, "eliminar un producto", Rol.ADMINISTRADOR);
        boolean eliminado = productoDAO.eliminar(productoId);
        Auditoria.registrar(rolSolicitante, "eliminación de producto", "productoId=" + productoId);
        return eliminado;
    }
}
