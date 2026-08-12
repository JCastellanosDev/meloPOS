# melo — Contexto de negocio y reglas del proyecto

Este archivo documenta el **negocio** detrás de melo (no la arquitectura de código, que ya es autoexplicativa desde `nav/`, `model/`, `util/`, `validation/`, `view/`, `controller/`). Está pensado para que cualquier sesión de Claude Code entienda las reglas reales antes de tocar una pantalla o proponer un modelo de datos.

Este documento se construyó por entrevista directa con el dueño del proyecto. Donde la respuesta fue "no es prioridad ahora" o quedó ambigua, se marca explícitamente como **pendiente** — no se debe asumir una regla que no está aquí.

## Qué es melo

Sistema de punto de venta (POS) de escritorio para un restaurante, hecho en JavaFX. Opera en español (México), en pesos (MXN).

**Tipo de negocio**: melo es un **POS genérico para negocios de comida** — pensado para operar tanto en un local individual como en cadenas de restaurantes. No es el sistema interno de un único restaurante con identidad propia; es una plataforma de punto de venta que cualquier negocio de comida (fonda, restaurante, cadena) podría usar.

**Multi-sucursal: CONFIRMADO, ya está en el esquema (`db/schema.sql`).** No asumas un solo menú, un solo inventario o una sola caja global — cada sucursal tiene sus propios `productos` (precio, disponibilidad e inventario propios), `usuarios`, `mesas` y `turnos`. `categorias`, `modificadores` y `clientes` son compartidos por toda la cadena. `ordenes` NO guarda `sucursal_id` propio: se deriva de `usuario_id → usuarios.sucursal_id` (evita duplicidad, ver el comentario al inicio de `schema.sql`).

## Roles de usuario

El sistema debe distinguir estos cuatro roles (**aún no implementado** — hoy no hay login ni permisos, cualquiera puede tocar cualquier pantalla):

- **Mesero**: toma pedidos, asigna y gestiona mesas.
- **Cajero**: cobra cuentas, abre y cierra turno/caja.
- **Cocina**: ve los pedidos entrantes y los marca como completados.
- **Administrador/Gerente**: reportes, inventario, configuración del sistema.

No se mencionó un rol de "Repartidor" con acceso propio al sistema — los domicilios se gestionan desde el rol de Mesero/Cajero, no desde una app de repartidor.

## Facturación

**Solo ticket/recibo simple.** No se requiere CFDI (factura fiscal timbrada ante el SAT) para el MVP. No implementes captura de RFC, régimen fiscal ni integración con un PAC a menos que el dueño lo pida explícitamente — es una pieza grande (identidad fiscal del cliente, catálogos SAT, timbrado, cancelaciones) que hoy está fuera de alcance.

## Flujo de un pedido

El flujo **es distinto por canal** y el orden de "pagar" respecto a "preparar" cambia en cada uno — esto es una regla de negocio confirmada, no una propuesta:

- **Para comer ahí (comedor)**: Ordenar → **Pagar** → Preparar.
- **Para llevar**: Ordenar → **Pagar** → Preparación → Empaquetado → Listo.
- **Para pasar por él (pickup) o a domicilio**: Ordenar → Preparar → Recoger/Entregar → **Pagar**.

Nota la diferencia clave: en comedor y para llevar se **cobra antes de preparar**; en pickup/domicilio se **cobra al momento de recoger o entregar**. Cualquier máquina de estados de pedido debe ser consciente del canal — no uses un único flujo universal.

Hoy en el código no existe una máquina de estados completa por canal — `KitchenDisplay` muestra las órdenes reales en `EN_PREPARACION` (ver `OrdenDAO.obtenerPorEstado`) y "Completar Pedido" las avanza directo a `ENTREGADA`, sin estados intermedios (p.ej. `LISTA`) ni distinción de canal todavía.

## Pagos

