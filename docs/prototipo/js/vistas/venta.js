import { exigirRol, exigirTurno, pintarBarra } from '../sesion.js';
import { cargar, guardar } from '../estado.js';
import { buscarViajes, asientosOcupados, calcularTotal, calcularCambio, confirmarVenta, cancelarVenta, porId, fechaLocal } from '../logica.js';
import { h, esc, boton, croquis, toast, dinero, hora, fechaCorta, abrirVentana } from '../ui.js';
import { ticket } from '../ticket.js';

const cuenta = await exigirRol('TAQUILLERO');
const turno = await exigirTurno(cuenta);
pintarBarra(cuenta);

const app = document.getElementById('app');
app.classList.add('ancho');
let estado = cargar();
let b;

const tarifa = id => porId(estado.tarifas, id);
const unidadDe = viaje => porId(estado.unidades, viaje.idUnidad);
const lineas = () => [...b.elegidos].sort((x, y) => x - y).map(num => {
  const t = tarifa(b.tarifas.get(num) ?? 1);
  return { num, tarifa: t, precioBase: b.opcion.precio, descuento: t.descuento, precio: b.opcion.precio * (1 - t.descuento) };
});
const total = () => b.opcion && b.elegidos.size ? calcularTotal(lineas()) : 0;

const pantalla = h(`<div class="venta-una-marco">
  <div class="encabezado"><div><h1 id="titulo-venta">Vender boleto</h1><p id="sub-venta">Todo a la vista mientras atiende al cliente.</p></div></div>
  <div class="venta-una">
    <section class="panel arriba" id="p-viaje"></section>
    <section class="panel arriba" id="p-horarios"></section>
    <section class="panel abajo" id="p-asientos"></section>
    <section class="panel abajo" id="p-cobro"></section>
  </div>
</div>`);
app.append(pantalla);
const $ = id => pantalla.querySelector('#' + id);
const titulo = (n, t, extra = '') => `<div class="panel-titulo"><h2>${t}</h2>${extra}</div>`;

function reiniciar(inicial = {}) {
  b = { idOrigen: cuenta.idTerminal, idDestino: null, fecha: fechaLocal(new Date()), opcion: null,
    elegidos: new Set(), tarifas: new Map(), titular: { nombre: '', identificacion: '' }, recibido: '', reemplazaA: null, ...inicial };
  $('titulo-venta').textContent = b.reemplazaA ? 'Cambiar boleto' : 'Vender boleto';
  $('sub-venta').innerHTML = b.reemplazaA ? `Reemplaza al folio <span class="mono">${esc(b.reemplazaA)}</span>. Ajuste los asientos y cobre el boleto nuevo.` : 'Todo a la vista mientras atiende al cliente.';
  pintarViaje(); pintarAsientos(); pintarCobro();
}

// "Cambiar boleto" llega desde Boletos vendidos: cancela el anterior y carga su viaje, titular y asientos.
function desdeCambio() {
  const id = Number(sessionStorage.getItem('zaca-cambio'));
  if (!id) return null;
  sessionStorage.removeItem('zaca-cambio');
  const anterior = porId(estado.ventas, id);
  if (!anterior) { toast('No se encontró el boleto a cambiar.', 'error'); return null; }
  try {
    cancelarVenta(estado, anterior.id, new Date(), cuenta.id);
  } catch {
    toast('Ese boleto ya no se puede cambiar.', 'error');
    return null;
  }
  guardar(estado);
  const viaje = porId(estado.viajes, anterior.idViaje);
  const ruta = porId(estado.rutas, viaje.idRuta);
  const idOrigen = ruta.paradas[anterior.ordenOrigen].idTerminal;
  const idDestino = ruta.paradas[anterior.ordenDestino].idTerminal;
  const fecha = viaje.fechaSalida.slice(0, 10);
  toast(`Folio ${anterior.folio} cancelado. Devuelva ${dinero(anterior.total)} y cobre el boleto nuevo.`, 'aviso');
  return { idOrigen, idDestino, fecha, titular: { ...anterior.titular }, reemplazaA: anterior.folio,
    opcion: buscarViajes(estado, idOrigen, idDestino, fecha).find(o => o.viaje.id === viaje.id) ?? null,
    elegidos: new Set(anterior.asientos.map(a => a.num)), tarifas: new Map(anterior.asientos.map(a => [a.num, a.idTarifa])) };
}

