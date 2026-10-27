import { exigirRol, pintarBarra } from '../sesion.js';
import { cargar, guardar } from '../estado.js';
import { licenciaPorVencer, porId, fechaLocal, erroresTaquilla, motivosNoEliminar, eliminarPersona } from '../logica.js';
import { h, esc, tabla, boton, abrirVentana, toast } from '../ui.js';

const cuenta = await exigirRol('ADMINISTRADOR');
pintarBarra(cuenta);

const app = document.getElementById('app');
let estado = cargar();

const CURP = /^[A-Z]{4}[0-9]{6}[HMX][A-Z]{5}[A-Z0-9][0-9]$/;
const opcionesDe = (lista, texto) => lista.map(x => [x.id, texto(x)]);
const nombre = p => `${p.nombre} ${p.apPaterno} ${p.apMaterno}`;
const capital = s => s[0] + s.slice(1).toLowerCase();
const folios = (r, id) => erroresTaquilla(id ? porId(estado.taquillas, id) : { ...r, foliosUsados: 0 }, r);

function chipLicencia(p) {
  if (p.puesto !== 'CONDUCTOR') return '<span class="sec">—</span>';
  if (!p.vigencia || p.vigencia < fechaLocal(new Date())) return `<span class="chip error">Vencida ${esc(p.vigencia ?? '')}</span>`;
  if (licenciaPorVencer(p, new Date())) return `<span class="chip aviso">Vence ${esc(p.vigencia)}</span>`;
  return `<span class="chip ok">Vigente a ${esc(p.vigencia)}</span>`;
}

