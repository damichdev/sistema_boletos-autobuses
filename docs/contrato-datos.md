# Contrato de datos por pantalla — Sistema Zaca

Fecha: 24 de octubre de 2026 · Basado en el prototipo (`docs/prototipo/`), el diseño de vistas (`docs/2026-10-24-diseno-vistas.md`) y el script `TERMINAL ZACARIASS.txt`.

## Para qué sirve este documento

Es el punto de acuerdo entre front, back y base de datos. Para cada pantalla dice:

- **Lee:** qué datos muestra y de qué tabla salen.
- **Escribe:** qué guarda y en qué tabla.
- **Reglas en la base:** lo que PostgreSQL debe garantizar por sí mismo (restricciones, triggers, funciones), aunque el front también lo valide.
- **Estado en el script:** ✅ ya existe · ⚠️ existe pero hay que ajustarlo · ❌ falta.
- **Endpoint sugerido:** la llamada que el front hará al backend Java (REST). Es una propuesta; se ajusta en conjunto.

**Regla de trabajo:** si una pantalla necesita algo que no está aquí, se agrega primero a este documento y después se programa.

**Fuera de alcance por ahora:** Reportes del administrador. Cuentas de usuario entró al alcance el 25 oct 2026 (orden de construcción: administrador → despachador → taquillero).

## Convenciones

| Tema | Acuerdo |
|---|---|
| Nombres en la base | `snake_case`, esquemas `administracion`, `viajes`, `ventas` |
| Nombres en JSON | `camelCase` (`idViaje`, `ordenOrigen`) |
| Fechas | `timestamptz`; el JSON viaja en ISO 8601 con zona |
| Dinero | `NUMERIC(10,2)`; en JSON como número con 2 decimales |
| Catálogos | IDs fijos insertados con `OVERRIDING SYSTEM VALUE` (el `EXCLUDE` depende de que CANCELADO = 4) |
| Tramo | `int4range` semiabierto `[orden_origen, orden_destino)`: quien baja en una parada y quien sube en ella no chocan |
| Cambios al esquema | Solo con migraciones numeradas (`V001__...sql`), nunca editando un script ya aplicado |

## Arquitectura y tecnologías

| Capa | Tecnología |
|---|---|
| Frontend | React + Vite, React Router, la hoja de estilos del prototipo |
| Backend | Java + Spring Boot, API REST, **JdbcTemplate** con SQL a mano. **Sin JPA ni Hibernate** |
| Base de datos | PostgreSQL: tablas, vistas, funciones, procedures y triggers en scripts SQL versionados |
| Distribución | Pgpool-II como único punto de conexión; streaming: nodo 1 Tapachula (escritura), nodo 2 Tuxtla (lectura) |
| Taller | postgres_fdw hacia `taller_db`, expuesto como vista normal |

**Patrón:** MVC en capas + DTO. Controller (HTTP) → Service (reglas y `@Transactional`, traduce errores) → Repository (DAO con JdbcTemplate) → Pgpool.

**La base se crea con SQL, nunca desde Java.** Scripts numerados `V001__...sql`, aplicados con psql/pgAdmin o Flyway.

| Objeto | Uso | Llamada |
|---|---|---|
| Vista | Consultas armadas | `SELECT * FROM vista WHERE ...` |
| Función | Solo lecturas con parámetros | `SELECT * FROM viajes.buscar_salidas(?, ?, ?)` |
| Procedure | **Todo lo que modifica datos** | `CALL ventas.confirmar_venta(...)` |
| Trigger | Validaciones automáticas | No se llama; el Service atrapa su error |

**Regla por Pgpool:** una función que escribe, llamada con `SELECT`, puede caer en la réplica de solo lectura. Todo lo que escribe va como `PROCEDURE` con `CALL`. Pgpool con `disable_load_balance_on_write = 'transaction'`.

## Códigos de error (`RAISE EXCEPTION ... USING ERRCODE = 'Z000x'`)

