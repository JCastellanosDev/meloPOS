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

La pantalla "Personal" (`PersonalController`) ya existe y lista el personal real de la sucursal activa, pero es solo lectura -- no es el sistema de roles/permisos de esta sección (que sigue sin implementar) ni permite crear/editar usuarios todavía.

## Facturación

**Solo ticket/recibo simple.** No se requiere CFDI (factura fiscal timbrada ante el SAT) para el MVP. No implementes captura de RFC, régimen fiscal ni integración con un PAC a menos que el dueño lo pida explícitamente — es una pieza grande (identidad fiscal del cliente, catálogos SAT, timbrado, cancelaciones) que hoy está fuera de alcance.

## Flujo de un pedido

El flujo **es distinto por canal** y el orden de "pagar" respecto a "preparar" cambia en cada uno — esto es una regla de negocio confirmada, no una propuesta:

- **Para comer ahí (comedor)**: Ordenar → **Pagar** → Preparar.
- **Para llevar**: Ordenar → **Pagar** → Preparación → Empaquetado → Listo.
- **Para pasar por él (pickup) o a domicilio**: Ordenar → Preparar → Recoger/Entregar → **Pagar**.

Nota la diferencia clave: en comedor y para llevar se **cobra antes de preparar**; en pickup/domicilio se **cobra al momento de recoger o entregar**. Cualquier máquina de estados de pedido debe ser consciente del canal — no uses un único flujo universal.

Hoy en el código no existe una máquina de estados completa por canal — `KitchenDisplay` muestra las órdenes reales en `EN_PREPARACION` (ver `OrdenDAO.obtenerPorEstado`) y "Completar Pedido" las avanza directo a `ENTREGADA`, sin estados intermedios (p.ej. `LISTA`) ni distinción de canal todavía.

**KitchenDisplay ya no es una pestaña del sidebar**: es una ventana de escritorio independiente (`Navigator.abrirVentana`, implementado en `SceneManager`), pensada para un monitor/terminal de cocina aparte de la terminal de ventas -- no tiene sidebar ni topbar, solo su propia franja de estado con reloj real. Se abre desde el botón "Cocina" del sidebar, que ya no navega dentro de la ventana principal.

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

**Geocodificación: implementada con Mapbox** (`mx.edu.utch.melo.geo.MapboxGeocodificador`, Geocoding API v5). `PedidosController` geocodifica la dirección capturada al guardar un cliente y llena `clientes.latitud`/`clientes.longitud` — de forma "best effort": si falla (sin token configurado, sin internet, dirección no encontrada), el cliente se guarda igual sin coordenadas, nunca bloquea el registro. **Requiere `mapbox.token=<tu token>` en el `.env` local** (no committeado); sin esa línea, `MapboxGeocodificador` lanza `IllegalStateException` al construirse y el registro de clientes queda sin geocodificar (capturado y absorbido, no rompe la UI).

**Ruta real + costo de envío: ya implementado.** El botón "Ubicar" en `Pedidos.fxml` (`PedidosController.onUbicar`) calcula la ruta real desde la sucursal activa hasta la dirección capturada usando `mx.edu.utch.melo.geo.MapboxServicioRutas` (interfaz `ServicioRutas`, igual patrón que `Geocodificador`): la API de Directions de Mapbox da la distancia real y el trazo de la ruta, y la API de Static Images dibuja un mapa con el trazo + los dos pines, que se muestra directo en el recuadro del formulario (sin `WebView`, es una sola imagen). La distancia calculada viaja a `MenuPedidoController` vía `SesionActual.setDistanciaKmEnProceso` y ahí sí se aplica la fórmula `costo_envio = tarifa_base_envio + tarifa_por_km * distancia_km` al crear la orden DOMICILIO (`ordenes.distancia_km`/`costo_envio` ya no quedan en `null`/`0`, y el envío se suma al total). Si no se presiona "Ubicar" antes de "Tomar Pedido", el pedido igual se puede mandar a cocina, solo que sin distancia/costo de envío calculados.

