# Plan de construcción — Prototipo de vistas Zaca

**Meta:** prototipo web navegable de las 12 pantallas del sistema de boletaje, con datos de ejemplo, como referencia de diseño antes de elegir React o Angular.

**Arquitectura:** HTML, CSS y JavaScript sin frameworks, con ES modules. Una página por pantalla y una hoja de estilos compartida con la paleta en variables CSS. Toda la lógica de negocio (tramos, precios, validaciones) vive en un módulo puro, `js/logica.js`, probado con `node --test`. El estado (boletos vendidos, viajes creados) se guarda en `localStorage` para que una venta hecha en taquilla aparezca en el manifiesto del despachador.

**Tecnología:** HTML5, CSS con variables, JS (ES2022, módulos). Node 24 solo para pruebas (`node --test`). CDN fijados: `html5-qrcode@2.3.8` (escaneo), `qrcode-generator@1.4.4` (QR del ticket), `chart.js@4.4.1` (reportes). Google Fonts: IBM Plex Sans e IBM Plex Mono.

**Diseño:** `docs/2026-10-24-diseno-vistas.md`. Quien ejecute debe leer el diseño y este plan.

## Restricciones globales

- Paleta exacta: barra `#252B33`, fondo `#F1F2F4`, tarjetas `#FFFFFF`, líneas `#DDE0E4`, texto `#1B1F24`, texto secundario `#5F6670`, acción `#D9580D`, verde `#137A44`, ámbar `#8A6100`, rojo `#B42318`. El naranja solo va en acciones, nunca como fondo grande. El rojo solo en errores y cancelaciones.
- Tipografía: IBM Plex Sans; IBM Plex Mono para horas, folios y montos.
- No usar nada de `vistas_inspiracion.html`.
- Cada rol solo ve sus pantallas. Una URL de otro rol redirige al inicio del rol propio.
- Taquilla y administración se diseñan para escritorio (mínimo 1366 px). Despacho, para tableta (mínimo 768 px) con botones de al menos 48 px de alto.
- Sin exportación, sin pago con tarjeta, sin vista para celular.
- Textos de la interfaz en español de México.
- Se sirve con `python -m http.server 8080` desde `prototipo/`, porque los ES modules no cargan con `file://`.
- **Sin commits.** La carpeta vive dentro del repo del Escritorio. La usuaria decide cuándo y cómo se versiona. Donde una tarea normal haría commit, aquí solo se verifica.

## Foco de revisión

Casos que el diseño implica y que un usuario encontraría primero. Cada uno tiene su prueba en la tarea indicada:

1. **Tramo semiabierto:** quien baja en Huixtla y quien sube en Huixtla pueden usar el mismo asiento. Esperado: el asiento aparece libre para el segundo. Prueba en Tarea 1.
2. **Asiento ganado por otra taquilla** entre la ventana 3 y "Confirmar". Esperado: la confirmación falla con un mensaje y regresa a Asientos sin cobrar. Prueba en Tarea 1 (`confirmarVenta`); en la UI, Tarea 3.
3. **Cancelar después de la hora de salida.** Esperado: no se permite. Prueba en Tarea 1 (`puedeCancelar`).
4. **QR escaneado dos veces, o de otro viaje u otra escala.** Esperado: ámbar con la hora del primer escaneo, o rojo con el motivo. Prueba en Tarea 1 (`validarQR`).
5. **Destino anterior al origen, origen igual a destino, o recibido menor que el total.** Esperado: no aparecen horarios, o el botón Confirmar queda desactivado. Pruebas en Tarea 1 (`buscarViajes`, `calcularCambio`).

---

## Estructura de archivos

