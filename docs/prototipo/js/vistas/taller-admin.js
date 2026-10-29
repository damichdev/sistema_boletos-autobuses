import { exigirRol, pintarBarra } from '../sesion.js';
import { cargar } from '../estado.js';
import { resumenTaller } from '../logica.js';
import { h, esc, dinero, tabla } from '../ui.js';

const cuenta = await exigirRol('ADMINISTRADOR');
pintarBarra(cuenta);

const app = document.getElementById('app');
const estado = cargar();
const r = resumenTaller(estado);
const cifra = (t, v, n) => `<div class="tarjeta" style="padding:18px"><div class="sec chico">${t}</div><div class="mono" style="font-size:30px;font-weight:600;margin-top:4px">${v}</div><div class="sec chico">${n}</div></div>`;
const dias = d => d ? d.toFixed(1) : '—';

app.append(
  h('<div class="encabezado"><div><h1>Taller</h1><p>Auditoría de gasto y desempeño. Solo cuentan las órdenes cerradas; las abiertas llevan un costo estimado.</p></div></div>'),
  h(`<div class="rejilla tres" style="margin-bottom:20px">
    ${cifra('Gasto en taller', dinero(r.gasto), `${r.cerradas} órdenes cerradas`)}
    ${cifra('Costo por orden', dinero(r.cerradas ? r.gasto / r.cerradas : 0), 'promedio')}
    ${cifra('Órdenes abiertas', r.abiertas, `${dias(r.dias)} días promedio por reparación`)}</div>`),
  tabla({
    titulo: 'Por mecánico',
    columnas: [
      { titulo: 'Mecánico', valor: m => `${esc(m.nombre)} ${esc(m.apPaterno)}` },
      { titulo: 'Órdenes cerradas', valor: m => `<span class="mono">${m.ordenes}</span>` },
      { titulo: 'Costo total', valor: m => `<span class="mono">${dinero(m.costo)}</span>` },
      { titulo: 'Costo por orden', valor: m => `<span class="mono">${m.ordenes ? dinero(m.costo / m.ordenes) : '—'}</span>` },
      { titulo: 'Días por orden', valor: m => `<span class="mono">${dias(m.dias)}</span>` }
    ],
    filas: r.porMecanico
  }),
  h('<div style="height:24px"></div>'),
  tabla({
    titulo: 'Por unidad',
    columnas: [
      { titulo: 'Unidad', valor: b => `<b class="mono">${esc(b.placa)}</b>` },
      { titulo: 'Órdenes cerradas', valor: b => `<span class="mono">${b.ordenes}</span>` },
      { titulo: 'Costo total', valor: b => `<span class="mono">${dinero(b.costo)}</span>` }
    ],
    filas: r.porBus
  })
);
