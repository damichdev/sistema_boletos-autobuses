import { exigirRol, pintarBarra } from '../sesion.js';
import { cargar, guardar } from '../estado.js';
import { validarQR } from '../logica.js';
import { h, esc } from '../ui.js';
import { viajesDeEscala, selectorViaje } from './despacho-comun.js';

const cuenta = await exigirRol('DESPACHADOR');
pintarBarra(cuenta);

const app = document.getElementById('app');
const lista = viajesDeEscala(cargar(), cuenta.idTerminal);
app.append(h('<div class="encabezado"><div><h1>Abordaje</h1><p>Escanee el código QR del boleto en la puerta del autobús.</p></div></div>'));

if (!lista.length) {
  app.append(h('<div class="tarjeta vacio">No hay viajes que salgan hoy de esta terminal.</div>'));
} else {
  let viaje = null;
  const { el, actual } = selectorViaje(lista, x => { viaje = x; });
  viaje = actual;

  const zona = h(`<div class="columnas" style="display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1fr);gap:20px;margin-top:16px">
    <section class="tarjeta" style="padding:18px;display:flex;flex-direction:column;gap:14px">
      <div id="lector" style="width:100%;min-height:260px;background:var(--barra);border-radius:8px;display:grid;place-items:center;color:#9AA1AB;overflow:hidden"></div>
      <form id="manual" class="rejilla" style="grid-template-columns:1fr auto;align-items:end">
        <div class="campo"><label for="folio">Teclear folio</label><input id="folio" class="entrada mono" placeholder="TAP1-000001" autocomplete="off"></div>
        <button class="btn grande" type="submit">Validar</button>
      </form>
    </section>
    <section id="resultado" class="tarjeta" style="display:grid;place-items:center;text-align:center;padding:28px;min-height:360px"></section>
  </div>`);
  app.append(el, zona);
  const resultado = zona.querySelector('#resultado');
  const enEspera = () => { resultado.style.background = ''; resultado.style.color = ''; resultado.innerHTML = '<div class="sec" style="font-size:18px">Esperando boleto…</div>'; };
  enEspera();

  let ocupado = false;
  let regreso;
  function validar(folio) {
    if (ocupado || !folio) return;
    ocupado = true;
    const estado = cargar();
    const r = validarQR(estado, folio.trim().toUpperCase(), viaje.viaje.id, cuenta.idTerminal, new Date(), cuenta.id);
    guardar(estado);
    const [fondo, texto, icono, titulo, detalle] = {
      OK: ['var(--ok)', '#fff', '✓', 'Puede abordar',
        `${r.venta?.asientos.length} pasajero${r.venta?.asientos.length > 1 ? 's' : ''} · asientos ${r.venta?.asientos.map(a => a.num).join(', ')}`],
      YA_ESCANEADO: ['var(--aviso-fuerte)', '#2B1E00', '!', 'QR ya escaneado', `Se escaneó a las ${r.hora}. Asientos ${r.venta?.asientos.map(a => a.num).join(', ')}`],
      INVALIDO: ['var(--error)', '#fff', '✕', 'No válido', r.motivo]
    }[r.resultado];
    resultado.style.background = fondo;
    resultado.style.color = texto;
    resultado.innerHTML = `<div><div style="font-size:72px;line-height:1;font-weight:700">${icono}</div>
      <div style="font-size:34px;font-weight:700;margin-top:10px">${titulo}</div>
      <div style="font-size:19px;margin-top:8px">${esc(detalle)}</div>
      ${r.venta ? `<div class="mono" style="margin-top:14px;opacity:.85">${esc(r.venta.folio)} · ${esc(r.venta.titular.nombre)}</div>` : ''}</div>`;
    clearTimeout(regreso);
    regreso = setTimeout(() => { enEspera(); ocupado = false; }, 3000);
  }

  zona.querySelector('#manual').onsubmit = e => {
    e.preventDefault();
    const campo = zona.querySelector('#folio');
    ocupado = false;
    validar(campo.value);
    campo.value = '';
  };

  const lector = zona.querySelector('#lector');
  if (window.Html5Qrcode) {
    const qr = new window.Html5Qrcode('lector');
    qr.start({ facingMode: 'environment' }, { fps: 10, qrbox: 220 }, texto => validar(texto), () => {})
      .catch(() => { lector.textContent = 'Cámara no disponible. Use el campo "Teclear folio".'; });
  } else {
    lector.textContent = 'Lector QR no cargado. Use el campo "Teclear folio".';
  }
}