Métodos aceptados: **efectivo, tarjeta, transferencia**, y **división de cuenta entre varios métodos** (ej. parte en efectivo y parte con tarjeta en la misma cuenta). Hoy `PaymentPortalController` solo maneja efectivo con cálculo de cambio vía numpad; tarjeta/transferencia son visuales (tiles seleccionables) sin lógica de cobro real, y no existe división de una cuenta entre métodos.

**No se maneja propina dentro del sistema** — se gestiona fuera de melo. No agregues captura ni cálculo de propina salvo instrucción explícita.

## Precios e IVA

**Depende del producto**: no todos los platillos necesariamente llevan la misma tasa de IVA (posibles exentos o tasas distintas). Esto es una **discrepancia con el código actual**: `Totales.TASA_IVA = 0.16` aplica un 16% plano a *toda* la orden, sin distinción por producto. No lo corrijas de forma improvisada — el modelo de datos para "tasa de IVA por producto" debe salir del diseño de base de datos que el dueño está haciendo aparte.

## Mesas

**No es prioridad ahora.** El nav item "Mesas" está deshabilitado a propósito en `SidebarController`. No construyas mapa de salón, capacidad ni reservaciones sin que te lo pidan — cuando se retome, aclarar primero si se quiere algo básico (número + ocupada/libre) o con reservaciones a futuro.

## Turno y caja (corte de caja)

Modelo básico confirmado: el cajero **abre turno con un monto inicial**, opera, y al **cerrar el turno el sistema debe cuadrar** lo esperado (ventas registradas) contra lo contado físicamente. No se mencionaron múltiples cajeros simultáneos bajo el mismo turno — asume un cajero por turno salvo que se diga lo contrario.

El botón "Cerrar Turno" ya existe visualmente en `PaymentPortal.fxml` (`sidebar-extra-section`) pero **no tiene lógica de negocio implementada** — es el siguiente paso natural cuando se retome este tema.

## Domicilios

**CONFIRMADO: el costo de envío se calcula por distancia real, no por zona fija.** Fórmula ya en el esquema: `costo_envio = sucursales.tarifa_base_envio + sucursales.tarifa_por_km * distancia_km`, configurable por sucursal. Requiere coordenadas (`latitud`/`longitud`) tanto en `sucursales` (origen) como en `clientes` (destino).

**Geocodificación: implementada con Mapbox** (`mx.edu.utch.melo.geo.MapboxGeocodificador`, Geocoding API v5). `RegisterClientController` geocodifica la dirección capturada al guardar un cliente y llena `clientes.latitud`/`clientes.longitud` — de forma "best effort": si falla (sin token configurado, sin internet, dirección no encontrada), el cliente se guarda igual sin coordenadas, nunca bloquea el registro. **Requiere `mapbox.token=<tu token>` en el `.env` local** (no committeado); sin esa línea, `MapboxGeocodificador` lanza `IllegalStateException` al construirse y el registro de clientes queda sin geocodificar (capturado y absorbido, no rompe la UI). `mx.edu.utch.melo.geo.CalculadorDistancia` (fórmula de Haversine) ya existe para calcular distancia entre dos coordenadas, pero **todavía no hay ninguna pantalla que cree órdenes de tipo DOMICILIO** — `ordenes.distancia_km` y `costo_envio` siguen sin llenarse en la práctica porque ese flujo de creación no está implementado (ver más abajo).

`DeliveryView.fxml`: el panel derecho ("Pedidos Activos") **ya muestra órdenes reales** de `TipoOrden.DOMICILIO` activas vía `DeliveryController` (solo lectura — no crea ni asigna repartidores, no existe esa entidad en el esquema). El mapa central sigue siendo una maqueta visual estática (superficie sin mapa real, pin de "Zona Norte" de ejemplo, banner de sugerencia de IA) — eso es trabajo aparte, no de esta conexión.

## Promociones y descuentos

**No es necesario por ahora.** No agregues campos ni lógica de descuentos/promos sin que se pida explícitamente.

## Inventario