```
prototipo/
  package.json              {"type":"module","scripts":{"test":"node --test"}}
  index.html                inicio de sesión
  css/estilos.css           variables de paleta + componentes (barra, ventana, botones, tabla, asientos, chips)
  js/datos.js               datos de ejemplo (semilla)
  js/estado.js              lee/escribe estado en localStorage, sembrado desde datos.js
  js/logica.js              reglas de negocio puras (sin DOM)
  js/sesion.js              login, guardia por rol, barra superior
  js/ui.js                  helpers de DOM: ventana modal, tabla con buscador, croquis de asientos, toast
  taquilla/venta.html       + js/vistas/venta.js
  taquilla/boletos.html     + js/vistas/boletos.js
  despacho/programar.html   + js/vistas/programar.js
  despacho/manifiesto.html  + js/vistas/manifiesto.js   (incluye "Marcar en ruta")
  despacho/abordaje.html    + js/vistas/abordaje.js
  admin/rutas.html          + js/vistas/rutas.js
  admin/unidades.html, taquillas.html, personal.html, cuentas.html  + js/vistas/catalogos.js
  admin/reportes.html       + js/vistas/reportes.js
  tests/logica.test.js
```

## Modelo de datos (forma de `datos.js` y del estado)

Los nombres siguen al script SQL para facilitar la conexión futura.

```js
terminales:  [{ id, nombre }]                                  // Tapachula, Huixtla, Pijijiapan, Tonalá, Tuxtla Gutiérrez
rutas:       [{ id, nombre, servicio, paradas: [{ idTerminal, orden, km, min }],
                precios: [{ ordenOrigen, ordenDestino, precio }] }]   // precio por tramo, en pesos
tarifas:     [{ id, nombre, descuento }]                       // ADULTO 0, ESTUDIANTE 0.5, INAPAM 0.5, INFANTIL 0.5
unidades:    [{ id, placa, modelo, anio, km, tipo, layout:'2+2'|'2+1'|'1+1', capacidad, enMantenimiento }]
personal:    [{ id, nombre, apPaterno, apMaterno, curp, telefono, puesto, licencia, vigencia }]  // vigencia 'YYYY-MM-DD'
taquillas:   [{ id, idTerminal, identificador, serie, rangoInicial, rangoFinal, foliosUsados, activo }]
cuentas:     [{ id, idPersonal, rol:'ADMINISTRADOR'|'DESPACHADOR'|'TAQUILLERO', idTaquilla, idTerminal, username, password }]
viajes:      [{ id, idRuta, fechaSalida:'YYYY-MM-DDTHH:mm', idUnidad, idConductor, estado:'PROGRAMADO'|'ABORDANDO'|'EN RUTA'|'CERRADO' }]
ventas:      [{ id, folio, idTaquilla, idViaje, titular:{ nombre, identificacion }, ordenOrigen, ordenDestino,
                asientos:[{ num, idTarifa, precio, abordado:false }], total, recibido, estado:'VIGENTE'|'CANCELADA',
                fecha, reemplazaA:null, escaneadoEn:null }]
```

La semilla debe incluir, para hoy: una ruta Tapachula → Tuxtla con las 5 paradas; 3 viajes (08:00, 09:30, 11:00); ventas que ocupen asientos en tramos distintos; una unidad en mantenimiento y un chofer con licencia vencida; una cuenta por rol (`admin`, `despacho.tapachula`, `taquilla.tapachula`, contraseña `zaca2026`).

---

### Tarea 1: Lógica de negocio con pruebas

**Archivos:**
- Crear: `prototipo/package.json`, `prototipo/js/logica.js`
- Prueba: `prototipo/tests/logica.test.js`

