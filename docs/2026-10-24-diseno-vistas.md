# Diseño de vistas — Sistema de boletaje Zaca

Fecha: 24 de octubre de 2026 · Estado: diseño de vistas definitivo (sujeto a ajustes)

## Intención

Mientras el encargado de la base de datos corrige el script `TERMINAL ZACARIASS.txt`, se avanza con el diseño de las pantallas del sistema. El objetivo es tener un prototipo navegable de todas las vistas, con datos de ejemplo, que el equipo pueda ver, comentar y ajustar. Más adelante se conecta a la base real.

Es un diseño vivo: se espera que cambie conforme avance el proyecto.

## Metas

- Cubrir todas las pantallas de los tres roles (taquillero, despachador, administrador) y el inicio de sesión.
- Que la venta en taquilla sea rápida y clara mientras el taquillero habla con el cliente.
- Que el despachador pueda trabajar de pie en el andén con una tableta.
- Que el aspecto transmita eficiencia, confianza y rapidez operativa.

## No metas (por ahora)

- **Cuentas de usuario y Reportes del administrador quedan fuera del alcance actual** (decisión del 24 oct 2026). El prototipo los conserva solo como referencia.

- Exportar reportes o contabilidad (Excel, CSV, PDF). Queda pendiente para después.
- Conexión real a PostgreSQL. El prototipo usa datos de ejemplo.
- Pago con tarjeta. Solo efectivo.
- Vista para celular. Las pantallas son para computadora de escritorio (taquilla y administración) y tableta (despachador).

## Anti-metas (lo que haría fallar el diseño aunque funcione)

- Tonos infantiles, paletas "relajantes de spa" o uso excesivo del rojo.
- Parecerse al prototipo anterior `vistas_inspiracion.html`; no se usa como referencia.
- Que un rol vea o pueda usar funciones de otro (un despachador cobrando, un taquillero en logística).

## Restricciones

- Las vistas son páginas web.
- La sobreventa por tramo la controla la base (`int4range` + `EXCLUDE`). La pantalla solo muestra lo que la base dice que está libre en el tramo del pasajero.
- Ver la sección "Pendientes para la base de datos": varias pantallas necesitan columnas o tablas que el script todavía no tiene.

## Roles y pantallas

### Todos

**Inicio de sesión.** Usuario y contraseña. Al entrar, cada persona va directo a las pantallas de su rol y no ve las de los demás. En la barra superior se ve el nombre del usuario, su taquilla o terminal y el estado del nodo (por ejemplo "Nodo Tapachula en línea").

### Taquillero (computadora de escritorio)

**1. Venta de boletos — todo en una pantalla (definitivo, 24 oct 2026).** Cuatro paneles de tamaño fijo en cuadrícula 2 × 2, sin numeración:

- **Destino** (arriba izquierda): origen (la terminal del taquillero, editable), destino y fecha (hoy por defecto).
- **Horario** (arriba derecha, misma altura que Destino): las 4 próximas salidas en dos columnas con hora, tipo, lugares libres en el tramo y precio. Si hay más, un botón "Ver los N horarios del día" abre la lista completa en una ventana; el horario elegido siempre queda a la vista en el panel.
- **Asientos** (abajo izquierda): croquis del autobús visto desde arriba según su acomodo (2+2, 2+1, 1+1), con la leyenda en columna a la izquierda. Colores: **celeste** libre, **gris** ocupado en el tramo del pasajero, **verde** elegido. El asiento del chofer aparece siempre ocupado, alineado con la primera columna. Se pueden elegir varios asientos (familias). El botón "Mostrar al cliente" abre el autobús en pantalla completa, con asientos grandes, para que el cliente elija; "Listo" o Esc regresan con los asientos ya marcados.
- **Titular, tarifas y cobro** (abajo derecha): nombre completo y CURP o identificación del titular, tarifa por asiento (Adulto, Estudiante, INAPAM, Infantil, con descuento automático), total, efectivo recibido y cambio. "Confirmar e imprimir" se activa solo con titular completo y efectivo suficiente. Al confirmar, el ticket aparece en una ventana con "Reimprimir" y "Nueva venta".

Los paneles no cambian de tamaño: lo que no cabe (lista de tarifas de muchos asientos) se desplaza dentro de su panel. La versión anterior en 6 ventanas se descartó.

**El ticket impreso** lleva: folio, ruta, fecha y hora, unidad, nombre del titular, lista de asientos ("6, 7, 8"), total y **un solo código QR** que ampara a todos los asientos del boleto.

**2. Boletos vendidos.** Lista de boletos de la taquilla, con buscador. Acciones:

