package mx.edu.utch.melo.service;

import mx.edu.utch.melo.config.Configuracion;
import mx.edu.utch.melo.dao.ControlIntentosPinDAO;
import mx.edu.utch.melo.dao.UsuarioDAO;
import mx.edu.utch.melo.model.EstadoBloqueoPin;
import mx.edu.utch.melo.model.Rol;
import mx.edu.utch.melo.model.Usuario;
import mx.edu.utch.melo.security.Auditoria;
import mx.edu.utch.melo.security.ControlAcceso;
import mx.edu.utch.melo.security.PinBloqueadoException;
import mx.edu.utch.melo.security.PinHasher;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Reglas de negocio sobre el personal de una sucursal, detrás de {@link UsuarioDAO}. */
public class UsuarioService {

    /**
     * Configurable vía database.properties/.env (mismo mecanismo que ConexionDB, ver
     * Configuracion) -- no hardcodeado a un valor fijo, ver auditoría de Fase 6.
     */
    private static final int INTENTOS_MAXIMOS =
            Integer.parseInt(Configuracion.obtener("seguridad.pin.intentosMaximos", "5"));
    private static final long MINUTOS_BLOQUEO =
            Long.parseLong(Configuracion.obtener("seguridad.pin.bloqueoMinutos", "5"));

    private final UsuarioDAO usuarioDAO;
    private final ControlIntentosPinDAO controlIntentosPinDAO;

    public UsuarioService(UsuarioDAO usuarioDAO, ControlIntentosPinDAO controlIntentosPinDAO) {
        this.usuarioDAO = usuarioDAO;
        this.controlIntentosPinDAO = controlIntentosPinDAO;
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
     *
     * Bloqueo por intentos fallidos (ver auditoría de Fase 6): el contador vive por SUCURSAL, no
     * por usuario -- el login es "el PIN implica la identidad" (se prueba contra todos los
     * usuarios activos de la sucursal), así que un PIN que no coincide con nadie no se le puede
     * cargar a ninguna cuenta en particular. El bloqueo persiste en base de datos (no en memoria):
     * reiniciar la aplicación de la terminal no debe servir para evadirlo. Se resetea solo con un
     * login exitoso o cuando el bloqueo expira por tiempo (no hace falta intervención de un
     * administrador para levantarlo, ver "no bloquear permanentemente sin recuperación").
     */
    public Optional<Usuario> autenticarPorPin(int sucursalId, String pin) {
        if (bloqueoVigente(sucursalId)) {
            LocalDateTime bloqueadoHasta = controlIntentosPinDAO.obtenerEstado(sucursalId)
                    .map(EstadoBloqueoPin::bloqueadoHasta)
                    .orElseThrow();
            throw new PinBloqueadoException(bloqueadoHasta);
        }

        for (Usuario usuario : usuarioDAO.obtenerPorSucursal(sucursalId)) {
            if (!usuario.isActivo()) {
                continue;
            }
            String almacenado = usuario.getPinAcceso();
            boolean coincide;
            if (PinHasher.esHash(almacenado)) {
                coincide = PinHasher.verificar(pin, almacenado);
            } else if (pin.equals(almacenado)) {
                usuario.setPinAcceso(PinHasher.hash(pin));
                usuarioDAO.actualizar(usuario);
                coincide = true;
            } else {
                coincide = false;
            }
            if (coincide) {
                controlIntentosPinDAO.reiniciar(sucursalId);
                return Optional.of(usuario);
            }
        }

        registrarIntentoFallido(sucursalId);
        return Optional.empty();
    }

    /**
     * Autentica y exige que el PIN sea de un ADMINISTRADOR -- para autorizaciones tipo "manager
     * override" (ver PagoService.aplicarDescuento). Vacío tanto si el PIN no coincide con nadie
     * como si coincide con alguien que no es Administrador -- un mensaje genérico en ambos casos,
     * no le da a quien está tecleando pistas de si el PIN existe pero es de otro rol (ver
     * melo-security, "no revelar información innecesaria"). Reutiliza autenticarPorPin, así que
     * hereda el mismo bloqueo por intentos fallidos.
     */
    public Optional<Usuario> autenticarAdministrador(int sucursalId, String pin) {
        return autenticarPorPin(sucursalId, pin).filter(usuario -> usuario.getRol() == Rol.ADMINISTRADOR);
    }

    /**
     * true si el bloqueo sigue vigente. Si ya expiró, limpia el estado antes de regresar false --
     * así el contador arranca en 1 en el próximo fallo, en vez de seguir acumulando sobre el
     * conteo del ciclo de bloqueo anterior (ver auditoría de Fase 7).
     */
    private boolean bloqueoVigente(int sucursalId) {
        Optional<EstadoBloqueoPin> estado = controlIntentosPinDAO.obtenerEstado(sucursalId);
        if (estado.isEmpty()) {
            return false;
        }
        if (estado.get().estaBloqueado()) {
            return true;
        }
        if (estado.get().bloqueadoHasta() != null) {
            controlIntentosPinDAO.reiniciar(sucursalId);
        }
        return false;
    }

    /**
     * Incrementa el contador de forma atómica en el propio SQL (ver ControlIntentosPinDAO#incrementarIntentosFallidos)
     * en vez de leer-en-Java + escribir (ver auditoría de Fase 7: dos terminales de la misma
     * sucursal fallando el PIN casi al mismo tiempo podían perder un intento con el patrón
     * anterior de lectura-modificación-escritura -- el UPDATE atómico de MySQL no pierde
     * incrementos concurrentes, aunque la lectura del valor resultante pueda quedar un instante
     * desfasada).
     */
    private void registrarIntentoFallido(int sucursalId) {
        int intentos = controlIntentosPinDAO.incrementarIntentosFallidos(sucursalId);
        if (intentos >= INTENTOS_MAXIMOS) {
            LocalDateTime bloqueadoHasta = LocalDateTime.now().plusMinutes(MINUTOS_BLOQUEO);
            controlIntentosPinDAO.guardarEstado(sucursalId, intentos, bloqueadoHasta);
            // Sin Rol: ningún usuario fue identificado (ver Auditoria -- no se sabe quién intentó).
            Auditoria.registrar(null, "bloqueo temporal de PIN por intentos fallidos",
                    "sucursalId=" + sucursalId + " intentos=" + intentos + " bloqueadoHasta=" + bloqueadoHasta);
        }
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