**Interfaces (produce):**
- `precioTramo(ruta, ordenOrigen, ordenDestino) -> number | null`: `null` si no hay precio o si `ordenOrigen >= ordenDestino`.
- `buscarViajes(estado, idTerminalOrigen, idTerminalDestino, fecha:'YYYY-MM-DD') -> [{ viaje, ruta, ordenOrigen, ordenDestino, precio, libres }]`: solo rutas donde el origen va antes que el destino, viajes de esa fecha en estado PROGRAMADO o ABORDANDO, ordenados por hora.
- `asientosOcupados(estado, idViaje, ordenOrigen, ordenDestino) -> Set<number>`: tramos semiabiertos `[o, d)`. Dos tramos chocan si `o1 < d2 && o2 < d1`. Ignora ventas CANCELADA.
- `calcularTotal(asientos:[{ precioBase, descuento }]) -> number`: redondeado a 2 decimales.
- `calcularCambio(total, recibido) -> number | null`: `null` si `recibido < total`.
- `confirmarVenta(estado, borrador, ahora:Date) -> venta`: vuelve a revisar los asientos, asigna el siguiente folio de la taquilla (`serie + '-' + 6 dígitos`) e incrementa `foliosUsados`. Lanza `Error('ASIENTO_OCUPADO')` si algún asiento ya está tomado y `Error('SIN_FOLIOS')` si se acabó el rango. Muta `estado` y devuelve la venta.
- `puedeCancelar(estado, venta, ahora:Date) -> boolean`: solo si está VIGENTE y `ahora < fechaSalida` del viaje.
- `cancelarVenta(estado, idVenta, ahora) -> venta`: lanza `Error('NO_CANCELABLE')` si no se puede.
- `validarQR(estado, folio, idViaje, idTerminal, ahora) -> { resultado:'OK'|'YA_ESCANEADO'|'INVALIDO', motivo?, venta?, hora? }`: INVALIDO si no existe, está cancelada, es de otro viaje o la terminal no es la de subida. YA_ESCANEADO si `escaneadoEn` ya existe. OK marca todos los asientos `abordado=true` y fija `escaneadoEn`.
- `validarAsignacion(estado, idUnidad, idConductor, fechaSalida) -> string[]`: lista de motivos de bloqueo; vacía = válido. Motivos: `'Unidad en mantenimiento'`, `'El empleado no es CONDUCTOR'`, `'Licencia vencida'` (si `vigencia < fecha de salida`).
- `licenciaPorVencer(persona, hoy, dias=30) -> boolean`.

- [ ] **Paso 1: escribir las pruebas que fallan** en `tests/logica.test.js`, con `node:test` y `node:assert/strict`, y un estado de prueba mínimo construido en el propio archivo (ruta de 5 paradas, capacidad 8). Casos:
  - `precioTramo` devuelve el precio configurado para (0,4) y `null` para (3,1) y (2,2).
  - `asientosOcupados`: una venta del asiento 5 en (0,1) **no** ocupa el 5 para (1,4); una venta del 5 en (0,2) **sí** lo ocupa para (1,4); una venta CANCELADA no ocupa nada.
  - `buscarViajes` devuelve `[]` si el destino va antes que el origen; si no, devuelve los viajes ordenados por hora con `libres = capacidad - ocupados`.
  - `calcularTotal([{precioBase:350,descuento:0},{precioBase:350,descuento:0.5},{precioBase:350,descuento:0.5}])` da `700`.
  - `calcularCambio(700, 1000)` da `300`; `calcularCambio(700, 500)` da `null`.
  - `confirmarVenta` asigna el folio `A-000001` y luego `A-000002`; lanza `ASIENTO_OCUPADO` si otro borrador ya tomó el asiento en un tramo que choca; lanza `SIN_FOLIOS` cuando `foliosUsados` llega a `rangoFinal - rangoInicial + 1`.
  - `puedeCancelar` es `true` un minuto antes de la salida y `false` a la hora exacta y después.
  - `validarQR`: primera lectura OK y marca 3 asientos abordados; segunda lectura YA_ESCANEADO con `hora`; folio de otro viaje INVALIDO; venta cancelada INVALIDO.
  - `validarAsignacion` devuelve los tres motivos para unidad en mantenimiento + mecánico + licencia vencida, y `[]` para un caso válido.
- [ ] **Paso 2: correr y confirmar que falla.** `cd prototipo && npm test`. Esperado: FAIL, módulo `../js/logica.js` no encontrado.
- [ ] **Paso 3: implementar `js/logica.js`** con las firmas de arriba. Funciones puras salvo `confirmarVenta`, `cancelarVenta` y `validarQR`, que mutan el `estado` recibido.
- [ ] **Paso 4: correr y confirmar que pasa.** `npm test`. Esperado: todas las pruebas en verde, 0 fallas.

