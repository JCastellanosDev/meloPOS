package mx.edu.utch.melo.service;

import mx.edu.utch.melo.dao.DescuentoDAO;
import mx.edu.utch.melo.dao.DescuentoDAO.ConfiguracionDescuento;
import mx.edu.utch.melo.model.Rol;
import mx.edu.utch.melo.model.TipoDescuento;
import mx.edu.utch.melo.security.AccesoDenegadoException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DescuentoServiceTest {

    @Test
    void actualizarConfiguracionRechazaARolesQueNoSonAdministrador() {
        DescuentoDAOFalso dao = new DescuentoDAOFalso();
        DescuentoService service = new DescuentoService(dao);

        assertThrows(AccesoDenegadoException.class,
                () -> service.actualizarConfiguracion(Rol.CAJERO, TipoDescuento.EMPLEADO, "Nuevo", new BigDecimal("0.30")));
        assertEquals(0, new BigDecimal("0.20").compareTo(dao.configuraciones.get(TipoDescuento.EMPLEADO).porcentaje()),
                "no debe cambiar nada si el rol no está autorizado");
    }

    @Test
    void actualizarConfiguracionConAdministradorGuardaEtiquetaYPorcentaje() {
        DescuentoDAOFalso dao = new DescuentoDAOFalso();
        DescuentoService service = new DescuentoService(dao);

        service.actualizarConfiguracion(Rol.ADMINISTRADOR, TipoDescuento.PROMOCION, "Oferta", new BigDecimal("0.15"));

        ConfiguracionDescuento actualizada = dao.configuraciones.get(TipoDescuento.PROMOCION);
        assertEquals("Oferta", actualizada.etiqueta());
        assertEquals(0, new BigDecimal("0.15").compareTo(actualizada.porcentaje()));
    }

    @Test
    void actualizarConfiguracionRechazaEtiquetaVacia() {
        DescuentoDAOFalso dao = new DescuentoDAOFalso();
        DescuentoService service = new DescuentoService(dao);

        assertThrows(DescuentoService.EtiquetaInvalidaException.class,
                () -> service.actualizarConfiguracion(Rol.ADMINISTRADOR, TipoDescuento.AJUSTE, "   ", new BigDecimal("0.10")));
    }

    @Test
    void actualizarConfiguracionRechazaValorNegativo() {
        DescuentoDAOFalso dao = new DescuentoDAOFalso();
        DescuentoService service = new DescuentoService(dao);

        assertThrows(DescuentoService.PorcentajeInvalidoException.class,
                () -> service.actualizarConfiguracion(Rol.ADMINISTRADOR, TipoDescuento.AJUSTE, "Ajuste", new BigDecimal("-0.01")));
    }

    @Test
    void actualizarConfiguracionRechazaMasDeCien() {
        DescuentoDAOFalso dao = new DescuentoDAOFalso();
        DescuentoService service = new DescuentoService(dao);

        assertThrows(DescuentoService.PorcentajeInvalidoException.class,
                () -> service.actualizarConfiguracion(Rol.ADMINISTRADOR, TipoDescuento.AJUSTE, "Ajuste", new BigDecimal("1.01")));
    }

    @Test
    void obtenerConfiguracionesDelegaAlDao() {
        DescuentoDAOFalso dao = new DescuentoDAOFalso();
        DescuentoService service = new DescuentoService(dao);

        assertEquals(dao.configuraciones, service.obtenerConfiguraciones());
    }

    private static class DescuentoDAOFalso implements DescuentoDAO {
        Map<TipoDescuento, ConfiguracionDescuento> configuraciones = new EnumMap<>(Map.of(
                TipoDescuento.EMPLEADO, new ConfiguracionDescuento("Empleado", new BigDecimal("0.20")),
                TipoDescuento.CORTESIA, new ConfiguracionDescuento("Cortesía", new BigDecimal("1.00")),
                TipoDescuento.PROMOCION, new ConfiguracionDescuento("Promoción", new BigDecimal("0.10")),
                TipoDescuento.AJUSTE, new ConfiguracionDescuento("Ajuste", new BigDecimal("0.05"))
        ));

        @Override
        public BigDecimal obtenerPorcentaje(TipoDescuento tipo) {
            return configuraciones.get(tipo).porcentaje();
        }

        @Override
        public Map<TipoDescuento, ConfiguracionDescuento> obtenerTodos() {
            return configuraciones;
        }

        @Override
        public boolean actualizar(TipoDescuento tipo, String etiqueta, BigDecimal porcentaje) {
            configuraciones.put(tipo, new ConfiguracionDescuento(etiqueta, porcentaje));
            return true;
        }
    }
}