| SQLSTATE | API | HTTP | Cuándo |
|---|---|---|---|
| 23P01 | ASIENTO_OCUPADO | 409 | `EXCLUDE` detecta tramo traslapado |
| 23505 | DUPLICADO | 409 | Violación de `UNIQUE` |
| Z0001 | VIAJE_CERRADO | 409 | Venta en viaje salido o en ruta |
| Z0002 | SIN_FOLIOS | 409 | Rango de folios agotado |
| Z0003 | EFECTIVO_INSUFICIENTE | 422 | Lo recibido no cubre el total |
| Z0004 | NO_CANCELABLE | 409 | Ya cancelado o viaje salido |
| Z0005 | UNIDAD_EN_MANTENIMIENTO | 422 | Reporte en `taller_db` |
| Z0006 | LICENCIA_VENCIDA | 422 | Vence antes de la salida |
| Z0007 | NO_ES_CONDUCTOR | 422 | Puesto distinto de CONDUCTOR |
| Z0008 | RUTA_EN_USO | 409 | Cambio de paradas con viajes |
| Z0009 | FOLIOS_EMITIDOS | 409 | Cambio de serie o inicio con folios usados |
| Z0010 | TAQUILLA_OCUPADA | 409 | Ventanilla con turno abierto |
| Z0011 | NO_ELIMINABLE | 409 | Chofer con viajes pendientes |
| Z0012 | VIAJE_EMPALMADO | 409 | Unidad o chofer con otro viaje en ese horario |

---

## 1. Inicio de sesión

| | |
|---|---|
| **Lee** | `administracion.personal_app` (username, contraseña, rol, sede, activo) + `administracion.personal` (nombre para la barra lateral) |
| **Escribe** | Nada |
| **Reglas en la base** | `username` único ✅. La contraseña se guarda como hash (bcrypt), nunca en texto ❌. Una cuenta inactiva no entra ❌ |
| **Endpoint** | `POST /api/sesion` → `{ idCuenta, username, nombreCompleto, rol, idTerminal, nombreTerminal }` |

**Cambios al script:**
- ❌ `personal_app.password_hash TEXT NOT NULL`
- ❌ `personal_app.activo BOOLEAN NOT NULL DEFAULT TRUE`
- ⚠️ `personal_app.id_taquilla` → sustituir por `id_terminal` (sede base). La ventanilla se elige al abrir turno.

## 2. Abrir turno (taquillero)

> Nació con el diseño de Cuentas. Confirmar si entra en esta etapa; si no, el taquillero queda ligado a una taquilla fija.

| | |
|---|---|
| **Lee** | `administracion.taquilla` de la sede de la cuenta (activas) + turnos abiertos para saber cuáles están ocupadas y por quién |
| **Escribe** | Inserta un turno al abrir; pone `fin` al cerrar sesión |
| **Reglas en la base** | No puede haber dos turnos abiertos en la misma taquilla ni dos turnos abiertos de la misma cuenta ❌ |
| **Endpoints** | `GET /api/taquillas/disponibles` · `POST /api/turnos` · `POST /api/turnos/{id}/cerrar` |

**Cambios al script:**
```sql
CREATE TABLE administracion.turno(
  id_turno INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_app INT NOT NULL REFERENCES administracion.personal_app(id_app),
  id_taquilla INT NOT NULL REFERENCES administracion.taquilla(id_taquilla),
  inicio TIMESTAMPTZ NOT NULL DEFAULT now(),
  fin TIMESTAMPTZ
);
CREATE UNIQUE INDEX turno_taquilla_abierta ON administracion.turno(id_taquilla) WHERE fin IS NULL;
CREATE UNIQUE INDEX turno_cuenta_abierta  ON administracion.turno(id_app)      WHERE fin IS NULL;
```

---

## 3. Venta (taquillero) — pantalla única con 4 paneles

### 3.1 Panel Destino

| | |
|---|---|
| **Lee** | `administracion.terminal` (lista de ciudades). El origen por defecto es la sede de la cuenta |
| **Endpoint** | `GET /api/terminales` → `[{ idTerminal, nombre }]` |

Estado: ✅ la tabla existe.

### 3.2 Panel Horario

| | |
|---|---|
| **Lee** | Salidas de la fecha cuya ruta pasa por el origen **antes** que por el destino, en estado PROGRAMADO o ABORDANDO y aún no salidas. Por cada una: hora, tipo de autobús, servicio, placa, **lugares libres en el tramo** y **precio del tramo** |
| **Reglas en la base** | Una función que haga la búsqueda completa, para que el back no repita la lógica de tramos |
| **Endpoint** | `GET /api/salidas?origen=1&destino=5&fecha=2026-10-25` → `[{ idViaje, fechaSalida, tipoBus, servicio, placa, ordenOrigen, ordenDestino, libres, precio }]` |

