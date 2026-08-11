# melo — Contexto de negocio y reglas del proyecto

Este archivo documenta el **negocio** detrás de melo (no la arquitectura de código, que ya es autoexplicativa desde `nav/`, `model/`, `util/`, `validation/`, `view/`, `controller/`). Está pensado para que cualquier sesión de Claude Code entienda las reglas reales antes de tocar una pantalla o proponer un modelo de datos.

Este documento se construyó por entrevista directa con el dueño del proyecto. Donde la respuesta fue "no es prioridad ahora" o quedó ambigua, se marca explícitamente como **pendiente** — no se debe asumir una regla que no está aquí.

## Qué es melo

Sistema de punto de venta (POS) de escritorio para un restaurante, hecho en JavaFX. Opera en español (México), en pesos (MXN).

**Tipo de negocio**: melo es un **POS genérico para negocios de comida** — pensado para operar tanto en un local individual como en cadenas de restaurantes. No es el sistema interno de un único restaurante con identidad propia; es una plataforma de punto de venta que cualquier negocio de comida (fonda, restaurante, cadena) podría usar. Ten esto en cuenta al nombrar cosas o al decidir si algo debe ser configurable por negocio (ej. no asumas un solo logo, una sola sucursal, o un menú fijo "quemado" en el código — el menú, precios y catálogo deben poder variar por negocio/sucursal cuando se diseñe la base de datos).

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

Hoy en el código no existe una máquina de estados para pedidos — `KitchenDisplay` solo permite "Completar Pedido" (quita el ticket de la vista), sin más estados intermedios, y no distingue canal.

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

El costo de envío es **variable por distancia/zona** — consistente con lo que ya insinúa `DeliveryView.fxml` (zonas con demanda, tiempos estimados, todo con datos de ejemplo estáticos hoy). Falta: la regla real de cálculo (tarifa por km, por zona fija, mínimo de envío, etc.) — pendiente de definir con el dueño cuando se implemente de verdad.

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

## Base de datos: NO TOCAR sin permiso explícito

El dueño está diseñando el modelo relacional y evaluando SGBD por su cuenta, **fuera de estas sesiones**. No propongas ni crees esquemas, tablas, entidades JPA/Hibernate, DAOs/Repositories, ni elijas MySQL/PostgreSQL/SQLite/etc. a menos que te lo pida explícitamente en esa conversación.

Lo que sí es útil dejar aquí son los **requisitos de datos que ya salieron de esta entrevista**, para que el dueño los tenga a la mano cuando diseñe el modelo:

- Tasa de IVA **por producto**, no global.
- Modificadores con **precio propio** (no solo texto).
- Inventario con **cantidades reales** y umbral de stock bajo.
- Historial de pedidos por cliente.
- Notas por cliente (texto libre).
- Turno de caja: monto de apertura, ventas del turno, monto contado al cierre, diferencia.
- Domicilio: tarifa de envío variable por zona/distancia.
- División de una cuenta entre varios métodos de pago (probablemente una tabla de "pagos" 1-a-muchos por orden, no un solo método por orden).
- Al ser un POS pensado para cadenas, el modelo probablemente necesita un concepto de **negocio/sucursal** desde la base (menú, precios, inventario y turnos aislados por sucursal) — no un menú único global como hoy en el código.
- El estado de una orden depende del **canal** (comedor/para llevar/domicilio-pickup): el momento en que se cobra cambia según el canal, así que "pagado" no siempre es el mismo paso en la secuencia.

## Convenciones técnicas ya establecidas

(Contexto rápido para no reinventar lo que ya existe — ver también el código mismo, que es la fuente de verdad.)

- Todo el texto de UI en español; formato de moneda vía `util.Dinero` (`$#,##0.00`, `Locale.US` para el separador de miles).
- Paquetes por responsabilidad: `nav` (navegación/DI), `model` (datos en memoria), `util` (helpers puros), `validation`, `view` (factories de nodos JavaFX), `controller`.
- Navegación entre pantallas vía `Navigator` (interfaz) + `SceneManager`, inyectado por constructor a través de `ControllerFactory` — no uses `SceneManager.getInstance()` ni estáticos, sigue el patrón de inyección existente.
- Estilos centralizados en `styles.css` con variables de color; **no combines dos `styleClass` que redeclaren la misma propiedad `-fx-*` en el mismo nodo** (ver el comentario al inicio de `styles.css` — hay un bug de JavaFX documentado ahí).
- Todo el estado de negocio (órdenes, tickets, turno) vive en memoria (`ObservableList`, campos del controlador) — se pierde al cerrar la app. Así es a propósito hasta que exista base de datos.
- Hay 33 tests (`src/test/java`) cubriendo lógica pura (`Totales`, `Dinero`, `ClienteValidator`, `EntradaMonetaria`) y carga de las 6 pantallas (`FxmlSmokeTest`). Si agregas lógica de negocio nueva, sepárala en una clase sin dependencias de JavaFX UI para poder probarla igual.
