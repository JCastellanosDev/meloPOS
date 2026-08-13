package mx.edu.utch.melo.security;

import mx.edu.utch.melo.model.Rol;
import mx.edu.utch.melo.nav.Pantalla;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ControlAccesoTest {

    @Test
    void administradorPuedeAccederATodasLasPantallas() {
        for (Pantalla pantalla : Pantalla.values()) {
            assertTrue(ControlAcceso.puedeAcceder(Rol.ADMINISTRADOR, pantalla),
                    "ADMINISTRADOR debería poder acceder a " + pantalla);
        }
    }

    @Test
    void meseroPuedeTomarPedidosPeroNoCobrar() {
        assertTrue(ControlAcceso.puedeAcceder(Rol.MESERO, Pantalla.MENU));
        assertTrue(ControlAcceso.puedeAcceder(Rol.MESERO, Pantalla.PEDIDOS));
        assertTrue(ControlAcceso.puedeAcceder(Rol.MESERO, Pantalla.MENU_PEDIDO));

        assertFalse(ControlAcceso.puedeAcceder(Rol.MESERO, Pantalla.ORDENES));
        assertFalse(ControlAcceso.puedeAcceder(Rol.MESERO, Pantalla.CAJA));
    }

    @Test
    void cajeroPuedeCobrarYAbrirCajaPeroNoEsConfiguracion() {
        assertTrue(ControlAcceso.puedeAcceder(Rol.CAJERO, Pantalla.ORDENES));
        assertTrue(ControlAcceso.puedeAcceder(Rol.CAJERO, Pantalla.CAJA));

        assertFalse(ControlAcceso.puedeAcceder(Rol.CAJERO, Pantalla.AJUSTES));
        assertFalse(ControlAcceso.puedeAcceder(Rol.CAJERO, Pantalla.INVENTARIO));
        assertFalse(ControlAcceso.puedeAcceder(Rol.CAJERO, Pantalla.PERSONAL));
    }

    @Test
    void cocinaSoloVeKitchenDisplay() {
        assertTrue(ControlAcceso.puedeAcceder(Rol.COCINA, Pantalla.COCINA));

        assertFalse(ControlAcceso.puedeAcceder(Rol.COCINA, Pantalla.MENU));
        assertFalse(ControlAcceso.puedeAcceder(Rol.COCINA, Pantalla.ORDENES));
        assertFalse(ControlAcceso.puedeAcceder(Rol.COCINA, Pantalla.CAJA));
        assertFalse(ControlAcceso.puedeAcceder(Rol.COCINA, Pantalla.AJUSTES));
    }

    @Test
    void dashboardYCambiarUsuarioSonAccesiblesParaCualquierRol() {
        for (Rol rol : Rol.values()) {
            assertTrue(ControlAcceso.puedeAcceder(rol, Pantalla.DASHBOARD), "DASHBOARD debería aceptar a " + rol);
            assertTrue(ControlAcceso.puedeAcceder(rol, Pantalla.CAMBIAR_USUARIO), "CAMBIAR_USUARIO debería aceptar a " + rol);
        }
    }

    @Test
    void exigirRolNoLanzaCuandoElRolEstaPermitido() {
        assertDoesNotThrow(() -> ControlAcceso.exigirRol(Rol.ADMINISTRADOR, "operación de prueba", Rol.ADMINISTRADOR));
    }

    @Test
    void exigirRolLanzaCuandoElRolNoEstaPermitido() {
        AccesoDenegadoException excepcion = assertThrows(AccesoDenegadoException.class,
                () -> ControlAcceso.exigirRol(Rol.MESERO, "eliminar un producto", Rol.ADMINISTRADOR));

        assertTrue(excepcion.getMessage().contains("eliminar un producto"));
    }

    @Test
    void exigirRolAceptaVariosRolesPermitidos() {
        assertDoesNotThrow(() -> ControlAcceso.exigirRol(Rol.CAJERO, "abrir un turno de caja", Rol.CAJERO, Rol.ADMINISTRADOR));
        assertDoesNotThrow(() -> ControlAcceso.exigirRol(Rol.ADMINISTRADOR, "abrir un turno de caja", Rol.CAJERO, Rol.ADMINISTRADOR));
        assertThrows(AccesoDenegadoException.class,
                () -> ControlAcceso.exigirRol(Rol.MESERO, "abrir un turno de caja", Rol.CAJERO, Rol.ADMINISTRADOR));
    }
}