- **Cancelar:** devuelve el dinero y libera los asientos. Solo antes de la hora de salida del viaje; después el botón se desactiva.
- **Cambiar boleto:** no existe cancelación parcial. Para quitar o cambiar asientos se cancela el boleto completo y se emite uno nuevo. En pantalla es un solo botón que cancela el anterior y abre la venta (en modo "Cambiar boleto") con el mismo viaje, titular, asientos y tarifas ya cargados. El nuevo ticket indica "reemplaza al folio X".

Solo la taquilla puede liberar un asiento. Nadie más.

### Despachador / checador (tableta)

Vista definitiva desde el 24 oct 2026, sujeta a cambios posteriores. Botones grandes, pensados para usarse de pie y con una mano.

**3. Programar salida.** Aquí nacen los viajes del día: elige ruta y fecha, teclea la hora de salida y asigna unidad y chofer en el mismo paso. Al guardar, el viaje aparece automáticamente en la venta de las taquillas.
- Si la unidad tiene reporte de mantenimiento (consulta a `taller_db`), el sistema bloquea la asignación y explica el motivo.
- Si la licencia del chofer está vencida o el puesto no es CONDUCTOR, también se bloquea.

- **Salidas de hoy que pasan por la terminal:** tabla con hora, ruta, unidad, chofer y estado, con barra de búsqueda (hora, ruta, placa, chofer o estado).
- **Ver detalle** (solo consulta, ventana flotante): hora, ruta, servicio y estado; recorrido con hora estimada por parada y cuántos suben y bajan; ocupación por tramo en barras; unidad y chofer con vigencia de licencia; botón "Ver manifiesto" que abre esa salida. No muestra lo vendido ni permite editar o cancelar la salida.

**4. Manifiesto.** Lista de pasajeros que deben **subir en esta escala** (no el viaje completo). Arriba: hora de salida de la escala, unidad, "Abordaron X de Y pasajeros" (sin barra de progreso) y el botón "Marcar en ruta". Tabla con buscador y columnas: Asiento, Titular, Destino, Tarifa, Folio y Estado (Abordó, Pendiente o No se presentó).

**5. Abordaje con QR.** El despachador escanea el QR del boleto con la cámara de la tableta. La respuesta es grande y de color, visible de un vistazo:
- **Verde — puede abordar**, con número de pasajeros y asientos (un boleto familiar marca todos sus asientos como abordados con un solo escaneo).
- **Ámbar — QR ya escaneado**, con la hora en que se escaneó.
- **Rojo — no válido**: boleto cancelado o de otro viaje.

**6. Salida del viaje.** Cuando llega la hora oficial, el despachador cambia el estado del viaje a "En ruta". El autobús no espera: quien no se presentó aparece como "no se presentó" en el manifiesto, pero su boleto sigue vigente y su asiento sigue bloqueado durante todo el viaje (puede alcanzar el autobús en otra escala). El despachador no puede cancelar ni revender.

### Administrador (computadora de escritorio)

Arma el "molde" del negocio. Las pantallas de alta son tablas con buscador y un botón "Nuevo" que abre su propia ventana, igual que la venta.

**7. Rutas.** Dibuja la ruta parada por parada (orden, kilómetros y minutos desde el origen) y define el **precio de cada tramo** (Tapachula→Huixtla tiene un precio y Tapachula→Tuxtla otro).

**8. Unidades.** Alta de autobuses: placa, modelo, año, kilometraje, tipo de servicio y acomodo de asientos.

**9. Taquillas.** Alta de ventanillas por terminal, con su serie de folios y rango, para que ningún ticket se repita.

**10. Personal.** Alta de empleados con puesto. A los choferes se les registra licencia y fecha de vencimiento; la pantalla avisa cuando una licencia está por vencer.

**11. Cuentas de usuario (identidades nominales).** Una cuenta personal por empleado, nunca genérica. Tabla: usuario único (`m.constantino`: inicial + apellido paterno, sin acentos, con número si se repite), nombre completo, rol (Taquillero, Despachador, Administrador), sede base y estado (Activo/Inactivo). "+ Nueva cuenta" pide la persona (solo personal sin cuenta), el rol (sugerido por su puesto), la sede asignada y una contraseña temporal; el usuario se genera solo. Editar cambia rol, sede o contraseña. Activar/Desactivar: una cuenta inactiva no puede iniciar sesión; el administrador no puede desactivarse a sí mismo.

**Inicio de sesión y turno.** Al entrar se valida usuario, contraseña, estado y rol. El taquillero pasa primero por **Abrir turno**: elige una ventanilla activa de su sede que no esté ocupada (las ocupadas muestran quién la tiene desde qué hora). Sin turno no puede vender ni cancelar. "Cerrar turno y salir" libera la ventanilla. El despachador trabaja en su sede sin turno.

**Auditoría.** Cada venta, cancelación, abordaje, programación de salida, salida "En ruta" y apertura o cierre de turno queda en una bitácora con fecha, usuario y nombre completo de la persona. El boleto guarda quién lo vendió (columna "Vendió" en Boletos vendidos y "Atendió" en el ticket) y quién lo canceló.

