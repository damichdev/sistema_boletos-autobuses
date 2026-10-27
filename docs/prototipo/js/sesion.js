import { cargar, guardar } from './estado.js';
import { porId, motivoSinAcceso, turnoAbierto, cerrarTurno } from './logica.js';
import { h, esc } from './ui.js';

const CLAVE = 'zaca-sesion';

export const INICIO = {
  ADMINISTRADOR: '/admin/rutas.html',
  DESPACHADOR: '/despacho/programar.html',
  TAQUILLERO: '/taquilla/turno.html'
};

const ICONOS = {
  venta: 'M4 7h16v3a2 2 0 0 0 0 4v3H4v-3a2 2 0 0 0 0-4zM14 7v10',
  boletos: 'M9 6h11M9 12h11M9 18h11M4 6h.01M4 12h.01M4 18h.01',
  programar: 'M3 5h18v16H3zM3 10h18M8 3v4M16 3v4M12 14v3h3',
  manifiesto: 'M9 3h6v4H9zM7 5H5v16h14V5h-2M8 12h8M8 16h5',
  abordaje: 'M4 4h6v6H4zM14 4h6v6h-6zM4 14h6v6H4zM14 14h3v3h-3zM18 18h2v2h-2z',
  rutas: 'M6 21a2 2 0 1 0 0-4 2 2 0 0 0 0 4zM18 7a2 2 0 1 0 0-4 2 2 0 0 0 0 4zM8 19h7a3.5 3.5 0 0 0 0-7H9a3.5 3.5 0 0 1 0-7h7',
  unidades: 'M5 3h14a1 1 0 0 1 1 1v12H4V4a1 1 0 0 1 1-1zM4 10h16M7 16v3M17 16v3M8 13h.01M16 13h.01',
  taquillas: 'M3 21h18M5 21V8l7-4 7 4v13M9 21v-5h6v5',
  personal: 'M9 11a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7zM2 21c0-4 3-6 7-6s7 2 7 6M16 4.5a3.5 3.5 0 0 1 0 7M18 15c2.5.6 4 2.5 4 6',
  cuentas: 'M3 5h18v14H3zM10 12a2 2 0 1 1-4 0 2 2 0 0 1 4 0zM14 10h4M14 14h4',
  reportes: 'M4 20V10M10 20V4M16 20v-7M22 20H2',
  salir: 'M9 4H5v16h4M16 8l4 4-4 4M20 12H9'
};
const icono = nombre => `<svg class="icono" viewBox="0 0 24 24" aria-hidden="true"><path d="${ICONOS[nombre]}"/></svg>`;

const MENU = {
  TAQUILLERO: [['Venta', '/taquilla/venta.html', 'venta'], ['Boletos vendidos', '/taquilla/boletos.html', 'boletos']],
  DESPACHADOR: [['Programar salida', '/despacho/programar.html', 'programar'], ['Manifiesto', '/despacho/manifiesto.html', 'manifiesto'], ['Abordaje', '/despacho/abordaje.html', 'abordaje']],
  ADMINISTRADOR: [['Rutas', '/admin/rutas.html', 'rutas'], ['Unidades', '/admin/unidades.html', 'unidades'], ['Taquillas', '/admin/taquillas.html', 'taquillas'],
    ['Personal', '/admin/personal.html', 'personal'], ['Cuentas', '/admin/cuentas.html', 'cuentas'], ['Reportes', '/admin/reportes.html', 'reportes']]
};

// Devuelve { cuenta } o { error }; una cuenta inactiva no entra aunque la contraseña sea correcta.
export function iniciarSesion(username, password) {
  const cuenta = cargar().cuentas.find(c => c.username === username.trim().toLowerCase() && c.password === password);
  if (!cuenta) return { error: 'Usuario o contraseña incorrectos.' };
  const motivo = motivoSinAcceso(cuenta);
  if (motivo) return { error: motivo };
  sessionStorage.setItem(CLAVE, String(cuenta.id));
  return { cuenta };
}

export function cuentaActual() {
  const id = Number(sessionStorage.getItem(CLAVE));
  return id ? porId(cargar().cuentas, id) ?? null : null;
}

// Si la cuenta no corresponde, redirige y deja la página detenida (la promesa nunca se resuelve).
export function exigirRol(...roles) {
  const cuenta = cuentaActual();
  const destino = !cuenta ? '/index.html' : !roles.includes(cuenta.rol) ? INICIO[cuenta.rol] : null;
  if (!destino) return Promise.resolve(cuenta);
  location.replace(destino);
  return new Promise(() => {});
}

// El taquillero solo trabaja con un turno abierto en una ventanilla de su sede.
export function exigirTurno(cuenta) {
  const turno = turnoAbierto(cargar(), cuenta.id);
  if (turno) return Promise.resolve(turno);
  location.replace('/taquilla/turno.html');
  return new Promise(() => {});
}

export function pintarBarra(cuenta) {
  const estado = cargar();
  const persona = porId(estado.personal, cuenta.idPersonal);
  const terminal = porId(estado.terminales, cuenta.idTerminal);
  const turno = turnoAbierto(estado, cuenta.id);
  const taquilla = turno ? porId(estado.taquillas, turno.idTaquilla) : null;
  const lugar = taquilla ? `Turno en ${taquilla.identificador} · ${terminal.nombre}`
    : `${{ ADMINISTRADOR: 'Oficina', DESPACHADOR: 'Andén', TAQUILLERO: 'Sin turno' }[cuenta.rol]} · ${terminal.nombre}`;
  const seccion = { TAQUILLERO: 'Taquilla', DESPACHADOR: 'Despacho', ADMINISTRADOR: 'Administración' }[cuenta.rol];
  const lateral = h(`<aside class="lateral">
    <span class="marca"><i></i><span>ZACA</span></span>
    <span class="seccion">${seccion}</span>
    <nav class="nav" aria-label="Principal">${MENU[cuenta.rol].map(([t, url, ic]) =>
      `<a href="${url}" title="${t}" class="${location.pathname === url ? 'activo' : ''}" ${location.pathname === url ? 'aria-current="page"' : ''}>${icono(ic)}<span>${t}</span></a>`).join('')}</nav>
    <span class="relleno"></span>
    <span class="nodo" title="Nodo Tapachula en línea"><i></i><span>Nodo Tapachula en línea</span></span>
    <span class="quien"><b>${esc(persona.nombre)} ${esc(persona.apPaterno)}</b><span class="mono">${esc(cuenta.username)}</span><span>${esc(lugar)}</span></span>
    <button class="salir" type="button" title="${turno ? 'Cerrar turno y salir' : 'Salir'}">${icono('salir')}<span>${turno ? 'Cerrar turno y salir' : 'Salir'}</span></button>
  </aside>`);
  lateral.querySelector('.salir').onclick = () => {
    const e = cargar();
    if (cerrarTurno(e, cuenta.id, new Date())) guardar(e);
    sessionStorage.removeItem(CLAVE);
    location.href = '/index.html';
  };
  document.body.classList.add('con-lateral');
  document.body.prepend(lateral);
}
