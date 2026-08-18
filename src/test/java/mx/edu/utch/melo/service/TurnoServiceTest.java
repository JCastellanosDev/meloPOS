package mx.edu.utch.melo.service;

import mx.edu.utch.melo.dao.ReporteDAO;
import mx.edu.utch.melo.dao.TurnoDAO;
import mx.edu.utch.melo.model.Rol;
import mx.edu.utch.melo.model.Turno;
import mx.edu.utch.melo.model.reporte.PlatilloVendido;
import mx.edu.utch.melo.model.reporte.ResumenTurno;
import mx.edu.utch.melo.model.reporte.VentaDiaria;
import mx.edu.utch.melo.security.AccesoDenegadoException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TurnoServiceTest {

    @Test
    void abrirTurnoConstruyeElTurnoCorrectamente() {
        TurnoDAOFalso turnoDAO = new TurnoDAOFalso();
        TurnoService service = new TurnoService(turnoDAO, new ReporteDAOFalso());

        service.abrirTurno(Rol.CAJERO, 1, 9, new BigDecimal("500.00"));

        assertEquals(1, turnoDAO.ultimoCreado.getSucursalId());
        assertEquals(9, turnoDAO.ultimoCreado.getUsuarioId());
        assertEquals(new BigDecimal("500.00"), turnoDAO.ultimoCreado.getMontoApertura());
        assertEquals(null, turnoDAO.ultimoCreado.getMontoCierreContado());
        assertEquals(null, turnoDAO.ultimoCreado.getFechaCierre());
        assertNotNull(turnoDAO.ultimoCreado.getFechaApertura());
    }

    @Test
    void abrirTurnoRechazaARolesQueNoSonCajeroNiAdministrador() {
        TurnoService service = new TurnoService(new TurnoDAOFalso(), new ReporteDAOFalso());

        assertThrows(AccesoDenegadoException.class, () ->
                service.abrirTurno(Rol.MESERO, 1, 9, new BigDecimal("500.00")));
        assertThrows(AccesoDenegadoException.class, () ->
                service.abrirTurno(Rol.COCINA, 1, 9, new BigDecimal("500.00")));
    }

    @Test
    void abrirTurnoAceptaAdministrador() {
        TurnoDAOFalso turnoDAO = new TurnoDAOFalso();
        TurnoService service = new TurnoService(turnoDAO, new ReporteDAOFalso());

        service.abrirTurno(Rol.ADMINISTRADOR, 1, 9, new BigDecimal("500.00"));

        assertEquals(9, turnoDAO.ultimoCreado.getUsuarioId());
    }

    @Test
    void cerrarTurnoCuandoElEfectivoContadoCoincideExactamenteConLoEsperado() {
        // esperado = apertura(500) + efectivo vendido(1200) = 1700
        TurnoDAOFalso turnoDAO = new TurnoDAOFalso();
        turnoDAO.turnoExistente = turnoConApertura(1, new BigDecimal("500.00"));
        ReporteDAOFalso reporteDAO = new ReporteDAOFalso();
        reporteDAO.resumen = resumenCon(new BigDecimal("1200.00"));
        TurnoService service = new TurnoService(turnoDAO, reporteDAO);

        TurnoService.ResultadoCierre resultado = service.cerrarTurno(Rol.CAJERO, 1, new BigDecimal("1700.00"));

        assertEquals(new BigDecimal("1700.00"), resultado.montoEsperado());
        assertEquals(0, resultado.diferencia().compareTo(BigDecimal.ZERO));
    }

    @Test
    void cerrarTurnoDetectaSobranteEnCaja() {
        TurnoDAOFalso turnoDAO = new TurnoDAOFalso();
        turnoDAO.turnoExistente = turnoConApertura(1, new BigDecimal("500.00"));
        ReporteDAOFalso reporteDAO = new ReporteDAOFalso();
        reporteDAO.resumen = resumenCon(new BigDecimal("1200.00"));
        TurnoService service = new TurnoService(turnoDAO, reporteDAO);

        TurnoService.ResultadoCierre resultado = service.cerrarTurno(Rol.CAJERO, 1, new BigDecimal("1750.00"));

        assertEquals(0, new BigDecimal("50.00").compareTo(resultado.diferencia()));
    }

    @Test
    void cerrarTurnoDetectaFaltanteEnCaja() {
        TurnoDAOFalso turnoDAO = new TurnoDAOFalso();
        turnoDAO.turnoExistente = turnoConApertura(1, new BigDecimal("500.00"));
        ReporteDAOFalso reporteDAO = new ReporteDAOFalso();
        reporteDAO.resumen = resumenCon(new BigDecimal("1200.00"));
        TurnoService service = new TurnoService(turnoDAO, reporteDAO);

        TurnoService.ResultadoCierre resultado = service.cerrarTurno(Rol.CAJERO, 1, new BigDecimal("1650.00"));

        assertEquals(0, new BigDecimal("-50.00").compareTo(resultado.diferencia()));
    }

    @Test
    void cerrarTurnoSoloCuentaElEfectivoNoTarjetaNiTransferencia() {
        // ver CLAUDE.md: tarjeta/transferencia no mueven el cajón físico, así que no cuentan
        // contra el monto esperado -- aunque las ventas totales del turno sean mucho más altas.
        TurnoDAOFalso turnoDAO = new TurnoDAOFalso();
        turnoDAO.turnoExistente = turnoConApertura(1, new BigDecimal("500.00"));
        ReporteDAOFalso reporteDAO = new ReporteDAOFalso();
        reporteDAO.resumen = new ResumenTurno(1, new BigDecimal("300.00"), new BigDecimal("5000.00"),
                new BigDecimal("2000.00"), new BigDecimal("7300.00"), 12);
        TurnoService service = new TurnoService(turnoDAO, reporteDAO);

        TurnoService.ResultadoCierre resultado = service.cerrarTurno(Rol.CAJERO, 1, new BigDecimal("800.00"));

        assertEquals(0, new BigDecimal("800.00").compareTo(resultado.montoEsperado()));
        assertEquals(0, BigDecimal.ZERO.compareTo(resultado.diferencia()));
    }

    @Test
    void cerrarTurnoActualizaElTurnoConLaFechaYElMontoContado() {
        TurnoDAOFalso turnoDAO = new TurnoDAOFalso();
        turnoDAO.turnoExistente = turnoConApertura(1, new BigDecimal("500.00"));
        ReporteDAOFalso reporteDAO = new ReporteDAOFalso();
        reporteDAO.resumen = resumenCon(new BigDecimal("1200.00"));
        TurnoService service = new TurnoService(turnoDAO, reporteDAO);

        service.cerrarTurno(Rol.CAJERO, 1, new BigDecimal("1700.00"));

        assertTrue(turnoDAO.actualizarLlamado);
        assertEquals(new BigDecimal("1700.00"), turnoDAO.turnoExistente.getMontoCierreContado());
        assertNotNull(turnoDAO.turnoExistente.getFechaCierre());
    }

    @Test
    void cerrarTurnoLanzaSiElTurnoNoExiste() {
        // turnoDAO.turnoExistente se queda null -- simula un turnoId que ya no existe (borrado o
        // mal capturado desde la pantalla). No debe intentar actualizar nada.
        TurnoDAOFalso turnoDAO = new TurnoDAOFalso();
        ReporteDAOFalso reporteDAO = new ReporteDAOFalso();
        reporteDAO.resumen = resumenCon(new BigDecimal("1200.00"));
        TurnoService service = new TurnoService(turnoDAO, reporteDAO);

        assertThrows(java.util.NoSuchElementException.class,
                () -> service.cerrarTurno(Rol.CAJERO, 1, new BigDecimal("1700.00")));
        assertFalse(turnoDAO.actualizarLlamado);
    }

    @Test
    void cerrarTurnoRechazaARolesQueNoSonCajeroNiAdministrador() {
        TurnoDAOFalso turnoDAO = new TurnoDAOFalso();
        turnoDAO.turnoExistente = turnoConApertura(1, new BigDecimal("500.00"));
        ReporteDAOFalso reporteDAO = new ReporteDAOFalso();
        reporteDAO.resumen = resumenCon(new BigDecimal("1200.00"));
        TurnoService service = new TurnoService(turnoDAO, reporteDAO);

        assertThrows(AccesoDenegadoException.class, () -> service.cerrarTurno(Rol.MESERO, 1, new BigDecimal("1700.00")));
        assertFalse(turnoDAO.actualizarLlamado, "sin permiso, no debería tocar la base de datos");
    }

    private static Turno turnoConApertura(int id, BigDecimal montoApertura) {
        Turno turno = new Turno();
        turno.setId(id);
        turno.setSucursalId(1);
        turno.setUsuarioId(9);
        turno.setMontoApertura(montoApertura);
        return turno;
    }

    private static ResumenTurno resumenCon(BigDecimal totalEfectivo) {
        return new ResumenTurno(1, totalEfectivo, BigDecimal.ZERO, BigDecimal.ZERO, totalEfectivo, 5);
    }

    private static class TurnoDAOFalso implements TurnoDAO {
        Turno ultimoCreado;
        Turno turnoExistente;
        boolean actualizarLlamado;

        @Override
        public Turno crear(Turno turno) {
            this.ultimoCreado = turno;
            return turno;
        }

        @Override
        public Optional<Turno> obtenerPorId(Integer id) {
            return Optional.ofNullable(turnoExistente);
        }

        @Override
        public List<Turno> obtenerTodos() {
            return List.of();
        }

        @Override
        public boolean actualizar(Turno turno) {
            this.actualizarLlamado = true;
            return true;
        }

        @Override
        public boolean eliminar(Integer id) {
            return true;
        }

        @Override
        public Optional<Turno> obtenerTurnoAbierto(int usuarioId) {
            return Optional.ofNullable(turnoExistente);
        }
    }

    private static class ReporteDAOFalso implements ReporteDAO {
        ResumenTurno resumen;

        @Override
        public List<VentaDiaria> obtenerVentasPorPeriodo(int sucursalId, LocalDate desde, LocalDate hasta) {
            return List.of();
        }

        @Override
        public List<PlatilloVendido> obtenerPlatillosMasVendidos(int sucursalId, LocalDate desde, LocalDate hasta, int limite) {
            return List.of();
        }

        @Override
        public ResumenTurno obtenerResumenTurno(int turnoId) {
            return resumen;
        }
    }
}
