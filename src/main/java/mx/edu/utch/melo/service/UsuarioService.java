package mx.edu.utch.melo.service;

import mx.edu.utch.melo.dao.UsuarioDAO;
import mx.edu.utch.melo.model.Rol;
import mx.edu.utch.melo.model.Usuario;
import mx.edu.utch.melo.security.Auditoria;
import mx.edu.utch.melo.security.ControlAcceso;
import mx.edu.utch.melo.security.PinHasher;

import java.util.List;
import java.util.Optional;

/** Reglas de negocio sobre el personal de una sucursal, detrás de {@link UsuarioDAO}. */
public class UsuarioService {

    private final UsuarioDAO usuarioDAO;

    public UsuarioService(UsuarioDAO usuarioDAO) {
        this.usuarioDAO = usuarioDAO;
    }

    /**
     * Alta de personal con su PIN de acceso a la terminal (ver CambiarUsuarioController, que lo
     * consume). El PIN se guarda hasheado (ver PinHasher, auditoría de Fase 5) -- nunca en texto
     * plano. Como dos PIN iguales generan hashes distintos (sal aleatoria), la unicidad por
     * sucursal que antes garantizaba la base de datos ahora se valida aquí.
     */
    public Usuario registrar(Rol rolSolicitante, int sucursalId, String nombre, String pin, Rol rol) {
        ControlAcceso.exigirRol(rolSolicitante, "registrar personal nuevo", Rol.ADMINISTRADOR);
        if (existePinEnUso(sucursalId, pin)) {
            throw new PinDuplicadoException();
        }
        Usuario usuario = new Usuario();
        usuario.setSucursalId(sucursalId);
        usuario.setNombre(nombre);
        usuario.setPinAcceso(PinHasher.hash(pin));
        usuario.setRol(rol);
        usuario.setActivo(true);
        Usuario creado = usuarioDAO.crear(usuario);
        Auditoria.registrar(rolSolicitante, "alta de personal", "nombre=\"" + nombre + "\" rolAsignado=" + rol);
        return creado;
    }

    /**
     * Autentica por PIN dentro de una sucursal (ver CambiarUsuarioController y el login semilla de
     * HelloApplication). Migración gradual: si encuentra un PIN heredado en texto plano que
     * coincide, lo re-hashea y lo guarda de inmediato -- así cada usuario existente queda
     * migrado a un hash real la primera vez que inicia sesión con éxito, sin acción manual y sin
     * romper su acceso (ver auditoría de Fase 5).
     */
    public Optional<Usuario> autenticarPorPin(int sucursalId, String pin) {
        for (Usuario usuario : usuarioDAO.obtenerPorSucursal(sucursalId)) {
            if (!usuario.isActivo()) {
                continue;
            }
            String almacenado = usuario.getPinAcceso();
            if (PinHasher.esHash(almacenado)) {
                if (PinHasher.verificar(pin, almacenado)) {
                    return Optional.of(usuario);
                }
            } else if (pin.equals(almacenado)) {
                usuario.setPinAcceso(PinHasher.hash(pin));
                usuarioDAO.actualizar(usuario);
                return Optional.of(usuario);
            }
        }
        return Optional.empty();
    }

    private boolean existePinEnUso(int sucursalId, String pin) {
        List<Usuario> usuariosSucursal = usuarioDAO.obtenerPorSucursal(sucursalId);
        for (Usuario usuario : usuariosSucursal) {
            String almacenado = usuario.getPinAcceso();
            boolean coincide = PinHasher.esHash(almacenado)
                    ? PinHasher.verificar(pin, almacenado)
                    : pin.equals(almacenado);
            if (coincide) {
                return true;
            }
        }
        return false;
    }

    public static class PinDuplicadoException extends RuntimeException {
        public PinDuplicadoException() {
            super("Ya existe un usuario con ese PIN en esta sucursal.");
        }
    }
}
