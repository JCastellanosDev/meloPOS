-- ============================================================
-- Esquema relacional de melo POS -- multi-sucursal.
--
-- Cubre todas las reglas de negocio ya confirmadas en CLAUDE.md:
-- varias sucursales, IVA por producto, modificadores con costo
-- propio, inventario con cantidades, historial y notas de cliente,
-- turno de caja, envío a domicilio calculado por distancia, y
-- división de una cuenta entre varios métodos de pago. Todas las
-- llaves foráneas están conectadas a su llave primaria correspondiente.
--
-- Decisiones de diseño confirmadas contigo en esta conversación:
--   1. Varias sucursales desde ahora (no una sola).
--   2. Envío a domicilio calculado por DISTANCIA, no por zona fija.
--
-- Dos que seguí decidiendo yo (no las tocaste, siguen igual que antes):
--   3. Inventario a nivel de PRODUCTO, no de ingrediente/receta.
--   4. Modificadores reutilizables por catálogo, compartidos entre
--      sucursales (no uno distinto por cada platillo).
--
-- Qué es de cadena (compartido) y qué es de sucursal (propio):
--   - categorias y modificadores son catálogo COMPARTIDO por toda la
--     cadena -- "Bebidas" o "Extra queso" significan lo mismo en
--     cualquier sucursal.
--   - usuarios, mesas, productos (con su propio precio/inventario) y
--     turnos son propios de CADA sucursal.
--   - clientes es compartido: un cliente de la cadena no es de una
--     sola sucursal.
--   - ordenes NO guarda sucursal_id propio -- se deriva de
--     ordenes.usuario_id -> usuarios.sucursal_id (cada empleado
--     pertenece a una sucursal fija). Guardarlo aparte sería
--     exactamente la duplicidad que pediste evitar, ya que nunca
--     puede quedar desincronizado del usuario que tomó la orden.
--
-- Este script DROPEA y recrea las tablas -- pensado para iterar el
-- diseño en desarrollo, no para correr contra una base con datos
-- reales que quieras conservar.
-- ============================================================

CREATE DATABASE IF NOT EXISTS melo_pos
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE melo_pos;

-- Orden inverso de dependencias para poder dropear sin violar FKs.
DROP TABLE IF EXISTS pagos;
DROP TABLE IF EXISTS detalle_orden_modificador;
DROP TABLE IF EXISTS detalle_orden;
DROP TABLE IF EXISTS ordenes;
DROP TABLE IF EXISTS producto_modificador;
DROP TABLE IF EXISTS productos;
DROP TABLE IF EXISTS turnos;
DROP TABLE IF EXISTS mesas;
DROP TABLE IF EXISTS usuarios;
DROP TABLE IF EXISTS modificadores;
DROP TABLE IF EXISTS clientes;
DROP TABLE IF EXISTS categorias;
DROP TABLE IF EXISTS sucursales;

-- ============================================================
-- Tablas base (sin dependencias)
-- ============================================================

-- latitud/longitud son el punto de origen para calcular la distancia
-- de cada envío a domicilio. tarifa_base_envio + tarifa_por_km es la
-- fórmula de cobro (costo_envio = tarifa_base_envio + tarifa_por_km *
-- distancia_km), configurable por sucursal porque cada una puede
-- tener su propia política de envío.
-- Dirección estructurada (calle/numero/colonia/codigo_postal/ciudad/
-- estado/pais) en vez de una sola columna de texto libre -- así el
-- capturista llena cada parte por separado y la app arma la dirección
-- completa para geocodificar (ver Sucursal.getDireccionCompleta()).
CREATE TABLE sucursales (
    id                  INT AUTO_INCREMENT PRIMARY KEY,
    nombre              VARCHAR(120)    NOT NULL,
    calle               VARCHAR(150)    NOT NULL,
    numero              VARCHAR(20)     NOT NULL,
    colonia             VARCHAR(100)    NOT NULL,
    codigo_postal       VARCHAR(10)     NOT NULL,
    ciudad              VARCHAR(100)    NOT NULL,
    estado              VARCHAR(100)    NOT NULL,
    pais                VARCHAR(100)    NOT NULL DEFAULT 'México',
    latitud             DECIMAL(10, 7)  NULL,
    longitud            DECIMAL(10, 7)  NULL,
    telefono            VARCHAR(20)     NULL,
    tarifa_base_envio   DECIMAL(10, 2)  NOT NULL DEFAULT 0,
    tarifa_por_km       DECIMAL(10, 2)  NOT NULL DEFAULT 0,
    activa              BOOLEAN         NOT NULL DEFAULT TRUE,
    -- No todo restaurante cobra IVA; a diferencia de tasa_iva por producto (pendiente,
    -- ver CLAUDE.md), este es un interruptor simple de cadena/sucursal: todo o nada.
    aplica_iva          BOOLEAN         NOT NULL DEFAULT TRUE,
    UNIQUE KEY uk_sucursales_nombre (nombre)
);

