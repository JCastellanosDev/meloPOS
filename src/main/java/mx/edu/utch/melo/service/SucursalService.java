package mx.edu.utch.melo.service;

import mx.edu.utch.melo.dao.SucursalDAO;
import mx.edu.utch.melo.model.Rol;
import mx.edu.utch.melo.model.Sucursal;
import mx.edu.utch.melo.security.Auditoria;
import mx.edu.utch.melo.security.ControlAcceso;

import java.util.Optional;

/** Reglas de negocio sobre la sucursal, detrás de {@link SucursalDAO}. */
public class SucursalService {

    private final SucursalDAO sucursalDAO;

    public SucursalService(SucursalDAO sucursalDAO) {
        this.sucursalDAO = sucursalDAO;
    }

    public Sucursal obtenerPorId(int sucursalId) {
        return sucursalDAO.obtenerPorId(sucursalId).orElseThrow();
    }

    /**
     * Igual que {@link #obtenerPorId(int)}, pero sin lanzar si no existe -- para pantallas que
     * toleran una sucursal ausente y muestran un valor por defecto en vez de fallar (ver
     * MenuPOSController: encabezado con "—" y aplicaIva=true si la sucursal no se encontró).
     */
    public Optional<Sucursal> obtenerPorIdOpcional(int sucursalId) {
        return sucursalDAO.obtenerPorId(sucursalId);
    }

    /** Cada restaurante decide si cobra IVA o no (ver CLAUDE.md, sección Precios e IVA). */
    public boolean actualizarAplicaIva(Rol rolSolicitante, Sucursal sucursal, boolean aplicaIva) {
        ControlAcceso.exigirRol(rolSolicitante, "cambiar la configuración de IVA de la sucursal", Rol.ADMINISTRADOR);
        sucursal.setAplicaIva(aplicaIva);
        boolean actualizado = sucursalDAO.actualizar(sucursal);
        Auditoria.registrar(rolSolicitante, "cambio de configuración de IVA",
                "sucursalId=" + sucursal.getId() + " aplicaIva=" + aplicaIva);
        return actualizado;
    }
}
