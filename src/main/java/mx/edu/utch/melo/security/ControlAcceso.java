package mx.edu.utch.melo.security;

import mx.edu.utch.melo.model.Rol;
import mx.edu.utch.melo.nav.Pantalla;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Qué rol puede entrar a qué pantalla (ver CLAUDE.md, sección "Roles de usuario", y la auditoría
 * de Fase 5: "no basta con ocultar botones"). ADMINISTRADOR ve todo -- es el rol de gerencia,
 * dueño de reportes/inventario/configuración, y en la práctica también necesita poder supervisar
 * el resto. Los otros tres roles solo entran a su área funcional:
 *
 * <pre>
 * MESERO         -> toma pedidos, domicilios (CLAUDE.md: "domicilios ... desde Mesero/Cajero")
 * CAJERO         -> cobra cuentas, corte de caja, domicilios
 * COCINA         -> Kitchen Display
 * ADMINISTRADOR  -> todas las pantallas
 * </pre>
 *
 * DASHBOARD y CAMBIAR_USUARIO están abiertas a cualquier rol: la primera es la pantalla de
 * inicio, la segunda es justamente cómo alguien cambia a su propio rol.
 */
public final class ControlAcceso {

    private static final Map<Rol, Set<Pantalla>> PANTALLAS_POR_ROL = construirMapa();

    private ControlAcceso() {
    }

    public static boolean puedeAcceder(Rol rol, Pantalla pantalla) {
        return PANTALLAS_POR_ROL.getOrDefault(rol, Set.of()).contains(pantalla);
    }

    /**
     * Para operaciones sensibles dentro de un Service (ver auditoría de Fase 5: "no basta con
     * ocultar botones, las operaciones sensibles deben validar permisos mediante lógica de
     * aplicación"). Lanza si {@code rolSolicitante} no está entre los permitidos.
     */
    public static void exigirRol(Rol rolSolicitante, String operacion, Rol... rolesPermitidos) {
        for (Rol permitido : rolesPermitidos) {
            if (rolSolicitante == permitido) {
                return;
            }
        }
        throw new AccesoDenegadoException(rolSolicitante, operacion);
    }

    private static Map<Rol, Set<Pantalla>> construirMapa() {
        Map<Rol, Set<Pantalla>> mapa = new EnumMap<>(Rol.class);

        mapa.put(Rol.MESERO, EnumSet.of(
                Pantalla.DASHBOARD, Pantalla.CAMBIAR_USUARIO,
                Pantalla.MENU, Pantalla.PEDIDOS, Pantalla.MENU_PEDIDO, Pantalla.DOMICILIO));

        mapa.put(Rol.CAJERO, EnumSet.of(
                Pantalla.DASHBOARD, Pantalla.CAMBIAR_USUARIO,
                Pantalla.MENU, Pantalla.ORDENES, Pantalla.CAJA, Pantalla.DOMICILIO, Pantalla.SELECCIONAR_DESCUENTO));

        mapa.put(Rol.COCINA, EnumSet.of(
                Pantalla.DASHBOARD, Pantalla.CAMBIAR_USUARIO,
                Pantalla.COCINA));

        mapa.put(Rol.ADMINISTRADOR, EnumSet.allOf(Pantalla.class));

        return mapa;
    }
}