function pintarViaje() {
  const opciones = sel => estado.terminales.map(t => `<option value="${t.id}" ${t.id === sel ? 'selected' : ''}>${esc(t.nombre)}</option>`).join('');
  const p = $('p-viaje');
  p.innerHTML = `${titulo(1, 'Destino')}
    <div class="rejilla">
      <div class="rejilla dos">
      <div class="campo"><label for="origen">Origen</label><select id="origen" class="entrada">${opciones(b.idOrigen)}</select></div>
      <div class="campo"><label for="destino">Destino</label><select id="destino" class="entrada"><option value="">Elija el destino…</option>${opciones(b.idDestino)}</select></div>
      </div>
      <div class="campo"><label for="fecha">Fecha</label><input id="fecha" type="date" class="entrada" value="${b.fecha}" min="${fechaLocal(new Date())}"></div>
    </div>`;
  const form = p.querySelector('.rejilla');
  form.oninput = form.onchange = () => {
    b.idOrigen = Number(p.querySelector('#origen').value);
    b.idDestino = Number(p.querySelector('#destino').value) || null;
    b.fecha = p.querySelector('#fecha').value;
    b.opcion = null; b.elegidos.clear();
    pintarHorarios(); pintarAsientos(); pintarCobro();
  };
  pintarHorarios();
  p.querySelector('#destino').focus();
}

const VISIBLES = 4;

function tarjetaHorario(o, alElegir) {
  const u = unidadDe(o.viaje);
  const op = h(`<button type="button" class="horario ${b.opcion?.viaje.id === o.viaje.id ? 'elegida' : ''}" ${o.libres ? '' : 'disabled'}>
    <span class="hora mono">${hora(o.viaje.fechaSalida)}</span>
    <span class="detalle"><b>${esc(u.tipo[0] + u.tipo.slice(1).toLowerCase())}</b> · ${esc(o.ruta.servicio.toLowerCase())}<br><span class="sec">${o.libres ? `${o.libres} libres` : 'Lleno'}</span></span>
    <span class="mono precio">${dinero(o.precio)}</span></button>`);
  op.onclick = () => {
    if (b.opcion?.viaje.id !== o.viaje.id) b.elegidos.clear();
    b.opcion = o;
    alElegir?.();
    pintarHorarios(); pintarAsientos(); pintarCobro();
  };
  return op;
}

function pintarHorarios() {
  const p = $('p-horarios');
  p.innerHTML = titulo(2, 'Horario');
  const caja = h('<div class="horarios"></div>');
  p.append(caja);
  const aviso = t => { caja.className = 'horarios vacio-horarios'; caja.innerHTML = `<p class="sec">${t}</p>`; };
  if (!b.idDestino) return aviso('Elija el destino para ver las salidas.');
  if (b.idDestino === b.idOrigen) return aviso('El origen y el destino deben ser distintos.');
  const ahora = new Date();
  const lista = buscarViajes(estado, b.idOrigen, b.idDestino, b.fecha).filter(o => new Date(o.viaje.fechaSalida) > ahora);
  if (!lista.length) return aviso('No hay salidas para ese tramo en esa fecha.');
  // El panel no cambia de tamaño: se muestran las próximas salidas y el horario elegido siempre queda a la vista.
  let visibles = lista.slice(0, VISIBLES);
  const elegido = lista.find(o => o.viaje.id === b.opcion?.viaje.id);
  if (elegido && !visibles.includes(elegido)) visibles = [...visibles.slice(0, VISIBLES - 1), elegido];
  caja.replaceChildren(...visibles.map(o => tarjetaHorario(o)));
  if (lista.length > VISIBLES) {
    const todos = boton(`Ver los ${lista.length} horarios del día`, () => {
      const cuerpo = h('<div class="rejilla" style="gap:8px"></div>');
      const cerrar = abrirVentana({ titulo: `Horarios · ${fechaCorta(b.fecha + 'T12:00')}`, ancho: 520, cuerpo });
      cuerpo.append(...lista.map(o => tarjetaHorario(o, () => cerrar())));
    }, 'secundario chico');
    todos.classList.add('ver-todos');
    p.append(todos);
  }
}

