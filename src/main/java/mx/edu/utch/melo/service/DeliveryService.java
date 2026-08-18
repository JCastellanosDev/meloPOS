package mx.edu.utch.melo.service;

import mx.edu.utch.melo.dao.ClienteDAO;
import mx.edu.utch.melo.dao.OrdenDAO;
import mx.edu.utch.melo.model.Cliente;
import mx.edu.utch.melo.model.Orden;
import mx.edu.utch.melo.model.TipoOrden;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Órdenes activas de un canal que no se cobra al tomar el pedido (DOMICILIO o PARA_RECOGER),
 * para las pantallas que las listan: Delivery (DOMICILIO, ver DeliveryController) y Pedidos
 * (PARA_RECOGER, ver PedidosController -- órdenes creadas por {@code VentaService.crearOrdenDomicilio}
 * sin distancia calculada, ver su javadoc). Junta {@code OrdenDAO.obtenerActivasPorTipo(tipo)}
 * con una lectura por lote de {@link ClienteDAO#obtenerPorIds(List)} para resolver los clientes
 * de todas las órdenes -- mismo patrón de coordinar más de un DAO que
 * {@link PagoService#obtenerRecibo}, pero en 2 consultas totales en vez de 1+N (antes: una
 * lectura de {@code ClienteDAO.obtenerPorId} por cada orden dentro de un bucle -- N+1 clásico
 * contra MySQL para pantallas que recargan en cada regreso de foco y en cada cierre de popup
 * "Agregar"/"Cobrar"). Se eligió el batch sobre un JOIN nuevo en OrdenDAO para no tocar
 * OrdenDAO/OrdenDAOImpl -- otro cambio en paralelo ya los está tocando por cancelación de
 * órdenes -- y porque el batch ya deja esto en 2 consultas totales sin necesitar un DTO cruzado
 * Orden+Cliente nuevo. "Agregar"/"Cobrar" no viven aquí: no tocan DAO directamente, solo
 * preparan SesionActual y navegan (ver DeliveryController/PedidosController), así que no hay
 * nada de negocio que extraer de esa parte.
 */
public class DeliveryService {

    private final OrdenDAO ordenDAO;
    private final ClienteDAO clienteDAO;

    public DeliveryService(OrdenDAO ordenDAO, ClienteDAO clienteDAO) {
        this.ordenDAO = ordenDAO;
        this.clienteDAO = clienteDAO;
    }

    /** Órdenes activas (no pagadas ni canceladas) del tipo dado, con su cliente ya resuelto. */
    public List<PedidoActivo> obtenerPedidosActivos(TipoOrden tipo) {
        List<Orden> ordenes = ordenDAO.obtenerActivasPorTipo(tipo);

        List<Integer> idsClientes = ordenes.stream()
                .map(Orden::getClienteId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        Map<Integer, Cliente> clientesPorId = clienteDAO.obtenerPorIds(idsClientes).stream()
                .collect(Collectors.toMap(Cliente::getId, Function.identity()));

        List<PedidoActivo> resultado = new ArrayList<>();
        for (Orden orden : ordenes) {
            Cliente cliente = orden.getClienteId() == null ? null : clientesPorId.get(orden.getClienteId());
            resultado.add(new PedidoActivo(orden, cliente));
        }
        return resultado;
    }

    public record PedidoActivo(Orden orden, Cliente cliente) {
    }
}