**Cambios al script:**
- ⚠️ `viajes.ruta_paradas`: hoy cada fila tiene origen **y** destino; el diseño necesita **una fila por terminal** con su `orden` (0, 1, 2…), km y minutos desde el origen. Detalle en el PDF de revisión, punto 6.
- ❌ **Precio por tramo:**
  ```sql
  CREATE TABLE viajes.precio_tramo(
    id_ruta INT REFERENCES viajes.ruta(id_ruta) ON DELETE CASCADE,
    orden_origen INT, orden_destino INT,
    precio NUMERIC(10,2) NOT NULL CHECK (precio > 0),
    PRIMARY KEY (id_ruta, orden_origen, orden_destino),
    CHECK (orden_origen < orden_destino)
  );
  ```
- ❌ Función `viajes.buscar_salidas(id_origen, id_destino, fecha)` que devuelva las columnas del endpoint.

### 3.3 Panel Asientos (y vista "Mostrar al cliente")

| | |
|---|---|
| **Lee** | Acomodo (`layout`: 2+2, 2+1, 1+1) y `capacidad` de la unidad del viaje; números de asiento **ocupados en el tramo** del pasajero |
| **Reglas en la base** | Un asiento está ocupado si hay un boleto no cancelado del mismo viaje cuyo tramo se traslapa (`&&`) con el pedido |
| **Endpoint** | `GET /api/salidas/{idViaje}/asientos?ordenOrigen=0&ordenDestino=4` → `{ layout, capacidad, ocupados: [1, 2, 5] }` |

**Cambios al script:**
- ✅ `viajes.tipo_bus` + `viajes.layout` + `unidad` ya dan acomodo y capacidad.
- ❌ Función `ventas.asientos_ocupados(id_viaje, orden_origen, orden_destino) RETURNS INT[]`.

### 3.4 Panel Titular y tarifas

| | |
|---|---|
| **Lee** | `ventas.tarifa` con su **porcentaje de descuento** (Adulto, Estudiante, INAPAM, Infantil) |
| **Datos que captura** | Nombre completo e identificación (CURP u otra) **solo del titular**; una tarifa por asiento |
| **Endpoint** | `GET /api/tarifas` → `[{ idTarifa, nombre, descuento }]` |

**Cambios al script:**
- ❌ `ventas.tarifa.descuento NUMERIC(5,2) NOT NULL DEFAULT 0` (0.50 = 50 %).
- ⚠️ `ventas.pasajero.curp` es `NOT NULL` y con formato CURP. El titular puede identificarse con INE u otro documento. Propuesta: `identificacion TEXT NOT NULL` y `curp` opcional.

### 3.5 Panel Cobro → Confirmar e imprimir

Es la operación más delicada: **un cobro = una venta con varios asientos, un folio y un QR.**

| | |
|---|---|
| **Escribe** | 1 venta (titular, folio, total, recibido, quién vendió, taquilla) + 1 boleto por asiento (tarifa, precio, tramo) + folio consumido de la taquilla |
| **Reglas en la base** | Todo en **una transacción**: si un asiento ya se vendió en otra taquilla, el `EXCLUDE` falla y no se cobra nada. No se vende en un viaje ya salido o en ruta. `recibido >= total`. El folio sale del rango de la taquilla y nunca se repite |
| **Endpoint** | `POST /api/ventas` |

Petición:
```json
{ "idViaje": 2, "idTaquilla": 1, "titular": { "nombre": "María López Pérez", "identificacion": "LOPM900514MCSPRR01" },
  "ordenOrigen": 0, "ordenDestino": 4, "recibido": 1000,
  "asientos": [ { "num": 6, "idTarifa": 1 }, { "num": 7, "idTarifa": 3 }, { "num": 8, "idTarifa": 4 } ],
  "reemplazaA": null }
```
Respuesta `201`: `{ idVenta, folio: "TAP1-000009", total: 700.00, cambio: 300.00, qr: "TAP1-000009" }`.
Errores esperados: `409 ASIENTO_OCUPADO` (con la lista de asientos perdidos), `409 VIAJE_CERRADO`, `409 SIN_FOLIOS`, `422 EFECTIVO_INSUFICIENTE`.

> El precio lo calcula el **backend o la base** a partir de `precio_tramo` y `tarifa.descuento`; el front solo lo muestra. El total que manda el front no se confía.

