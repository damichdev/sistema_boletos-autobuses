import { exigirRol, exigirTurno, pintarBarra } from '../sesion.js';
import { cargar, guardar } from '../estado.js';
import { puedeCancelar, cancelarVenta, porId } from '../logica.js';
import { h, esc, tabla, boton, abrirVentana, toast, dinero, hora, fechaCorta } from '../ui.js';
import { ticket } from '../ticket.js';

const cuenta = await exigirRol('TAQUILLERO');
await exigirTurno(cuenta);
pintarBarra(cuenta);

const app = document.getElementById('app');
let estado = cargar();

const viajeDe = v => porId(estado.viajes, v.idViaje);
function tramo(v) {
  const ruta = porId(estado.rutas, viajeDe(v).idRuta);
  const t = o => porId(estado.terminales, ruta.paradas[o].idTerminal).nombre;
  return `${t(v.ordenOrigen)} → ${t(v.ordenDestino)}`;
}
const ordenadas = () => [...estado.ventas].sort((a, b) => b.id - a.id);

function chipEstado(v) {
  if (v.estado === 'CANCELADA') return '<span class="chip error">Cancelado</span>';
  if (v.asientos.every(a => a.abordado)) return '<span class="chip neutro">Abordado</span>';
  if (new Date(viajeDe(v).fechaSalida) <= new Date()) return '<span class="chip aviso">Ya salió</span>';
  return '<span class="chip ok">Vigente</span>';
}

function confirmarCancelacion(v) {
  const cuerpo = h(`<div class="rejilla">
    <p>Se cancelará el folio <b class="mono">${esc(v.folio)}</b> de <b>${esc(v.titular.nombre)}</b>. Los asientos <b class="mono">${v.asientos.map(a => a.num).join(', ')}</b> quedan libres para venderse de nuevo.</p>
    <div class="total"><span>Devolver al cliente</span><b>${dinero(v.total)}</b></div>
  </div>`);
  const cerrar = abrirVentana({ titulo: 'Cancelar boleto', cuerpo, pie: [
    boton('No, regresar', () => cerrar(), 'secundario'),
    boton('Cancelar y devolver', () => {
      estado = cargar();
      try {
        cancelarVenta(estado, v.id, new Date(), cuenta.id);
        guardar(estado);
        toast(`Folio ${v.folio} cancelado. Devuelva ${dinero(v.total)}.`);
      } catch {
        toast('El autobús ya salió; este boleto ya no se puede cancelar.', 'error');
      }
      cerrar();
      lista.actualizar(ordenadas());
    }, 'peligro')
  ] });
}

function confirmarCambio(v) {
  const cuerpo = h(`<p>El folio <b class="mono">${esc(v.folio)}</b> se cancela y se devuelven <b>${dinero(v.total)}</b>. Después se abre la venta con el mismo viaje, titular y asientos para que los ajuste y cobre el boleto nuevo.</p>`);
  const cerrar = abrirVentana({ titulo: 'Cambiar boleto', cuerpo, pie: [
    boton('No, regresar', () => cerrar(), 'secundario'),
    boton('Continuar al cambio', () => { sessionStorage.setItem('zaca-cambio', String(v.id)); location.href = '/taquilla/venta.html'; })
  ] });
}

function verTicket(v) {
  const cerrar = abrirVentana({ titulo: `Folio ${v.folio}`, cuerpo: ticket(estado, v), pie: [
    boton('Cerrar', () => cerrar(), 'secundario'), boton('Reimprimir', () => window.print())
  ] });
}

app.append(h(`<div class="encabezado"><div><h1>Boletos vendidos</h1><p>Busque por folio o titular en todas las taquillas. Solo se cancela o cambia antes de la salida del autobús.</p></div></div>`));

const lista = tabla({
  titulo: 'Boletos',
  columnas: [
    { titulo: 'Folio', valor: v => `<span class="mono">${esc(v.folio)}</span>${v.reemplazaA ? `<div class="sec chico">reemplaza ${esc(v.reemplazaA)}</div>` : ''}` },
    { titulo: 'Salida', valor: v => `<span class="mono">${hora(viajeDe(v).fechaSalida)}</span> <span class="sec chico">${fechaCorta(viajeDe(v).fechaSalida)}</span>` },
    { titulo: 'Tramo', valor: v => esc(tramo(v)) },
    { titulo: 'Titular', valor: v => esc(v.titular.nombre) },
    { titulo: 'Asientos', valor: v => `<span class="mono">${v.asientos.map(a => a.num).join(', ')}</span>` },
    { titulo: 'Total', valor: v => `<span class="mono">${dinero(v.total)}</span>` },
    { titulo: 'Vendió', valor: v => `<span class="mono chico">${esc(porId(estado.cuentas, v.idCuenta)?.username ?? '—')}</span>` },
    { titulo: 'Estado', valor: chipEstado }
  ],
  filas: ordenadas(),
  buscar: (v, t) => v.folio.toLowerCase().includes(t) || v.titular.nombre.toLowerCase().includes(t),
  acciones: v => {
    const activo = puedeCancelar(estado, v, new Date());
    const cambiar = boton('Cambiar', () => confirmarCambio(v), 'secundario chico');
    const cancelar = boton('Cancelar', () => confirmarCancelacion(v), 'peligro chico');
    cambiar.disabled = cancelar.disabled = !activo;
    return [boton('Ticket', () => verTicket(v), 'secundario chico'), cambiar, cancelar];
  },
  vacio: 'No hay boletos que coincidan con la búsqueda.'
});
app.append(lista);