**12. Reportes.** Gráficas de ocupación y de ingresos por día y por semana, en pantalla. Sin exportar por ahora.

## Estilo visual

- **Paleta "pizarra y naranja de señalización":**
  - Barra superior: gris pizarra `#252B33`.
  - Fondo: `#F1F2F4`; tarjetas y ventanas: blanco; líneas: `#DDE0E4`.
  - Texto: `#1B1F24`; texto secundario: `#5F6670`.
  - Acción principal (botones, horario elegido, opción activa del menú): naranja `#D9580D`.
  - Asientos: libre celeste `#E3F3FD` (borde `#6FB7E3`), ocupado gris `#D3D7DC`, elegido verde `#137A44`. El naranja va **solo en acciones**, nunca como fondo grande.
  - Estados: verde `#137A44` (correcto, puede abordar), ámbar `#8A6100` (aviso), rojo `#B42318` (solo errores y cancelaciones).
- **Tipografía:** IBM Plex Sans para todo; IBM Plex Mono para horas, folios y montos. *(Decisión del diseñador; ajustable.)*
- Navegación en barra lateral izquierda gris pizarra con íconos; en tableta se reduce a un riel de íconos de 72 px.
- Inicio de sesión: tarjeta blanca centrada que flota sobre fondo pizarra.
- Ventanas emergentes con barra de título, esquinas ligeramente redondeadas y sombra suave.

## Cómo se construye

- Páginas web. Arquitectura del sistema final: **frontend en React o Angular** (decisión pendiente del equipo), **backend en Java**, **PostgreSQL con Pgpool-II y postgres_fdw**. No se usa JSP ni Servlets.
- *(Decisión del diseñador:)* mientras se elige el framework, se hace un **prototipo estático** en HTML, CSS y JavaScript sin frameworks: una página por pantalla, una hoja de estilos compartida con la paleta como variables CSS y datos de ejemplo en un archivo JS. Es referencia de diseño, no código de producción. Las variables CSS y el marcado de cada ventana se pasan casi tal cual a componentes de React o Angular, sea cual sea el elegido.
- *(Decisión del diseñador:)* el escaneo QR del prototipo usa la cámara del navegador mediante una librería ligera por CDN, con un campo de texto como respaldo para teclear el folio si la cámara no está disponible.

## Otras decisiones del diseñador (ajustables)

- **Boletos vendidos** busca cualquier boleto por folio o nombre del titular, sin limitarse a la taquilla que lo vendió, para que el pasajero pueda cancelar en otra ventanilla.
- **Cancelar** devuelve el total pagado.
- El **despachador** trabaja en una terminal fija (la de su cuenta). El manifiesto y el abordaje muestran solo lo de esa escala.
- Descuentos de ejemplo en el prototipo: Estudiante, INAPAM e Infantil al 50 %. Los reales los define la base.

## Lo que queda a criterio de quien construye

- Estructura de carpetas y nombres de archivos del prototipo.
- Datos de ejemplo (rutas, horarios, nombres) siempre que sean realistas para Chiapas.
- Detalle de las gráficas de reportes.
- Cómo se navega entre pantallas dentro de cada rol (menú superior o lateral), respetando que cada rol solo ve las suyas.

## Pendientes para la base de datos

- **Cuentas nominales:** `personal_app` necesita `activo BOOLEAN NOT NULL DEFAULT TRUE`; la sede base (`id_terminal`) en lugar de `id_taquilla`, que pasa al turno.
- **Turno:** tabla nueva `administracion.turno(id_turno, id_app, id_taquilla, inicio, fin)` con una restricción que impida dos turnos abiertos en la misma taquilla (índice único parcial `WHERE fin IS NULL`).
- **Bitácora:** tabla nueva de auditoría (fecha, id_app, acción, detalle) o un trigger por tabla; `boleto` debe guardar quién canceló además de `id_vendedor`.
- **Puesto ADMINISTRADOR** en el catálogo `administracion.puesto`.
- **Nueva ciudad:** el `INSERT` en `administracion.terminal` necesita un `id_conexion` por defecto.

Estas pantallas piden cosas que el script actual no tiene (ver también `Revision_Script_Terminal.pdf`):

- **Boleto familiar:** hoy cada renglón de `boleto` es un asiento con su propio pasajero. Falta una tabla de venta o grupo que una varios asientos bajo un titular, un folio y un QR.
- **Precio por tramo:** no existe en `ruta` ni en `ruta_paradas`.
- **Contraseña** en `personal_app`.
- **Folio** en `boleto`.
- **Descuento** en `tarifa`.
- **Código QR:** valor único por boleto para escanear y validar.
- Estado "no se presentó" en el manifiesto: puede calcularse (vigente y no abordado al salir) sin columna nueva.