function pintarAsientos() {
  const p = $('p-asientos');
  p.classList.toggle('apagado', !b.opcion);
  if (!b.opcion) { p.innerHTML = `${titulo(3, 'Asientos')}<div class="vacio">Elija un horario para ver el autobús.</div>`; return; }
  const o = b.opcion;
  const u = unidadDe(o.viaje);
  p.innerHTML = titulo(3, 'Asientos', `<span class="sec chico">${hora(o.viaje.fechaSalida)} · ${esc(u.placa)} · ${u.layout}</span>`);
  const ampliar = boton('⛶ Mostrar al cliente', mostrarAlCliente, 'secundario chico');
  ampliar.title = 'Ver el autobús en pantalla completa';
  p.querySelector('.panel-titulo').append(ampliar);
  p.append(croquis({ layout: u.layout, capacidad: u.capacidad, ocupados: asientosOcupados(estado, o.viaje.id, o.ordenOrigen, o.ordenDestino),
    elegidos: b.elegidos, alElegir: pintarCobro }));
}

// Vista grande del autobús para que el cliente elija su lugar; comparte los asientos elegidos con la venta.
function mostrarAlCliente() {
  const o = b.opcion;
  const u = unidadDe(o.viaje);
  const nombre = orden => porId(estado.terminales, o.ruta.paradas[orden].idTerminal).nombre;
  const capa = h(`<div class="autobus-grande" role="dialog" aria-label="Elija su asiento">
    <header>
      <div><div class="sec">${esc(nombre(o.ordenOrigen))} → ${esc(nombre(o.ordenDestino))} · ${hora(o.viaje.fechaSalida)}</div>
        <h1>Elija su asiento</h1></div>
      <div class="elegidos-grande" aria-live="polite"></div>
    </header>
    <div class="zona"></div>
  </div>`);
  const resumen = () => {
    capa.querySelector('.elegidos-grande').textContent = b.elegidos.size
      ? `Asiento${b.elegidos.size > 1 ? 's' : ''} ${[...b.elegidos].sort((x, y) => x - y).join(', ')}` : 'Toque un asiento libre';
  };
  capa.querySelector('.zona').append(croquis({ layout: u.layout, capacidad: u.capacidad,
    ocupados: asientosOcupados(estado, o.viaje.id, o.ordenOrigen, o.ordenDestino), elegidos: b.elegidos, alElegir: resumen }));
  const cerrar = () => {
    document.removeEventListener('keydown', alTeclear);
    if (document.fullscreenElement) document.exitFullscreen().catch(() => {});
    capa.remove();
    pintarAsientos(); pintarCobro();
  };
  const alTeclear = e => { if (e.key === 'Escape') cerrar(); };
  const listo = boton('Listo', cerrar, 'grande');
  capa.querySelector('header').append(listo);
  document.addEventListener('keydown', alTeclear);
  // Si el navegador sale de pantalla completa por su cuenta (Esc), también se cierra la vista.
  capa.addEventListener('fullscreenchange', () => { if (!document.fullscreenElement && capa.isConnected) cerrar(); });
  document.body.append(capa);
  resumen();
  capa.requestFullscreen?.().catch(() => {});
  listo.focus();
}

