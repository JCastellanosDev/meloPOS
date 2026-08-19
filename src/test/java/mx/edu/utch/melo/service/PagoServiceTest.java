package mx.edu.utch.melo.service;

import mx.edu.utch.melo.dao.DescuentoDAO;
import mx.edu.utch.melo.dao.DescuentoDAO.ConfiguracionDescuento;
import mx.edu.utch.melo.dao.DetalleOrdenDAO;
import mx.edu.utch.melo.dao.OrdenDAO;
import mx.edu.utch.melo.dao.PagoDAO;
import mx.edu.utch.melo.dao.ProductoDAO;
import mx.edu.utch.melo.dao.SucursalDAO;
import mx.edu.utch.melo.dao.TurnoDAO;
import mx.edu.utch.melo.dao.UsuarioDAO;
import mx.edu.utch.melo.db.TrabajoTransaccional;
import mx.edu.utch.melo.db.Transaccionador;
import mx.edu.utch.melo.model.DetalleOrden;
import mx.edu.utch.melo.model.EstadoOrden;
import mx.edu.utch.melo.model.MetodoPago;
import mx.edu.utch.melo.model.Modificador;
import mx.edu.utch.melo.model.ModificadorAplicado;
import mx.edu.utch.melo.model.Orden;
import mx.edu.utch.melo.model.Pago;
import mx.edu.utch.melo.model.Producto;
import mx.edu.utch.melo.model.Rol;
import mx.edu.utch.melo.model.Sucursal;
import mx.edu.utch.melo.model.TipoDescuento;
import mx.edu.utch.melo.model.TipoOrden;
import mx.edu.utch.melo.model.Turno;
import mx.edu.utch.melo.model.Usuario;
import mx.edu.utch.melo.security.AccesoDenegadoException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PagoServiceTest {

    private static final Transaccionador TRANSACCIONADOR_FALSO = new Transaccionador() {
        @Override
        public <T> T ejecutarEnTransaccion(TrabajoTransaccional<T> trabajo) {
            try {
                return trabajo.ejecutar(null);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    };

    @Test
    void obtenerReciboJuntaOrdenCajeroSucursalYLineas() {
        OrdenDAOFalso ordenDAO = new OrdenDAOFalso();
        ordenDAO.orden = ordenDeEjemplo();
        UsuarioDAOFalso usuarioDAO = new UsuarioDAOFalso();
        usuarioDAO.usuario = usuarioDeEjemplo();
        SucursalDAOFalso sucursalDAO = new SucursalDAOFalso();
        sucursalDAO.sucursal = new Sucursal();
        sucursalDAO.sucursal.setNombre("Crepas de Oro");
        DetalleOrdenDAOFalso detalleDAO = new DetalleOrdenDAOFalso();
        detalleDAO.detalles = List.of(detalleDeEjemplo(1, 10, 2, "50.00"));
        ProductoDAOFalso productoDAO = new ProductoDAOFalso();
        productoDAO.producto = productoDeEjemplo(10, "Tacos al Pastor");

        PagoService service = new PagoService(ordenDAO, detalleDAO, productoDAO, new PagoDAOFalso(),
                usuarioDAO, new TurnoDAOFalso(), sucursalDAO, new DescuentoDAOFalso(), TRANSACCIONADOR_FALSO);

        PagoService.DatosRecibo recibo = service.obtenerRecibo(1, 1);

        assertEquals("Crepas de Oro", recibo.sucursal().getNombre());
        assertEquals("Cajero Demo", recibo.cajero().getNombre());
        assertEquals(1, recibo.lineas().size());
        assertEquals("Tacos al Pastor", recibo.lineas().get(0).nombre());
        assertEquals(0, new BigDecimal("100.00").compareTo(recibo.lineas().get(0).monto()));
    }

    @Test
    void obtenerReciboUsaUnNombreDeRespaldoSiElProductoYaNoExiste() {
        OrdenDAOFalso ordenDAO = new OrdenDAOFalso();
        ordenDAO.orden = ordenDeEjemplo();
        DetalleOrdenDAOFalso detalleDAO = new DetalleOrdenDAOFalso();
        detalleDAO.detalles = List.of(detalleDeEjemplo(1, 999, 1, "50.00"));
        ProductoDAOFalso productoDAO = new ProductoDAOFalso();
        productoDAO.producto = null; // el producto fue eliminado después de la venta

        PagoService service = new PagoService(ordenDAO, detalleDAO, productoDAO, new PagoDAOFalso(),
                new UsuarioDAOFalso(), new TurnoDAOFalso(), new SucursalDAOFalso(), new DescuentoDAOFalso(), TRANSACCIONADOR_FALSO);

        PagoService.DatosRecibo recibo = service.obtenerRecibo(1, 1);

        assertEquals("Producto #999", recibo.lineas().get(0).nombre());
    }

    @Test
    void registrarPagoCreaElPagoYAvanzaLaOrdenAEnPreparacion() {
        OrdenDAOFalso ordenDAO = new OrdenDAOFalso();
        ordenDAO.orden = ordenDeEjemplo();
        PagoDAOFalso pagoDAO = new PagoDAOFalso();
        PagoService service = new PagoService(ordenDAO, new DetalleOrdenDAOFalso(), new ProductoDAOFalso(), pagoDAO,
                new UsuarioDAOFalso(), new TurnoDAOFalso(), new SucursalDAOFalso(), new DescuentoDAOFalso(), TRANSACCIONADOR_FALSO);

        boolean resultado = service.registrarPago(1, MetodoPago.EFECTIVO, new BigDecimal("371.20"), 9);

        assertTrue(resultado);
        assertEquals(MetodoPago.EFECTIVO, pagoDAO.ultimoCreado.getMetodoPago());
        assertEquals(0, new BigDecimal("371.20").compareTo(pagoDAO.ultimoCreado.getMonto()));
        assertEquals(EstadoOrden.EN_PREPARACION, ordenDAO.orden.getEstado());
    }

    @Test
    void registrarPagoAsignaElTurnoAbiertoDelCajero() {
        OrdenDAOFalso ordenDAO = new OrdenDAOFalso();
        ordenDAO.orden = ordenDeEjemplo();
        TurnoDAOFalso turnoDAO = new TurnoDAOFalso();
        Turno turnoAbierto = new Turno();
        turnoAbierto.setId(77);
        turnoDAO.turnoAbierto = turnoAbierto;
        PagoService service = new PagoService(ordenDAO, new DetalleOrdenDAOFalso(), new ProductoDAOFalso(),
                new PagoDAOFalso(), new UsuarioDAOFalso(), turnoDAO, new SucursalDAOFalso(), new DescuentoDAOFalso(), TRANSACCIONADOR_FALSO);

        service.registrarPago(1, MetodoPago.EFECTIVO, new BigDecimal("371.20"), 9);

        assertEquals(77, ordenDAO.orden.getTurnoId());
    }

    @Test
    void registrarPagoNoBloqueaElCobroSiElCajeroNoTieneTurnoAbierto() {
        // ver CLAUDE.md: si el cajero olvidó abrir turno, el cobro no se bloquea -- queda con
        // turno_id null, igual que hoy.
        OrdenDAOFalso ordenDAO = new OrdenDAOFalso();
        ordenDAO.orden = ordenDeEjemplo();
        TurnoDAOFalso turnoDAO = new TurnoDAOFalso();
        turnoDAO.turnoAbierto = null;
        PagoService service = new PagoService(ordenDAO, new DetalleOrdenDAOFalso(), new ProductoDAOFalso(),
                new PagoDAOFalso(), new UsuarioDAOFalso(), turnoDAO, new SucursalDAOFalso(), new DescuentoDAOFalso(), TRANSACCIONADOR_FALSO);

        boolean resultado = service.registrarPago(1, MetodoPago.TARJETA, new BigDecimal("371.20"), 9);

        assertTrue(resultado);
        assertNull(ordenDAO.orden.getTurnoId());
    }

    @Test
    void registrarPagoSiFallaLaCreacionDelPagoLaOrdenNoAvanzaDeEstado() {
        // ver PagoService: registrar el pago y avanzar la orden es una sola operación lógica
        // dentro de una transacción -- si falla crear el Pago, la orden nunca debe llegar a
        // EN_PREPARACION (la base de datos real revierte todo vía Transaccionador; aquí se
        // verifica que el Service ni siquiera intenta avanzar el estado tras la falla).
        OrdenDAOFalso ordenDAO = new OrdenDAOFalso();
        ordenDAO.orden = ordenDeEjemplo();
        PagoDAOFalso pagoDAO = new PagoDAOFalso();
        pagoDAO.lanzarAlCrear = true;
        PagoService service = new PagoService(ordenDAO, new DetalleOrdenDAOFalso(), new ProductoDAOFalso(), pagoDAO,
                new UsuarioDAOFalso(), new TurnoDAOFalso(), new SucursalDAOFalso(), new DescuentoDAOFalso(), TRANSACCIONADOR_FALSO);

        assertThrows(RuntimeException.class,
                () -> service.registrarPago(1, MetodoPago.EFECTIVO, new BigDecimal("371.20"), 9));
        assertEquals(EstadoOrden.PENDIENTE, ordenDAO.orden.getEstado());
        assertNull(ordenDAO.orden.getTurnoId());
    }

    @Test
    void registrarPagoDeUnaOrdenDomicilioLaAvanzaAEntregadaNoAEnPreparacion() {
        // ver CLAUDE.md: domicilio se cobra al entregar, no antes de preparar -- para esa orden
        // el pago es el ÚLTIMO paso, así que regresarla a EN_PREPARACION la resucitaría como
        // pedido activo en Kitchen Display (regresión real, ver PagoService.estadoTrasPago).
        OrdenDAOFalso ordenDAO = new OrdenDAOFalso();
        ordenDAO.orden = ordenDeEjemplo();
        ordenDAO.orden.setTipoOrden(TipoOrden.DOMICILIO);
        ordenDAO.orden.setEstado(EstadoOrden.EN_PREPARACION);
        PagoService service = new PagoService(ordenDAO, new DetalleOrdenDAOFalso(), new ProductoDAOFalso(),
                new PagoDAOFalso(), new UsuarioDAOFalso(), new TurnoDAOFalso(), new SucursalDAOFalso(), new DescuentoDAOFalso(), TRANSACCIONADOR_FALSO);

        service.registrarPago(1, MetodoPago.EFECTIVO, new BigDecimal("371.20"), 9);

        assertEquals(EstadoOrden.ENTREGADA, ordenDAO.orden.getEstado());
    }

    @Test
    void registrarPagoDeUnaOrdenParaRecogerLaAvanzaAEntregadaNoAEnPreparacion() {
        OrdenDAOFalso ordenDAO = new OrdenDAOFalso();
        ordenDAO.orden = ordenDeEjemplo();
        ordenDAO.orden.setTipoOrden(TipoOrden.PARA_RECOGER);
        // igual que DOMICILIO: nace en EN_PREPARACION y se cobra ahí (ver
        // PagoService.estadoEsperadoAntesDePagar) -- CLAUDE.md agrupa pickup/domicilio bajo el
        // mismo flujo "Ordenar -> Preparar -> Recoger/Entregar -> Pagar".
        ordenDAO.orden.setEstado(EstadoOrden.EN_PREPARACION);
        PagoService service = new PagoService(ordenDAO, new DetalleOrdenDAOFalso(), new ProductoDAOFalso(),
                new PagoDAOFalso(), new UsuarioDAOFalso(), new TurnoDAOFalso(), new SucursalDAOFalso(), new DescuentoDAOFalso(), TRANSACCIONADOR_FALSO);

        service.registrarPago(1, MetodoPago.EFECTIVO, new BigDecimal("371.20"), 9);

        assertEquals(EstadoOrden.ENTREGADA, ordenDAO.orden.getEstado());
    }

    @Test
    void registrarPagoDeUnaOrdenDomicilioEnListaSeCobraSinProblema() {
        // Bug real reportado en producción: KitchenDisplayController.marcarEntregada manda las
        // órdenes de DOMICILIO/PARA_RECOGER a LISTA (no directo a ENTREGADA) al completarse en
        // cocina, justo para que sigan siendo cobrables en Delivery -- pero una versión anterior
        // de estaEnEstadoCobrable solo aceptaba EN_PREPARACION, así que CUALQUIER pedido a
        // domicilio que ya hubiera pasado por cocina (el caso normal) rechazaba el cobro con
        // OrdenYaFueCobradaException, aunque nunca se había pagado.
        OrdenDAOFalso ordenDAO = new OrdenDAOFalso();
        ordenDAO.orden = ordenDeEjemplo();
        ordenDAO.orden.setTipoOrden(TipoOrden.DOMICILIO);
        ordenDAO.orden.setEstado(EstadoOrden.LISTA);
        PagoService service = new PagoService(ordenDAO, new DetalleOrdenDAOFalso(), new ProductoDAOFalso(),
                new PagoDAOFalso(), new UsuarioDAOFalso(), new TurnoDAOFalso(), new SucursalDAOFalso(), new DescuentoDAOFalso(), TRANSACCIONADOR_FALSO);

        service.registrarPago(1, MetodoPago.EFECTIVO, new BigDecimal("371.20"), 9);

        assertEquals(EstadoOrden.ENTREGADA, ordenDAO.orden.getEstado());
    }

    @Test
    void registrarPagoDeUnaOrdenParaLlevarSigueAvanzandoAEnPreparacion() {
        // contraste explícito con domicilio/para_recoger: para_llevar sigue cobrando antes de
        // preparar (ver CLAUDE.md), así que no debe entrar en la rama de "pago es el último paso".
        OrdenDAOFalso ordenDAO = new OrdenDAOFalso();
        ordenDAO.orden = ordenDeEjemplo();
        ordenDAO.orden.setTipoOrden(TipoOrden.PARA_LLEVAR);
        PagoService service = new PagoService(ordenDAO, new DetalleOrdenDAOFalso(), new ProductoDAOFalso(),
                new PagoDAOFalso(), new UsuarioDAOFalso(), new TurnoDAOFalso(), new SucursalDAOFalso(), new DescuentoDAOFalso(), TRANSACCIONADOR_FALSO);

        service.registrarPago(1, MetodoPago.EFECTIVO, new BigDecimal("371.20"), 9);

        assertEquals(EstadoOrden.EN_PREPARACION, ordenDAO.orden.getEstado());
    }

    @Test
    void registrarPagoDivididoCreaDosPagosYAvanzaLaOrdenUnaSolaVez() {
        OrdenDAOFalso ordenDAO = new OrdenDAOFalso();
        ordenDAO.orden = ordenDeEjemplo();
        PagoDAOFalso pagoDAO = new PagoDAOFalso();
        PagoService service = new PagoService(ordenDAO, new DetalleOrdenDAOFalso(), new ProductoDAOFalso(), pagoDAO,
                new UsuarioDAOFalso(), new TurnoDAOFalso(), new SucursalDAOFalso(), new DescuentoDAOFalso(), TRANSACCIONADOR_FALSO);

        boolean resultado = service.registrarPagoDividido(1, new BigDecimal("200.00"), new BigDecimal("171.20"), 9);

        assertTrue(resultado);
        assertEquals(2, pagoDAO.creados.size());
        assertEquals(MetodoPago.EFECTIVO, pagoDAO.creados.get(0).getMetodoPago());
        assertEquals(0, new BigDecimal("200.00").compareTo(pagoDAO.creados.get(0).getMonto()));
        assertEquals(MetodoPago.TARJETA, pagoDAO.creados.get(1).getMetodoPago());
        assertEquals(0, new BigDecimal("171.20").compareTo(pagoDAO.creados.get(1).getMonto()));
        assertEquals(EstadoOrden.EN_PREPARACION, ordenDAO.orden.getEstado());
    }

    @Test
    void registrarPagoDivididoOmiteElMetodoConMontoCero() {
        OrdenDAOFalso ordenDAO = new OrdenDAOFalso();
        ordenDAO.orden = ordenDeEjemplo();
        PagoDAOFalso pagoDAO = new PagoDAOFalso();
        PagoService service = new PagoService(ordenDAO, new DetalleOrdenDAOFalso(), new ProductoDAOFalso(), pagoDAO,
                new UsuarioDAOFalso(), new TurnoDAOFalso(), new SucursalDAOFalso(), new DescuentoDAOFalso(), TRANSACCIONADOR_FALSO);

        service.registrarPagoDividido(1, new BigDecimal("371.20"), BigDecimal.ZERO, 9);

        assertEquals(1, pagoDAO.creados.size(), "un monto en cero no debe generar un Pago vacío");
        assertEquals(MetodoPago.EFECTIVO, pagoDAO.creados.get(0).getMetodoPago());
    }

    @Test
    void registrarPagoDivididoLanzaSiLaSumaNoCoincideConElTotal() {
        OrdenDAOFalso ordenDAO = new OrdenDAOFalso();
        ordenDAO.orden = ordenDeEjemplo();
        PagoDAOFalso pagoDAO = new PagoDAOFalso();
        PagoService service = new PagoService(ordenDAO, new DetalleOrdenDAOFalso(), new ProductoDAOFalso(), pagoDAO,
                new UsuarioDAOFalso(), new TurnoDAOFalso(), new SucursalDAOFalso(), new DescuentoDAOFalso(), TRANSACCIONADOR_FALSO);

        assertThrows(PagoService.MontoPagadoNoCoincideException.class,
                () -> service.registrarPagoDividido(1, new BigDecimal("100.00"), new BigDecimal("100.00"), 9));
        assertTrue(pagoDAO.creados.isEmpty(), "si la suma no coincide, no debe crear ningún pago");
        assertEquals(EstadoOrden.PENDIENTE, ordenDAO.orden.getEstado());
    }

    @Test
    void registrarPagoRechazaElTotalOriginalDespuesDeAplicarUnDescuento() {
        // ver aplicarDescuento/registrarPago: una vez aplicado el descuento, orden.getTotal() ya
        // es el monto descontado -- cobrar el monto ORIGINAL (sin descuento) ya no coincide con
        // lo que la orden vale ahora, y debe rechazarse igual que cualquier otro monto incorrecto.
        OrdenDAOFalso ordenDAO = new OrdenDAOFalso();
        ordenDAO.orden = ordenDeEjemplo(); // total 371.20
        PagoDAOFalso pagoDAO = new PagoDAOFalso();
        PagoService service = new PagoService(ordenDAO, new DetalleOrdenDAOFalso(), new ProductoDAOFalso(), pagoDAO,
                new UsuarioDAOFalso(), new TurnoDAOFalso(), new SucursalDAOFalso(), new DescuentoDAOFalso(), TRANSACCIONADOR_FALSO);

        service.aplicarDescuento(Rol.ADMINISTRADOR, 1, TipoDescuento.PROMOCION); // 10% -> total 334.08

        assertThrows(PagoService.MontoPagadoNoCoincideException.class,
                () -> service.registrarPago(1, MetodoPago.EFECTIVO, new BigDecimal("371.20"), 9));
        assertTrue(pagoDAO.creados.isEmpty(), "no debe registrar ningún pago si el monto no coincide con el total ya descontado");
    }

    @Test
    void registrarPagoAceptaElTotalYaDescontadoDespuesDeAplicarUnDescuento() {
        OrdenDAOFalso ordenDAO = new OrdenDAOFalso();
        ordenDAO.orden = ordenDeEjemplo(); // total 371.20
        PagoDAOFalso pagoDAO = new PagoDAOFalso();
        PagoService service = new PagoService(ordenDAO, new DetalleOrdenDAOFalso(), new ProductoDAOFalso(), pagoDAO,
                new UsuarioDAOFalso(), new TurnoDAOFalso(), new SucursalDAOFalso(), new DescuentoDAOFalso(), TRANSACCIONADOR_FALSO);

        service.aplicarDescuento(Rol.ADMINISTRADOR, 1, TipoDescuento.PROMOCION); // 10% -> total 334.08
        boolean resultado = service.registrarPago(1, MetodoPago.EFECTIVO, new BigDecimal("334.08"), 9);

        assertTrue(resultado);
        assertEquals(0, new BigDecimal("334.08").compareTo(pagoDAO.ultimoCreado.getMonto()));
        assertEquals(EstadoOrden.EN_PREPARACION, ordenDAO.orden.getEstado());
    }

    @Test
    void aplicarDescuentoRestaElPorcentajeDeLaCategoriaDelTotal() {
        OrdenDAOFalso ordenDAO = new OrdenDAOFalso();
        ordenDAO.orden = ordenDeEjemplo(); // total 371.20
        PagoService service = new PagoService(ordenDAO, new DetalleOrdenDAOFalso(), new ProductoDAOFalso(),
                new PagoDAOFalso(), new UsuarioDAOFalso(), new TurnoDAOFalso(), new SucursalDAOFalso(), new DescuentoDAOFalso(), TRANSACCIONADOR_FALSO);

        Orden actualizada = service.aplicarDescuento(Rol.ADMINISTRADOR, 1, TipoDescuento.PROMOCION); // 10%

        assertEquals(TipoDescuento.PROMOCION, actualizada.getTipoDescuento());
        assertEquals(0, new BigDecimal("37.12").compareTo(actualizada.getMontoDescuento()));
        assertEquals(0, new BigDecimal("334.08").compareTo(actualizada.getTotal()));
    }

    @Test
    void aplicarDescuentoRechazaARolesQueNoSonAdministrador() {
        OrdenDAOFalso ordenDAO = new OrdenDAOFalso();
        ordenDAO.orden = ordenDeEjemplo();
        PagoService service = new PagoService(ordenDAO, new DetalleOrdenDAOFalso(), new ProductoDAOFalso(),
                new PagoDAOFalso(), new UsuarioDAOFalso(), new TurnoDAOFalso(), new SucursalDAOFalso(), new DescuentoDAOFalso(), TRANSACCIONADOR_FALSO);

        assertThrows(AccesoDenegadoException.class, () -> service.aplicarDescuento(Rol.CAJERO, 1, TipoDescuento.PROMOCION));
        assertNull(ordenDAO.orden.getTipoDescuento());
    }

    @Test
    void aplicarDescuentoReemplazaUnDescuentoPrevioRecalculandoDesdeElTotalOriginal() {
        OrdenDAOFalso ordenDAO = new OrdenDAOFalso();
        ordenDAO.orden = ordenDeEjemplo(); // total 371.20
        PagoService service = new PagoService(ordenDAO, new DetalleOrdenDAOFalso(), new ProductoDAOFalso(),
                new PagoDAOFalso(), new UsuarioDAOFalso(), new TurnoDAOFalso(), new SucursalDAOFalso(), new DescuentoDAOFalso(), TRANSACCIONADOR_FALSO);

        service.aplicarDescuento(Rol.ADMINISTRADOR, 1, TipoDescuento.EMPLEADO); // 20% -> total 296.96
        Orden actualizada = service.aplicarDescuento(Rol.ADMINISTRADOR, 1, TipoDescuento.PROMOCION); // 10% del original

        assertEquals(TipoDescuento.PROMOCION, actualizada.getTipoDescuento());
        assertEquals(0, new BigDecimal("37.12").compareTo(actualizada.getMontoDescuento()));
        assertEquals(0, new BigDecimal("334.08").compareTo(actualizada.getTotal()));
    }

    @Test
    void quitarDescuentoRegresaElTotalOriginal() {
        OrdenDAOFalso ordenDAO = new OrdenDAOFalso();
        ordenDAO.orden = ordenDeEjemplo();
        PagoService service = new PagoService(ordenDAO, new DetalleOrdenDAOFalso(), new ProductoDAOFalso(),
                new PagoDAOFalso(), new UsuarioDAOFalso(), new TurnoDAOFalso(), new SucursalDAOFalso(), new DescuentoDAOFalso(), TRANSACCIONADOR_FALSO);
        service.aplicarDescuento(Rol.ADMINISTRADOR, 1, TipoDescuento.EMPLEADO);

        Orden actualizada = service.quitarDescuento(1);

        assertNull(actualizada.getTipoDescuento());
        assertEquals(0, BigDecimal.ZERO.compareTo(actualizada.getMontoDescuento()));
        assertEquals(0, new BigDecimal("371.20").compareTo(actualizada.getTotal()));
    }

    @Test
    void quitarDescuentoSinDescuentoPrevioNoCambiaNada() {
        OrdenDAOFalso ordenDAO = new OrdenDAOFalso();
        ordenDAO.orden = ordenDeEjemplo();
        PagoService service = new PagoService(ordenDAO, new DetalleOrdenDAOFalso(), new ProductoDAOFalso(),
                new PagoDAOFalso(), new UsuarioDAOFalso(), new TurnoDAOFalso(), new SucursalDAOFalso(), new DescuentoDAOFalso(), TRANSACCIONADOR_FALSO);

        Orden resultado = service.quitarDescuento(1);

        assertEquals(0, new BigDecimal("371.20").compareTo(resultado.getTotal()));
    }

    @Test
    void quitarDescuentoConRolRechazaARolesQueNoSonAdministrador() {
        // asimetría de autorización encontrada en esta auditoría: la sobrecarga con Rol es la
        // versión segura -- ver PagoService.quitarDescuento(Rol, int) y su Javadoc, y el Javadoc de
        // quitarDescuento(int) (deprecated) que documenta por qué la insegura sigue existiendo.
        OrdenDAOFalso ordenDAO = new OrdenDAOFalso();
        ordenDAO.orden = ordenDeEjemplo();
        PagoService service = new PagoService(ordenDAO, new DetalleOrdenDAOFalso(), new ProductoDAOFalso(),
                new PagoDAOFalso(), new UsuarioDAOFalso(), new TurnoDAOFalso(), new SucursalDAOFalso(), new DescuentoDAOFalso(), TRANSACCIONADOR_FALSO);
        service.aplicarDescuento(Rol.ADMINISTRADOR, 1, TipoDescuento.EMPLEADO); // 20% -> total 296.96

        assertThrows(AccesoDenegadoException.class, () -> service.quitarDescuento(Rol.CAJERO, 1));
        assertEquals(TipoDescuento.EMPLEADO, ordenDAO.orden.getTipoDescuento(), "el descuento no debe quitarse si el rol no autoriza");
        assertEquals(0, new BigDecimal("296.96").compareTo(ordenDAO.orden.getTotal()));
    }

    @Test
    void quitarDescuentoConRolPermiteAAdministradorYRegresaElTotalOriginal() {
        OrdenDAOFalso ordenDAO = new OrdenDAOFalso();
        ordenDAO.orden = ordenDeEjemplo();
        PagoService service = new PagoService(ordenDAO, new DetalleOrdenDAOFalso(), new ProductoDAOFalso(),
                new PagoDAOFalso(), new UsuarioDAOFalso(), new TurnoDAOFalso(), new SucursalDAOFalso(), new DescuentoDAOFalso(), TRANSACCIONADOR_FALSO);
        service.aplicarDescuento(Rol.ADMINISTRADOR, 1, TipoDescuento.EMPLEADO);

        Orden actualizada = service.quitarDescuento(Rol.ADMINISTRADOR, 1);

        assertNull(actualizada.getTipoDescuento());
        assertEquals(0, new BigDecimal("371.20").compareTo(actualizada.getTotal()));
    }

    @Test
    void registrarPagoRechazaMontoNegativo() {
        // ver auditoría de integridad de pagos: ningún escenario de negocio válido paga un monto
        // negativo -- la guardia debe rechazarlo de forma explícita, no depender solo de que el
        // total de la orden nunca sea negativo (invariante indirecta, no una regla enforced aquí).
        OrdenDAOFalso ordenDAO = new OrdenDAOFalso();
        ordenDAO.orden = ordenDeEjemplo();
        PagoDAOFalso pagoDAO = new PagoDAOFalso();
        PagoService service = new PagoService(ordenDAO, new DetalleOrdenDAOFalso(), new ProductoDAOFalso(), pagoDAO,
                new UsuarioDAOFalso(), new TurnoDAOFalso(), new SucursalDAOFalso(), new DescuentoDAOFalso(), TRANSACCIONADOR_FALSO);

        assertThrows(PagoService.MontoInvalidoException.class,
                () -> service.registrarPago(1, MetodoPago.EFECTIVO, new BigDecimal("-10.00"), 9));
        assertTrue(pagoDAO.creados.isEmpty());
        assertEquals(EstadoOrden.PENDIENTE, ordenDAO.orden.getEstado());
    }

    @Test
    void registrarPagoAceptaMontoCeroCuandoElTotalYaEsCeroPorCortesia() {
        // contraste con el caso negativo: CORTESIA (ver TipoDescuento) es un descuento del 100% --
        // un total legítimamente en cero, que debe poder "cobrarse" con un pago de monto cero para
        // avanzar la orden igual que cualquier otro cobro.
        OrdenDAOFalso ordenDAO = new OrdenDAOFalso();
        ordenDAO.orden = ordenDeEjemplo();
        PagoDAOFalso pagoDAO = new PagoDAOFalso();
        PagoService service = new PagoService(ordenDAO, new DetalleOrdenDAOFalso(), new ProductoDAOFalso(), pagoDAO,
                new UsuarioDAOFalso(), new TurnoDAOFalso(), new SucursalDAOFalso(), new DescuentoDAOFalso(), TRANSACCIONADOR_FALSO);
        service.aplicarDescuento(Rol.ADMINISTRADOR, 1, TipoDescuento.CORTESIA); // 100% -> total 0.00

        boolean resultado = service.registrarPago(1, MetodoPago.EFECTIVO, BigDecimal.ZERO, 9);

        assertTrue(resultado);
        assertEquals(EstadoOrden.EN_PREPARACION, ordenDAO.orden.getEstado());
    }

    @Test
    void registrarPagoRechazaUnSegundoCobroSobreUnaOrdenYaPagada() {
        // ver auditoría de integridad de pagos: el total de la orden NO cambia entre el primer y
        // el segundo intento, así que sin esta guardia "la suma coincide con el total" por sí sola
        // no detecta el doble cobro -- este test reproduce justo ese escenario.
        OrdenDAOFalso ordenDAO = new OrdenDAOFalso();
        ordenDAO.orden = ordenDeEjemplo();
        PagoDAOFalso pagoDAO = new PagoDAOFalso();
        PagoService service = new PagoService(ordenDAO, new DetalleOrdenDAOFalso(), new ProductoDAOFalso(), pagoDAO,
                new UsuarioDAOFalso(), new TurnoDAOFalso(), new SucursalDAOFalso(), new DescuentoDAOFalso(), TRANSACCIONADOR_FALSO);
        service.registrarPago(1, MetodoPago.EFECTIVO, new BigDecimal("371.20"), 9);
        assertEquals(1, pagoDAO.creados.size());

        assertThrows(PagoService.OrdenYaFueCobradaException.class,
                () -> service.registrarPago(1, MetodoPago.EFECTIVO, new BigDecimal("371.20"), 9));
        assertEquals(1, pagoDAO.creados.size(), "el segundo intento no debe crear un segundo Pago");
    }

    @Test
    void registrarPagoRechazaCobrarUnaOrdenCancelada() {
        OrdenDAOFalso ordenDAO = new OrdenDAOFalso();
        ordenDAO.orden = ordenDeEjemplo();
        ordenDAO.orden.setEstado(EstadoOrden.CANCELADA);
        PagoDAOFalso pagoDAO = new PagoDAOFalso();
        PagoService service = new PagoService(ordenDAO, new DetalleOrdenDAOFalso(), new ProductoDAOFalso(), pagoDAO,
                new UsuarioDAOFalso(), new TurnoDAOFalso(), new SucursalDAOFalso(), new DescuentoDAOFalso(), TRANSACCIONADOR_FALSO);

        assertThrows(PagoService.OrdenCanceladaException.class,
                () -> service.registrarPago(1, MetodoPago.EFECTIVO, new BigDecimal("371.20"), 9));
        assertTrue(pagoDAO.creados.isEmpty());
    }

    private static Orden ordenDeEjemplo() {
        Orden orden = new Orden();
        orden.setId(1);
        orden.setTipoOrden(TipoOrden.COMEDOR);
        orden.setUsuarioId(9);
        orden.setEstado(EstadoOrden.PENDIENTE);
        orden.setSubtotal(new BigDecimal("320.00"));
        orden.setImpuestos(new BigDecimal("51.20"));
        orden.setCostoEnvio(BigDecimal.ZERO);
        orden.setTotal(new BigDecimal("371.20"));
        return orden;
    }

    private static Usuario usuarioDeEjemplo() {
        Usuario usuario = new Usuario();
        usuario.setId(9);
        usuario.setNombre("Cajero Demo");
        return usuario;
    }

    private static DetalleOrden detalleDeEjemplo(int ordenId, int productoId, int cantidad, String precioUnitario) {
        DetalleOrden detalle = new DetalleOrden();
        detalle.setOrdenId(ordenId);
        detalle.setProductoId(productoId);
        detalle.setCantidad(cantidad);
        detalle.setPrecioUnitario(new BigDecimal(precioUnitario));
        return detalle;
    }

    private static Producto productoDeEjemplo(int id, String nombre) {
        Producto producto = new Producto();
        producto.setId(id);
        producto.setNombre(nombre);
        return producto;
    }

    private static class OrdenDAOFalso implements OrdenDAO {
        Orden orden;

        @Override
        public Orden crear(Orden orden) {
            return orden;
        }

        @Override
        public Orden crear(Orden orden, Connection conexion) {
            return orden;
        }

        @Override
        public Optional<Orden> obtenerPorId(Integer id) {
            return Optional.ofNullable(orden);
        }

        @Override
        public Optional<Orden> obtenerPorId(Integer id, Connection conexion) {
            return Optional.ofNullable(orden);
        }

        @Override
        public List<Orden> obtenerTodos() {
            return List.of();
        }

        @Override
        public boolean actualizar(Orden orden) {
            return true;
        }

        @Override
        public boolean actualizar(Orden orden, Connection conexion) {
            this.orden = orden;
            return true;
        }

        @Override
        public boolean eliminar(Integer id) {
            return true;
        }

        @Override
        public List<Orden> obtenerPorEstado(EstadoOrden estado) {
            return List.of();
        }

        @Override
        public List<Orden> obtenerActivasPorMesa(int mesaId) {
            return List.of();
        }

        @Override
        public List<Orden> obtenerActivasPorTipo(TipoOrden tipoOrden) {
            return List.of();
        }

        @Override
        public int siguienteNumeroOrden(int sucursalId) {
            return 1;
        }

        @Override
        public boolean cancelar(int ordenId, Connection conexion) {
            if (orden != null && orden.getId() == ordenId) {
                orden.setEstado(EstadoOrden.CANCELADA);
                return true;
            }
            return false;
        }
    }

    private static class DetalleOrdenDAOFalso implements DetalleOrdenDAO {
        List<DetalleOrden> detalles = List.of();

        @Override
        public DetalleOrden crear(DetalleOrden detalle) {
            return detalle;
        }

        @Override
        public DetalleOrden crear(DetalleOrden detalle, Connection conexion) {
            return detalle;
        }

        @Override
        public Optional<DetalleOrden> obtenerPorId(Integer id) {
            return Optional.empty();
        }

        @Override
        public List<DetalleOrden> obtenerTodos() {
            return List.of();
        }

        @Override
        public boolean actualizar(DetalleOrden detalle) {
            return true;
        }

        @Override
        public boolean eliminar(Integer id) {
            return true;
        }

        @Override
        public List<DetalleOrden> obtenerPorOrden(int ordenId) {
            return detalles;
        }

        @Override
        public List<DetalleOrden> obtenerPorOrden(int ordenId, Connection conexion) {
            return detalles;
        }

        @Override
        public List<ModificadorAplicado> obtenerModificadores(int detalleOrdenId) {
            return List.of();
        }

        @Override
        public void agregarModificador(int detalleOrdenId, int modificadorId, BigDecimal precioExtra) {
        }
    }

    private static class ProductoDAOFalso implements ProductoDAO {
        Producto producto;

        @Override
        public Producto crear(Producto producto) {
            return producto;
        }

        @Override
        public Optional<Producto> obtenerPorId(Integer id) {
            return Optional.ofNullable(producto);
        }

        @Override
        public List<Producto> obtenerTodos() {
            return List.of();
        }

        @Override
        public boolean actualizar(Producto producto) {
            return true;
        }

        @Override
        public boolean eliminar(Integer id) {
            return true;
        }

        @Override
        public List<Producto> obtenerTodosActivos() {
            return List.of();
        }

        @Override
        public List<Producto> obtenerPorCategoria(int categoriaId) {
            return List.of();
        }

        @Override
        public List<Producto> obtenerPorSucursal(int sucursalId) {
            return List.of();
        }

        @Override
        public List<Modificador> obtenerModificadores(int productoId) {
            return List.of();
        }

        @Override
        public void asignarModificador(int productoId, int modificadorId) {
        }

        @Override
        public void quitarModificador(int productoId, int modificadorId) {
        }

        @Override
        public boolean descontarStock(int productoId, int cantidad, Connection conexion) {
            return true;
        }

        @Override
        public void restaurarStock(int productoId, int cantidad, Connection conexion) {
        }
    }

    private static class PagoDAOFalso implements PagoDAO {
        Pago ultimoCreado;
        final List<Pago> creados = new java.util.ArrayList<>();
        boolean lanzarAlCrear;

        @Override
        public Pago crear(Pago pago) {
            return crear(pago, null);
        }

        @Override
        public Pago crear(Pago pago, Connection conexion) {
            if (lanzarAlCrear) {
                throw new RuntimeException("fallo simulado al insertar el pago");
            }
            this.ultimoCreado = pago;
            this.creados.add(pago);
            return pago;
        }

        @Override
        public Optional<Pago> obtenerPorId(Integer id) {
            return Optional.empty();
        }

        @Override
        public List<Pago> obtenerTodos() {
            return List.of();
        }

        @Override
        public boolean actualizar(Pago pago) {
            return true;
        }

        @Override
        public boolean eliminar(Integer id) {
            return true;
        }

        @Override
        public List<Pago> obtenerPorOrden(int ordenId) {
            return List.of();
        }
    }

    private static class UsuarioDAOFalso implements UsuarioDAO {
        Usuario usuario;

        @Override
        public Usuario crear(Usuario usuario) {
            return usuario;
        }

        @Override
        public Optional<Usuario> obtenerPorId(Integer id) {
            return Optional.ofNullable(usuario);
        }

        @Override
        public List<Usuario> obtenerTodos() {
            return List.of();
        }

        @Override
        public boolean actualizar(Usuario usuario) {
            return true;
        }

        @Override
        public boolean eliminar(Integer id) {
            return true;
        }

        @Override
        public List<Usuario> obtenerPorSucursal(int sucursalId) {
            return List.of();
        }
    }

    private static class TurnoDAOFalso implements TurnoDAO {
        Turno turnoAbierto;

        @Override
        public Turno crear(Turno turno) {
            return turno;
        }

        @Override
        public Optional<Turno> obtenerPorId(Integer id) {
            return Optional.empty();
        }

        @Override
        public List<Turno> obtenerTodos() {
            return List.of();
        }

        @Override
        public boolean actualizar(Turno turno) {
            return true;
        }

        @Override
        public boolean eliminar(Integer id) {
            return true;
        }

        @Override
        public Optional<Turno> obtenerTurnoAbierto(int usuarioId) {
            return Optional.ofNullable(turnoAbierto);
        }
    }

    private static class SucursalDAOFalso implements SucursalDAO {
        Sucursal sucursal = new Sucursal();

        @Override
        public Sucursal crear(Sucursal sucursal) {
            return sucursal;
        }

        @Override
        public Optional<Sucursal> obtenerPorId(Integer id) {
            return Optional.ofNullable(sucursal);
        }

        @Override
        public List<Sucursal> obtenerTodos() {
            return List.of();
        }

        @Override
        public boolean actualizar(Sucursal sucursal) {
            return true;
        }

        @Override
        public boolean eliminar(Integer id) {
            return true;
        }

        @Override
        public List<Sucursal> obtenerActivas() {
            return List.of();
        }
    }

    /** Porcentajes iguales a los que traía TipoDescuento como constante, para no cambiar el
     * resultado de ninguna prueba existente que no sea específicamente sobre el porcentaje. */
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
