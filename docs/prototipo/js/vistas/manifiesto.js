import { exigirRol, pintarBarra } from '../sesion.js';
import { cargar, guardar } from '../estado.js';
import { porId, registrar } from '../logica.js';
import { h, esc, tabla, boton, toast } from '../ui.js';
import { viajesDeEscala, selectorViaje, horaDe } from './despacho-comun.js';

const cuenta = await exigirRol('DESPACHADOR');
pintarBarra(cuenta);

const app = document.getElementById('app');
let estado = cargar();
const lista = viajesDeEscala(estado, cuenta.idTerminal);
const aqui = porId(estado.terminales, cuenta.idTerminal).nombre;

app.append(h(`<div class="encabezado"><div><h1>Manifiesto</h1><p>Pasajeros que suben en ${esc(aqui)}. El autobús sale a su hora; quien no llegue conserva su asiento.</p></div></div>`));

if (!lista.length) {
  app.append(h('<div class="tarjeta vacio">No hay viajes que salgan hoy de esta terminal.</div>'));
} else {
  const { el, actual } = selectorViaje(lista, pintar);
  const zona = h('<div style="display:flex;flex-direction:column;gap:20px;margin-top:16px"></div>');
  app.append(el, zona);
  pintar(actual);

  function pintar(x) {
    estado = cargar();
    const viaje = porId(estado.viajes, x.viaje.id);
    const salio = new Date() >= x.sale;
    const registrada = viaje.salidas?.[cuenta.idTerminal];
    const suben = estado.ventas.filter(v => v.idViaje === viaje.id && v.estado === 'VIGENTE' && v.ordenOrigen === x.orden);
    const asientos = suben.flatMap(v => v.asientos);
    const abordados = asientos.filter(a => a.abordado).length;
    const parada = orden => porId(estado.terminales, x.ruta.paradas[orden].idTerminal).nombre;
    const tarifa = id => porId(estado.tarifas, id).nombre.toLowerCase();

    const cabeza = h(`<section class="tarjeta" style="padding:20px;display:flex;gap:28px;align-items:center;flex-wrap:wrap">
      <div><div class="sec chico">Sale de ${esc(aqui)}</div><div class="mono" style="font-size:30px;font-weight:600">${horaDe(x.sale)}</div></div>
      <div><div class="sec chico">Unidad</div><div style="font-size:18px;font-weight:600">${esc(porId(estado.unidades, viaje.idUnidad).placa)}</div></div>
      <div style="flex:1;min-width:220px"><div class="sec chico">Abordaron</div>
        <div style="font-size:18px;font-weight:600">${abordados} de ${asientos.length} pasajeros</div></div>
    </section>`);
    const salida = registrada
      ? h(`<span class="chip neutro" style="font-size:15px;padding:10px 16px">En ruta desde las ${horaDe(new Date(registrada))}</span>`)
      : boton(salio ? 'Marcar en ruta' : `Disponible a las ${horaDe(x.sale)}`, () => {
          viaje.salidas = { ...viaje.salidas, [cuenta.idTerminal]: new Date().toISOString() };
          viaje.estado = 'EN RUTA';
          registrar(estado, cuenta.id, 'SALIDA', `Viaje ${viaje.id} sale de ${aqui}`, new Date());
          guardar(estado);
          toast('Viaje en ruta. Buen viaje.');
          pintar(x);
        }, 'grande');
    salida.disabled = !salio;
    cabeza.append(salida);

    const chip = a => a.abordado ? '<span class="chip ok">Abordó</span>'
      : salio ? '<span class="chip neutro">No se presentó</span>' : '<span class="chip aviso">Pendiente</span>';
    const filas = suben.flatMap(v => v.asientos.map(a => ({ v, a }))).sort((p, q) => p.a.num - q.a.num);
    const t = tabla({
      titulo: 'Suben en esta escala',
      columnas: [
        { titulo: 'Asiento', valor: f => `<b class="mono" style="font-size:17px">${f.a.num}</b>` },
        { titulo: 'Titular', valor: f => esc(f.v.titular.nombre) },
        { titulo: 'Destino', valor: f => esc(parada(f.v.ordenDestino)) },
        { titulo: 'Tarifa', valor: f => esc(tarifa(f.a.idTarifa)) },
        { titulo: 'Folio', valor: f => `<span class="mono">${esc(f.v.folio)}</span>` },
        { titulo: 'Estado', valor: f => chip(f.a) }
      ],
      filas,
      buscar: (f, s) => f.v.titular.nombre.toLowerCase().includes(s) || f.v.folio.toLowerCase().includes(s) || String(f.a.num) === s,
      vacio: 'Nadie sube en esta escala.'
    });
    zona.replaceChildren(cabeza, t);
  }
}