-- Catálogo compartido por toda la cadena, no por sucursal.
CREATE TABLE categorias (
    id      INT AUTO_INCREMENT PRIMARY KEY,
    nombre  VARCHAR(80) NOT NULL,
    UNIQUE KEY uk_categorias_nombre (nombre)
);

-- Compartidos entre sucursales: un cliente de la cadena, no de una sola tienda.
-- latitud/longitud son la dirección de entrega geocodificada, para calcular
-- distancia contra la sucursal que atienda el domicilio.
CREATE TABLE clientes (
    id         INT AUTO_INCREMENT PRIMARY KEY,
    nombre     VARCHAR(150)    NOT NULL,
    telefono   VARCHAR(20)     NOT NULL,
    direccion  VARCHAR(300)    NULL,
    latitud    DECIMAL(10, 7)  NULL,
    longitud   DECIMAL(10, 7)  NULL,
    notas      VARCHAR(500)    NULL,
    UNIQUE KEY uk_clientes_telefono (telefono)
);

-- Catálogo compartido: "Extra queso" significa lo mismo en cualquier
-- sucursal. Cada sucursal decide a qué productos suyos lo ofrece vía
-- producto_modificador.
CREATE TABLE modificadores (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    nombre        VARCHAR(100)    NOT NULL,
    precio_extra  DECIMAL(10, 2)  NOT NULL DEFAULT 0,
    UNIQUE KEY uk_modificadores_nombre (nombre)
);

-- ============================================================
-- Tablas propias de cada sucursal
-- ============================================================

