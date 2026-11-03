# Zaca Bus: pendientes del sistema central

Fecha del análisis: 2026-11-02. Se hizo leyendo el código de `zaca_bus/`, el script `TERMINAL ZACARIASS VERSION MASTER 3.0.txt`, `CAMBIOS Y ASI.txt`, `TALLER.BD.txt` y consultando la base `zacabus` (`localhost:5432`).

Orden de trabajo acordado: **primero se termina Taller y Jefe de taller (sección 1)**. Todo lo demás (secciones 2 a 5) se hace después.

Convención: `[ ]` pendiente. "Verificado" significa que se comprobó en el código o en la base; "sin verificar" significa que es una inferencia.

---

## 1. Taller y Jefe de taller (se hace primero)

### 1.1 Estado actual

| Pieza | Estado |
|---|---|
| Tabla `administracion.taller` (clave, nombre, ciudad, dirección, bahías, activo) | Existe en la base central. |
| `sp_guardar_taller`, `sp_asignar_encargado` | Existen en la base central. |
| Reglas de personal (solo MECANICO y JEFE_TALLER llevan `id_taller`; un jefe activo por taller, índice `unq_jefe_taller_activo`; el jefe con cuenta debe conservar su taller) | Existen como triggers e índice. |
| Base de datos de taller (`TALLER.BD.txt`: `estado_taller`, `bus_taller`, `mecanico`, `orden_completa`, `v_semaforo_unidades`) | Solo es un archivo de diseño. **No hay base `taller` creada** en el servidor y la extensión `postgres_fdw` **no está instalada** (extensiones actuales: `plpgsql`, `btree_gist`). |
| `TallerDAO` y `OrdenTallerDAO` | Son stubs: devuelven listas vacías y no guardan nada. |
| `TalleresController` (taller.fxml, taller-form.fxml) | Solo frontend. "Guardar" cierra sin persistir. |
| `OrdenesTallerController` (ordenes-taller.fxml, orden-detalle.fxml) | Solo lectura (modo auditoría), con lista vacía. Las vistas `orden-ingreso-form.fxml` y `orden-cerrar-form.fxml` existen pero siguen conectadas a `VistaController` (solo navegan). |
| Inicio de sesión del rol `JEFE_TALLER` | Abre `taller.fxml`, que es la pantalla del administrador. No hay menú lateral ni pantallas propias del jefe de taller. |
| Formulario de empleado: taller del mecánico | No se guarda (`dao.guardar(..., null)`), y `TallerDAO.nombresActivos()` está vacío. |

### 1.2 Decisiones por tomar antes de programar

- [x] **Una base o dos.** DECIDIDO: el taller es una base aparte (`taller`), con el script `TALLER.BD.txt`. Pendiente crearla: hoy no existe en el servidor. `postgres_fdw` está disponible en la instalación de PostgreSQL 16 (verificado), solo falta ejecutar `CREATE EXTENSION` en `zacabus`.
- [ ] **Mecánicos.** `TALLER.BD.txt` define una tabla `mecanico` propia, pero el sistema central ya tiene el puesto `MECANICO` en `administracion.personal`. Decidir cuál es la fuente de verdad; hoy están duplicados.
- [ ] **Identificar la unidad.** `bus_taller` usa `placa` como clave; en el central es `viajes.unidad.placa` (única). Decidir si el vínculo es por placa o por `id_unidad`.
- [ ] **Semáforo de unidades.** El taller marca una unidad como OPERATIVO, EN MANTENIMIENTO o DE BAJA. Definir cómo afecta al central: hoy `viajes.unidad.activo` es un booleano y `fn_asignar_conductor` solo exige `activo = TRUE`.

### 1.3 Tareas de base de datos