function pintarCobro() {
  const p = $('p-cobro');
  const listo = b.opcion && b.elegidos.size;
  p.classList.toggle('apagado', !listo);
  p.innerHTML = `${titulo(4, 'Titular y tarifas')}
    <div class="rejilla dos">
      <div class="campo"><label for="nombre">Nombre completo</label><input id="nombre" class="entrada" value="${esc(b.titular.nombre)}" ${listo ? '' : 'disabled'} autocomplete="off"></div>
      <div class="campo"><label for="ident">CURP o identificación</label><input id="ident" class="entrada" value="${esc(b.titular.identificacion)}" ${listo ? '' : 'disabled'} autocomplete="off"></div>
    </div>
    <div id="tarifas" class="lista-tarifas">${listo ? '' : '<p class="sec chico">Elija los asientos en el autobús.</p>'}</div>
    ${titulo(5, 'Cobro')}
    <div class="total"><span>Total</span><b id="total">${dinero(total())}</b></div>
    <div class="rejilla dos" style="align-items:end">
      <div class="campo"><label for="recibido">Recibido</label><input id="recibido" class="entrada mono" type="number" min="0" step="0.5" value="${b.recibido}" ${listo ? '' : 'disabled'}></div>
      <div class="campo"><label>Cambio</label><div id="cambio" class="mono" style="font-size:22px;font-weight:600;height:42px;display:flex;align-items:center">—</div></div>
    </div>`;
  const tarifas = p.querySelector('#tarifas');
  if (listo) for (const l of lineas()) {
    const fila = h(`<div class="fila-tarifa"><b class="mono">${l.num}</b>
      <select class="entrada" aria-label="Tarifa del asiento ${l.num}">${estado.tarifas.map(t =>
        `<option value="${t.id}" ${t.id === l.tarifa.id ? 'selected' : ''}>${esc(t.nombre[0] + t.nombre.slice(1).toLowerCase())}${t.descuento ? ` −${t.descuento * 100}%` : ''}</option>`).join('')}</select>
      <span class="mono">${dinero(l.precio)}</span></div>`);
    fila.querySelector('select').onchange = e => { b.tarifas.set(l.num, Number(e.target.value)); pintarCobro(); };
    tarifas.append(fila);
  }
  const confirmar = boton('Confirmar e imprimir', cobrar, 'grande');
  confirmar.style.width = '100%';
  p.append(h('<div class="alerta aviso" id="falta" hidden></div>'), confirmar);
  const revisar = () => {
    b.titular = { nombre: p.querySelector('#nombre').value.trim(), identificacion: p.querySelector('#ident').value.trim() };
    b.recibido = p.querySelector('#recibido').value;
    const cambio = b.recibido === '' ? null : calcularCambio(total(), Number(b.recibido));
    p.querySelector('#cambio').textContent = cambio === null ? '—' : dinero(cambio);
    const falta = p.querySelector('#falta');
    falta.hidden = !(listo && b.recibido !== '' && cambio === null);
    falta.textContent = `Faltan ${dinero(total() - Number(b.recibido))}.`;
    confirmar.disabled = !listo || !b.titular.nombre || !b.titular.identificacion || cambio === null;
  };
  p.oninput = revisar;
  revisar();
}

function cobrar() {
  estado = cargar(); // otra taquilla pudo vender mientras se atendía al cliente
  try {
    const venta = confirmarVenta(estado, {
      idTaquilla: turno.idTaquilla, idCuenta: cuenta.id, idViaje: b.opcion.viaje.id, titular: b.titular,
      ordenOrigen: b.opcion.ordenOrigen, ordenDestino: b.opcion.ordenDestino,
      asientos: lineas().map(l => ({ num: l.num, idTarifa: l.tarifa.id, precio: l.precio })),
      total: total(), recibido: Number(b.recibido), reemplazaA: b.reemplazaA
    }, new Date());
    guardar(estado);
    const cerrar = abrirVentana({ titulo: 'Venta confirmada', ancho: 640,
      cuerpo: (() => { const c = h(`<div class="rejilla"><div class="alerta" style="background:var(--ok-bg);color:var(--ok);font-weight:600">✓ Entregue el ticket: asiento${venta.asientos.length > 1 ? 's' : ''} ${venta.asientos.map(a => a.num).join(', ')}.</div></div>`); c.append(ticket(estado, venta)); return c; })(),
      pie: [boton('Reimprimir', () => window.print(), 'secundario'), boton('Nueva venta', () => { cerrar(); reiniciar(); })] });
  } catch (e) {
    const avisos = {
      ASIENTO_OCUPADO: 'Uno de los asientos se acaba de vender en otra taquilla. Elija otro.',
      VIAJE_CERRADO: 'Ese autobús ya salió o está en ruta. Elija otro horario.',
      SIN_FOLIOS: 'Esta taquilla ya no tiene folios disponibles. Avise al administrador.'
    };
    if (!avisos[e.message]) throw e;
    toast(avisos[e.message], 'error');
    if (e.message === 'ASIENTO_OCUPADO') {
      const ocupados = asientosOcupados(estado, b.opcion.viaje.id, b.opcion.ordenOrigen, b.opcion.ordenDestino);
      [...b.elegidos].filter(n => ocupados.has(n)).forEach(n => b.elegidos.delete(n));
    }
    if (e.message === 'VIAJE_CERRADO') { b.opcion = null; b.elegidos.clear(); }
    pintarHorarios(); pintarAsientos(); pintarCobro();
  }
}

reiniciar(desdeCambio() ?? {});
