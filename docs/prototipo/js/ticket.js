import { porId, nombreCompleto } from './logica.js';
import { h, esc, dinero, hora, fechaCorta } from './ui.js';

// Ticket impreso: un solo QR (el folio) ampara todos los asientos de la venta.
export function ticket(estadoActual, venta) {
  const viaje = porId(estadoActual.viajes, venta.idViaje);
  const ruta = porId(estadoActual.rutas, viaje.idRuta);
  const u = porId(estadoActual.unidades, viaje.idUnidad);
  const nombre = o => porId(estadoActual.terminales, ruta.paradas[o].idTerminal).nombre;
  const el = h(`<div class="ticket">
    <div>
      <div class="marca-ticket">ZACA AUTOBUSES</div>
      <div>Folio <b>${esc(venta.folio)}</b></div>
      ${venta.reemplazaA ? `<div>Reemplaza al folio ${esc(venta.reemplazaA)}</div>` : ''}
      <div style="margin-top:8px"><b>${esc(nombre(venta.ordenOrigen))} → ${esc(nombre(venta.ordenDestino))}</b></div>
      <div>${fechaCorta(viaje.fechaSalida)} · ${hora(viaje.fechaSalida)} · Unidad ${esc(u.placa)}</div>
      <div>Titular: ${esc(venta.titular.nombre)}</div>
      ${venta.idCuenta && porId(estadoActual.cuentas, venta.idCuenta) ? `<div>Atendió: ${esc(nombreCompleto(porId(estadoActual.personal, porId(estadoActual.cuentas, venta.idCuenta).idPersonal)))}</div>` : ''}
      <div style="margin-top:8px">Asientos <span class="asientos-ticket">${venta.asientos.map(a => a.num).join(', ')}</span> (${venta.asientos.length} pasajero${venta.asientos.length > 1 ? 's' : ''})</div>
      <div>Total ${dinero(venta.total)} · Recibido ${dinero(venta.recibido)} · Cambio ${dinero(venta.recibido - venta.total)}</div>
      <div class="sec" style="margin-top:8px">Presente este código al abordar.</div>
    </div>
    <div class="qr"></div></div>`);
  if (window.qrcode) {
    const q = window.qrcode(0, 'M');
    q.addData(venta.folio);
    q.make();
    el.querySelector('.qr').innerHTML = q.createSvgTag({ cellSize: 4, margin: 0 });
  }
  return el;
}
