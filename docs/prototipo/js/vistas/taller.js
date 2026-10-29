import { exigirRol, pintarBarra } from '../sesion.js';
import { cargar, guardar } from '../estado.js';
import { abrirOrden, cerrarOrden, semaforo, mecanicos, viajesPendientesDe, porId, fechaLocal, horaLocal } from '../logica.js';
import { h, esc, tabla, boton, abrirVentana, toast, dinero, hora, fechaCorta } from '../ui.js';

const cuenta = await exigirRol('ADMINISTRADOR');
pintarBarra(cuenta);

const app = document.getElementById('app');
let estado = cargar();
const ahoraLocal = () => { const d = new Date(); return `${fechaLocal(d)}T${horaLocal(d)}`; };
const nombre = m => `${m.nombre} ${m.apPaterno}`;
const capital = s => s[0] + s.slice(1).toLowerCase();
const fecha = f => f ? `${fechaCorta(f)} <span class="sec chico mono">${hora(f)}</span>` : '<span class="sec">—</span>';
const opciones = (lista, texto, sel) => lista.map(x => `<option value="${esc(x[0])}" ${String(x[0]) === String(sel) ? 'selected' : ''}>${esc(x[1])}</option>`).join('');
const MENSAJES = {
  ORDEN_ABIERTA: 'Esa unidad ya tiene una orden abierta.', MECANICO_INVALIDO: 'Elija un mecánico del personal.',
  COSTO_INVALIDO: 'El costo no puede ser negativo.', SALIDA_ANTES_DE_INGRESO: 'La salida no puede ser anterior al ingreso.', ORDEN_CERRADA: 'La orden ya estaba cerrada.'
};

app.append(h('<div class="encabezado"><div><h1>Órdenes de taller</h1><p>Registre el ingreso de un autobús, asigne mecánico y cierre la orden con su costo. El estado del bus se actualiza solo y el despacho lo ve al instante.</p></div></div>'));

const ordenes = tabla({
  titulo: 'Órdenes',
  columnas: [
    { titulo: 'Orden', valor: o => `<b class="mono">#${o.id}</b>` },
    { titulo: 'Unidad', valor: o => `<b class="mono">${esc(o.placa)}</b>` },
    { titulo: 'Mecánico', valor: o => esc(nombre(porId(estado.personal, o.idMecanico))) },
    { titulo: 'Ingreso', valor: o => fecha(o.fechaIngreso) },
    { titulo: 'Salida', valor: o => fecha(o.fechaSalida) },
    { titulo: 'Diagnóstico', valor: o => `<span title="${esc(o.diagnostico)}">${esc(o.diagnostico || '—')}</span>` },
    { titulo: 'Costo', valor: o => `<span class="mono">${dinero(o.costo)}</span>${o.fechaSalida ? '' : ' <span class="sec chico">est.</span>'}` },
    { titulo: 'Estado', valor: o => o.fechaSalida ? '<span class="chip neutro">Cerrada</span>' : '<span class="chip aviso">Abierta</span>' }
  ],
  filas: [...estado.ordenes].reverse(),
  buscar: (o, t) => [o.placa, nombre(porId(estado.personal, o.idMecanico)), o.diagnostico, o.fechaSalida ? 'cerrada' : 'abierta'].join(' ').toLowerCase().includes(t),
  acciones: o => o.fechaSalida ? [] : [boton('Cerrar orden', () => cerrar(o), 'secundario chico')],
  extra: [boton('+ Registrar ingreso', ingreso)]
});

const flota = tabla({
  titulo: 'Estado de la flota (lo que ve el despacho)',
  columnas: [
    { titulo: 'Unidad', valor: u => `<b class="mono">${esc(u.placa)}</b> <span class="sec chico">${esc(u.modelo)}</span>` },
    { titulo: 'Estatus', valor: u => { const s = semaforo(estado, u.placa); return `<span class="chip ${s.aptoParaViajar ? 'ok' : s.estatus === 'DE BAJA' ? 'error' : 'aviso'}">${esc(capital(s.estatus))}</span>`; } },
    { titulo: 'Último ingreso', valor: u => fecha(estado.busesTaller.find(b => b.placa === u.placa)?.ultimaFechaIngreso) },
    { titulo: 'Observaciones', valor: u => esc(estado.busesTaller.find(b => b.placa === u.placa)?.observaciones || '—') }
  ],
  filas: estado.unidades
});
app.append(ordenes, h('<div style="height:24px"></div>'), flota);

function refrescar() {
  estado = cargar();
  ordenes.actualizar([...estado.ordenes].reverse());
  flota.actualizar(estado.unidades);
}