- [ ] Crear la base `taller` y ejecutar `TALLER.BD.txt` (el script ya fija la zona horaria de la base `taller`).
- [ ] Configurar la conexión entre bases (`postgres_fdw`, servidor foráneo, mapeo de usuario, `IMPORT FOREIGN SCHEMA`).
- [ ] Vistas de lectura para talleres: lista con ciudad, bahías, número de autobuses, número de mecánicos y encargado (lo que muestra `Taller` en Java).
- [ ] Vistas de lectura para órdenes: número, taller, unidad, servicio, mecánico, bahía, ingreso, estado, descripción, quién registró y última actualización (lo que muestra `OrdenTaller`).
- [ ] Faltan columnas en el diseño de `orden_completa` que la pantalla ya muestra: taller, bahía, servicio/tipo de mantenimiento, estado de la orden, quién registró.
- [ ] Procedimientos: abrir orden (ingreso), asignar mecánico, cambiar estado, cerrar orden (con costo y fecha de salida), cancelar orden.
- [ ] Reglas de negocio de la orden: una sola orden abierta por unidad, el mecánico debe pertenecer al taller, no pasar de las bahías disponibles, no cerrar con fecha anterior al ingreso.
- [ ] Efecto de la orden en el central: al abrir una orden, la unidad deja de poder asignarse a salidas; al cerrarla, vuelve a estar disponible. Revisar qué pasa con las salidas ya programadas de esa unidad.
- [ ] Lista de talleres activos para asignar mecánicos y de jefes de taller libres (hoy `encargadosDisponibles()` está vacío).
- [ ] Ampliar `administracion.v_personal` para devolver terminal, taquilla y taller (hoy no los trae), para que la lista de personal muestre la asignación del mecánico y del jefe.

### 1.4 Tareas de Java

- [ ] `TallerDAO`: listar, guardar (`sp_guardar_taller`), asignar encargado (`sp_asignar_encargado`), activar/desactivar, nombres de talleres activos, encargados disponibles. Todo mediante vistas y procedimientos, sin consultas directas a tablas.
- [ ] `OrdenTallerDAO`: listar, detalle, abrir, asignar mecánico, cambiar estado, cerrar.
- [ ] `TalleresController` y `taller-form.fxml`: guardar de verdad, validar clave y nombre únicos, mensajes de error claros.
- [ ] `OrdenesTallerController`: pasar de "modo auditoría" a operar (ingresar y cerrar órdenes con los formularios `orden-ingreso-form` y `orden-cerrar-form`, hoy sin controlador propio).
- [ ] Guardar el taller del mecánico en el formulario de empleado.
- [ ] **Pantallas propias del Jefe de taller:** menú lateral (`barra-jefe-taller.fxml`), solo ve y opera su taller, sin acceso a las pantallas del administrador. Hoy el rol entra a `taller.fxml` sin restricción por taller.
- [ ] Separar los controladores que hoy sirven a varias pantallas (ya se hizo con Personal; en Taller, `TalleresController` y `OrdenesTallerController` atienden 2 FXML cada uno).
- [ ] Probar con datos reales: un taller, un jefe con cuenta, un mecánico, una unidad y una orden de ingreso a cierre.

---

## 2. Pendientes del sistema central (después del taller)

### 2.1 Pantallas que siguen siendo maqueta (verificado: usan `VistaController`, solo navegan)

- [ ] `abrir-turno.fxml`: el taquillero debe abrir y cerrar turno. Hoy la venta calcula el turno por hora (`VentaDAO.turnoActual()`: matutino antes de las 14 h, vespertino antes de las 20 h, nocturno después). Falta tabla de turnos abiertos, procedimientos de apertura y cierre, y usar el turno abierto al vender.
- [ ] `cambiar-boleto.fxml`: no hay lógica de cambio de boleto. El script ya tiene `reemplaza_a` en la venta y `sp_registrar_venta` lo acepta, pero la pantalla no lo usa.
- [ ] `mostrar-cliente.fxml`: pantalla para el cliente, sin lógica.
- [ ] No existe ninguna pantalla de reportes (ventas por turno, por taquilla, por ruta, ocupación, cortes).