const CONFIG = {
  unidades: {
    titulo: 'Unidades', singular: 'unidad', descripcion: 'Autobuses de la flota con su acomodo de asientos.',
    columnas: [
      { titulo: 'Placa', valor: u => `<b class="mono">${esc(u.placa)}</b>` },
      { titulo: 'Modelo', valor: u => `${esc(u.modelo)} <span class="sec chico">${u.anio}</span>` },
      { titulo: 'Tipo', valor: u => esc(capital(u.tipo)) },
      { titulo: 'Acomodo', valor: u => `<span class="mono">${u.layout}</span> · ${u.capacidad} asientos` },
      { titulo: 'Kilometraje', valor: u => `<span class="mono">${u.km.toLocaleString('es-MX')}</span>` },
      { titulo: 'Estado', valor: u => u.enMantenimiento ? '<span class="chip aviso">En taller</span>' : '<span class="chip ok">Disponible</span>' }
    ],
    campos: () => [
      { clave: 'placa', etiqueta: 'Placa', requerido: true },
      { clave: 'modelo', etiqueta: 'Modelo', requerido: true },
      { clave: 'anio', etiqueta: 'Año', tipo: 'number', requerido: true, validar: v => v > 1900 && v <= new Date().getFullYear() + 1 || 'Año fuera de rango' },
      { clave: 'km', etiqueta: 'Kilometraje', tipo: 'number', requerido: true, validar: v => v >= 0 || 'No puede ser negativo' },
      { clave: 'tipo', etiqueta: 'Tipo de servicio', opciones: ['PRIMERA', 'EJECUTIVO', 'ORDINARIO'].map(x => [x, capital(x)]) },
      { clave: 'layout', etiqueta: 'Acomodo', opciones: ['2+2', '2+1', '1+1'].map(x => [x, x]) },
      { clave: 'capacidad', etiqueta: 'Capacidad', tipo: 'number', requerido: true, validar: v => v > 0 || 'Debe ser mayor que 0' }
    ],
    nuevo: () => ({ placa: '', modelo: '', anio: new Date().getFullYear(), km: 0, tipo: 'PRIMERA', layout: '2+2', capacidad: 40, enMantenimiento: false }),
    buscar: (u, t) => (u.placa + u.modelo).toLowerCase().includes(t)
  },
  taquillas: {
    titulo: 'Taquillas', singular: 'taquilla', descripcion: 'Cada ventanilla tiene su serie y rango de folios para que ningún ticket se repita.',
    columnas: [
      { titulo: 'Taquilla', valor: t => `<b class="mono">${esc(t.identificador)}</b>` },
      { titulo: 'Terminal', valor: t => esc(porId(estado.terminales, t.idTerminal).nombre) },
      { titulo: 'Serie', valor: t => `<span class="mono">${esc(t.serie)}</span>` },
      { titulo: 'Rango', valor: t => `<span class="mono">${t.rangoInicial} – ${t.rangoFinal}</span>` },
      { titulo: 'Folios usados', valor: t => `<span class="mono">${t.foliosUsados}</span> <span class="sec chico">de ${t.rangoFinal - t.rangoInicial + 1}</span>` },
      { titulo: 'Estado', valor: t => t.activo ? '<span class="chip ok">Activa</span>' : '<span class="chip neutro">Inactiva</span>' }
    ],
    campos: () => [
      { clave: 'idTerminal', etiqueta: 'Ciudad / terminal', opciones: opcionesDe(estado.terminales, x => x.nombre), numero: true, nuevaCiudad: true },
      { clave: 'identificador', etiqueta: 'Identificador', requerido: true },
      { clave: 'serie', etiqueta: 'Serie de folios', requerido: true, validar: (v, r, id) => estado.taquillas.some(t => t.serie === v && t.id !== id) ? 'Esa serie ya la usa otra taquilla' : folios(r, id).serie ?? true },
      { clave: 'rangoInicial', etiqueta: 'Folio inicial', tipo: 'number', requerido: true, validar: (v, r, id) => folios(r, id).rangoInicial ?? true },
      { clave: 'rangoFinal', etiqueta: 'Folio final', tipo: 'number', requerido: true, validar: (v, r, id) => folios(r, id).rangoFinal ?? true }
    ],
    nuevo: () => ({ idTerminal: 1, identificador: '', serie: '', rangoInicial: 1, rangoFinal: 5000, foliosUsados: 0, activo: true }),
    buscar: (t, s) => (t.identificador + t.serie).toLowerCase().includes(s)
  },
  personal: {
    titulo: 'Personal', singular: 'empleado', descripcion: 'Empleados de las terminales. A los choferes se les vigila la vigencia de la licencia.',
    columnas: [
      { titulo: 'Nombre', valor: p => esc(nombre(p)) },
      { titulo: 'Puesto', valor: p => esc(capital(p.puesto)) },
      { titulo: 'CURP', valor: p => `<span class="mono chico">${esc(p.curp)}</span>` },
      { titulo: 'Teléfono', valor: p => `<span class="mono">${esc(p.telefono ?? '')}</span>` },
      { titulo: 'Licencia', valor: chipLicencia }
    ],
    campos: () => [
      { clave: 'nombre', etiqueta: 'Nombre(s)', requerido: true },
      { clave: 'apPaterno', etiqueta: 'Apellido paterno', requerido: true },
      { clave: 'apMaterno', etiqueta: 'Apellido materno', requerido: true },
      { clave: 'curp', etiqueta: 'CURP', requerido: true, mayusculas: true, validar: v => CURP.test(v) || 'CURP con formato inválido' },
      { clave: 'telefono', etiqueta: 'Teléfono', validar: v => !v || /^[0-9]{10}$/.test(v) || 'Deben ser 10 dígitos' },
      { clave: 'puesto', etiqueta: 'Puesto', opciones: ['TAQUILLERO', 'DESPACHADOR', 'ADMINISTRADOR', 'CONDUCTOR', 'MECANICO'].map(x => [x, capital(x)]) },
      { clave: 'licencia', etiqueta: 'Licencia (choferes)', validar: (v, r) => r.puesto !== 'CONDUCTOR' || !!v || 'Obligatoria para choferes' },
      { clave: 'vigencia', etiqueta: 'Vigencia de licencia', tipo: 'date', validar: (v, r) => r.puesto !== 'CONDUCTOR' || !!v || 'Obligatoria para choferes' }
    ],
    nuevo: () => ({ nombre: '', apPaterno: '', apMaterno: '', curp: '', telefono: '', puesto: 'CONDUCTOR', licencia: '', vigencia: '' }),
    // Una sección por puesto, cada una con su propio botón para dar de alta.
    grupos: [
      { campo: 'puesto', valor: 'TAQUILLERO', titulo: 'Taquilla', boton: '+ Agregar taquillero' },
      { campo: 'puesto', valor: 'DESPACHADOR', titulo: 'Despacho', boton: '+ Agregar despachador' },
      { campo: 'puesto', valor: 'ADMINISTRADOR', titulo: 'Administración', boton: '+ Agregar administrador' },
      { campo: 'puesto', valor: 'CONDUCTOR', titulo: 'Choferes', boton: '+ Agregar chofer' },
      { campo: 'puesto', valor: 'MECANICO', titulo: 'Mecánicos', boton: '+ Agregar mecánico' }
    ],
    buscar: (p, t) => (nombre(p) + p.curp + (p.telefono ?? '')).toLowerCase().includes(t),
    tituloFila: p => nombre(p),
    detalleFila: p => [p.curp, p.telefono].filter(Boolean).join(' · ')
  }
};

