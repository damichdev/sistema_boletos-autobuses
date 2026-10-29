import { exigirRol, pintarBarra } from '../sesion.js';
import { cargar, guardar } from '../estado.js';
import { validarAsignacion, semaforo, registrar, porId, fechaLocal, ocupacionPorTramo, movimientoPorParada, licenciaPorVencer, horaLocal } from '../logica.js';
import { h, esc, tabla, boton, toast, hora, abrirVentana } from '../ui.js';

const cuenta = await exigirRol('DESPACHADOR');
pintarBarra(cuenta);

const app = document.getElementById('app');
let estado = cargar();
const nombre = p => `${p.nombre} ${p.apPaterno}`;
const pasaPorAqui = ruta => ruta.paradas.some(p => p.idTerminal === cuenta.idTerminal);
const deHoy = () => estado.viajes
  .filter(v => v.fechaSalida.startsWith(fechaLocal(new Date())) && pasaPorAqui(porId(estado.rutas, v.idRuta)))
  .sort((a, b) => a.fechaSalida.localeCompare(b.fechaSalida));

const opciones = (lista, texto) => lista.map(x => `<option value="${x.id}">${esc(texto(x))}</option>`).join('');
const form = h(`<section class="ventana">
  <div class="ventana-titulo"><h2>Nueva salida</h2></div>
  <div class="ventana-cuerpo">
    <div class="rejilla dos">
      <div class="campo"><label for="ruta">Ruta</label><select id="ruta" class="entrada">${opciones(estado.rutas, r => r.nombre)}</select></div>
      <div class="rejilla dos">
        <div class="campo"><label for="fecha">Fecha</label><input id="fecha" type="date" class="entrada" value="${fechaLocal(new Date())}" min="${fechaLocal(new Date())}"></div>
        <div class="campo"><label for="hora">Hora de salida</label><input id="hora" type="time" class="entrada mono" step="300"></div>
      </div>
      <div class="campo"><label for="unidad">Unidad</label><select id="unidad" class="entrada"><option value="">Elija la unidad…</option>${opciones(estado.unidades, u => `${u.placa} · ${u.tipo} · ${u.capacidad} asientos${semaforo(estado, u.placa).aptoParaViajar ? '' : ' · ' + semaforo(estado, u.placa).estatus}`)}</select></div>
      <div class="campo"><label for="chofer">Chofer</label><select id="chofer" class="entrada"><option value="">Elija el chofer…</option>${opciones(estado.personal.filter(p => p.puesto === 'CONDUCTOR'), p => `${nombre(p)} · licencia vence ${p.vigencia}`)}</select></div>
    </div>
    <div class="alerta aviso" id="motivos" hidden></div>
  </div>
  <div class="ventana-pie"><span class="sec chico" style="align-self:center">Al guardar, la salida aparece en la venta de las taquillas.</span></div>
</section>`);

const guardarBtn = boton('Guardar salida', () => {
  const v = valores();
  estado = cargar();
  estado.viajes.push({ id: Math.max(0, ...estado.viajes.map(x => x.id)) + 1, idRuta: v.idRuta, fechaSalida: v.fechaSalida,
    idUnidad: v.idUnidad, idConductor: v.idConductor, estado: 'PROGRAMADO' });
  registrar(estado, cuenta.id, 'PROGRAMACION', `Salida ${v.fechaSalida.replace('T', ' ')} · unidad ${porId(estado.unidades, v.idUnidad).placa}`, new Date());
  guardar(estado);
  toast(`Salida de las ${v.fechaSalida.slice(11)} guardada y disponible en taquilla.`);
  form.querySelector('#hora').value = '';
  revisar();
  lista.actualizar(deHoy());
}, 'grande');
form.querySelector('.ventana-pie').append(guardarBtn);

function valores() {
  const q = id => form.querySelector('#' + id).value;
  return { idRuta: Number(q('ruta')), idUnidad: Number(q('unidad')), idConductor: Number(q('chofer')),
    fechaSalida: q('fecha') && q('hora') ? `${q('fecha')}T${q('hora')}` : '' };
}

function revisar() {
  const v = valores();
  const completo = v.idUnidad && v.idConductor && v.fechaSalida;
  const motivos = v.fechaSalida || v.idUnidad || v.idConductor
    ? validarAsignacion(estado, v.idUnidad, v.idConductor, v.fechaSalida || fechaLocal(new Date())) : [];
  if (v.fechaSalida && new Date(v.fechaSalida) <= new Date()) motivos.push('La hora de salida ya pasó');
  const caja = form.querySelector('#motivos');
  caja.hidden = !motivos.length;
  caja.innerHTML = `<b>No se puede asignar:</b> ${motivos.map(esc).join(' · ')}`;
  guardarBtn.disabled = !completo || motivos.length > 0;
}
form.oninput = revisar;
revisar();

