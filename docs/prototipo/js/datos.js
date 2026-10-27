// Datos de ejemplo. Los viajes se generan alrededor de la hora actual para que siempre haya salidas "hoy".
import { fechaLocal, horaLocal } from './logica.js';

const terminales = [
  { id: 1, nombre: 'Tapachula' }, { id: 2, nombre: 'Huixtla' }, { id: 3, nombre: 'Pijijiapan' },
  { id: 4, nombre: 'Tonalá' }, { id: 5, nombre: 'Tuxtla Gutiérrez' }
];

function ruta(id, nombre, servicio, paradas) {
  const precios = [];
  for (let o = 0; o < paradas.length; o++)
    for (let d = o + 1; d < paradas.length; d++)
      precios.push({ ordenOrigen: o, ordenDestino: d, precio: Math.round((paradas[d][1] - paradas[o][1]) * 0.9 / 5) * 5 });
  return { id, nombre, servicio, precios,
    paradas: paradas.map(([idTerminal, km, min], orden) => ({ idTerminal, orden, km, min })) };
}

const rutas = [
  ruta(1, 'Costa: Tapachula – Tuxtla', 'ORDINARIO', [[1, 0, 0], [2, 40, 50], [3, 140, 130], [4, 215, 200], [5, 390, 360]]),
  ruta(2, 'Directo Tapachula – Tuxtla', 'DIRECTO', [[1, 0, 0], [5, 390, 330]])
];

const tarifas = [
  { id: 1, nombre: 'ADULTO', descuento: 0 }, { id: 2, nombre: 'ESTUDIANTE', descuento: 0.5 },
  { id: 3, nombre: 'INAPAM', descuento: 0.5 }, { id: 4, nombre: 'INFANTIL', descuento: 0.5 }
];

const unidades = [
  { id: 1, placa: 'CHS-1201', modelo: 'Irizar i6', anio: 2022, km: 182400, tipo: 'PRIMERA', layout: '2+2', capacidad: 40, enMantenimiento: false },
  { id: 2, placa: 'CHS-0703', modelo: 'Volvo 9800', anio: 2021, km: 240150, tipo: 'EJECUTIVO', layout: '2+1', capacidad: 30, enMantenimiento: false },
  { id: 3, placa: 'CHS-0315', modelo: 'Mercedes Multego', anio: 2019, km: 410900, tipo: 'ORDINARIO', layout: '2+2', capacidad: 40, enMantenimiento: true },
  { id: 4, placa: 'CHS-1488', modelo: 'Irizar i8', anio: 2024, km: 61200, tipo: 'PRIMERA', layout: '2+2', capacidad: 40, enMantenimiento: false }
];

const hoy = new Date();
const enDias = n => { const d = new Date(hoy); d.setDate(d.getDate() + n); return fechaLocal(d); };

const personal = [
  { id: 1, nombre: 'Roberto', apPaterno: 'Gómez', apMaterno: 'Ruiz', curp: 'GORR800312HCSMZB04', telefono: '9621234567', puesto: 'CONDUCTOR', licencia: 'CHS-E-55821', vigencia: enDias(400) },
  { id: 2, nombre: 'Luis', apPaterno: 'Martínez', apMaterno: 'Coutiño', curp: 'MACL850721HCSRTS09', telefono: '9627654321', puesto: 'CONDUCTOR', licencia: 'CHS-E-61207', vigencia: enDias(12) },
  { id: 3, nombre: 'Ernesto', apPaterno: 'Velázquez', apMaterno: 'Pérez', curp: 'VEPE790105HCSLRR02', telefono: '9625550101', puesto: 'CONDUCTOR', licencia: 'CHS-E-40018', vigencia: enDias(-20) },
  { id: 4, nombre: 'Michelle', apPaterno: 'Constantino', apMaterno: 'Méndez', curp: 'COMM000815MCSNNCA1', telefono: '9621112233', puesto: 'TAQUILLERO', licencia: null, vigencia: null },
  { id: 5, nombre: 'Andrea', apPaterno: 'López', apMaterno: 'Toledo', curp: 'LOTA950302MCSPLN07', telefono: '9643334455', puesto: 'TAQUILLERO', licencia: null, vigencia: null },
  { id: 6, nombre: 'Jorge', apPaterno: 'Hernández', apMaterno: 'Cruz', curp: 'HECJ880910HCSRRR05', telefono: '9625556677', puesto: 'DESPACHADOR', licencia: null, vigencia: null },
  { id: 7, nombre: 'Carmen', apPaterno: 'Ramos', apMaterno: 'Díaz', curp: 'RADC910427MCSMZR03', telefono: '9647778899', puesto: 'DESPACHADOR', licencia: null, vigencia: null },
  { id: 8, nombre: 'Ana', apPaterno: 'Morales', apMaterno: 'Santiago', curp: 'MOSA870618MCSRNN08', telefono: '9629990011', puesto: 'ADMINISTRADOR', licencia: null, vigencia: null },
  { id: 9, nombre: 'Pedro', apPaterno: 'Aguilar', apMaterno: 'Nájera', curp: 'AUNP760223HCSGJD06', telefono: '9621239876', puesto: 'MECANICO', licencia: null, vigencia: null }
];

const taquillas = [
  { id: 1, idTerminal: 1, identificador: 'TAP-01', serie: 'TAP1', rangoInicial: 1, rangoFinal: 5000, foliosUsados: 0, activo: true },
  { id: 2, idTerminal: 2, identificador: 'HUI-01', serie: 'HUI1', rangoInicial: 1, rangoFinal: 3000, foliosUsados: 0, activo: true },
  { id: 3, idTerminal: 1, identificador: 'TAP-02', serie: 'TAP2', rangoInicial: 1, rangoFinal: 5000, foliosUsados: 0, activo: true }
];