### Tarea 2: Base visual, datos, estado y sesión

**Archivos:**
- Crear: `css/estilos.css`, `js/datos.js`, `js/estado.js`, `js/sesion.js`, `js/ui.js`, `index.html`
- Prueba: agregar a `tests/logica.test.js` una prueba de integridad de la semilla.

**Interfaces (produce):**
- `datos.js`: `export const semilla` con la forma del modelo de datos.
- `estado.js`: `cargar() -> estado` (siembra `localStorage['zaca']` la primera vez), `guardar(estado)`, `reiniciar()`.
- `sesion.js`: `iniciarSesion(username, password) -> cuenta | null`, `exigirRol(...roles)` (redirige si no corresponde y devuelve la cuenta), `pintarBarra(cuenta, titulo)` (barra con ZACA, título, usuario, terminal o taquilla, "Nodo Tapachula en línea" y "Salir").
- `ui.js`: `abrirVentana({ titulo, paso, total, contenido, botones }) -> cerrar()`, `tabla({ columnas, filas, buscar, acciones })`, `croquis({ layout, capacidad, ocupados:Set, elegidos:Set, alElegir })`, `toast(texto, tipo:'ok'|'aviso'|'error')`.
- Página de inicio por rol: ADMINISTRADOR → `admin/rutas.html`, DESPACHADOR → `despacho/programar.html`, TAQUILLERO → `taquilla/venta.html`.

- [ ] **Paso 1: prueba de semilla:** cada `viaje.idRuta`, `idUnidad` e `idConductor` existe; cada venta usa asientos `<= capacidad`; los precios de cada ruta cubren todos los pares `o < d`. Correr `npm test` y ver que falla.
- [ ] **Paso 2: escribir `datos.js`** con la semilla descrita arriba hasta que la prueba pase.
- [ ] **Paso 3: escribir `estilos.css`** con las variables de la paleta en `:root` y los componentes: barra superior, ventana (barra de título, contador "n / 6", cuerpo, pie con Atrás y Siguiente), botón primario naranja, botón secundario, tabla, asiento (libre: blanco con borde; ocupado: gris; elegido: naranja), chips verde/ámbar/rojo. Las ventanas aparecen sobre un fondo atenuado.
- [ ] **Paso 4: escribir `estado.js`, `sesion.js`, `ui.js` e `index.html`** (login centrado, con un error en rojo si la contraseña es incorrecta).
- [ ] **Paso 5: verificar en el navegador.** `python -m http.server 8080` y abrir `http://localhost:8080`. Entrar con cada una de las 3 cuentas y comprobar que cada una cae en su página de inicio. Con sesión de taquillero, abrir `/admin/rutas.html` y comprobar que regresa a la venta.

### Tarea 3: Venta en 6 ventanas

**Archivos:** crear `taquilla/venta.html` y `js/vistas/venta.js`.

**Interfaces:** consume `buscarViajes`, `asientosOcupados`, `precioTramo`, `calcularTotal`, `calcularCambio` y `confirmarVenta` (Tarea 1); `abrirVentana`, `croquis` y `toast` (Tarea 2). Produce `iniciarVenta(borradorInicial?)`, que la Tarea 4 usa para "Cambiar boleto".