**Importante para que esto funcione: `sucursales.latitud`/`longitud` deben estar cargadas.** Si algún día quedan en `NULL` (sucursal nueva sin ubicar), `onUbicar` no puede calcular ninguna ruta y muestra un mensaje explícito: "La sucursal activa no tiene coordenadas configuradas todavía", sin importar que el token de Mapbox esté bien puesto. Todavía no hay pantalla para capturar/editar la ubicación de una sucursal -- hay que cargarla directo en la base de datos (`sucursales.latitud`/`longitud`, geocodificando la dirección con la misma API de Mapbox).

**Geocodificar direcciones incompletas es poco confiable sin ayuda -- confirmado con pruebas reales.** El cliente en Pedidos normalmente solo da calle + número (a veces + colonia), sin ciudad/estado, y a veces el nombre de un negocio en vez de una dirección ("Alsúper Arboledas"). Probado en vivo contra la API real de Mapbox: geocodificar "Calle Hidalgo 100" sin más contexto regresó una dirección en Tabasco, a más de 1000 km de la sucursal en Chihuahua; "Alsúper Arboledas" sin contexto regresó una carretera cerca de Ciudad de México. Por eso `Geocodificador` tiene un segundo método, `geocodificar(String direccion, Coordenadas cercaDe)` (default: ignora `cercaDe` y cae al de un solo argumento -- para no obligar a otros proveedores a soportarlo), que `MapboxGeocodificador` sí implementa usando el parámetro `proximity` de la API de Mapbox (ancla la búsqueda cerca de un punto) más `country=mx` fijo. `PedidosController` combina dos mitigaciones: le pega la ciudad/estado/país de la sucursal activa al texto (`completarDireccion`, ver arriba) **y** pasa las coordenadas de la sucursal como `cercaDe`. En las pruebas, pegar el texto ya arreglaba la mayoría de los casos por sí solo; `proximity` *solo* (sin el texto pegado) en realidad empeoró un caso -- por eso se usan las dos mitigaciones juntas, no una en lugar de la otra. Esto sigue siendo heurístico, no infalible: una dirección realmente ambigua o mal escrita puede seguir sin encontrarse.

**Límite real (no de código, de datos): Mapbox a veces no tiene el número de casa indexado en una calle.** Confirmado en vivo con "Av. Montes Americanos 9501, Chihuahua": sin importar el formato del texto (con/sin "#", número antes o después, con/sin abreviatura), Mapbox nunca regresó el `9501` en la coincidencia -- el campo `address` de la respuesta venía en `null`, señal de que esa calle no tiene datos de numeración en su base, no que la búsqueda esté mal armada. El pin cae en algún punto genérico de la calle, "a varias calles" del número real. No hay forma de arreglar esto por completo desde el código: es una limitación del dataset de Mapbox para esa calle. Mitigación agregada: botón **"Ver en Google Maps"** junto al mapa (`PedidosController.onVerEnGoogleMaps`, requiere `java.desktop`/`Desktop.browse`) que abre el navegador del sistema con la misma dirección + contexto de sucursal, para que el mesero verifique/ajuste a simple vista contra un segundo proveedor (Google suele tener mejor cobertura de numeración en México en zonas donde Mapbox no la tiene). No hay pin arrastrable ni mapa interactivo embebido todavía -- el mapa en pantalla sigue siendo una imagen estática (ver arriba); eso requeriría un `WebView` con Mapbox GL JS, que es una pieza más grande, no incluida en este alcance.

**Dirección de sucursal: estructurada, no texto libre.** `sucursales` guarda `calle`, `numero`, `colonia`, `codigo_postal`, `ciudad`, `estado`, `pais` como columnas separadas (antes era un solo `direccion VARCHAR`) -- así el capturista llena cada parte, en vez de un cuadro de texto libre propenso a direcciones mal formateadas. `Sucursal.getDireccionCompleta()` arma la línea completa a partir de esas columnas, para mostrar en pantalla (`AjustesController`) o para geocodificar. La sucursal semilla de `schema.sql` ya trae la dirección real del dueño ("Crepas de Oro", Av de las Águilas 3046, Arboledas, 31110 Chihuahua, Chih, México) con sus coordenadas ya geocodificadas -- por eso el flujo de "Ubicar" en Pedidos ya funciona de punta a punta en esta base. `clientes.direccion` **sigue siendo un solo campo de texto libre** -- no se pidió estructurarlo igual, solo el de sucursales.

