package mx.edu.utch.melo.service;

import mx.edu.utch.melo.dao.DescuentoDAO;
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
    void actualizarPorcentajeRechazaARolesQueNoSonAdministrador() {
        DescuentoDAOFalso dao = new DescuentoDAOFalso();
        DescuentoService service = new DescuentoService(dao);

        assertThrows(AccesoDenegadoException.class,
                () -> service.actualizarPorcentaje(Rol.CAJERO, TipoDescuento.EMPLEADO, new BigDecimal("0.30")));
        assertEquals(0, new BigDecimal("0.20").compareTo(dao.porcentajes.get(TipoDescuento.EMPLEADO)),
                "no debe cambiar nada si el rol no está autorizado");
    }

    @Test
    void actualizarPorcentajeConAdministradorGuardaYAuditaria() {
        DescuentoDAOFalso dao = new DescuentoDAOFalso();
        DescuentoService service = new DescuentoService(dao);

        service.actualizarPorcentaje(Rol.ADMINISTRADOR, TipoDescuento.PROMOCION, new BigDecimal("0.15"));

        assertEquals(0, new BigDecimal("0.15").compareTo(dao.porcentajes.get(TipoDescuento.PROMOCION)));
    }

    @Test
    void actualizarPorcentajeRechazaValorNegativo() {
        DescuentoDAOFalso dao = new DescuentoDAOFalso();
        DescuentoService service = new DescuentoService(dao);

        assertThrows(DescuentoService.PorcentajeInvalidoException.class,
                () -> service.actualizarPorcentaje(Rol.ADMINISTRADOR, TipoDescuento.AJUSTE, new BigDecimal("-0.01")));
    }

    @Test
    void actualizarPorcentajeRechazaMasDeCien() {
        DescuentoDAOFalso dao = new DescuentoDAOFalso();
        DescuentoService service = new DescuentoService(dao);

        assertThrows(DescuentoService.PorcentajeInvalidoException.class,
                () -> service.actualizarPorcentaje(Rol.ADMINISTRADOR, TipoDescuento.AJUSTE, new BigDecimal("1.01")));
    }

    @Test
    void obtenerPorcentajesDelegaAlDao() {
        DescuentoDAOFalso dao = new DescuentoDAOFalso();
        DescuentoService service = new DescuentoService(dao);

        assertEquals(dao.porcentajes, service.obtenerPorcentajes());
    }

    private static class DescuentoDAOFalso implements DescuentoDAO {
        Map<TipoDescuento, BigDecimal> porcentajes = new EnumMap<>(Map.of(
                TipoDescuento.EMPLEADO, new BigDecimal("0.20"),
                TipoDescuento.CORTESIA, new BigDecimal("1.00"),
                TipoDescuento.PROMOCION, new BigDecimal("0.10"),
                TipoDescuento.AJUSTE, new BigDecimal("0.05")
        ));

        @Override
        public BigDecimal obtenerPorcentaje(TipoDescuento tipo) {
            return porcentajes.get(tipo);
        }

        @Override
        public Map<TipoDescuento, BigDecimal> obtenerTodos() {
            return porcentajes;
        }

        @Override
        public boolean actualizar(TipoDescuento tipo, BigDecimal porcentaje) {
            porcentajes.put(tipo, porcentaje);
            return true;
        }
    }
}
