// Reglas de negocio puras: sin DOM, probadas con `npm test`.

const ACTIVOS = ['PROGRAMADO', 'ABORDANDO'];

const porId = (lista, id) => lista.find(x => x.id === id);
const fechaLocal = d => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
const horaLocal = d => `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
const redondear = n => Math.round(n * 100) / 100;

export function precioTramo(ruta, ordenOrigen, ordenDestino) {
  if (ordenOrigen >= ordenDestino) return null;
  const p = ruta.precios.find(x => x.ordenOrigen === ordenOrigen && x.ordenDestino === ordenDestino);
  return p ? p.precio : null;
}

export function ordenEnRuta(ruta, idTerminal) {
  const p = ruta.paradas.find(x => x.idTerminal === idTerminal);
  return p ? p.orden : null;
}

// Tramos semiabiertos [o, d): quien baja en una parada y quien sube en ella no chocan.
export function asientosOcupados(estado, idViaje, ordenOrigen, ordenDestino) {
  const ocupados = new Set();
  for (const v of estado.ventas) {
    if (v.idViaje !== idViaje || v.estado !== 'VIGENTE') continue;
    if (v.ordenOrigen < ordenDestino && ordenOrigen < v.ordenDestino) v.asientos.forEach(a => ocupados.add(a.num));
  }
  return ocupados;
}

export function buscarViajes(estado, idTerminalOrigen, idTerminalDestino, fecha) {
  const resultado = [];
  for (const ruta of estado.rutas) {
    const o = ordenEnRuta(ruta, idTerminalOrigen);
    const d = ordenEnRuta(ruta, idTerminalDestino);
    if (o === null || d === null || o >= d) continue;
    for (const viaje of estado.viajes) {
      if (viaje.idRuta !== ruta.id || !viaje.fechaSalida.startsWith(fecha) || !ACTIVOS.includes(viaje.estado)) continue;
      const unidad = porId(estado.unidades, viaje.idUnidad);
      resultado.push({ viaje, ruta, ordenOrigen: o, ordenDestino: d, precio: precioTramo(ruta, o, d),
        libres: unidad.capacidad - asientosOcupados(estado, viaje.id, o, d).size });
    }
  }
  return resultado.sort((a, b) => a.viaje.fechaSalida.localeCompare(b.viaje.fechaSalida));
}

export function calcularTotal(asientos) {
  return redondear(asientos.reduce((s, a) => s + a.precioBase * (1 - a.descuento), 0));
}

export function calcularCambio(total, recibido) {
  return recibido < total ? null : redondear(recibido - total);
}

export function confirmarVenta(estado, borrador, ahora) {
  const viaje = porId(estado.viajes, borrador.idViaje);
  if (!ACTIVOS.includes(viaje.estado) || ahora >= new Date(viaje.fechaSalida)) throw new Error('VIAJE_CERRADO');
  const ocupados = asientosOcupados(estado, borrador.idViaje, borrador.ordenOrigen, borrador.ordenDestino);
  if (borrador.asientos.some(a => ocupados.has(a.num))) throw new Error('ASIENTO_OCUPADO');
  const taquilla = porId(estado.taquillas, borrador.idTaquilla);
  if (taquilla.foliosUsados >= taquilla.rangoFinal - taquilla.rangoInicial + 1) throw new Error('SIN_FOLIOS');
  const numero = taquilla.rangoInicial + taquilla.foliosUsados;
  taquilla.foliosUsados++;
  const venta = {
    id: Math.max(0, ...estado.ventas.map(v => v.id)) + 1,
    folio: `${taquilla.serie}-${String(numero).padStart(6, '0')}`,
    idTaquilla: borrador.idTaquilla, idViaje: borrador.idViaje, titular: borrador.titular,
    ordenOrigen: borrador.ordenOrigen, ordenDestino: borrador.ordenDestino,
    asientos: borrador.asientos.map(a => ({ ...a, abordado: false })),
    total: borrador.total, recibido: borrador.recibido, estado: 'VIGENTE',
    fecha: ahora.toISOString(), reemplazaA: borrador.reemplazaA ?? null, escaneadoEn: null, idCuenta: borrador.idCuenta ?? null
  };
  estado.ventas.push(venta);
  registrar(estado, venta.idCuenta, 'VENTA', `Folio ${venta.folio} · asientos ${venta.asientos.map(a => a.num).join(', ')} · $${venta.total}`, ahora);
  return venta;
}

export function puedeCancelar(estado, venta, ahora) {
  const viaje = porId(estado.viajes, venta.idViaje);
  return venta.estado === 'VIGENTE' && ahora < new Date(viaje.fechaSalida);
}

export function cancelarVenta(estado, idVenta, ahora, idCuenta = null) {
  const venta = porId(estado.ventas, idVenta);
  if (!venta || !puedeCancelar(estado, venta, ahora)) throw new Error('NO_CANCELABLE');
  venta.estado = 'CANCELADA';
  venta.canceladaPor = idCuenta;
  registrar(estado, idCuenta, 'CANCELACION', `Folio ${venta.folio} · devolución $${venta.total}`, ahora);
  return venta;
}

export function validarQR(estado, folio, idViaje, idTerminal, ahora, idCuenta = null) {
  const venta = estado.ventas.find(v => v.folio === folio);
  if (!venta) return { resultado: 'INVALIDO', motivo: 'Folio no encontrado' };
  if (venta.estado !== 'VIGENTE') return { resultado: 'INVALIDO', motivo: 'Boleto cancelado', venta };
  if (venta.idViaje !== idViaje) return { resultado: 'INVALIDO', motivo: 'Boleto de otro viaje', venta };
  const ruta = porId(estado.rutas, porId(estado.viajes, idViaje).idRuta);
  if (ordenEnRuta(ruta, idTerminal) !== venta.ordenOrigen) return { resultado: 'INVALIDO', motivo: 'No sube en esta terminal', venta };
  if (venta.escaneadoEn) return { resultado: 'YA_ESCANEADO', venta, hora: horaLocal(new Date(venta.escaneadoEn)) };
  venta.escaneadoEn = ahora.toISOString();
  venta.asientos.forEach(a => { a.abordado = true; });
  registrar(estado, idCuenta, 'ABORDAJE', `Folio ${venta.folio} · asientos ${venta.asientos.map(a => a.num).join(', ')}`, ahora);
  return { resultado: 'OK', venta };
}

export function validarAsignacion(estado, idUnidad, idConductor, fechaSalida) {
  const motivos = [];
  const unidad = porId(estado.unidades, idUnidad);
  const persona = porId(estado.personal, idConductor);
  if (unidad && !semaforo(estado, unidad.placa).aptoParaViajar) motivos.push(`Unidad no apta: ${semaforo(estado, unidad.placa).estatus.toLowerCase()}`);
  if (persona && persona.puesto !== 'CONDUCTOR') motivos.push('El empleado no es CONDUCTOR');
  if (persona && (!persona.vigencia || persona.vigencia < fechaSalida.slice(0, 10))) motivos.push('Licencia vencida');
  return motivos;
}

// Equivale a v_semaforo_unidades (taller_db por FDW). Una placa sin registro en taller no se bloquea.
export function semaforo(estado, placa) {
  const bus = estado.busesTaller.find(b => b.placa === placa);
  return { placa, estatus: bus ? porId(estado.estadosTaller, bus.idEstado).nombre : 'SIN REGISTRO EN TALLER', aptoParaViajar: !bus || bus.idEstado === 1 };
}

export const viajesPendientesDe = (estado, placa) => estado.viajes.filter(v => ACTIVOS.includes(v.estado) && porId(estado.unidades, v.idUnidad).placa === placa);

// Ingreso físico: deja el bus en taller (2 preventivo, 3 correctivo) y abre su orden. Costo = estimado hasta cerrarla.
// Los mecánicos son personal con puesto MECANICO (se dan de alta en Personal).
export const mecanicos = estado => estado.personal.filter(p => p.puesto === 'MECANICO');

export function abrirOrden(estado, { placa, idMecanico, idEstado, fechaIngreso, diagnostico, costo, observaciones = null }, idCuenta, ahora) {
  if (estado.ordenes.some(o => o.placa === placa && !o.fechaSalida)) throw new Error('ORDEN_ABIERTA');
  if (![2, 3].includes(idEstado)) throw new Error('ESTADO_INVALIDO');
  if (porId(estado.personal, idMecanico)?.puesto !== 'MECANICO') throw new Error('MECANICO_INVALIDO');
  if (!(costo >= 0)) throw new Error('COSTO_INVALIDO');
  let bus = estado.busesTaller.find(b => b.placa === placa);
  if (!bus) estado.busesTaller.push(bus = { placa, idEstado: 1, ultimaFechaIngreso: null, observaciones: null });
  bus.idEstado = idEstado;
  bus.ultimaFechaIngreso = fechaIngreso;
  if (observaciones) bus.observaciones = observaciones;
  const orden = { id: Math.max(0, ...estado.ordenes.map(o => o.id)) + 1, placa, idMecanico, fechaIngreso, fechaSalida: null, diagnostico, costo };
  estado.ordenes.push(orden);
  registrar(estado, idCuenta, 'INGRESO_TALLER', `Orden ${orden.id} · ${placa}`, ahora);
  return orden;
}

// Al cerrar, el bus vuelve a OPERATIVO (1) o se da de baja (4).
export function cerrarOrden(estado, idOrden, { fechaSalida, diagnostico, costo, idEstado }, idCuenta, ahora) {
  const orden = porId(estado.ordenes, idOrden);
  if (!orden || orden.fechaSalida) throw new Error('ORDEN_CERRADA');
  if (fechaSalida < orden.fechaIngreso) throw new Error('SALIDA_ANTES_DE_INGRESO');
  if (!(costo >= 0)) throw new Error('COSTO_INVALIDO');
  Object.assign(orden, { fechaSalida, diagnostico, costo });
  estado.busesTaller.find(b => b.placa === orden.placa).idEstado = idEstado;
  registrar(estado, idCuenta, 'SALIDA_TALLER', `Orden ${orden.id} · ${orden.placa} · $${costo}`, ahora);
  return orden;
}

// Auditoría: solo órdenes cerradas suman gasto; las abiertas llevan un costo estimado.
export function resumenTaller(estado) {
  const cerradas = estado.ordenes.filter(o => o.fechaSalida);
  const suma = os => os.reduce((s, o) => s + o.costo, 0);
  const dias = os => os.length ? os.reduce((s, o) => s + (new Date(o.fechaSalida) - new Date(o.fechaIngreso)) / 864e5, 0) / os.length : 0;
  const agrupar = (clave, lista) => lista.map(x => { const os = cerradas.filter(o => o[clave[0]] === x[clave[1]]); return { ...x, ordenes: os.length, costo: suma(os), dias: dias(os) }; });
  return {
    gasto: suma(cerradas), abiertas: estado.ordenes.length - cerradas.length, dias: dias(cerradas), cerradas: cerradas.length,
    porMecanico: agrupar(['idMecanico', 'id'], mecanicos(estado)),
    porBus: agrupar(['placa', 'placa'], estado.busesTaller)
  };
}

export function licenciaPorVencer(persona, hoy, dias = 30) {
  if (!persona.vigencia) return false;
  const limite = new Date(hoy);
  limite.setDate(limite.getDate() + dias);
  return persona.vigencia >= fechaLocal(hoy) && persona.vigencia <= fechaLocal(limite);
}

export function rutaEnUso(estado, idRuta) {
  return estado.viajes.some(v => v.idRuta === idRuta);
}

// Con folios emitidos, mover la serie o el inicio repetiría números de ticket.
export function erroresTaquilla(original, cambios) {
  const errores = {};
  const emitidos = original.foliosUsados > 0;
  if (emitidos && cambios.serie !== original.serie) errores.serie = 'No se puede cambiar: ya hay folios emitidos';
  if (emitidos && cambios.rangoInicial !== original.rangoInicial) errores.rangoInicial = 'No se puede cambiar: ya hay folios emitidos';
  if (cambios.rangoFinal < cambios.rangoInicial) errores.rangoFinal = 'Debe ser mayor o igual al inicial';
  else if (cambios.rangoFinal < original.rangoInicial + original.foliosUsados - 1) errores.rangoFinal = `Debe cubrir los ${original.foliosUsados} folios ya emitidos`;
  return errores;
}

// Asientos distintos ocupados en cada tramo entre paradas consecutivas.
export function ocupacionPorTramo(estado, idViaje) {
  const viaje = porId(estado.viajes, idViaje);
  const ruta = porId(estado.rutas, viaje.idRuta);
  const capacidad = porId(estado.unidades, viaje.idUnidad).capacidad;
  return ruta.paradas.slice(0, -1).map((p, i) => ({
    ordenOrigen: i, ordenDestino: i + 1, ocupados: asientosOcupados(estado, idViaje, i, i + 1).size, capacidad
  }));
}

export function movimientoPorParada(estado, idViaje) {
  const ruta = porId(estado.rutas, porId(estado.viajes, idViaje).idRuta);
  const ventas = estado.ventas.filter(v => v.idViaje === idViaje && v.estado === 'VIGENTE');
  return ruta.paradas.map(p => ({
    orden: p.orden,
    suben: ventas.filter(v => v.ordenOrigen === p.orden).reduce((s, v) => s + v.asientos.length, 0),
    bajan: ventas.filter(v => v.ordenDestino === p.orden).reduce((s, v) => s + v.asientos.length, 0)
  }));
}

// Un chofer con viajes pendientes dejaría salidas sin conductor; borrar la propia cuenta cerraría la sesión en uso.
export function motivosNoEliminar(estado, idPersona, idCuentaActual) {
  const motivos = [];
  if (estado.viajes.some(v => v.idConductor === idPersona && ['PROGRAMADO', 'ABORDANDO', 'EN RUTA'].includes(v.estado)))
    motivos.push('Tiene viajes programados o en ruta como chofer');
  if (estado.cuentas.some(c => c.id === idCuentaActual && c.idPersonal === idPersona)) motivos.push('Es la persona con la sesión abierta');
  return motivos;
}

export function eliminarPersona(estado, idPersona) {
  estado.personal = estado.personal.filter(p => p.id !== idPersona);
  estado.cuentas = estado.cuentas.filter(c => c.idPersonal !== idPersona);
}

// --- Cuentas nominales, turnos y bitácora ---

export const nombreCompleto = p => [p.nombre, p.apPaterno, p.apMaterno].filter(Boolean).join(' ');

// Cada movimiento queda a nombre de la persona física de la cuenta que lo hizo.
export function registrar(estado, idCuenta, accion, detalle, ahora) {
  if (idCuenta == null) return;
  const cuenta = porId(estado.cuentas, idCuenta);
  const persona = cuenta && porId(estado.personal, cuenta.idPersonal);
  estado.bitacora ??= [];
  estado.bitacora.push({ id: estado.bitacora.length + 1, fecha: ahora.toISOString(), idCuenta,
    usuario: cuenta?.username ?? null, persona: persona ? nombreCompleto(persona) : null, accion, detalle });
}

// Usuario nominal: inicial del nombre + apellido paterno, sin acentos (m.constantino, m.constantino2…).
export function generarUsuario(estado, persona) {
  const base = `${persona.nombre[0]}.${persona.apPaterno}`.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().replace(/[^a-z.]/g, '');
  let usuario = base;
  for (let n = 2; estado.cuentas.some(c => c.username === usuario); n++) usuario = base + n;
  return usuario;
}

export function motivoSinAcceso(cuenta) {
  return cuenta.activo === false ? 'La cuenta está inactiva. Consulte al administrador.' : null;
}

export function turnoAbierto(estado, idCuenta) {
  return (estado.turnos ?? []).find(t => t.idCuenta === idCuenta && !t.fin) ?? null;
}

export function taquillasDisponibles(estado, idTerminal) {
  const ocupadas = new Set((estado.turnos ?? []).filter(t => !t.fin).map(t => t.idTaquilla));
  return estado.taquillas.filter(t => t.activo && t.idTerminal === idTerminal && !ocupadas.has(t.id));
}

export function abrirTurno(estado, idCuenta, idTaquilla, ahora) {
  estado.turnos ??= [];
  if (estado.turnos.some(t => t.idTaquilla === idTaquilla && !t.fin && t.idCuenta !== idCuenta)) throw new Error('TAQUILLA_OCUPADA');
  if (turnoAbierto(estado, idCuenta)) cerrarTurno(estado, idCuenta, ahora);
  const turno = { id: estado.turnos.length + 1, idCuenta, idTaquilla, inicio: ahora.toISOString(), fin: null };
  estado.turnos.push(turno);
  registrar(estado, idCuenta, 'APERTURA_TURNO', `Taquilla ${porId(estado.taquillas, idTaquilla).identificador ?? idTaquilla}`, ahora);
  return turno;
}

export function cerrarTurno(estado, idCuenta, ahora) {
  const turno = turnoAbierto(estado, idCuenta);
  if (!turno) return null;
  turno.fin = ahora.toISOString();
  registrar(estado, idCuenta, 'CIERRE_TURNO', `Taquilla ${porId(estado.taquillas, turno.idTaquilla).identificador ?? turno.idTaquilla}`, ahora);
  return turno;
}

export { fechaLocal, horaLocal, porId };