const cuentas = [
  // Cuentas nominales: una por persona, con su sede base. La ventanilla se elige al abrir turno.
  { id: 1, idPersonal: 8, rol: 'ADMINISTRADOR', idTerminal: 1, username: 'a.morales', password: 'zaca2026', activo: true },
  { id: 2, idPersonal: 6, rol: 'DESPACHADOR', idTerminal: 1, username: 'j.hernandez', password: 'zaca2026', activo: true },
  { id: 3, idPersonal: 7, rol: 'DESPACHADOR', idTerminal: 2, username: 'c.ramos', password: 'zaca2026', activo: true },
  { id: 4, idPersonal: 4, rol: 'TAQUILLERO', idTerminal: 1, username: 'm.constantino', password: 'zaca2026', activo: true },
  { id: 5, idPersonal: 5, rol: 'TAQUILLERO', idTerminal: 2, username: 'a.lopez', password: 'zaca2026', activo: true }
];

// Salidas en medias horas alrededor de ahora: una ya salió y el resto está por salir.
function salida(minutos) {
  const d = new Date(hoy);
  d.setMinutes(Math.round((d.getMinutes() + minutos) / 30) * 30, 0, 0);
  return `${fechaLocal(d)}T${horaLocal(d)}`;
}

const viajes = [
  { id: 1, idRuta: 1, fechaSalida: salida(-60), idUnidad: 2, idConductor: 1, estado: 'EN RUTA' },
  { id: 2, idRuta: 1, fechaSalida: salida(60), idUnidad: 1, idConductor: 1, estado: 'PROGRAMADO' },
  { id: 3, idRuta: 2, fechaSalida: salida(120), idUnidad: 4, idConductor: 2, estado: 'PROGRAMADO' },
  { id: 4, idRuta: 1, fechaSalida: salida(210), idUnidad: 2, idConductor: 2, estado: 'PROGRAMADO' }
];

// Horario fijo de hoy y mañana para que siempre haya salidas, sin importar a qué hora se abra el prototipo.
const HORARIO = ['06:00', '07:30', '09:00', '10:30', '12:00', '13:30', '15:00', '16:30', '18:00', '19:30', '21:00'];
for (const dias of [0, 1]) {
  HORARIO.forEach((h, i) => viajes.push({ id: viajes.length + 1, idRuta: i % 3 === 1 ? 2 : 1, fechaSalida: `${enDias(dias)}T${h}`,
    idUnidad: [1, 4, 2][i % 3], idConductor: [1, 2][i % 2], estado: 'PROGRAMADO' }));
}

const titulares = [
  ['María López Pérez', 'LOPM900514MCSPRR01'], ['José Ramírez Cruz', 'INE 1234567890'], ['Lucía Hernández Gómez', 'HEGL020101MCSRMC09'],
  ['Familia Torres', 'INE 9988776655'], ['Daniel Ortiz Solís', 'OISD991212HCSRLN03'], ['Rosa Pérez Vázquez', 'INAPAM 445566'],
  ['Carlos Mendoza León', 'MELC870303HCSNNR02'], ['Fernanda Ruiz Aguilar', 'INE 5566778899']
];

// [viaje, taquilla, ordenOrigen, ordenDestino, asientos [num, idTarifa], abordados]
const ventasBase = [
  [1, 1, 0, 4, [[3, 1], [4, 1]], true], [1, 1, 0, 2, [[10, 1]], false],
  [2, 1, 0, 4, [[1, 1], [2, 3]], false], [2, 1, 0, 1, [[5, 1]], false], [2, 2, 1, 4, [[5, 2]], false],
  [2, 1, 0, 4, [[11, 1], [12, 1], [15, 4]], false], [2, 1, 0, 3, [[20, 1]], false], [2, 2, 1, 4, [[21, 1], [22, 1]], false],
  [3, 1, 0, 1, [[7, 1], [8, 3]], false], [4, 1, 0, 4, [[9, 1]], false]
];

const ventas = ventasBase.map(([idViaje, idTaquilla, o, d, asientos, abordado], i) => {
  const r = rutas.find(x => x.id === viajes.find(v => v.id === idViaje).idRuta);
  const base = r.precios.find(p => p.ordenOrigen === o && p.ordenDestino === d).precio;
  const t = taquillas.find(x => x.id === idTaquilla);
  const folio = `${t.serie}-${String(t.rangoInicial + t.foliosUsados++).padStart(6, '0')}`;
  const filas = asientos.map(([num, idTarifa]) => ({ num, idTarifa, precio: base * (1 - tarifas.find(x => x.id === idTarifa).descuento), abordado }));
  const total = filas.reduce((s, a) => s + a.precio, 0);
  const [nombre, identificacion] = titulares[i % titulares.length];
  return { id: i + 1, folio, idTaquilla, idViaje, titular: { nombre, identificacion }, ordenOrigen: o, ordenDestino: d,
    asientos: filas, total, recibido: Math.ceil(total / 100) * 100, estado: 'VIGENTE', fecha: hoy.toISOString(),
    reemplazaA: null, escaneadoEn: abordado ? hoy.toISOString() : null, idCuenta: idTaquilla === 1 ? 4 : 5 };
});

export const semilla = { terminales, rutas, tarifas, unidades, personal, taquillas, cuentas, viajes, ventas, turnos: [], bitacora: [] };