`DeliveryView.fxml`: el panel derecho ("Pedidos Activos") **ya muestra órdenes reales** de `TipoOrden.DOMICILIO` activas vía `DeliveryController` (solo lectura — no crea ni asigna repartidores, no existe esa entidad en el esquema). El mapa central sigue siendo una maqueta visual estática (superficie sin mapa real, pin de "Zona Norte" de ejemplo, banner de sugerencia de IA) — eso es trabajo aparte, no de esta conexión.

## Promociones y descuentos

**No es necesario por ahora.** No agregues campos ni lógica de descuentos/promos sin que se pida explícitamente.

## Inventario

**Inventario real con cantidades**, no solo disponibilidad sí/no: cada platillo/ingrediente debe descontar existencias y el sistema debe poder generar alertas de stock bajo. `Producto.tieneStockBajo()` (compara `cantidadDisponible` contra `stockMinimo`) ya existe y `InventarioController` lo usa para mostrar la alerta en la pantalla de Inventario (solo lectura por ahora: lista los productos de la sucursal activa con sus existencias). **Todavía falta**: que vender un platillo descuente `cantidad_disponible` automáticamente -- hoy nadie descuenta stock al cobrar una orden, así que las cantidades mostradas no bajan solas todavía.

## Modificadores de platillos

**Pueden tener costo adicional** (ej. "extra queso" suma a el precio). Hoy `ItemOrden.modificadores` es solo una lista de strings decorativos sin impacto en el precio — `getSubtotal()` únicamente multiplica `precioUnitario * cantidad`. Cuando se aborde esto, cada modificador necesita su propio precio (probablemente `List<Modificador>` con nombre + precio, en vez de `List<String>`).

## Clientes

Además de nombre/teléfono/dirección (que ya captura `PedidosController`), el sistema debe eventualmente guardar:
- **Historial de pedidos** — qué ha pedido antes el cliente.
- **Notas del cliente** — preferencias, alérgenos, cliente VIP, etc.

No se pidió programa de lealtad/puntos — no lo agregues sin que se solicite.

## Reportes

Los dos reportes que el administrador/gerente necesita como prioridad:
- **Ventas por día/periodo.**
- **Platillos más vendidos.**

**Ya implementados** en `ReportesController`/`ReporteDAO` (ver más abajo, sección de conexión a base de datos): selector de rango de fechas (por defecto últimos 7 días), gráfica de barras de ventas por día y ranking de platillos más vendidos, ambos con datos reales de la sucursal activa.

No se pidió reporte de desempeño por mesero/cajero — no es prioridad.

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

