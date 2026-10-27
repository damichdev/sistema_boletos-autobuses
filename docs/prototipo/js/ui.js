// Piezas de interfaz compartidas por todas las pantallas.

export const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

export function h(html) {
  const t = document.createElement('template');
  t.innerHTML = html.trim();
  return t.content.firstElementChild;
}

export const dinero = n => '$' + Number(n).toLocaleString('es-MX', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
export const hora = fechaSalida => fechaSalida.slice(11, 16);
export const fechaCorta = fechaSalida => new Date(fechaSalida).toLocaleDateString('es-MX', { weekday: 'short', day: 'numeric', month: 'short' });

export function boton(texto, alClic, clase = '') {
  const b = h(`<button type="button" class="btn ${clase}">${texto}</button>`);
  b.onclick = alClic;
  return b;
}

// Ventana con barra de título, contador de paso opcional, cuerpo y pie.
export function ventana({ titulo, paso, total, cuerpo, pie = [], alCerrar }) {
  const v = h(`<section class="ventana" role="dialog" aria-label="${esc(titulo)}">
    <div class="ventana-titulo"><h2>${esc(titulo)}</h2>${paso ? `<span class="paso">${paso} / ${total}</span>` : ''}</div>
    <div class="ventana-cuerpo"></div>
    <div class="ventana-pie"></div>
  </section>`);
  if (alCerrar) {
    const x = h('<button type="button" class="cerrar" aria-label="Cerrar">×</button>');
    x.onclick = alCerrar;
    v.querySelector('.ventana-titulo').append(x);
  }
  const c = v.querySelector('.ventana-cuerpo');
  typeof cuerpo === 'string' ? (c.innerHTML = cuerpo) : c.append(cuerpo);
  const p = v.querySelector('.ventana-pie');
  if (pie.length) p.append(...pie); else p.remove();
  return v;
}

export function abrirVentana(opciones) {
  const velo = h('<div class="velo"></div>');
  const cerrar = () => { velo.remove(); document.removeEventListener('keydown', alTeclear); };
  // Con ventanas encimadas, Esc cierra solo la de arriba.
  const alTeclear = e => { if (e.key === 'Escape' && velo === [...document.querySelectorAll('.velo')].pop()) cerrar(); };
  const v = ventana({ ...opciones, alCerrar: cerrar });
  if (opciones.ancho) v.style.width = `min(${opciones.ancho}px, 100%)`;
  velo.append(v);
  velo.addEventListener('mousedown', e => { if (e.target === velo) cerrar(); });
  document.addEventListener('keydown', alTeclear);
  document.body.append(velo);
  velo.querySelector('input, select, .btn')?.focus();
  return cerrar;
}

// Tabla con buscador. columnas: [{ titulo, valor: fila => html }]; acciones: fila => [botones].
export function tabla({ titulo, columnas, filas, buscar, acciones, extra = [], vacio = 'Sin registros' }) {
  const el = h(`<section class="tarjeta">
    <div class="tarjeta-cabeza"><h2>${esc(titulo)}</h2><div style="display:flex;gap:10px;align-items:center"></div></div>
    <div class="tabla-envoltura"><table class="tabla"><thead><tr>${columnas.map(c => `<th>${esc(c.titulo)}</th>`).join('')}${acciones ? '<th></th>' : ''}</tr></thead><tbody></tbody></table></div>
  </section>`);
  const zona = el.querySelector('.tarjeta-cabeza div');
  let texto = '';
  if (buscar) {
    const b = h('<input class="buscador" type="search" placeholder="Buscar…" aria-label="Buscar">');
    b.oninput = () => { texto = b.value.trim().toLowerCase(); pintar(); };
    zona.append(b);
  }
  zona.append(...extra);
  const cuerpo = el.querySelector('tbody');
  let datos = filas;
  function pintar() {
    const visibles = texto ? datos.filter(f => buscar(f, texto)) : datos;
    cuerpo.replaceChildren(...visibles.map(f => {
      const tr = h(`<table><tr>${columnas.map(c => `<td>${c.valor(f)}</td>`).join('')}</tr></table>`).querySelector('tr');
      if (acciones) {
        const td = document.createElement('td');
        const caja = h('<div class="acciones"></div>');
        caja.append(...acciones(f));
        td.append(caja);
        tr.append(td);
      }
      return tr;
    }));
    if (!visibles.length) cuerpo.append(h(`<table><tr><td class="vacio" colspan="${columnas.length + 1}">${esc(vacio)}</td></tr></table>`).querySelector('tr'));
  }
  pintar();
  el.actualizar = nuevas => { datos = nuevas; pintar(); };
  return el;
}

// Croquis del autobús visto desde arriba. Numera fila por fila, de izquierda a derecha.
export function croquis({ layout, capacidad, ocupados, elegidos, alElegir }) {
  const [izq, der] = layout.split('+').map(Number);
  const plantilla = `repeat(${izq}, 40px) 26px repeat(${der}, 40px)`;
  const el = h(`<div style="display:flex;flex-direction:column;gap:14px;align-items:center">
    <div class="autobus"><div class="frente" style="--izq:${izq};--der:${der}"><span class="asiento ocupado chofer" title="Chofer" aria-label="Asiento del chofer, ocupado"><svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="8"/><circle cx="12" cy="12" r="2"/><path d="M4 12h6M14 12h6M12 14v6"/></svg></span><span>Puerta</span></div><div class="filas" style="grid-template-columns:${plantilla};--izq:${izq};--der:${der}"></div></div>
    <div class="leyenda"><span><i></i>Libre</span><span><i class="ocupado"></i>Ocupado</span><span><i class="elegido"></i>Elegido</span></div>
  </div>`);
  const filas = el.querySelector('.filas');
  let num = 1;
  while (num <= capacidad) {
    for (let lado = 0; lado < 2; lado++) {
      for (let i = 0; i < (lado ? der : izq); i++) {
        if (num > capacidad) { filas.append(document.createElement('span')); continue; }
        const n = num++;
        const ocupado = ocupados.has(n);
        const b = h(`<button type="button" class="asiento ${ocupado ? 'ocupado' : ''} ${elegidos.has(n) ? 'elegido' : ''}" ${ocupado ? 'disabled' : ''} aria-pressed="${elegidos.has(n)}" aria-label="Asiento ${n}${ocupado ? ', ocupado' : ''}">${n}</button>`);
        b.onclick = () => {
          elegidos.has(n) ? elegidos.delete(n) : elegidos.add(n);
          b.classList.toggle('elegido');
          b.setAttribute('aria-pressed', elegidos.has(n));
          alElegir?.(elegidos);
        };
        filas.append(b);
      }
      if (!lado) filas.append(document.createElement('span'));
    }
  }
  return el;
}

let temporizador;
export function toast(texto, tipo = 'ok') {
  let t = document.getElementById('toast');
  if (!t) { t = h('<div id="toast" role="status" aria-live="polite"></div>'); document.body.append(t); }
  t.className = tipo;
  t.textContent = texto;
  t.hidden = false;
  clearTimeout(temporizador);
  temporizador = setTimeout(() => { t.hidden = true; }, 3500);
}
