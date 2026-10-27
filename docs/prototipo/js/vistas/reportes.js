import { exigirRol, pintarBarra } from '../sesion.js';
import { cargar } from '../estado.js';
import { porId, fechaLocal } from '../logica.js';
import { h, dinero, hora } from '../ui.js';

const cuenta = await exigirRol('ADMINISTRADOR');
pintarBarra(cuenta);

const app = document.getElementById('app');
const estado = cargar();
let periodo = 'dia';
const graficas = [];

// Ventas vigentes, por día de salida del viaje.
function datos() {
  const hoy = new Date();
  const dias = periodo === 'dia' ? 1 : 7;
  const fechas = Array.from({ length: dias }, (_, i) => { const d = new Date(hoy); d.setDate(d.getDate() - (dias - 1 - i)); return fechaLocal(d); });
  const viajes = estado.viajes.filter(v => fechas.includes(v.fechaSalida.slice(0, 10)));
  const ventas = estado.ventas.filter(v => v.estado === 'VIGENTE' && viajes.some(x => x.id === v.idViaje));
  const ocupacion = viajes.map(v => {
    const cap = porId(estado.unidades, v.idUnidad).capacidad;
    const asientos = new Set(ventas.filter(x => x.idViaje === v.id).flatMap(x => x.asientos.map(a => a.num))).size;
    return { viaje: v, pct: Math.round(asientos / cap * 100) };
  });
  return {
    fechas, ventas, ocupacion,
    boletos: ventas.reduce((s, v) => s + v.asientos.length, 0),
    ingresos: ventas.reduce((s, v) => s + v.total, 0),
    promedio: ocupacion.length ? Math.round(ocupacion.reduce((s, o) => s + o.pct, 0) / ocupacion.length) : 0,
    porDia: fechas.map(f => ventas.filter(v => porId(estado.viajes, v.idViaje).fechaSalida.startsWith(f)).reduce((s, v) => s + v.total, 0))
  };
}

function pintar() {
  graficas.splice(0).forEach(g => g.destroy());
  const d = datos();
  const cifra = (t, v, n) => `<div class="tarjeta" style="padding:18px"><div class="sec chico">${t}</div><div class="mono" style="font-size:30px;font-weight:600;margin-top:4px">${v}</div><div class="sec chico">${n}</div></div>`;
  const selector = h(`<div style="display:flex;gap:6px">${[['dia', 'Hoy'], ['semana', 'Últimos 7 días']].map(([k, t]) =>
    `<button type="button" class="btn ${periodo === k ? '' : 'secundario'} chico" data-p="${k}">${t}</button>`).join('')}</div>`);
  selector.onclick = e => { if (e.target.dataset.p) { periodo = e.target.dataset.p; pintar(); } };
  const encabezado = h('<div class="encabezado"><div><h1>Reportes</h1><p>Ocupación e ingresos según la fecha de salida de los viajes. No incluye boletos cancelados.</p></div></div>');
  encabezado.append(selector);
  app.replaceChildren(
    encabezado,
    h(`<div class="rejilla tres" style="margin-bottom:20px">
      ${cifra('Boletos vendidos', d.boletos, 'asientos')}
      ${cifra('Ingresos', dinero(d.ingresos), periodo === 'dia' ? 'hoy' : 'en 7 días')}
      ${cifra('Ocupación promedio', d.promedio + ' %', `${d.ocupacion.length} viajes`)}</div>`),
    h(`<div class="rejilla dos columnas">
      <section class="tarjeta"><div class="tarjeta-cabeza"><h2>Ingresos por día</h2></div><div style="padding:16px;height:300px"><canvas id="g-ingresos" aria-label="Ingresos por día"></canvas></div></section>
      <section class="tarjeta"><div class="tarjeta-cabeza"><h2>Ocupación por viaje</h2></div><div style="padding:16px;height:300px"><canvas id="g-ocupacion" aria-label="Ocupación por viaje"></canvas></div></section>
    </div>`)
  );
  if (!window.Chart) return;
  window.Chart.defaults.font.family = "'IBM Plex Sans', sans-serif";
  window.Chart.defaults.color = '#5F6670';
  const ejes = { x: { grid: { display: false } }, y: { beginAtZero: true, grid: { color: '#DDE0E4' } } };
  graficas.push(new window.Chart(document.getElementById('g-ingresos'), {
    type: 'bar',
    data: { labels: d.fechas.map(f => new Date(f + 'T12:00').toLocaleDateString('es-MX', { weekday: 'short', day: 'numeric' })),
      datasets: [{ data: d.porDia, backgroundColor: '#252B33', borderRadius: 4, maxBarThickness: 48 }] },
    options: { maintainAspectRatio: false, plugins: { legend: { display: false }, tooltip: { callbacks: { label: c => dinero(c.raw) } } }, scales: ejes }
  }));
  graficas.push(new window.Chart(document.getElementById('g-ocupacion'), {
    type: 'bar',
    data: { labels: d.ocupacion.map(o => `${hora(o.viaje.fechaSalida)} ${porId(estado.rutas, o.viaje.idRuta).servicio.toLowerCase()}`),
      datasets: [{ data: d.ocupacion.map(o => o.pct), backgroundColor: '#5F6670', borderRadius: 4, maxBarThickness: 48 }] },
    options: { maintainAspectRatio: false, plugins: { legend: { display: false }, tooltip: { callbacks: { label: c => c.raw + ' %' } } },
      scales: { ...ejes, y: { ...ejes.y, max: 100, ticks: { callback: v => v + ' %' } } } }
  }));
}

pintar();