**Cambios al script:**
```sql
CREATE TABLE ventas.venta(
  id_venta INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  folio TEXT NOT NULL UNIQUE,                      -- serie + número, ej. TAP1-000009
  id_taquilla INT NOT NULL REFERENCES administracion.taquilla(id_taquilla),
  id_vendedor INT NOT NULL REFERENCES administracion.personal_app(id_app),
  id_pasajero INT NOT NULL REFERENCES ventas.pasajero(id_pasajero),   -- el titular
  total NUMERIC(10,2) NOT NULL CHECK (total >= 0),
  recibido NUMERIC(10,2) NOT NULL CHECK (recibido >= total),
  reemplaza_a INT REFERENCES ventas.venta(id_venta),
  fecha TIMESTAMPTZ NOT NULL DEFAULT now(),
  escaneado_en TIMESTAMPTZ
);
-- boleto: un renglón por asiento, ligado a su venta
ALTER TABLE ventas.boleto ADD COLUMN id_venta INT NOT NULL REFERENCES ventas.venta(id_venta);
```
- ⚠️ `ventas.boleto`: `precio NOT NULL CHECK (precio >= 0)`. `tramo_ocupacion` lo calcula un **trigger** a partir de `id_terminal_origen/destino` y `ruta_paradas`; nunca lo manda el front.
- ⚠️ `administracion.taquilla`: `folios_usados DEFAULT 0` y `CHECK (rango_inicial <= rango_final)`.
- ❌ Procedure `ventas.confirmar_venta(...)` que haga todo lo anterior en una sola transacción y devuelva el folio por un parámetro `INOUT`.

### 3.6 Cambiar boleto (desde Boletos vendidos)

Equivale a **cancelar la venta anterior + crear una nueva** con `reemplazaA` = id de la anterior, en una sola transacción. El front precarga el viaje, el titular, los asientos y las tarifas de la venta original.

| | |
|---|---|
| **Endpoint** | `POST /api/ventas/{idVenta}/cambio` con el mismo cuerpo que `POST /api/ventas` |
| **Asientos** | `GET /api/salidas/{idViaje}/asientos?ordenOrigen&ordenDestino&excluirVenta={idVenta}`: los asientos del boleto original salen libres para poder conservarlos |
| **Si falla** | Si la venta nueva falla (asiento ocupado, efectivo), la original **sigue vigente**: todo en una transacción |

---

## 4. Boletos vendidos (taquillero)

| | |
|---|---|
| **Lee** | Ventas de **todas las taquillas**: folio, hora y fecha de salida, tramo (origen → destino), titular, asientos, total, estado (Vigente, Cancelado, Abordado, Ya salió), quién vendió. Búsqueda por folio o titular |
| **Escribe** | Cancelar: marca la venta y sus boletos como CANCELADO, guarda quién canceló |
| **Reglas en la base** | Solo se cancela si está vigente **y** el viaje no ha salido. Al cancelar, los asientos quedan libres (el `EXCLUDE` ya ignora los cancelados) |
| **Endpoints** | `GET /api/ventas?buscar=TAP1-0000` · `GET /api/ventas/{idVenta}/ticket` (reimprimir) · `POST /api/ventas/{idVenta}/cancelar` → `{ devolver: 700.00 }` · `409 NO_CANCELABLE` |
| **Cada fila trae** | `idVenta, folio, reemplazaA (folio), idViaje, fechaSalida, idOrigen, idDestino, origen, destino, titular {nombre, identificacion}, asientos [{num, idTarifa}], total, vendio (username), estado` con estado `VIGENTE`, `CANCELADO`, `ABORDADO` o `YA_SALIO`, calculado en la base |

**Cambios al script:**
- ❌ `ventas.venta.cancelada_por INT REFERENCES administracion.personal_app(id_app)` y `cancelada_en TIMESTAMPTZ`.
- ⚠️ Vista de consulta que junte venta + boletos + viaje + terminales para no hacer cinco JOIN desde Java.

---

## 5. Programar salida (despachador)

### 5.1 Formulario Nueva salida

| | |
|---|---|
| **Lee** | Rutas; unidades (placa, tipo, capacidad, **si está en taller**); choferes (puesto CONDUCTOR, **vigencia de licencia**) |
| **Escribe** | `viajes.viaje_programado` en estado PROGRAMADO (ruta, fecha y hora, unidad, chofer). Al guardarse aparece en la venta |
| **Reglas en la base** | Bloquear si la unidad tiene mantenimiento en `taller_db` (vía `postgres_fdw`), si el empleado no es CONDUCTOR, si la licencia vence antes de la salida o si la hora ya pasó. El mensaje debe decir **por qué** |
| **Endpoints** | `GET /api/unidades` · `GET /api/choferes` · `POST /api/salidas` → `422` con `{ motivos: ["Unidad en mantenimiento", "Licencia vencida"] }` |

