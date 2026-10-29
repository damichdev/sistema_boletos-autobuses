import { exigirRol, pintarBarra } from '../sesion.js';
import { cargar } from '../estado.js';
import { semaforo } from '../logica.js';
import { h, esc } from '../ui.js';

const cuenta = await exigirRol('DESPACHADOR');
pintarBarra(cuenta);

const app = document.getElementById('app');
const estado = cargar();
const capital = s => s[0] + s.slice(1).toLowerCase();
const unidades = estado.unidades.map(u => ({ u, s: semaforo(estado, u.placa) })).sort((a, b) => a.s.aptoParaViajar - b.s.aptoParaViajar || a.u.placa.localeCompare(b.u.placa));
const aptas = unidades.filter(x => x.s.aptoParaViajar).length;

app.append(
  h(`<div class="encabezado"><div><h1>Disponibilidad de unidades</h1>
    <p>${aptas} de ${unidades.length} unidades pueden salir. Las que están en taller no se pueden asignar en Programar salida.</p></div></div>`),
  h(`<div class="semaforo">${unidades.map(({ u, s }) => `<article class="luz-tarjeta ${s.aptoParaViajar ? 'apta' : 'bloqueada'}">
    <span class="luz" aria-hidden="true"></span>
    <div><b class="mono">${esc(u.placa)}</b><div class="sec chico">${esc(u.modelo)} · ${esc(capital(u.tipo))} · ${u.capacidad} asientos</div></div>
    <div class="luz-estado"><b>${s.aptoParaViajar ? 'Apta para viajar' : 'No disponible'}</b><span class="sec chico">${esc(capital(s.estatus))}</span></div>
  </article>`).join('')}</div>`)
);