function ingreso() {
  estado = cargar();
  const libres = estado.unidades.filter(u => !estado.ordenes.some(o => o.placa === u.placa && !o.fechaSalida));
  const activos = mecanicos(estado);
  if (!libres.length) return toast('Todas las unidades ya tienen una orden abierta.', 'aviso');
  if (!activos.length) return toast('No hay mecánicos. Dé de alta uno en Personal.', 'aviso');
  const form = h(`<form class="rejilla dos" novalidate>
    <div class="campo"><label for="i-unidad">Unidad</label><select id="i-unidad" class="entrada">${opciones(libres.map(u => [u.placa, `${u.placa} · ${u.modelo}`]))}</select></div>
    <div class="campo"><label for="i-mec">Mecánico</label><select id="i-mec" class="entrada">${opciones(activos.map(m => [m.id, nombre(m)]))}</select></div>
    <div class="campo"><label for="i-tipo">Tipo de mantenimiento</label><select id="i-tipo" class="entrada">${opciones([[2, 'Preventivo'], [3, 'Correctivo']])}</select></div>
    <div class="campo"><label for="i-fecha">Fecha y hora de ingreso</label><input id="i-fecha" type="datetime-local" class="entrada mono" value="${ahoraLocal()}"><span class="error" hidden></span></div>
    <div class="campo" style="grid-column:1/-1"><label for="i-diag">Diagnóstico inicial</label><input id="i-diag" class="entrada" autocomplete="off"></div>
    <div class="campo"><label for="i-costo">Costo estimado ($)</label><input id="i-costo" type="number" min="0" step="0.01" class="entrada mono" value="0"><span class="error" hidden></span></div>
    <div class="campo" style="grid-column:1/-1"><label for="i-obs">Observaciones del bus</label><input id="i-obs" class="entrada" autocomplete="off" placeholder="Opcional"></div>
    <div class="alerta aviso" id="i-aviso" style="grid-column:1/-1" hidden></div>
  </form>`);
  const aviso = () => {
    const pend = viajesPendientesDe(estado, form.querySelector('#i-unidad').value);
    const caja = form.querySelector('#i-aviso');
    caja.hidden = !pend.length;
    caja.innerHTML = `<b>Tiene ${pend.length} salida(s) programada(s):</b> ${pend.slice(0, 4).map(v => `${fechaCorta(v.fechaSalida)} ${hora(v.fechaSalida)}`).join(' · ')}${pend.length > 4 ? '…' : ''}. El despacho deberá cambiarles la unidad.`;
  };
  form.querySelector('#i-unidad').onchange = aviso;
  aviso();
  const cerrarVentana = abrirVentana({ titulo: 'Registrar ingreso de autobús', ancho: 680, cuerpo: form, pie: [
    boton('Cancelar', () => cerrarVentana(), 'secundario'),
    boton('Registrar ingreso', () => {
      const q = id => form.querySelector('#' + id).value;
      const costo = Number(q('i-costo'));
      const falla = (id, msg) => { const c = form.querySelector('#' + id); c.classList.toggle('invalida', !!msg); const e = c.parentElement.querySelector('.error'); e.hidden = !msg; e.textContent = msg; return !!msg; };
      if ([falla('i-fecha', q('i-fecha') ? '' : 'Campo obligatorio'), falla('i-costo', costo >= 0 ? '' : 'No puede ser negativo')].some(Boolean)) return;
      estado = cargar();
      try {
        abrirOrden(estado, { placa: q('i-unidad'), idMecanico: Number(q('i-mec')), idEstado: Number(q('i-tipo')), fechaIngreso: q('i-fecha'), diagnostico: q('i-diag').trim(), costo, observaciones: q('i-obs').trim() }, cuenta.id, new Date());
      } catch (e) { return toast(MENSAJES[e.message] ?? e.message, 'error'); }
      guardar(estado);
      cerrarVentana();
      toast(`${q('i-unidad')} ingresó a taller. El despacho ya no podrá asignarla.`);
      refrescar();
    })
  ] });
}

function cerrar(orden) {
  const form = h(`<form class="rejilla dos" novalidate>
    <div class="dato" style="grid-column:1/-1"><span class="sec chico">Orden #${orden.id}</span><b>${esc(orden.placa)}</b><span class="sec chico">Ingresó ${esc(orden.fechaIngreso.replace('T', ' '))}</span></div>
    <div class="campo"><label for="c-fecha">Fecha y hora de salida</label><input id="c-fecha" type="datetime-local" class="entrada mono" value="${ahoraLocal()}" min="${orden.fechaIngreso}"><span class="error" hidden></span></div>
    <div class="campo"><label for="c-estado">Estado del bus al salir</label><select id="c-estado" class="entrada">${opciones([[1, 'Operativo'], [4, 'De baja']])}</select></div>
    <div class="campo" style="grid-column:1/-1"><label for="c-diag">Diagnóstico final</label><input id="c-diag" class="entrada" autocomplete="off" value="${esc(orden.diagnostico)}"></div>
    <div class="campo"><label for="c-costo">Costo final ($)</label><input id="c-costo" type="number" min="0" step="0.01" class="entrada mono" value="${orden.costo}"><span class="error" hidden></span></div>
  </form>`);
  const cerrarVentana = abrirVentana({ titulo: 'Cerrar orden de taller', ancho: 680, cuerpo: form, pie: [
    boton('Cancelar', () => cerrarVentana(), 'secundario'),
    boton('Cerrar orden', () => {
      const q = id => form.querySelector('#' + id).value;
      const costo = Number(q('c-costo'));
      const falla = (id, msg) => { const c = form.querySelector('#' + id); c.classList.toggle('invalida', !!msg); const e = c.parentElement.querySelector('.error'); e.hidden = !msg; e.textContent = msg; return !!msg; };
      if ([falla('c-fecha', !q('c-fecha') ? 'Campo obligatorio' : q('c-fecha') < orden.fechaIngreso ? 'Anterior al ingreso' : ''),
        falla('c-costo', q('c-costo') !== '' && costo >= 0 ? '' : 'Costo inválido')].some(Boolean)) return;
      estado = cargar();
      try {
        cerrarOrden(estado, orden.id, { fechaSalida: q('c-fecha'), diagnostico: q('c-diag').trim(), costo, idEstado: Number(q('c-estado')) }, cuenta.id, new Date());
      } catch (e) { return toast(MENSAJES[e.message] ?? e.message, 'error'); }
      guardar(estado);
      cerrarVentana();
      toast(`Orden #${orden.id} cerrada. ${orden.placa} quedó ${Number(q('c-estado')) === 1 ? 'operativa' : 'de baja'}.`);
      refrescar();
    })
  ] });
}
