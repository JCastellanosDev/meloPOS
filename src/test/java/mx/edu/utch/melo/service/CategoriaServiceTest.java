package mx.edu.utch.melo.service;

import mx.edu.utch.melo.dao.CategoriaDAO;
import mx.edu.utch.melo.model.Categoria;
import mx.edu.utch.melo.model.Rol;
import mx.edu.utch.melo.security.AccesoDenegadoException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CategoriaServiceTest {

    @Test
    void crearAsignaElNombreCapturado() {
        CategoriaDAOFalso dao = new CategoriaDAOFalso();
        CategoriaService service = new CategoriaService(dao);

        service.crear(Rol.ADMINISTRADOR, "Bebidas");

        assertEquals("Bebidas", dao.ultimaCreada.getNombre());
    }

    @Test
    void crearRechazaARolesQueNoSonAdministrador() {
        CategoriaDAOFalso dao = new CategoriaDAOFalso();
        CategoriaService service = new CategoriaService(dao);

        assertThrows(AccesoDenegadoException.class, () -> service.crear(Rol.MESERO, "Bebidas"));
        assertNull(dao.ultimaCreada);
    }

    private static class CategoriaDAOFalso implements CategoriaDAO {
        Categoria ultimaCreada;

        @Override
        public Categoria crear(Categoria categoria) {
            categoria.setId(7);
            this.ultimaCreada = categoria;
            return categoria;
        }

        @Override
        public Optional<Categoria> obtenerPorId(Integer id) {
            return Optional.empty();
        }

        @Override
        public List<Categoria> obtenerTodos() {
            return List.of();
        }

        @Override
        public boolean actualizar(Categoria categoria) {
            return true;
        }

        @Override
        public boolean eliminar(Integer id) {
            return true;
        }
    }
}