**Inventario real con cantidades**, no solo disponibilidad sí/no: cada platillo/ingrediente debe descontar existencias y el sistema debe poder generar alertas de stock bajo. Esto es significativamente más que lo que existe hoy (`MenuPOSController.contenedorMenu` es un contenedor vacío pensado para cargar platillos desde BD, sin ningún concepto de stock todavía). Este es uno de los requisitos más importantes a llevar al diseño del modelo relacional.

## Modificadores de platillos

**Pueden tener costo adicional** (ej. "extra queso" suma a el precio). Hoy `ItemOrden.modificadores` es solo una lista de strings decorativos sin impacto en el precio — `getSubtotal()` únicamente multiplica `precioUnitario * cantidad`. Cuando se aborde esto, cada modificador necesita su propio precio (probablemente `List<Modificador>` con nombre + precio, en vez de `List<String>`).

## Clientes

Además de nombre/teléfono/dirección (que ya captura `RegisterClientController`), el sistema debe eventualmente guardar:
- **Historial de pedidos** — qué ha pedido antes el cliente.
- **Notas del cliente** — preferencias, alérgenos, cliente VIP, etc.

No se pidió programa de lealtad/puntos — no lo agregues sin que se solicite.

## Reportes

Los dos reportes que el administrador/gerente necesita como prioridad:
- **Ventas por día/periodo.**
- **Platillos más vendidos.**

No se pidió reporte de desempeño por mesero/cajero — no es prioridad. El nav item "Reportes" sigue deshabilitado hasta que se implemente.

## Fuera de alcance explícito (no lo hagas sin que te lo pidan)

- CFDI / facturación fiscal ante el SAT.
- Propina dentro del sistema.
- Promociones, descuentos, cupones.
- Programa de lealtad/puntos de cliente.
- Gestión de mesas con mapa de salón o reservaciones.
- Reportes de desempeño por empleado.
- Rol de "Repartidor" con acceso propio a la app.



**El esquema relacional ya existe y está aplicado**: `src/main/resources/db/schema.sql`, con todas las llaves foráneas conectadas (sucursales, categorias, clientes, modificadores, usuarios, mesas, turnos, productos, producto_modificador, ordenes, detalle_orden, detalle_orden_modificador, pagos). Ya cubre: IVA por producto, modificadores con precio propio, inventario con cantidades y stock mínimo, historial/notas de cliente, turno de caja, envío por distancia, división de pago entre métodos, y multi-sucursal. El script hace `DROP TABLE` antes de crear -- pensado para seguir iterando el diseño, no para correr contra datos reales que se quieran conservar.

**La capa Java (`model`/`dao`/`dao.impl`) ya existe completa y está verificada contra la base real** (inserción + lectura de punta a punta en las 13 tablas, incluyendo las dos tablas de relación). Un modelo/DAO/DAOImpl por cada tabla: `Sucursal`, `Categoria`, `Cliente`, `Modificador`, `Usuario`, `Mesa`, `Turno`, `Producto`, `Orden`, `DetalleOrden`, `Pago`, más `ModificadorAplicado` (representa una fila de `detalle_orden_modificador`, con el precio que tenía al momento de la venta). Las relaciones N:M (`producto_modificador`, `detalle_orden_modificador`) no tienen clase de entidad propia -- se manejan como métodos en `ProductoDAO`/`DetalleOrdenDAO` (`obtenerModificadores`, `asignarModificador`, `agregarModificador`), porque son relaciones puras sin ciclo de vida independiente.

**Ya conectado a los controladores de JavaFX** (vía `AppContext`, inyectado por `ControllerFactory`; consultas siempre en un hilo aparte con `mx.edu.utch.melo.async.Async`, nunca bloqueando el hilo de FX):