### 2.2 Ciclo de vida del viaje

- [ ] Los procedimientos `sp_marcar_viaje_abordando` y `sp_cerrar_viaje` existen en la base, pero **ningún código Java los llama** (verificado). Solo se usa `sp_marcar_viaje_en_ruta`.
- [ ] No hay validación de la secuencia de estados (por ejemplo, impedir pasar de PROGRAMADO a CERRADO).
- [ ] `sp_registrar_venta` solo rechaza viajes CERRADO. No comprueba que la salida del origen ya haya pasado; la restricción de horario existe solo en las consultas de búsqueda.

### 2.3 Ventas y boletos

- [ ] Total mostrado tras cancelar un solo asiento: la columna Total y el total del ticket siguen mostrando lo pagado en la venta original, sin descontar los asientos cancelados.
- [ ] La cancelación no registra devolución: no se guarda cuánto se reembolsó ni quién lo entregó. Cualquier taquillero o administrador puede cancelar cualquier venta; no se valida que sea de su taquilla o de su turno.
- [ ] El titular se guarda por CURP con `ON CONFLICT DO UPDATE`: si la CURP ya existe, **sobrescribe** el nombre del pasajero registrado antes.
- [ ] Abordaje: confirmar con la cámara que el QR por asiento (folio individual) se lee bien y que `ControlAbordajeController.resolver` se comporta bien con un solo boleto. No se probó en vivo.
- [ ] Imprimir: probar la impresión de varios boletos (una hoja por asiento) en una impresora real.

### 2.4 Personal, cuentas y roles

- [ ] La lista de personal muestra "—" en la columna Asignación porque `v_personal` no trae terminal ni taquilla.
- [ ] El filtro de ciudad y de terminal en Personal no filtra (hay comentarios "pendiente backend").
- [ ] Al editar un empleado, el formulario no precarga terminal ni taquilla.
- [ ] No hay pantalla ni procedimiento para que un usuario cambie su propia contraseña.
- [ ] No hay control de acceso por rol en la navegación: `App.irA` no revisa el rol de la sesión. Cualquier pantalla se abre si se conoce su nombre.
- [ ] Revisar y limpiar comentarios "pendiente backend" que ya no sean ciertos.

### 2.5 Calidad del código

- [ ] Separar controladores que atienden varias pantallas: `VentaController` (4 FXML, 21 campos), `UnidadesController` (3 FXML, 21 campos), `RutasController` (2 FXML, 19 campos), `ControlAbordajeController`, `ProgramarSalidaController`, `TerminalesController`. `PersonalController` ya se dividió.
- [ ] Pruebas: no hay pruebas automáticas de los procedimientos ni de los DAO.
- [ ] Datos de prueba: la base se vació (solo queda `dana.constantino`); falta un script de datos de ejemplo (terminales, rutas, unidades, taquillas, personal) para poder probar el sistema.

---

## 3. Pendientes de base de datos

### 3.1 Seguridad y roles

- [ ] **La aplicación se conecta como `postgres` (superusuario)**, según `.env` (`DB_USER=postgres`). Crear un rol de aplicación con los permisos mínimos.
- [ ] El script maestro **no tiene ningún `GRANT` ni `CREATE ROLE`** (verificado). Falta definir roles (administrador, taquillero, despachador, jefe de taller) y quitar el acceso directo a las tablas (`REVOKE`), dejando solo vistas, funciones y procedimientos.
- [ ] Hoy hay 15 procedimientos `SECURITY DEFINER` en los esquemas del sistema. Revisarlos uno por uno cuando se definan los roles, para que no permitan más de lo previsto.
- [ ] La función `fn_obtener_credencial` devuelve el hash de la contraseña a la aplicación. Revisar si se valida mejor dentro de la base.
- [ ] Limpiar roles ajenos al proyecto que existen en el servidor (`usr1`, `usr2`, `usr3`, `danaconstantino`, `app_redayuda`), si no se usan.

