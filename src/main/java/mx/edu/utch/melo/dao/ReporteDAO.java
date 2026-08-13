package mx.edu.utch.melo.dao;

import mx.edu.utch.melo.model.reporte.PlatilloVendido;
import mx.edu.utch.melo.model.reporte.ResumenTurno;
import mx.edu.utch.melo.model.reporte.VentaDiaria;

import java.time.LocalDate;
import java.util.List;

/**
 * Consultas de agregación para los reportes que pide CLAUDE.md (ventas por
 * periodo, platillos más vendidos). No extiende {@link CrudDAO}: no hay una
 * entidad "reporte" que se cree/actualice/elimine -- estas son lecturas que
 * combinan varias tablas (ordenes, detalle_orden, productos, usuarios), no
 * el acceso a una sola tabla como el resto de los DAO.
 */
public interface ReporteDAO {

    /** Suma de ventas por día, en el rango [desde, hasta], para la sucursal dada. */
    List<VentaDiaria> obtenerVentasPorPeriodo(int sucursalId, LocalDate desde, LocalDate hasta);

    /** Productos más vendidos (por cantidad) en el rango [desde, hasta], para la sucursal dada. */
    List<PlatilloVendido> obtenerPlatillosMasVendidos(int sucursalId, LocalDate desde, LocalDate hasta, int limite);

    /** Ventas del turno (para el corte de caja): desglose por método de pago y total de órdenes. */
    ResumenTurno obtenerResumenTurno(int turnoId);
}
