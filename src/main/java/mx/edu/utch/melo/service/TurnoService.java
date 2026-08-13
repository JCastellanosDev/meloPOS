package mx.edu.utch.melo.service;

import mx.edu.utch.melo.dao.ReporteDAO;
import mx.edu.utch.melo.dao.TurnoDAO;
import mx.edu.utch.melo.model.Rol;
import mx.edu.utch.melo.model.Turno;
import mx.edu.utch.melo.model.reporte.ResumenTurno;
import mx.edu.utch.melo.security.Auditoria;
import mx.edu.utch.melo.security.ControlAcceso;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Apertura y cierre de turno de caja (ver CajaController y CLAUDE.md, sección "Turno y caja").
 * Al cerrar, solo el efectivo cuenta contra lo esperado -- tarjeta/transferencia no mueven el
 * cajón físico.
 */
public class TurnoService {

    private final TurnoDAO turnoDAO;
    private final ReporteDAO reporteDAO;

    public TurnoService(TurnoDAO turnoDAO, ReporteDAO reporteDAO) {
        this.turnoDAO = turnoDAO;
        this.reporteDAO = reporteDAO;
    }

    public Optional<Turno> obtenerTurnoAbierto(int usuarioId) {
        return turnoDAO.obtenerTurnoAbierto(usuarioId);
    }

    public ResumenTurno obtenerResumen(int turnoId) {
        return reporteDAO.obtenerResumenTurno(turnoId);
    }

    /** Abre un turno nuevo para el cajero, con el monto inicial capturado. */
    public Turno abrirTurno(Rol rolSolicitante, int sucursalId, int usuarioId, BigDecimal montoApertura) {
        ControlAcceso.exigirRol(rolSolicitante, "abrir un turno de caja", Rol.CAJERO, Rol.ADMINISTRADOR);
        Turno turno = new Turno();
        turno.setSucursalId(sucursalId);
        turno.setUsuarioId(usuarioId);
        turno.setMontoApertura(montoApertura);
        turno.setMontoCierreContado(null);
        turno.setFechaApertura(LocalDateTime.now());
        turno.setFechaCierre(null);
        Turno creado = turnoDAO.crear(turno);
        Auditoria.registrar(rolSolicitante, "apertura de turno de caja",
                "usuarioId=" + usuarioId + " montoApertura=" + montoApertura);
        return creado;
    }

    /** Cierra el turno y calcula la diferencia contra lo esperado (apertura + ventas en efectivo). */
    public ResultadoCierre cerrarTurno(Rol rolSolicitante, int turnoId, BigDecimal montoContado) {
        ControlAcceso.exigirRol(rolSolicitante, "cerrar un turno de caja", Rol.CAJERO, Rol.ADMINISTRADOR);
        ResumenTurno resumen = reporteDAO.obtenerResumenTurno(turnoId);
        Turno turno = turnoDAO.obtenerPorId(turnoId).orElseThrow();

        BigDecimal montoEsperado = turno.calcularMontoEsperado(resumen.totalEfectivo());
        BigDecimal diferencia = montoContado.subtract(montoEsperado);

        turno.setMontoCierreContado(montoContado);
        turno.setFechaCierre(LocalDateTime.now());
        turnoDAO.actualizar(turno);

        Auditoria.registrar(rolSolicitante, "cierre de turno de caja",
                "turnoId=" + turnoId + " montoContado=" + montoContado + " diferencia=" + diferencia);
        return new ResultadoCierre(resumen, montoEsperado, montoContado, diferencia);
    }

    public record ResultadoCierre(ResumenTurno resumen, BigDecimal montoEsperado, BigDecimal montoContado,
                                   BigDecimal diferencia) {
    }
}
