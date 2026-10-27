import { exigirRol, pintarBarra } from '../sesion.js';
import { cargar, guardar } from '../estado.js';
import { porId, rutaEnUso } from '../logica.js';
import { h, esc, tabla, boton, abrirVentana, toast, dinero } from '../ui.js';

const cuenta = await exigirRol('ADMINISTRADOR');
pintarBarra(cuenta);

const app = document.getElementById('app');
let estado = cargar();
const terminal = id => porId(estado.terminales, id).nombre;

app.append(h('<div class="encabezado"><div><h1>Rutas</h1><p>El molde del negocio: paradas en orden y el precio de cada tramo.</p></div></div>'));

const lista = tabla({
  titulo: 'Rutas',
  columnas: [
    { titulo: 'Ruta', valor: r => `<b>${esc(r.nombre)}</b>` },
    { titulo: 'Servicio', valor: r => `<span class="chip neutro">${esc(r.servicio)}</span>` },
    { titulo: 'Paradas', valor: r => r.paradas.map(p => esc(terminal(p.idTerminal))).join(' <span class="sec">→</span> ') },
    { titulo: 'Distancia', valor: r => `<span class="mono">${r.paradas.at(-1).km} km</span>` },
    { titulo: 'Origen a destino', valor: r => `<span class="mono">${dinero(r.precios.find(p => p.ordenOrigen === 0 && p.ordenDestino === r.paradas.length - 1)?.precio ?? 0)}</span>` }
  ],
  filas: estado.rutas,
  buscar: (r, t) => r.nombre.toLowerCase().includes(t),
  acciones: r => [boton('Editar', () => editor(r), 'secundario chico')],
  extra: [boton('+ Nueva ruta', () => editor(null))]
});
app.append(lista);

