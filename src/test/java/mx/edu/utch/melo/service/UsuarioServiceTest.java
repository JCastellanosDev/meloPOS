package mx.edu.utch.melo.service;

import mx.edu.utch.melo.dao.UsuarioDAO;
import mx.edu.utch.melo.model.Rol;
import mx.edu.utch.melo.model.Usuario;
import mx.edu.utch.melo.security.AccesoDenegadoException;
import mx.edu.utch.melo.security.PinHasher;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UsuarioServiceTest {

    @Test
    void registrarNuncaGuardaElPinEnTextoPlano() {
        UsuarioDAOFalso dao = new UsuarioDAOFalso();
        UsuarioService service = new UsuarioService(dao);

        service.registrar(Rol.ADMINISTRADOR, 1, "Ana", "1234", Rol.MESERO);

        assertNotEquals("1234", dao.ultimoCreado.getPinAcceso());
        assertTrue(PinHasher.esHash(dao.ultimoCreado.getPinAcceso()));
        assertTrue(PinHasher.verificar("1234", dao.ultimoCreado.getPinAcceso()));
    }

    @Test
    void registrarRechazaARolesQueNoSonAdministrador() {
        UsuarioDAOFalso dao = new UsuarioDAOFalso();
        UsuarioService service = new UsuarioService(dao);

        assertThrows(AccesoDenegadoException.class, () ->
                service.registrar(Rol.CAJERO, 1, "Ana", "1234", Rol.MESERO));
    }

    @Test
    void registrarRechazaPinDuplicadoContraUnHashExistente() {
        UsuarioDAOFalso dao = new UsuarioDAOFalso();
        dao.usuarios.add(usuarioConPin(1, 1, "Luis", PinHasher.hash("1234"), Rol.CAJERO));
        UsuarioService service = new UsuarioService(dao);

        assertThrows(UsuarioService.PinDuplicadoException.class, () ->
                service.registrar(Rol.ADMINISTRADOR, 1, "Ana", "1234", Rol.MESERO));
    }

    @Test
    void registrarRechazaPinDuplicadoContraUnPinLegadoEnTextoPlano() {
        UsuarioDAOFalso dao = new UsuarioDAOFalso();
        dao.usuarios.add(usuarioConPin(1, 1, "Luis", "1234", Rol.CAJERO));
        UsuarioService service = new UsuarioService(dao);

        assertThrows(UsuarioService.PinDuplicadoException.class, () ->
                service.registrar(Rol.ADMINISTRADOR, 1, "Ana", "1234", Rol.MESERO));
    }

    @Test
    void registrarPermiteElMismoPinEnOtraSucursal() {
        // El PIN es único por sucursal, no globalmente (ver UsuarioDAO original).
        UsuarioDAOFalso dao = new UsuarioDAOFalso();
        dao.usuarios.add(usuarioConPin(1, 2, "Luis", PinHasher.hash("1234"), Rol.CAJERO));
        UsuarioService service = new UsuarioService(dao);

        // dao.obtenerPorSucursal(1) no regresa al usuario de la sucursal 2 -- ver el fake abajo.
        Usuario creado = service.registrar(Rol.ADMINISTRADOR, 1, "Ana", "1234", Rol.MESERO);

        assertEquals("Ana", creado.getNombre());
    }

    @Test
    void autenticarPorPinEncuentraAlUsuarioConHashCorrecto() {
        UsuarioDAOFalso dao = new UsuarioDAOFalso();
        Usuario luis = usuarioConPin(1, 1, "Luis", PinHasher.hash("1234"), Rol.CAJERO);
        dao.usuarios.add(luis);
        UsuarioService service = new UsuarioService(dao);

        Optional<Usuario> resultado = service.autenticarPorPin(1, "1234");

        assertTrue(resultado.isPresent());
        assertEquals("Luis", resultado.get().getNombre());
    }

    @Test
    void autenticarPorPinConPinIncorrectoNoEncuentraNada() {
        UsuarioDAOFalso dao = new UsuarioDAOFalso();
        dao.usuarios.add(usuarioConPin(1, 1, "Luis", PinHasher.hash("1234"), Rol.CAJERO));
        UsuarioService service = new UsuarioService(dao);

        assertTrue(service.autenticarPorPin(1, "9999").isEmpty());
    }

    @Test
    void autenticarPorPinIgnoraUsuariosInactivos() {
        UsuarioDAOFalso dao = new UsuarioDAOFalso();
        Usuario inactivo = usuarioConPin(1, 1, "Ex empleado", PinHasher.hash("1234"), Rol.MESERO);
        inactivo.setActivo(false);
        dao.usuarios.add(inactivo);
        UsuarioService service = new UsuarioService(dao);

        assertTrue(service.autenticarPorPin(1, "1234").isEmpty());
    }

    @Test
    void autenticarPorPinMigraUnPinLegadoEnTextoPlanoAUnHashReal() {
        UsuarioDAOFalso dao = new UsuarioDAOFalso();
        Usuario semilla = usuarioConPin(1, 1, "Administrador Demo", "0000", Rol.ADMINISTRADOR);
        dao.usuarios.add(semilla);
        UsuarioService service = new UsuarioService(dao);

        Optional<Usuario> resultado = service.autenticarPorPin(1, "0000");

        assertTrue(resultado.isPresent(), "el PIN legado en texto plano debe seguir autenticando");
        assertTrue(dao.actualizarLlamado, "debe re-hashear y guardar de inmediato al autenticar con éxito");
        assertTrue(PinHasher.esHash(semilla.getPinAcceso()), "el PIN en memoria queda migrado a hash");
        assertTrue(PinHasher.verificar("0000", semilla.getPinAcceso()));

        // Y una segunda autenticación debe seguir funcionando contra el hash ya migrado, sin volver
        // a "actualizar" innecesariamente.
        dao.actualizarLlamado = false;
        assertTrue(service.autenticarPorPin(1, "0000").isPresent());
        assertFalse(dao.actualizarLlamado, "ya migrado, no debería reescribirse en cada login");
    }

    @Test
    void autenticarPorPinEnSucursalSinUsuariosNoLanza() {
        UsuarioDAOFalso dao = new UsuarioDAOFalso();
        UsuarioService service = new UsuarioService(dao);

        assertTrue(service.autenticarPorPin(1, "0000").isEmpty());
    }

    private static Usuario usuarioConPin(int id, int sucursalId, String nombre, String pinAcceso, Rol rol) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setSucursalId(sucursalId);
        usuario.setNombre(nombre);
        usuario.setPinAcceso(pinAcceso);
        usuario.setRol(rol);
        usuario.setActivo(true);
        return usuario;
    }

    private static class UsuarioDAOFalso implements UsuarioDAO {
        final List<Usuario> usuarios = new ArrayList<>();
        Usuario ultimoCreado;
        boolean actualizarLlamado;

        @Override
        public Usuario crear(Usuario usuario) {
            usuario.setId(usuarios.size() + 1);
            this.ultimoCreado = usuario;
            usuarios.add(usuario);
            return usuario;
        }

        @Override
        public Optional<Usuario> obtenerPorId(Integer id) {
            return usuarios.stream().filter(u -> u.getId() == id).findFirst();
        }

        @Override
        public List<Usuario> obtenerTodos() {
            return List.copyOf(usuarios);
        }

        @Override
        public boolean actualizar(Usuario usuario) {
            this.actualizarLlamado = true;
            return true;
        }

        @Override
        public boolean eliminar(Integer id) {
            return usuarios.removeIf(u -> u.getId() == id);
        }

        @Override
        public List<Usuario> obtenerPorSucursal(int sucursalId) {
            return usuarios.stream().filter(u -> u.getSucursalId() == sucursalId).toList();
        }
    }
}