- `PedidosController` (pestaña "Pedidos" del sidebar, antes "Clientes" -- mismo formulario, reordenado: primero teléfono, con `onAction` en ese campo para buscar por teléfono y autocompletar nombre/dirección si ya existe) → `ClienteDAO` + `Geocodificador` (crea el cliente si es nuevo, o reutiliza el encontrado por teléfono sin volver a insertarlo), `SucursalDAO` + `ServicioRutas` (botón "Ubicar": calcula la ruta real y dibuja el mapa, ver arriba). "Tomar Pedido" ya no se queda ahí: guarda el id del cliente y la distancia calculada en `SesionActual` y abre `MenuPedidoController` como ventana emergente (ver `Pantalla.MENU_PEDIDO`) para elegir los platillos.
- `MenuPedidoController` (ventana emergente, no pestaña del sidebar, abierta desde `PedidosController`) → `ProductoDAO` (mismo grid de platillos que MenuPOS), `OrdenDAO`/`DetalleOrdenDAO`/`ClienteDAO`/`SucursalDAO`. "Mandar a Cocina" crea la orden como `TipoOrden.DOMICILIO` directo en `EstadoOrden.EN_PREPARACION` (aquí no se cobra antes de preparar, ver "Flujo de un pedido" arriba) -- por eso aparece de inmediato tanto en `DeliveryView` ("Pedidos Activos") como en `KitchenDisplay`, sin pasar por `PaymentPortal`. `distanciaKm`/`costoEnvio` ya se guardan reales cuando se calculó una ruta en Pedidos (el envío también se suma al total); si no, quedan en `null`/`0` sin bloquear el pedido.
- `MenuPOSController` → `ProductoDAO` (carga el menú real por sucursal), `OrdenDAO`/`DetalleOrdenDAO` (cobrar cuenta crea una orden COMEDOR real con su detalle).
- `PaymentPortalController` → `OrdenDAO`/`DetalleOrdenDAO`/`ProductoDAO` (recibo real de la orden en curso vía `SesionActual`), `PagoDAO` (registra el pago y avanza el estado a `EN_PREPARACION`).
- `KitchenDisplayController` → `OrdenDAO`/`DetalleOrdenDAO`/`ProductoDAO` (tickets reales de órdenes en `EN_PREPARACION`; "Completar Pedido" avanza la orden a `ENTREGADA`). Ventana independiente, no pestaña del sidebar (ver arriba).
- `DeliveryController` → `OrdenDAO`/`ClienteDAO`, **solo lectura** (lista órdenes DOMICILIO activas con cliente/dirección real).
- `ReportesController` → `ReporteDAO` (nuevo, no extiende `CrudDAO`: son consultas de agregación entre `ordenes`/`detalle_orden`/`productos`/`usuarios`, no el acceso a una sola tabla) -- ventas por periodo y platillos más vendidos de la sucursal activa.
- `PersonalController` → `UsuarioDAO.obtenerPorSucursal`, **solo lectura** (lista el personal de la sucursal activa: nombre, rol, activo/inactivo).
- `InventarioController` → `ProductoDAO.obtenerPorSucursal` (nuevo método: a diferencia de `obtenerTodosActivos()`, trae también los inactivos/sin stock), **solo lectura** con alerta de stock bajo. *Nota de un bug preexistente encontrado al agregar este método, no corregido porque no se pidió*: `obtenerTodosActivos()` (el que usa `MenuPOSController` para el menú) no filtra por `sucursal_id` -- en una cadena con más de una sucursal, MenuPOS podría mostrar productos de otra sucursal.
- `AjustesController` → `SucursalDAO.obtenerPorId`, **solo lectura** (datos de la sucursal activa: nombre, dirección, teléfono, tarifas de envío, estado).

**Todavía NO existe**: login por PIN (`SesionActual` hoy se inicia sin validar contra `usuarios`, cualquiera puede operar como el usuario semilla), apertura/cierre de turno con lógica de negocio (el botón "Cerrar Turno" sigue sin acción), y ninguna pantalla que cree una orden **PARA_RECOGER** (solo existen COMEDOR desde MenuPOS y DOMICILIO desde Pedidos → MenuPedido). Tampoco hay pantalla para capturar/editar `latitud`/`longitud` de una sucursal -- sin eso, "Ubicar" no puede calcular ninguna ruta (ver la nota de Domicilios arriba).

El estado de una orden depende del **canal** (comedor/para llevar/domicilio-pickup): el momento en que se cobra cambia según el canal, así que "pagado" no siempre es el mismo paso en la secuencia.

## Convenciones técnicas ya establecidas

(Contexto rápido para no reinventar lo que ya existe — ver también el código mismo, que es la fuente de verdad.)