### 3.2 Estructura

- [ ] **Consolidar los scripts.** `CAMBIOS Y ASI.txt` acumula cambios que ya están en la base real pero **no están en el script maestro**, y contiene sentencias que no se pueden ejecutar dos veces (`DROP PROCEDURE`, `ADD CONSTRAINT`). Pasar todo a un script maestro nuevo (V10) que se pueda ejecutar sobre una base vacía.
- [ ] Tabla de turnos abiertos y sus procedimientos (ver 2.1).
- [ ] Registro de devoluciones por cancelación (ver 2.3).
- [ ] Máquina de estados del viaje con validación de transiciones (ver 2.2).
- [ ] Todo lo del taller (ver sección 1.3).
- [ ] Auditoría: quién cambió qué y cuándo (rutas, tarifas, precios, personal, folios).
- [ ] Comprobación de una sola ruta de ida y su inversa: hoy nada relaciona una ruta con su contraria.

### 3.3 Infraestructura acordada y no hecha

- [ ] Pgpool-II, réplica en streaming y conmutación automática (el `.env` apunta al puerto 5432 directo, no al 9999 de Pgpool).
- [ ] Respaldos programados y prueba de restauración. Solo existe el respaldo manual hecho antes de limpiar la base (`zacabus_respaldo.sql`, en la carpeta temporal de la sesión).

---

## 4. Código Java con consultas directas a tablas

El resto de los DAO ya usa vistas, funciones o procedimientos. Estos son los que todavía consultan o modifican **tablas directamente** y deben pasar a un objeto de la base:

| Archivo | Método | Qué hace con tablas | Qué falta crear en la base |
|---|---|---|---|
| `dao/admin/UnidadDAO.java` | `acomodos()` | `SELECT` con `JOIN` sobre `viajes.tipo_bus`, `viajes.nombre_tipo_bus` y `viajes.layout`. | Vista `viajes.v_acomodos` (tipo de servicio, layout, capacidad). |
| `dao/admin/UnidadDAO.java` | `guardarAcomodo()` | `INSERT INTO viajes.tipo_bus ... WHERE NOT EXISTS` directo. | Procedimiento `viajes.sp_guardar_acomodo` que devuelva si era nuevo. |
| `dao/taquillero/VentaDAO.java` | `listar()` | `SELECT` de unas 10 tablas (`venta`, `boleto`, `taquilla`, `pasajero`, `personal_app`, `personal`, `viaje_programado`, `unidad`, `ruta_paradas`, `terminal`). | Vista `ventas.v_ventas_detalle`, con una fila por boleto (o función que devuelva los boletos de una venta). |
| `dao/taquillero/VentaDAO.java` | `registrar()` (segunda consulta) | `SELECT` sobre `ventas.boleto` y `administracion.taquilla` para armar los folios impresos. | Función `ventas.fn_boletos_de_venta(id_venta)` (o que el procedimiento los devuelva). |

Otros puntos de lógica de negocio que viven en Java y deberían estar en la base:

- [ ] `VentaDAO.turnoActual()`: calcula el turno por hora. Debe salir del turno abierto (ver 2.1).
- [ ] Validaciones repetidas en Java y en la base (formato de CURP y teléfono en `EmpleadoFormController`; ya existen como restricciones en la base). Se pueden dejar, pero la base es la que manda.

---

## 5. Orden sugerido de trabajo

1. Sección 1 completa (decisiones, base, DAO, controladores y pantallas del jefe de taller).
2. Sección 4 (consultas directas), porque es corta y prepara los permisos.
3. Sección 3.1 (roles y permisos en la base) y el control de acceso por rol en la navegación (2.4).
4. Turnos (2.1) y ciclo de vida del viaje (2.2).
5. Cambio de boleto, reportes y devoluciones (2.1, 2.3).
6. Consolidar scripts (3.2), pruebas y datos de ejemplo (2.5).
7. Infraestructura (3.3).