-- El mismo PIN puede repetirse en dos sucursales distintas (cada una
-- opera independiente) pero no dos veces dentro de la misma.
CREATE TABLE usuarios (
    id           INT AUTO_INCREMENT PRIMARY KEY,
    sucursal_id  INT           NOT NULL,
    nombre       VARCHAR(120)  NOT NULL,
    pin_acceso   VARCHAR(20)   NOT NULL,
    rol          ENUM('MESERO', 'CAJERO', 'COCINA', 'ADMINISTRADOR') NOT NULL,
    activo       BOOLEAN       NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_usuarios_sucursal
        FOREIGN KEY (sucursal_id) REFERENCES sucursales (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    UNIQUE KEY uk_usuarios_sucursal_pin (sucursal_id, pin_acceso)
);

-- La mesa "12" puede existir en varias sucursales a la vez, cada una la suya.
CREATE TABLE mesas (
    id           INT AUTO_INCREMENT PRIMARY KEY,
    sucursal_id  INT NOT NULL,
    numero       INT NOT NULL,
    capacidad    INT NULL,
    CONSTRAINT fk_mesas_sucursal
        FOREIGN KEY (sucursal_id) REFERENCES sucursales (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    UNIQUE KEY uk_mesas_sucursal_numero (sucursal_id, numero)
);

-- Corte de caja: se abre con un monto inicial y se cierra cuadrando
-- contra lo contado. "Lo esperado" y "la diferencia" se calculan a
-- partir de pagos/ordenes del turno, no se guardan aquí -- guardar un
-- total ya calculable en otra columna sería la duplicidad a evitar.
CREATE TABLE turnos (
    id                    INT AUTO_INCREMENT PRIMARY KEY,
    sucursal_id           INT       NOT NULL,
    usuario_id            INT       NOT NULL,
    monto_apertura        DECIMAL(10, 2) NOT NULL,
    monto_cierre_contado  DECIMAL(10, 2) NULL,
    fecha_apertura        DATETIME  NOT NULL,
    fecha_cierre          DATETIME  NULL,
    CONSTRAINT fk_turnos_sucursal
        FOREIGN KEY (sucursal_id) REFERENCES sucursales (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_turnos_usuario
        FOREIGN KEY (usuario_id) REFERENCES usuarios (id)
        ON DELETE RESTRICT ON UPDATE CASCADE
);

-- El mismo platillo conceptual (ej. "Hamburguesa Clásica") es una fila
-- por sucursal si dos sucursales lo venden -- así cada una maneja su
-- propio precio, disponibilidad e inventario, tal como pide CLAUDE.md.
-- tasa_iva es por producto (no global) y cantidad_disponible/
-- stock_minimo son el inventario real con alerta de stock bajo.
CREATE TABLE productos (
    id                    INT AUTO_INCREMENT PRIMARY KEY,
    sucursal_id           INT             NOT NULL,
    categoria_id          INT             NOT NULL,
    nombre                VARCHAR(150)    NOT NULL,
    descripcion           VARCHAR(500)    NULL,
    precio                DECIMAL(10, 2)  NOT NULL,
    tasa_iva              DECIMAL(5, 4)   NOT NULL DEFAULT 0.16,
    disponible            BOOLEAN         NOT NULL DEFAULT TRUE,
    cantidad_disponible   INT             NOT NULL DEFAULT 0,
    stock_minimo          INT             NOT NULL DEFAULT 0,
    imagen_path           VARCHAR(500)    NULL,
    CONSTRAINT fk_productos_sucursal
        FOREIGN KEY (sucursal_id) REFERENCES sucursales (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_productos_categoria
        FOREIGN KEY (categoria_id) REFERENCES categorias (id)
        ON DELETE RESTRICT ON UPDATE CASCADE
);

-- ============================================================
-- Catálogo: qué modificadores admite cada producto (N:M)
-- ============================================================

-- Llave primaria compuesta: el mismo par producto+modificador no puede
-- insertarse dos veces -- "sin duplicidad" reforzado por el propio esquema.
CREATE TABLE producto_modificador (
    producto_id      INT NOT NULL,
    modificador_id   INT NOT NULL,
    PRIMARY KEY (producto_id, modificador_id),
    CONSTRAINT fk_prodmod_producto
        FOREIGN KEY (producto_id) REFERENCES productos (id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_prodmod_modificador
        FOREIGN KEY (modificador_id) REFERENCES modificadores (id)
        ON DELETE CASCADE ON UPDATE CASCADE
);


CREATE TABLE ordenes (
    id               INT AUTO_INCREMENT PRIMARY KEY,
    tipo_orden       ENUM('COMEDOR', 'PARA_LLEVAR', 'PARA_RECOGER', 'DOMICILIO') NOT NULL,
    mesa_id          INT NULL,
    usuario_id       INT NOT NULL,
    cliente_id       INT NULL,
    turno_id         INT NULL,
    estado           ENUM('PENDIENTE', 'EN_PREPARACION', 'LISTA', 'ENTREGADA', 'PAGADA', 'CANCELADA')
                         NOT NULL DEFAULT 'PENDIENTE',
    subtotal         DECIMAL(10, 2)  NOT NULL,
    impuestos        DECIMAL(10, 2)  NOT NULL,
    distancia_km     DECIMAL(6, 2)   NULL,
    costo_envio      DECIMAL(10, 2)  NOT NULL DEFAULT 0,
    total            DECIMAL(10, 2)  NOT NULL,
    fecha_creacion   DATETIME        NOT NULL,
    CONSTRAINT fk_ordenes_usuario
        FOREIGN KEY (usuario_id) REFERENCES usuarios (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_ordenes_mesa
        FOREIGN KEY (mesa_id) REFERENCES mesas (id)
        ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT fk_ordenes_cliente
        FOREIGN KEY (cliente_id) REFERENCES clientes (id)
        ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT fk_ordenes_turno
        FOREIGN KEY (turno_id) REFERENCES turnos (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    INDEX idx_ordenes_estado (estado)
);

-- Artículos de cada orden. precio_unitario es snapshot del precio del
-- producto al momento de vender, misma razón que en ordenes. No se
-- guarda "subtotal_línea" porque cantidad * precio_unitario ya está en
-- la misma fila -- eso sí sería duplicidad a evitar.
CREATE TABLE detalle_orden (
    id               INT AUTO_INCREMENT PRIMARY KEY,
    orden_id         INT             NOT NULL,
    producto_id      INT             NOT NULL,
    cantidad         INT             NOT NULL,
    precio_unitario  DECIMAL(10, 2)  NOT NULL,
    -- Instrucción para cocina sobre este artículo (ej. "sin cebolla"), capturada al armar el carrito.
    nota             VARCHAR(300)    NULL,
    CONSTRAINT fk_detalle_orden
        FOREIGN KEY (orden_id) REFERENCES ordenes (id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_detalle_producto
        FOREIGN KEY (producto_id) REFERENCES productos (id)
        ON DELETE RESTRICT ON UPDATE CASCADE
);

-- Modificadores elegidos para un artículo de una orden ya tomada.
-- precio_extra también es snapshot, mismo razonamiento que arriba.
CREATE TABLE detalle_orden_modificador (
    detalle_orden_id  INT             NOT NULL,
    modificador_id    INT             NOT NULL,
    precio_extra      DECIMAL(10, 2)  NOT NULL,
    PRIMARY KEY (detalle_orden_id, modificador_id),
    CONSTRAINT fk_detmod_detalle
        FOREIGN KEY (detalle_orden_id) REFERENCES detalle_orden (id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_detmod_modificador
        FOREIGN KEY (modificador_id) REFERENCES modificadores (id)
        ON DELETE RESTRICT ON UPDATE CASCADE
);

-- Una orden puede pagarse con varios métodos (división de cuenta):
-- 1 orden : N pagos, en vez de una sola columna "metodo_pago" en ordenes.
CREATE TABLE pagos (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    orden_id      INT             NOT NULL,
    metodo_pago   ENUM('EFECTIVO', 'TARJETA', 'TRANSFERENCIA') NOT NULL,
    monto         DECIMAL(10, 2)  NOT NULL,
    fecha_pago    DATETIME        NOT NULL,
    CONSTRAINT fk_pagos_orden
        FOREIGN KEY (orden_id) REFERENCES ordenes (id)
        ON DELETE CASCADE ON UPDATE CASCADE
);

-- ============================================================
-- Datos semilla mínimos: sin esto, cualquier INSERT de prueba en
-- productos/usuarios/mesas fallaría por su FK a sucursales, y en
-- productos también por la FK a categorias. Bórralo o ajústalo cuando
-- tengas tus sucursales y categorías reales.
--
-- El usuario semilla es temporal: hoy no existe pantalla de login, así
-- que la app arranca "iniciando sesión" automáticamente con este
-- usuario (ver SesionActual.java) para poder crear órdenes reales.
-- Cuando exista login por PIN de verdad, este bootstrap se quita.
-- ============================================================
INSERT INTO sucursales (nombre, calle, numero, colonia, codigo_postal, ciudad, estado, pais, latitud, longitud, tarifa_base_envio, tarifa_por_km)
VALUES ('Crepas de Oro', 'Av de las Águilas', '3046', 'Arboledas', '31110', 'Chihuahua', 'Chihuahua', 'México', 28.6580350, -106.1239400, 20.00, 8.00);

INSERT INTO categorias (nombre) VALUES ('General');

INSERT INTO usuarios (sucursal_id, nombre, pin_acceso, rol, activo)
VALUES (1, 'Administrador Demo', '0000', 'ADMINISTRADOR', TRUE);