export function catalogo({ coleccion, columnas, campos }) {
  const cfg = CONFIG[coleccion];
  app.append(h(`<div class="encabezado"><div><h1>${esc(cfg.titulo)}</h1><p>${esc(cfg.descripcion)}</p></div></div>`));
  const secciones = cfg.grupos ? paneles() : [(() => {
    const t = tabla({
      titulo: `${estado[coleccion].length} registros`, columnas, filas: estado[coleccion], buscar: cfg.buscar,
      acciones: r => [boton('Editar', () => editar(r), 'secundario chico')],
      extra: [boton(`+ Nueva ${cfg.singular}`, () => editar(null))]
    });
    t.recargar = () => t.actualizar(estado[coleccion]);
    app.append(t);
    return t;
  })()];

  // Igual que la venta: paneles de tamaño fijo, uno por sección; la lista se desplaza dentro de su panel.
  function paneles() {
    const rejilla = h('<div class="paneles-personal"></div>');
    app.append(rejilla);
    return cfg.grupos.map(g => {
      const panel = h(`<section class="panel panel-personal ${g.valor === 'CONDUCTOR' ? 'ancho-doble' : ''}">
        <div class="panel-titulo"><h2>${esc(g.titulo)}</h2></div>
        <input class="buscador" type="search" placeholder="Buscar en ${esc(g.titulo.toLowerCase())}…" aria-label="Buscar en ${esc(g.titulo)}">
        <ul class="lista-personas"></ul>
      </section>`);
      const agregar = boton(g.boton, () => editar(null, { [g.campo]: g.valor }));
      agregar.style.width = '100%';
      panel.append(agregar);
      const lista = panel.querySelector('ul');
      const buscador = panel.querySelector('.buscador');
      panel.recargar = () => {
        const todos = estado[coleccion].filter(r => r[g.campo] === g.valor);
        const t = buscador.value.trim().toLowerCase();
        const visibles = t ? todos.filter(r => cfg.buscar(r, t)) : todos;
        lista.replaceChildren(...visibles.map(r => {
          const li = h(`<li><div class="persona">
            <b>${esc(cfg.tituloFila(r))}</b>
            <span class="sec chico mono">${esc(cfg.detalleFila(r))}</span>
            ${g.valor === 'CONDUCTOR' ? chipLicencia(r) : ''}</div></li>`);
          li.append(boton('Editar', () => editar(r), 'secundario chico'), boton('Eliminar', () => eliminar(r), 'secundario chico'));
          return li;
        }));
        if (!visibles.length) lista.append(h(`<li class="vacio">${t ? 'Sin coincidencias.' : `Sin personal en ${esc(g.titulo.toLowerCase())}.`}</li>`));
      };
      buscador.oninput = panel.recargar;
      panel.recargar();
      rejilla.append(panel);
      return panel;
    });
  }

  function eliminar(persona) {
    estado = cargar();
    const motivos = motivosNoEliminar(estado, persona.id, cuenta.id);
    const tieneCuenta = estado.cuentas.some(c => c.idPersonal === persona.id);
    const cuerpo = motivos.length
      ? h(`<div class="rejilla"><p>No se puede eliminar a <b>${esc(nombre(persona))}</b>:</p><div class="alerta aviso">${motivos.map(esc).join('<br>')}</div>
          ${motivos[0].startsWith('Tiene viajes') ? '<p class="sec chico">Asigne otro chofer a esas salidas o espere a que terminen.</p>' : ''}</div>`)
      : h(`<div class="rejilla"><p>Se eliminará a <b>${esc(nombre(persona))}</b> (${esc(capital(persona.puesto).toLowerCase().replace('mecanico', 'mecánico'))}).</p>
          ${tieneCuenta ? '<div class="alerta aviso">También se eliminará su cuenta de usuario y ya no podrá entrar al sistema.</div>' : ''}
          <p class="sec chico">Esta acción no se puede deshacer.</p></div>`);
    const pie = [boton(motivos.length ? 'Entendido' : 'No, regresar', () => cerrar(), 'secundario')];
    if (!motivos.length) pie.push(boton('Eliminar', () => {
      eliminarPersona(estado, persona.id);
      guardar(estado);
      cerrar();
      toast(`${nombre(persona)} fue eliminado del personal.`);
      secciones.forEach(t => t.recargar());
    }, 'peligro'));
    const cerrar = abrirVentana({ titulo: 'Eliminar empleado', cuerpo, pie });
  }

  // Alta rápida de ciudad/terminal sin salir del formulario; al guardar queda elegida en la lista.
  function nuevaCiudad(select) {
    const cuerpo = h(`<form class="rejilla" novalidate>
      <div class="campo"><label for="ciudad-nombre">Ciudad o municipio</label><input id="ciudad-nombre" class="entrada" placeholder="Mapastepec" autocomplete="off"><span class="error" hidden></span></div>
      <div class="campo"><label for="ciudad-dir">Dirección de la terminal</label><input id="ciudad-dir" class="entrada" placeholder="Calle, número, colonia" autocomplete="off"><span class="error" hidden></span></div>
    </form>`);
    const marcar = (id, msg) => {
      const campo = cuerpo.querySelector('#' + id);
      campo.classList.toggle('invalida', !!msg);
      const err = campo.parentElement.querySelector('.error');
      err.hidden = !msg;
      err.textContent = msg || '';
      return !msg;
    };
    const guardarCiudad = () => {
      const nombreCiudad = cuerpo.querySelector('#ciudad-nombre').value.trim();
      const direccion = cuerpo.querySelector('#ciudad-dir').value.trim();
      estado = cargar();
      const repetida = estado.terminales.some(t => t.nombre.toLowerCase() === nombreCiudad.toLowerCase());
      const ok = [marcar('ciudad-nombre', !nombreCiudad ? 'Campo obligatorio' : repetida ? 'Esa ciudad ya está registrada' : ''),
        marcar('ciudad-dir', direccion ? '' : 'Campo obligatorio')].every(Boolean);
      if (!ok) return;
      const terminal = { id: Math.max(0, ...estado.terminales.map(t => t.id)) + 1, nombre: nombreCiudad, direccion };
      estado.terminales.push(terminal);
      guardar(estado);
      select.append(h(`<option value="${terminal.id}">${esc(terminal.nombre)}</option>`));
      select.value = String(terminal.id);
      cerrarCiudad();
      toast(`${terminal.nombre} se agregó a la lista de ciudades.`);
    };
    cuerpo.onsubmit = e => { e.preventDefault(); guardarCiudad(); };
    const cerrarCiudad = abrirVentana({ titulo: 'Nueva ciudad', cuerpo, pie: [
      boton('Cancelar', () => cerrarCiudad(), 'secundario'), boton('Agregar ciudad', guardarCiudad)
    ] });
  }

  function editar(registro, preset = {}) {
    const datos = registro ? { ...registro } : { ...cfg.nuevo(), ...preset };
    const lista_campos = campos();
    const form = h(`<form class="rejilla dos" novalidate>${lista_campos.map(c => `<div class="campo"><label for="c-${c.clave}">${esc(c.etiqueta)}</label>
      ${c.opciones ? `<select id="c-${c.clave}" class="entrada">${c.opciones.map(([v, t]) => `<option value="${esc(v)}" ${String(datos[c.clave] ?? '') === String(v) ? 'selected' : ''}>${esc(t)}</option>`).join('')}</select>`
        : `<input id="c-${c.clave}" class="entrada${c.tipo === 'number' ? ' mono' : ''}" type="${c.tipo ?? 'text'}" value="${esc(datos[c.clave] ?? '')}" autocomplete="off">`}
      <span class="error" hidden></span></div>`).join('')}</form>`);
    lista_campos.filter(c => c.nuevaCiudad).forEach(c => {
      const sel = form.querySelector(`#c-${c.clave}`);
      const nueva = boton('+ Nueva ciudad', () => nuevaCiudad(sel), 'secundario chico');
      nueva.style.alignSelf = 'flex-start';
      sel.after(nueva);
    });
    const cerrar = abrirVentana({ titulo: registro ? `Editar ${cfg.singular}` : cfg.singular === 'empleado' ? `Alta de ${capital(datos.puesto).toLowerCase()}` : `Nueva ${cfg.singular}`, cuerpo: form, pie: [
      boton('Cancelar', () => cerrar(), 'secundario'),
      boton('Guardar', () => {
        const r = { ...datos };
        for (const c of lista_campos) {
          let v = form.querySelector(`#c-${c.clave}`).value.trim();
          if (c.mayusculas) v = v.toUpperCase();
          r[c.clave] = c.tipo === 'number' || (c.numero && v !== '') ? Number(v) : c.numero ? null : v;
        }
        let valido = true;
        for (const c of lista_campos) {
          const v = r[c.clave];
          const msg = c.requerido && (v === '' || v === null || Number.isNaN(v)) ? 'Campo obligatorio' : c.validar ? c.validar(v, r, registro?.id) : true;
          const caja = form.querySelector(`#c-${c.clave}`);
          const error = caja.parentElement.querySelector('.error');
          const ok = msg === true;
          caja.classList.toggle('invalida', !ok);
          error.hidden = ok;
          error.textContent = ok ? '' : msg;
          valido &&= ok;
        }
        if (!valido) return;
        estado = cargar();
        if (registro) Object.assign(porId(estado[coleccion], registro.id), r);
        else estado[coleccion].push({ ...r, id: Math.max(0, ...estado[coleccion].map(x => x.id)) + 1 });
        guardar(estado);
        cerrar();
        toast('Guardado.');
        secciones.forEach(t => t.recargar());
      })
    ] });
  }
}

const coleccion = app.dataset.coleccion;
catalogo({ coleccion, columnas: CONFIG[coleccion].columnas, campos: CONFIG[coleccion].campos });
