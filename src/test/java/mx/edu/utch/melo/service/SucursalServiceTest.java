package mx.edu.utch.melo.service;

import mx.edu.utch.melo.dao.SucursalDAO;
import mx.edu.utch.melo.model.Rol;
import mx.edu.utch.melo.model.Sucursal;
import mx.edu.utch.melo.security.AccesoDenegadoException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SucursalServiceTest {

    @Test
    void actualizarAplicaIvaCambiaElValorYPersiste() {
        SucursalDAOFalso dao = new SucursalDAOFalso();
        SucursalService service = new SucursalService(dao);
        Sucursal sucursal = new Sucursal();
        sucursal.setAplicaIva(true);

        service.actualizarAplicaIva(Rol.ADMINISTRADOR, sucursal, false);

        assertFalse(sucursal.isAplicaIva());
        assertTrue(dao.actualizarLlamado);
    }

    @Test
    void actualizarAplicaIvaRechazaARolesQueNoSonAdministrador() {
        SucursalDAOFalso dao = new SucursalDAOFalso();
        SucursalService service = new SucursalService(dao);
        Sucursal sucursal = new Sucursal();
        sucursal.setAplicaIva(true);

        assertThrows(AccesoDenegadoException.class, () -> service.actualizarAplicaIva(Rol.CAJERO, sucursal, false));

        assertTrue(sucursal.isAplicaIva(), "si no hay permiso, ni siquiera debería mutar el objeto en memoria");
        assertFalse(dao.actualizarLlamado);
    }

    private static class SucursalDAOFalso implements SucursalDAO {
        boolean actualizarLlamado;

        @Override
        public Sucursal crear(Sucursal sucursal) {
            return sucursal;
        }

        @Override
        public Optional<Sucursal> obtenerPorId(Integer id) {
            return Optional.empty();
        }

        @Override
        public List<Sucursal> obtenerTodos() {
            return List.of();
        }

        @Override
        public boolean actualizar(Sucursal sucursal) {
            this.actualizarLlamado = true;
            return true;
        }

        @Override
        public boolean eliminar(Integer id) {
            return true;
        }

        @Override
        public List<Sucursal> obtenerActivas() {
            return List.of();
        }
    }
}
