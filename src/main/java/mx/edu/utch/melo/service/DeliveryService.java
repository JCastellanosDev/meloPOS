package mx.edu.utch.melo.service;

import mx.edu.utch.melo.dao.ClienteDAO;
import mx.edu.utch.melo.dao.OrdenDAO;
import mx.edu.utch.melo.model.Cliente;
import mx.edu.utch.melo.model.Orden;
import mx.edu.utch.melo.model.TipoOrden;

import java.util.ArrayList;
import java.util.List;

/**
 * Pedidos a domicilio activos para la pantalla de Delivery (ver DeliveryController). Junta
 * {@code OrdenDAO.obtenerActivasPorTipo(DOMICILIO)} con una lectura de {@link ClienteDAO} por
 * cada orden para resolver su cliente -- mismo patrón que {@link PagoService#obtenerRecibo}
 * (coordina más de un DAO para armar el DTO que necesita una pantalla, no delega 1:1 a un
 * único método). "Agregar"/"Cobrar" no viven aquí: no tocan DAO directamente, solo preparan
 * SesionActual y navegan (ver DeliveryController), así que no hay nada de negocio que extraer
 * de esa parte.
 */
public class DeliveryService {

    private final OrdenDAO ordenDAO;
    private final ClienteDAO clienteDAO;

    public DeliveryService(OrdenDAO ordenDAO, ClienteDAO clienteDAO) {
        this.ordenDAO = ordenDAO;
        this.clienteDAO = clienteDAO;
    }

    /** Órdenes DOMICILIO activas (no pagadas ni canceladas) con su cliente ya resuelto. */
    public List<PedidoActivo> obtenerPedidosActivos() {
        List<PedidoActivo> resultado = new ArrayList<>();
        for (Orden orden : ordenDAO.obtenerActivasPorTipo(TipoOrden.DOMICILIO)) {
            Cliente cliente = orden.getClienteId() == null
                    ? null
                    : clienteDAO.obtenerPorId(orden.getClienteId()).orElse(null);
            resultado.add(new PedidoActivo(orden, cliente));
        }
        return resultado;
    }

    public record PedidoActivo(Orden orden, Cliente cliente) {
    }
}