- Todo el texto de UI en español; formato de moneda vía `util.Dinero` (`$#,##0.00`, `Locale.US` para el separador de miles).
- Paquetes por responsabilidad: `nav` (navegación/DI), `model` (datos en memoria), `util` (helpers puros), `validation`, `view` (factories de nodos JavaFX), `controller`.
- Navegación entre pantallas vía `Navigator` (interfaz) + `SceneManager`, inyectado por constructor a través de `ControllerFactory` — no uses `SceneManager.getInstance()` ni estáticos, sigue el patrón de inyección existente. Los controladores que solo navegan reciben `Navigator`; los que tocan base de datos reciben `AppContext` completo (registro de servicios con todos los DAO, la sesión y el geocodificador — ver `mx.edu.utch.melo.app.AppContext`).
- Estilos centralizados en `styles.css` con variables de color; **no combines dos `styleClass` que redeclaren la misma propiedad `-fx-*` en el mismo nodo** (ver el comentario al inicio de `styles.css` — hay un bug de JavaFX documentado ahí).
- Las órdenes, su detalle y los pagos ya se persisten en MySQL (ver arriba); lo que sigue en memoria y se pierde al cerrar la app es el carrito de una mesa antes de cobrar (`ItemOrden` en `MenuPOSController`) y el estado del numpad de cobro (`EntradaMonetaria`).
- Toda consulta o escritura a la base de datos desde un controlador debe ir en un `Supplier`/`Runnable` pasado a `mx.edu.utch.melo.async.Async.ejecutar(...)` (hilo aparte); nunca llames a un DAO directamente en el hilo de FX ni dentro de un callback de éxito/error de `Async` (eso reintroduce el bloqueo). Ver el patrón ya usado en `PaymentPortalController.construirDatosRecibo`. Importante: si el `Supplier` necesita algo de `SesionActual` (p. ej. `getSucursalActivaId()`), léelo DENTRO del lambda del `Supplier`, no antes -- `getSucursalActivaId()` lanza si no hay sesión iniciada, y evaluarlo en el hilo de FX antes de despachar a `Async` rompe la prueba de humo (que usa una sesión sin iniciar).
- Iconos vía Ikonli (`org.kordamp.ikonli.javafx.FontIcon` + `ikonli-materialdesign2-pack`, literales `mdi2<letra>-nombre-en-minusculas`, p. ej. `mdi2c-chef-hat`) -- ya no se usan emojis como iconos en los FXML (excepto símbolos simples de teclado como `⌫`, que no son parte de este sistema). Antes de usar un literal nuevo, verifica que exista inspeccionando las clases `MaterialDesign*` del jar descargado; un literal inválido lanza en tiempo de carga del FXML, no al compilar.
- `Navigator` tiene dos formas de mostrar una pantalla: `navigateTo(Pantalla)` (reemplaza la raíz de la ventana única de la app) y `abrirVentana(Pantalla, String)` (abre un `Stage` nuevo e independiente -- la usan Cocina, el cobro emergente de MenuPOS y MenuPedido). Al agregar una pantalla nueva, la gran mayoría de los casos son `navigateTo`.
- `SesionActual` pasa estado entre pantallas que no tienen relación de navegación directa: `ordenEnProcesoId` (MenuPOS → PaymentPortal), `clienteEnProcesoId` y `distanciaKmEnProceso` (Pedidos → MenuPedido). Sigue el mismo patrón para cualquier caso nuevo de "pantalla A abre una ventana emergente B que necesita saber de qué se está hablando".
- `Geocodificador` (dirección → coordenadas) y `ServicioRutas` (coordenadas → distancia real + imagen de mapa) son dos interfaces separadas a propósito, aunque ambas las implemente Mapbox hoy (`MapboxGeocodificador`, `MapboxServicioRutas`) -- resuelven problemas distintos (geocodificación vs. ruteo) y un controlador normalmente solo necesita una de las dos.
- Hay 38 tests (`src/test/java`) cubriendo lógica pura (`Totales`, `Dinero`, `ClienteValidator`, `EntradaMonetaria`) y carga de las 11 pantallas (`FxmlSmokeTest`, con un `AppContext` de prueba cuyos DAO son proxies que nunca tocan la base real). Si agregas lógica de negocio nueva, sepárala en una clase sin dependencias de JavaFX UI para poder probarla igual.