**Cambios al script:**
- ❌ Trigger `BEFORE INSERT OR UPDATE` en `viaje_programado` con las cuatro validaciones; la de mantenimiento consulta la tabla foránea de `taller_db`.
- ⚠️ `CHECK (id_relevo IS NULL OR id_relevo <> id_conductor)`.
- ⚠️ `fk_conductor_viaje` y `fk_relevo_viaje` con `ON DELETE RESTRICT` (hoy `CASCADE` borra viajes y boletos).
- ⚠️ `fecha_salida TIMESTAMPTZ` e índice por fecha.

### 5.2 Tabla de salidas de hoy + buscador

| | |
|---|---|
| **Lee** | Viajes de hoy cuya ruta pasa por la sede del despachador: hora, ruta, placa, chofer, estado |
| **Endpoint** | `GET /api/salidas/hoy?terminal=1` |

### 5.3 Ver detalle (solo consulta)

| | |
|---|---|
| **Lee** | Recorrido con hora estimada por parada (salida + `min_desde_origen`); **cuántos suben y bajan** en cada parada; **ocupación por tramo** (asientos ocupados / capacidad); unidad; chofer con vigencia de licencia |
| **Endpoint** | `GET /api/salidas/{idViaje}/detalle` → `{ paradas: [{ terminal, hora, suben, bajan }], tramos: [{ origen, destino, ocupados, capacidad }], unidad, chofer }` |

**Cambios al script:**
- ❌ Funciones `viajes.ocupacion_por_tramo(id_viaje)` y `viajes.movimiento_por_parada(id_viaje)`, o una vista que dé ambas.

---

## 6. Manifiesto (despachador)

| | |
|---|---|
| **Lee** | Selector de viajes de hoy que **salen de esta escala** (no los que terminan aquí). Encabezado: hora de salida de la escala, unidad, "abordaron X de Y". Tabla de quienes **suben en esta terminal**: asiento, titular, destino, tarifa, folio, estado (Abordó / Pendiente / No se presentó) |
| **Escribe** | "Marcar en ruta": registra la salida del viaje desde esta escala y, en el origen, cambia el estado a EN RUTA |
| **Reglas en la base** | "No se presentó" **no libera** el asiento: el boleto sigue vigente todo el viaje. "Marcar en ruta" solo a partir de la hora de salida de la escala |
| **Endpoints** | `GET /api/manifiesto?idViaje=2&terminal=1` · `POST /api/salidas/{idViaje}/en-ruta` |

**Cambios al script:**
- ⚠️ `ventas.v_manifiesto_pasajeros` existe, pero filtra por viaje completo; hace falta filtrarla por **terminal de subida**: una función con parámetros o una columna `id_terminal_origen` en la vista.
- ❌ Registro de salida por escala: tabla `viajes.salida_escala(id_viaje, id_terminal, hora_real, id_app)` o solo el estado del viaje si basta con el origen.
- ⚠️ La vista `viajes.cartelera_viajes` está incompleta en el script (PDF de revisión, punto 1).

---

## 7. Abordaje con QR (despachador)

| | |
|---|---|
| **Lee/Escribe** | El QR contiene el **folio**. Se valida contra el viaje elegido y la terminal del despachador. Si es válido, marca **todos** los asientos de la venta como ABORDADO y guarda la hora del escaneo |
| **Respuestas** | Verde `OK` (pasajeros y asientos) · Ámbar `YA_ESCANEADO` (hora del primer escaneo) · Rojo `INVALIDO` con motivo: folio no existe, cancelado, otro viaje, no sube en esta terminal |
| **Endpoint** | `POST /api/abordaje` `{ folio, idViaje, idTerminal }` → `{ resultado, motivo?, asientos?, hora? }` |

**Cambios al script:**
- ✅ Estado ABORDADO existe en `ventas.estado_boleto`.
- ❌ `ventas.venta.escaneado_en` (incluido en la tabla de 3.5).
- ❌ Procedure `ventas.validar_abordaje(folio, id_viaje, id_terminal)` que haga la validación y la marca en una transacción, para que dos tabletas no marquen el mismo QR a la vez.

