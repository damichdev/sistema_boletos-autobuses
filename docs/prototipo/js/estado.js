import { semilla } from './datos.js';
import { fechaLocal } from './logica.js';

const CLAVE = 'zaca';
// Subir VERSION cuando cambie la forma de la semilla, para que los navegadores con datos viejos se vuelvan a sembrar.
const VERSION = 7;

// Los viajes de ejemplo dependen de la hora en que se sembraron, así que se vuelven a sembrar cada día.
export function cargar() {
  try {
    const guardado = JSON.parse(localStorage.getItem(CLAVE));
    if (guardado && guardado.version === VERSION && guardado.sembradoEl === fechaLocal(new Date())) return guardado;
  } catch { /* datos corruptos: se vuelve a sembrar */ }
  return reiniciar();
}

export function guardar(estado) {
  try { localStorage.setItem(CLAVE, JSON.stringify(estado)); } catch { /* sin almacenamiento: el prototipo sigue en memoria */ }
}

export function reiniciar() {
  const estado = { ...structuredClone(semilla), version: VERSION, sembradoEl: fechaLocal(new Date()) };
  guardar(estado);
  return estado;
}