const lista = tabla({
  titulo: `Salidas de hoy que pasan por ${porId(estado.terminales, cuenta.idTerminal).nombre}`,
  columnas: [
    { titulo: 'Hora', valor: v => `<b class="mono">${hora(v.fechaSalida)}</b>` },
    { titulo: 'Ruta', valor: v => esc(porId(estado.rutas, v.idRuta).nombre) },
    { titulo: 'Unidad', valor: v => esc(porId(estado.unidades, v.idUnidad).placa) },
    { titulo: 'Chofer', valor: v => esc(nombre(porId(estado.personal, v.idConductor))) },
    { titulo: 'Estado', valor: v => `<span class="chip ${v.estado === 'EN RUTA' ? 'neutro' : 'ok'}">${v.estado === 'EN RUTA' ? 'En ruta' : 'Programado'}</span>` }
  ],
  filas: deHoy(),
  // Busca por hora, ruta, placa, chofer o estado.
  buscar: (v, t) => [hora(v.fechaSalida), porId(estado.rutas, v.idRuta).nombre, porId(estado.unidades, v.idUnidad).placa,
    nombre(porId(estado.personal, v.idConductor)), v.estado === 'EN RUTA' ? 'en ruta' : 'programado'].join(' ').toLowerCase().includes(t),
  acciones: v => [boton('Ver detalle', () => detalle(v.id), 'secundario chico')],
  vacio: 'No hay salidas que coincidan.'
});

app.append(h('<div class="encabezado"><div><h1>Programar salida</h1><p>Elija ruta, fecha y hora, y asigne unidad y chofer en el mismo paso.</p></div></div>'), form, h('<div style="height:24px"></div>'), lista);

// Detalle de solo consulta: recorrido, ocupación por tramo, unidad y chofer de una salida.
function detalle(idViaje) {
  estado = cargar();
  const v = porId(estado.viajes, idViaje);
  const ruta = porId(estado.rutas, v.idRuta);
  const u = porId(estado.unidades, v.idUnidad);
  const chofer = porId(estado.personal, v.idConductor);
  const terminal = orden => porId(estado.terminales, ruta.paradas[orden].idTerminal).nombre;
  const pasa = min => { const d = new Date(v.fechaSalida); d.setMinutes(d.getMinutes() + min); return horaLocal(d); };
  const movimiento = movimientoPorParada(estado, v.id);
  const tramos = ocupacionPorTramo(estado, v.id);
  const vencida = !chofer.vigencia || chofer.vigencia < v.fechaSalida.slice(0, 10);
  const licencia = vencida ? `<span class="chip error">Licencia vencida ${esc(chofer.vigencia ?? '')}</span>`
    : licenciaPorVencer(chofer, new Date()) ? `<span class="chip aviso">Licencia vence ${esc(chofer.vigencia)}</span>`
    : `<span class="chip ok">Licencia vigente a ${esc(chofer.vigencia)}</span>`;
  const enRuta = v.estado === 'EN RUTA';
  const mov = m => [m.suben ? `↑ ${m.suben} suben` : '', m.bajan ? `↓ ${m.bajan} bajan` : ''].filter(Boolean).join(' · ') || 'sin movimiento';

  const cuerpo = h(`<div class="detalle-salida">
    <div class="detalle-cabeza">
      <span class="mono" style="font-size:30px;font-weight:600">${hora(v.fechaSalida)}</span>
      <div style="flex:1"><b>${esc(ruta.nombre)}</b><div class="sec chico">Servicio ${esc(ruta.servicio.toLowerCase())} · ${esc(u.tipo.toLowerCase())}</div></div>
      <span class="chip ${enRuta ? 'neutro' : 'ok'}">${enRuta ? 'En ruta' : 'Programado'}</span>
    </div>
    <div class="rejilla dos">
      <section><h3>Recorrido</h3>
        <ol class="recorrido">${ruta.paradas.map((p, i) => `<li><span class="mono">${pasa(p.min)}</span><b>${esc(terminal(i))}</b>
          <span class="sec chico">${mov(movimiento[i])}</span></li>`).join('')}</ol>
      </section>
      <section><h3>Ocupación por tramo</h3>
        <div class="tramos">${tramos.map(t => `<div class="tramo">
          <div class="tramo-txt"><span>${esc(terminal(t.ordenOrigen))} → ${esc(terminal(t.ordenDestino))}</span><span class="mono">${t.ocupados}/${t.capacidad}</span></div>
          <div class="barra-tramo"><i style="width:${Math.round(t.ocupados / t.capacidad * 100)}%"></i></div></div>`).join('')}</div>
      </section>
    </div>
    <div class="rejilla dos">
      <div class="dato"><span class="sec chico">Unidad</span><b>${esc(u.placa)}</b><span class="sec chico">${esc(u.modelo)} · ${u.capacidad} asientos · ${u.layout}</span></div>
      <div class="dato"><span class="sec chico">Chofer</span><b>${esc(chofer.nombre)} ${esc(chofer.apPaterno)}</b>${licencia}</div>
    </div>
  </div>`);
  const cerrar = abrirVentana({ titulo: 'Detalle de la salida', ancho: 760, cuerpo, pie: [
    boton('Cerrar', () => cerrar(), 'secundario'),
    boton('Ver manifiesto', () => { sessionStorage.setItem('zaca-viaje', String(v.id)); location.href = '/despacho/manifiesto.html'; })
  ] });
}