- [ ] **Paso 1: implementar las ventanas** según el diseño: 1 Destino (origen ya puesto con la terminal de la taquilla), 2 Horario, 3 Asientos (selección múltiple), 4 Titular y tarifas (un selector de tarifa por asiento), 5 Cobro ("Confirmar e imprimir" desactivado mientras `calcularCambio` sea `null`), 6 Venta confirmada (ticket con folio, ruta, fecha y hora, unidad, titular, asientos, total y QR generado con `qrcode-generator` a partir del folio; botones Reimprimir y Nueva venta). "Reimprimir" llama a `window.print()` con un `@media print` que imprime solo el ticket.
- [ ] **Paso 2: manejar `ASIENTO_OCUPADO`:** mostrar un toast de error, regresar a la ventana 3 con el croquis actualizado y quitar el asiento perdido de los elegidos.
- [ ] **Paso 3: verificar en el navegador.** Vender 3 asientos (Adulto, INAPAM, Infantil) de Tapachula a Tuxtla a las 08:00. Esperado: total $700 con un precio base de $350, cambio $300 al recibir $1,000, y ticket con un solo QR. Vender otra vez y comprobar que esos asientos salen ocupados. Vender de Huixtla a Tuxtla y comprobar que un asiento vendido solo de Tapachula a Huixtla sale libre.

### Tarea 4: Boletos vendidos

**Archivos:** crear `taquilla/boletos.html` y `js/vistas/boletos.js`.

**Interfaces:** consume `puedeCancelar` y `cancelarVenta` (Tarea 1), `tabla` (Tarea 2) e `iniciarVenta` (Tarea 3).

- [ ] **Paso 1: implementar la tabla** con folio, viaje, titular, asientos, total y estado, y un buscador por folio o nombre del titular que busque en todas las taquillas. Acciones "Cancelar" y "Cambiar boleto", desactivadas cuando `puedeCancelar` es `false`. Cancelar pide confirmación en una ventana propia (no `confirm()`) que muestra el monto a devolver.
- [ ] **Paso 2: "Cambiar boleto"** cancela la venta y llama a `iniciarVenta` con el viaje, el tramo, el titular y los asientos previos ya cargados, empezando en la ventana 3. La venta nueva guarda `reemplazaA` con el folio anterior, y el ticket muestra "Reemplaza al folio X".
- [ ] **Paso 3: verificar en el navegador:** cancelar libera los asientos en la venta; un viaje con hora pasada tiene los botones desactivados; cambiar un boleto de 4 asientos a 3 produce un folio nuevo con la leyenda de reemplazo.

### Tarea 5: Programar salida (despachador)

**Archivos:** crear `despacho/programar.html` y `js/vistas/programar.js`.

**Interfaces:** consume `validarAsignacion` (Tarea 1).

- [ ] **Paso 1: implementar el formulario** (ruta, fecha, hora, unidad, chofer) y, debajo, la lista de salidas del día de la terminal del despachador. Al cambiar la unidad o el chofer se muestran en ámbar los motivos de bloqueo y "Guardar" queda desactivado mientras haya motivos. Al guardar se crea el viaje en estado PROGRAMADO.
- [ ] **Paso 2: verificar en tableta.** En las herramientas del navegador, con vista de 768 × 1024: elegir la unidad en mantenimiento muestra el motivo y bloquea Guardar; con unidad y chofer válidos, el viaje nuevo aparece en el paso 2 de la venta de taquilla.

### Tarea 6: Manifiesto y salida

**Archivos:** crear `despacho/manifiesto.html` y `js/vistas/manifiesto.js`.

- [ ] **Paso 1: implementar el selector de viaje** (los del día que pasan por la terminal del despachador) y la lista de quienes **suben en esta escala**: asiento, titular, tramo, tarifa y una marca de abordado. Arriba, un contador "abordaron X de Y". Pasada la hora de salida, quien no subió se marca "No se presentó" en gris (el asiento sigue ocupado).
- [ ] **Paso 2: botón "Marcar en ruta"**, activo solo desde la hora de salida. Cambia el estado del viaje a EN RUTA.
- [ ] **Paso 3: verificar en tableta** que la lista solo incluye a quienes suben en esta terminal y que el botón está desactivado antes de la hora.

### Tarea 7: Abordaje con QR

**Archivos:** crear `despacho/abordaje.html` y `js/vistas/abordaje.js`.

**Interfaces:** consume `validarQR` (Tarea 1).