---

## 8. Rutas (administrador)

| | |
|---|---|
| **Lee/Escribe** | Ruta (nombre, servicio) + paradas en orden (terminal, km y minutos desde el origen) + **precio de cada tramo** (todas las combinaciones origen < destino) |
| **Reglas en la base** | Primera parada en km 0 y minuto 0; km y minutos crecientes; una terminal no se repite en la ruta; todos los tramos con precio. **Si la ruta ya tiene viajes, no se pueden cambiar sus paradas** (solo nombre, tiempos y precios), porque los boletos guardan órdenes de parada |
| **Endpoints** | `GET /api/rutas` · `POST /api/rutas` · `PUT /api/rutas/{id}` → `409 RUTA_EN_USO` si se tocan paradas con viajes |

**Cambios al script:**
- ⚠️ Rediseño de `ruta_paradas` (ver 3.2) con `UNIQUE (id_ruta, orden)`, `UNIQUE (id_ruta, id_terminal)` y `CHECK (km_desde_origen >= 0)`.
- ❌ `precio_tramo` (ver 3.2).
- ⚠️ `fk_terminal_*_parada` con `ON DELETE RESTRICT`.

## 9. Unidades (administrador)

| | |
|---|---|
| **Lee/Escribe** | Placa, modelo, año, kilometraje, tipo de servicio, acomodo, capacidad. "En taller" se **lee** de `taller_db`, no se captura aquí |
| **Reglas en la base** | Placa única ✅; año entre 1901 y el año siguiente ⚠️; km ≥ 0 ✅; capacidad > 0 ✅ |
| **Endpoints** | `GET/POST/PUT /api/unidades` |

**Cambios al script:**
- ⚠️ `check_anio_futuro` permite modelos del año siguiente (`<= año actual + 1`).
- ❌ Vista o función que una `unidad` con su estado de mantenimiento desde el FDW.

## 10. Taquillas (administrador) + Nueva ciudad

| | |
|---|---|
| **Lee/Escribe** | Ciudad o terminal, identificador, serie, folio inicial, folio final, folios usados (solo lectura), activa |
| **Nueva ciudad** | Alta rápida en `administracion.terminal` (nombre y dirección) desde el formulario de la taquilla; queda disponible en todo el sistema |
| **Reglas en la base** | Serie única ✅. `rango_inicial <= rango_final` ❌. **Con folios ya emitidos no se puede cambiar la serie ni el folio inicial**, y el final debe cubrir los usados (trigger) ❌. Nombre de ciudad único sin importar mayúsculas ❌ |
| **Endpoints** | `GET/POST/PUT /api/taquillas` · `POST /api/terminales` |

**Cambios al script:**
- ❌ `UNIQUE` sobre `lower(nombre_terminal)`.
- ⚠️ `terminal.id_conexion` es `NOT NULL`: al dar de alta una ciudad hace falta un valor por defecto (por ejemplo DESCONECTADO hasta enlazar el nodo).
- ⚠️ `fk_taquilla_app` con `SET NULL` en lugar de `CASCADE` (o desaparece si se adopta el turno).

## 11. Personal (administrador)

| | |
|---|---|
| **Lee** | Empleados agrupados por puesto en paneles: Taquilla, Despacho, Administración, Choferes (con vigencia de licencia), Mecánicos |
| **Escribe** | Alta y edición: nombre, apellidos, CURP, teléfono, puesto; licencia y vigencia obligatorias para choferes. **Eliminar** empleado |
| **Reglas en la base** | CURP con formato válido y única ✅ (sexo `H` o `M`, como el script V6/V7); teléfono de 10 dígitos ✅; licencia y vigencia obligatorias si es CONDUCTOR ❌. **No se elimina** a un chofer con viajes programados o en ruta, ni a la persona con la sesión abierta; al eliminar, su cuenta se elimina también |
| **Endpoints** | `GET/POST/PUT /api/personal` · `DELETE /api/personal/{id}` → `409` con `{ motivos: [...] }` |

**Cambios al script:**
- ❌ Puesto **ADMINISTRADOR** en `administracion.puesto`.
- ❌ `CHECK` o trigger: si el puesto es CONDUCTOR, `licencia` y `vigencia` no nulos.
- ⚠️ Eliminar: `viaje_programado.id_conductor` con `RESTRICT` (ver 5.1); `personal_app.id_personal` con `CASCADE` ✅ ya está.
- Decidir en equipo: **¿borrado real o baja lógica?** Lo más seguro para el historial de ventas es `activo = false` en lugar de `DELETE`.