- `RegisterClientController` → `ClienteDAO` + `Geocodificador` (crea cliente, geocodifica dirección best-effort).
- `MenuPOSController` → `ProductoDAO` (carga el menú real por sucursal), `OrdenDAO`/`DetalleOrdenDAO` (cobrar cuenta crea una orden COMEDOR real con su detalle).
- `PaymentPortalController` → `OrdenDAO`/`DetalleOrdenDAO`/`ProductoDAO` (recibo real de la orden en curso vía `SesionActual`), `PagoDAO` (registra el pago y avanza el estado a `EN_PREPARACION`).
- `KitchenDisplayController` → `OrdenDAO`/`DetalleOrdenDAO`/`ProductoDAO` (tickets reales de órdenes en `EN_PREPARACION`; "Completar Pedido" avanza la orden a `ENTREGADA`).
- `DeliveryController` → `OrdenDAO`/`ClienteDAO`, **solo lectura** (lista órdenes DOMICILIO activas con cliente/dirección real).

**Todavía NO existe**: login por PIN (`SesionActual` hoy se inicia sin validar contra `usuarios`, cualquiera puede operar como el usuario semilla), apertura/cierre de turno con lógica de negocio (el botón "Cerrar Turno" sigue sin acción), y ninguna pantalla que **cree** una orden DOMICILIO o PARA_RECOGER (solo existe creación de orden COMEDOR desde MenuPOS) — por lo tanto el cálculo de `distancia_km`/`costo_envio` con `CalculadorDistancia` todavía no se invoca desde ningún flujo real, aunque la pieza ya existe.

El estado de una orden depende del **canal** (comedor/para llevar/domicilio-pickup): el momento en que se cobra cambia según el canal, así que "pagado" no siempre es el mismo paso en la secuencia.

## Convenciones técnicas ya establecidas

(Contexto rápido para no reinventar lo que ya existe — ver también el código mismo, que es la fuente de verdad.)

- Todo el texto de UI en español; formato de moneda vía `util.Dinero` (`$#,##0.00`, `Locale.US` para el separador de miles).
- Paquetes por responsabilidad: `nav` (navegación/DI), `model` (datos en memoria), `util` (helpers puros), `validation`, `view` (factories de nodos JavaFX), `controller`.
- Navegación entre pantallas vía `Navigator` (interfaz) + `SceneManager`, inyectado por constructor a través de `ControllerFactory` — no uses `SceneManager.getInstance()` ni estáticos, sigue el patrón de inyección existente. Los controladores que solo navegan reciben `Navigator`; los que tocan base de datos reciben `AppContext` completo (registro de servicios con todos los DAO, la sesión y el geocodificador — ver `mx.edu.utch.melo.app.AppContext`).
- Estilos centralizados en `styles.css` con variables de color; **no combines dos `styleClass` que redeclaren la misma propiedad `-fx-*` en el mismo nodo** (ver el comentario al inicio de `styles.css` — hay un bug de JavaFX documentado ahí).
- Las órdenes, su detalle y los pagos ya se persisten en MySQL (ver arriba); lo que sigue en memoria y se pierde al cerrar la app es el carrito de una mesa antes de cobrar (`ItemOrden` en `MenuPOSController`) y el estado del numpad de cobro (`EntradaMonetaria`).
- Toda consulta o escritura a la base de datos desde un controlador debe ir en un `Supplier`/`Runnable` pasado a `mx.edu.utch.melo.async.Async.ejecutar(...)` (hilo aparte); nunca llames a un DAO directamente en el hilo de FX ni dentro de un callback de éxito/error de `Async` (eso reintroduce el bloqueo). Ver el patrón ya usado en `PaymentPortalController.construirDatosRecibo`.
- Hay 33 tests (`src/test/java`) cubriendo lógica pura (`Totales`, `Dinero`, `ClienteValidator`, `EntradaMonetaria`) y carga de las 6 pantallas (`FxmlSmokeTest`, con un `AppContext` de prueba cuyos DAO son proxies que nunca tocan la base real). Si agregas lógica de negocio nueva, sepárala en una clase sin dependencias de JavaFX UI para poder probarla igual.