function editor(ruta) {
  // Los precios se guardan por par de terminales mientras se edita, así quitar o cambiar una parada no pierde los demás.
  const precios = new Map();
  let paradas = ruta ? ruta.paradas.map(p => ({ ...p })) : [{ idTerminal: estado.terminales[0].id, km: 0, min: 0 }, { idTerminal: estado.terminales[1].id, km: 0, min: 0 }];
  // Con viajes programados, cambiar paradas descuadraría los tramos de boletos ya vendidos.
  const fija = ruta && rutaEnUso(estado, ruta.id);
  if (ruta) ruta.precios.forEach(p => precios.set(`${ruta.paradas[p.ordenOrigen].idTerminal}-${ruta.paradas[p.ordenDestino].idTerminal}`, p.precio));

  const cuerpo = h(`<div class="rejilla">
    <div class="rejilla dos">
      <div class="campo"><label for="nombre">Nombre de la ruta</label><input id="nombre" class="entrada" value="${esc(ruta?.nombre ?? '')}"></div>
      <div class="campo"><label for="servicio">Servicio</label><select id="servicio" class="entrada">${['ORDINARIO', 'DIRECTO'].map(s => `<option ${ruta?.servicio === s ? 'selected' : ''}>${s}</option>`).join('')}</select></div>
    </div>
    ${fija ? '<div class="alerta aviso">Esta ruta ya tiene viajes: solo puede cambiar el nombre, los tiempos y los precios. Para otras paradas cree una ruta nueva.</div>' : ''}
    <div><h3 style="margin-bottom:8px">Paradas en orden</h3><table class="tabla"><thead><tr><th>#</th><th>Terminal</th><th>Km desde origen</th><th>Min desde origen</th><th></th></tr></thead><tbody id="paradas"></tbody></table></div>
    <div><h3 style="margin-bottom:8px">Precio por tramo</h3><p class="sec chico" style="margin-bottom:8px">Un precio para cada combinación de subida y bajada.</p>
      <div id="precios" class="rejilla tres"></div></div>
    <div class="alerta error" id="errores" hidden></div>
  </div>`);

  function pintar() {
    const tb = cuerpo.querySelector('#paradas');
    tb.replaceChildren(...paradas.map((p, i) => {
      const tr = h(`<table><tr><td class="mono">${i + 1}</td>
        <td><select class="entrada" style="height:36px">${estado.terminales.map(t => `<option value="${t.id}" ${t.id === p.idTerminal ? 'selected' : ''}>${esc(t.nombre)}</option>`).join('')}</select></td>
        <td><input class="entrada mono" style="height:36px;width:110px" type="number" min="0" value="${p.km}"></td>
        <td><input class="entrada mono" style="height:36px;width:110px" type="number" min="0" value="${p.min}"></td>
        <td><div class="acciones"></div></td></tr></table>`).querySelector('tr');
      const [sel, km, min] = tr.querySelectorAll('select, input');
      sel.onchange = () => { p.idTerminal = Number(sel.value); pintar(); };
      km.oninput = () => { p.km = Number(km.value); };
      min.oninput = () => { p.min = Number(min.value); };
      const quitar = boton('Quitar', () => { paradas.splice(i, 1); pintar(); }, 'secundario chico'); quitar.disabled = paradas.length <= 2;
      if (fija) { sel.disabled = km.disabled = quitar.disabled = true; }
      tr.querySelector('.acciones').append(quitar);
      return tr;
    }));
    const zona = cuerpo.querySelector('#precios');
    zona.replaceChildren();
    for (let o = 0; o < paradas.length; o++) for (let d = o + 1; d < paradas.length; d++) {
      const clave = `${paradas[o].idTerminal}-${paradas[d].idTerminal}`;
      const campo = h(`<div class="campo"><label>${esc(terminal(paradas[o].idTerminal))} → ${esc(terminal(paradas[d].idTerminal))}</label>
        <input class="entrada mono" type="number" min="0" step="5" placeholder="$" value="${precios.get(clave) ?? ''}"></div>`);
      campo.querySelector('input').oninput = e => precios.set(clave, e.target.value === '' ? undefined : Number(e.target.value));
      zona.append(campo);
    }
  }
  pintar();
  const agregar = boton('+ Agregar parada', () => {
    const libre = estado.terminales.find(t => !paradas.some(p => p.idTerminal === t.id)) ?? estado.terminales[0];
    const ultima = paradas.at(-1);
    paradas.push({ idTerminal: libre.id, km: ultima.km, min: ultima.min });
    pintar();
  }, 'secundario chico');
  agregar.disabled = !!fija;
  cuerpo.querySelector('#paradas').closest('div').append(h('<div style="height:8px"></div>'), agregar);

  const cerrar = abrirVentana({ titulo: ruta ? 'Editar ruta' : 'Nueva ruta', ancho: 820, cuerpo, pie: [
    boton('Cancelar', () => cerrar(), 'secundario'),
    boton('Guardar ruta', () => {
      const nombre = cuerpo.querySelector('#nombre').value.trim();
      const errores = [];
      if (!nombre) errores.push('Escriba el nombre de la ruta.');
      if (new Set(paradas.map(p => p.idTerminal)).size !== paradas.length) errores.push('Una terminal aparece dos veces.');
      if (paradas[0].km !== 0 || paradas[0].min !== 0) errores.push('La primera parada va en km 0 y minuto 0.');
      if (paradas.some((p, i) => i && p.km <= paradas[i - 1].km)) errores.push('Los kilómetros deben ir en aumento.');
      if (paradas.some((p, i) => i && p.min <= paradas[i - 1].min)) errores.push('Los minutos deben ir en aumento.');
      const tramos = [];
      for (let o = 0; o < paradas.length; o++) for (let d = o + 1; d < paradas.length; d++) {
        const precio = precios.get(`${paradas[o].idTerminal}-${paradas[d].idTerminal}`);
        if (!(precio > 0)) { errores.push('Falta el precio de algún tramo.'); o = d = paradas.length; break; }
        tramos.push({ ordenOrigen: o, ordenDestino: d, precio });
      }
      const caja = cuerpo.querySelector('#errores');
      caja.hidden = !errores.length;
      caja.innerHTML = errores.map(esc).join('<br>');
      if (errores.length) return;
      estado = cargar();
      const datos = { nombre, servicio: cuerpo.querySelector('#servicio').value, paradas: paradas.map((p, orden) => ({ ...p, orden })), precios: tramos };
      if (ruta) Object.assign(porId(estado.rutas, ruta.id), datos);
      else estado.rutas.push({ id: Math.max(0, ...estado.rutas.map(r => r.id)) + 1, ...datos });
      guardar(estado);
      cerrar();
      toast('Ruta guardada. Ya está disponible para programar salidas.');
      lista.actualizar(estado.rutas);
    })
  ] });
}
