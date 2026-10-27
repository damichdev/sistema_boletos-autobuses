import { porId, fechaLocal, ordenEnRuta } from '../logica.js';
import { h, esc, hora } from '../ui.js';

// Viajes de hoy que pasan por la terminal del despachador, con la hora a la que salen de esa escala.
export function viajesDeEscala(estado, idTerminal) {
  return estado.viajes
    .filter(v => v.fechaSalida.startsWith(fechaLocal(new Date())))
    .map(v => {
      const ruta = porId(estado.rutas, v.idRuta);
      const orden = ordenEnRuta(ruta, idTerminal);
      if (orden === null || orden === ruta.paradas.length - 1) return null;
      const sale = new Date(v.fechaSalida);
      sale.setMinutes(sale.getMinutes() + ruta.paradas[orden].min);
      return { viaje: v, ruta, orden, sale };
    })
    .filter(Boolean)
    .sort((a, b) => a.sale - b.sale);
}

export const horaDe = d => `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;

// Selector de viaje compartido por Manifiesto y Abordaje; recuerda la elección entre pantallas.
export function selectorViaje(lista, alCambiar) {
  const guardado = Number(sessionStorage.getItem('zaca-viaje'));
  const actual = lista.find(x => x.viaje.id === guardado) ?? lista.find(x => x.sale > new Date()) ?? lista[0];
  const el = h(`<div class="campo" style="max-width:520px"><label for="viaje">Viaje</label>
    <select id="viaje" class="entrada">${lista.map(x => `<option value="${x.viaje.id}" ${x === actual ? 'selected' : ''}>
      ${horaDe(x.sale)} · ${esc(x.ruta.nombre)} (sale de origen ${hora(x.viaje.fechaSalida)})</option>`).join('')}</select></div>`);
  el.querySelector('select').onchange = e => {
    sessionStorage.setItem('zaca-viaje', e.target.value);
    alCambiar(lista.find(x => x.viaje.id === Number(e.target.value)));
  };
  if (actual) sessionStorage.setItem('zaca-viaje', String(actual.viaje.id));
  return { el, actual };
}