---

## 11b. Cuentas de usuario (administrador)

| | |
|---|---|
| **Lee** | Usuario, nombre completo, rol, sede (`personal_app.id_terminal`), activo |
| **Escribe** | Alta: persona **sin cuenta**, rol, sede, contraseña temporal (mín. 8). Edición: rol, sede, activo y, si se escribe, contraseña nueva |
| **Reglas** | El **servidor genera el usuario**: inicial del nombre + apellido paterno, sin acentos, en minúsculas, con número si se repite (`m.constantino`, `m.constantino2`). El front solo muestra la vista previa con la misma regla. La contraseña se guarda con hash. Una cuenta inactiva no inicia sesión (`CUENTA_INACTIVA`). El administrador no puede desactivarse a sí mismo |
| **Endpoints** | `GET /api/cuentas` · `POST /api/cuentas` `{ idPersonal, rol, idTerminal, password }` · `PUT /api/cuentas/{id}` `{ rol, idTerminal, activo, password? }` |

Formas exactas de todos los DTOs del administrador: `frontend/src/api/tipos.ts` (sección "Administrador"). `GET /api/personal` incluye `tieneCuenta`; `GET /api/rutas` incluye `enUso`; `GET /api/unidades` incluye `enMantenimiento`; `DUPLICADO` lleva `detalle.campo` (`placa`, `curp`, `serie`, `identificador`, `nombre`).

---

## 12. Auditoría (transversal)

Cada venta, cancelación, abordaje, salida programada y salida "En ruta" debe quedar a nombre de la persona que la hizo.

| Movimiento | Dónde queda |
|---|---|
| Venta | `venta.id_vendedor` ✅ (ya existe como `boleto.id_vendedor`) |
| Cancelación | `venta.cancelada_por` ❌ |
| Abordaje, programación, salida | Tabla `administracion.bitacora(id, fecha, id_app, accion, detalle)` ❌ o triggers de auditoría por tabla |

---

## Resumen de cambios para la base, por prioridad

| # | Cambio | Pantallas que lo necesitan |
|---|---|---|
| 1 | Rediseño de `ruta_paradas` (una fila por terminal con `orden`) | Venta, Rutas, Manifiesto, Detalle |
| 2 | `precio_tramo` + `tarifa.descuento` | Venta, Rutas |
| 3 | Tabla `venta` (grupo, folio, QR, titular) + `boleto.id_venta` | Venta, Boletos vendidos, Abordaje |
| 4 | Funciones `buscar_salidas`, `asientos_ocupados`, `confirmar_venta` | Venta |
| 5 | Cascadas a `RESTRICT` en conductor, pasajero, viaje y terminal | Todas (evita borrar ventas) |
| 6 | `password_hash` y `activo` en `personal_app` | Inicio de sesión |
| 7 | Trigger de validación de `viaje_programado` (FDW, licencia, puesto) | Programar salida |
| 8 | Manifiesto por terminal de subida + `validar_abordaje` | Manifiesto, Abordaje |
| 9 | Puesto ADMINISTRADOR, reglas de taquilla y ciudad única | Personal, Taquillas |
| 10 | `turno` y `bitacora` (si se confirma el alcance) | Abrir turno, auditoría |

## Datos de prueba compartidos

El `seed.sql` debe reproducir los escenarios del prototipo (`docs/prototipo/js/datos.js`) para que front, back y base prueben lo mismo:

- **Ruta Costa:** Tapachula → Huixtla → Pijijiapan → Tonalá → Tuxtla (km 0, 40, 140, 215, 390). **Directo:** Tapachula → Tuxtla.
- **Familia de 3:** asientos 6, 7 y 8 con tarifas Adulto, INAPAM e Infantil.
- **Asiento compartido por tramos:** el 5 vendido Tapachula → Huixtla y otra vez Huixtla → Tuxtla.
- **Bloqueos al programar:** una unidad en taller (en `taller_db`), un chofer con licencia vencida y otro que vence en menos de 30 días.
- **Folios:** dos taquillas en Tapachula (TAP-01, TAP-02) y una en Huixtla (HUI-01), cada una con su serie.