- [ ] **Paso 1: implementar el lector** con `html5-qrcode@2.3.8` sobre la cámara trasera, más un campo "Teclear folio" como respaldo. El resultado ocupa media pantalla con fondo de color:
  - Verde: "Puede abordar · N pasajeros · asientos …".
  - Ámbar: "QR ya escaneado a las HH:mm".
  - Rojo: "No válido: motivo".
  Después de 3 segundos regresa al lector.
- [ ] **Paso 2: verificar en el navegador** con el campo de respaldo, tecleando el folio de la venta de la Tarea 3: la primera vez sale verde y el manifiesto la marca abordada; la segunda sale ámbar; un folio cancelado sale rojo.

### Tarea 8: Catálogos del administrador

**Archivos:** crear `admin/unidades.html`, `admin/taquillas.html`, `admin/personal.html`, `admin/cuentas.html` y `js/vistas/catalogos.js`.

**Interfaces:** consume `tabla` y `abrirVentana` (Tarea 2) y `licenciaPorVencer` (Tarea 1). Produce `catalogo({ coleccion, columnas, campos })`, que monta una tabla con buscador y "Nuevo/Editar" en ventana.

- [ ] **Paso 1: implementar `catalogo`** y las 4 páginas con sus campos:
  - Unidades: placa, modelo, año, km, tipo, layout, capacidad.
  - Taquillas: terminal, identificador, serie, rango inicial, rango final; muestra "folios usados" de solo lectura.
  - Personal: nombre, apellidos, CURP, teléfono, puesto, licencia, vigencia. Chip ámbar "Licencia por vencer" o rojo "Vencida".
  - Cuentas: empleado, rol, taquilla (solo si es TAQUILLERO), usuario, contraseña.
  Validar en el formulario lo mismo que el script SQL: CURP con `^[A-Z]{4}[0-9]{6}[HMX][A-Z]{5}[A-Z0-9][0-9]$`, teléfono de 10 dígitos, rango inicial ≤ rango final, capacidad > 0.
- [ ] **Paso 2: verificar en el navegador:** una CURP inválida muestra el error junto al campo y no guarda; el chofer de la semilla con licencia vencida muestra el chip rojo.

### Tarea 9: Rutas

**Archivos:** crear `admin/rutas.html` y `js/vistas/rutas.js`.

- [ ] **Paso 1: implementar la lista de rutas y el editor** en ventana: nombre, servicio, paradas en orden (agregar, quitar, subir y bajar; km y min por parada) y una **tabla de precios por tramo** con todos los pares origen-destino donde el origen va antes. No guarda si falta algún precio, si los km no van en aumento o si una terminal se repite.
- [ ] **Paso 2: verificar en el navegador:** una ruta nueva de 3 paradas pide exactamente 3 precios (A→B, A→C, B→C), y una vez guardada aparece en "Programar salida".

### Tarea 10: Reportes

**Archivos:** crear `admin/reportes.html` y `js/vistas/reportes.js`.

- [ ] **Paso 1: implementar** un selector Día/Semana, tres totales arriba (boletos vendidos, ingresos, ocupación promedio %) y dos gráficas con `chart.js@4.4.1`: ingresos por día y ocupación por viaje. Todo se calcula desde `estado.ventas` y `estado.viajes`, sin contar las ventas canceladas. Sin botón de exportar.
- [ ] **Paso 2: verificar en el navegador** que, después de vender en la Tarea 3, los ingresos del día suben en el monto vendido, y que bajan al cancelar.

### Tarea 11: Revisión final

- [ ] **Paso 1:** `npm test` en verde.
- [ ] **Paso 2:** recorrer el día completo en el navegador. El administrador crea una ruta; el despachador programa una salida; el taquillero vende un boleto familiar y otro de tramo intermedio; el despachador escanea, revisa el manifiesto y marca en ruta; el administrador ve el reporte.
- [ ] **Paso 3:** revisar que el naranja solo aparece en acciones, el rojo solo en errores y cancelaciones, y que la consola del navegador no muestra errores en ninguna página.
