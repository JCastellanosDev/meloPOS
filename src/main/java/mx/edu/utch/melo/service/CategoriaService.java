package mx.edu.utch.melo.service;

import mx.edu.utch.melo.dao.CategoriaDAO;
import mx.edu.utch.melo.model.Categoria;
import mx.edu.utch.melo.model.Rol;
import mx.edu.utch.melo.security.ControlAcceso;

import java.util.List;

/** Reglas de negocio sobre categorías de menú, detrás de {@link CategoriaDAO}. */
public class CategoriaService {

    private final CategoriaDAO categoriaDAO;

    public CategoriaService(CategoriaDAO categoriaDAO) {
        this.categoriaDAO = categoriaDAO;
    }

    public List<Categoria> obtenerTodos() {
        return categoriaDAO.obtenerTodos();
    }

    /** Cada restaurante puede tener sus propias categorías de menú, no solo "General". */
    public Categoria crear(Rol rolSolicitante, String nombre) {
        ControlAcceso.exigirRol(rolSolicitante, "crear una categoría", Rol.ADMINISTRADOR);
        Categoria categoria = new Categoria();
        categoria.setNombre(nombre);
        return categoriaDAO.crear(categoria);
    }
}
