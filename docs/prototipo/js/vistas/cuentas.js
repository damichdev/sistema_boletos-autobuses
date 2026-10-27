import { exigirRol, pintarBarra } from '../sesion.js';
import { cargar, guardar } from '../estado.js';
import { porId, generarUsuario, nombreCompleto } from '../logica.js';
import { h, esc, tabla, boton, abrirVentana, toast } from '../ui.js';

const cuenta = await exigirRol('ADMINISTRADOR');
pintarBarra(cuenta);

const app = document.getElementById('app');
let estado = cargar();
const ROLES = [['TAQUILLERO', 'Taquillero'], ['DESPACHADOR', 'Despachador'], ['ADMINISTRADOR', 'Administrador']];
const rolDe = r => ROLES.find(x => x[0] === r)[1];
const persona = c => porId(estado.personal, c.idPersonal);
const sede = c => porId(estado.terminales, c.idTerminal).nombre;
const opciones = (lista, sel) => lista.map(([v, t]) => `<option value="${esc(v)}" ${String(v) === String(sel) ? 'selected' : ''}>${esc(t)}</option>`).join('');

app.append(h(`<div class="encabezado"><div><h1>Cuentas de usuario</h1>
  <p>Una cuenta personal por empleado. La sede limita dónde puede trabajar y todo lo que haga queda registrado a su nombre.</p></div></div>`));

const lista = tabla({
  titulo: 'Cuentas',
  columnas: [
    { titulo: 'Usuario', valor: c => `<b class="mono">${esc(c.username)}</b>` },
    { titulo: 'Nombre completo', valor: c => esc(nombreCompleto(persona(c))) },
    { titulo: 'Rol', valor: c => esc(rolDe(c.rol)) },
    { titulo: 'Sede', valor: c => esc(sede(c)) },
    { titulo: 'Estado', valor: c => c.activo === false ? '<span class="chip neutro">Inactivo</span>' : '<span class="chip ok">Activo</span>' }
  ],
  filas: estado.cuentas,
  buscar: (c, t) => [c.username, nombreCompleto(persona(c)), rolDe(c.rol), sede(c)].join(' ').toLowerCase().includes(t),
  acciones: c => {
    const cambiar = boton(c.activo === false ? 'Activar' : 'Desactivar', () => alternar(c), 'secundario chico');
    if (c.id === cuenta.id) { cambiar.disabled = true; cambiar.title = 'No puede desactivar su propia cuenta'; }
    return [boton('Editar', () => formulario(c), 'secundario chico'), cambiar];
  },
  extra: [boton('+ Nueva cuenta', () => formulario(null))]
});
app.append(lista);

function refrescar() { estado = cargar(); lista.actualizar(estado.cuentas); }

function alternar(c) {
  estado = cargar();
  const actual = porId(estado.cuentas, c.id);
  actual.activo = actual.activo === false;
  guardar(estado);
  toast(`La cuenta ${actual.username} quedó ${actual.activo ? 'activa' : 'inactiva'}.`);
  refrescar();
}

function formulario(existente) {
  estado = cargar();
  const sinCuenta = estado.personal.filter(p => !estado.cuentas.some(c => c.idPersonal === p.id) || p.id === existente?.idPersonal);
  if (!existente && !sinCuenta.length) { toast('Todo el personal ya tiene cuenta. Dé de alta al empleado en Personal.', 'aviso'); return; }
  const rolPorPuesto = p => ROLES.some(r => r[0] === p.puesto) ? p.puesto : 'TAQUILLERO';
  const inicial = existente ?? { idPersonal: sinCuenta[0].id, rol: rolPorPuesto(sinCuenta[0]), idTerminal: estado.terminales[0].id };
  const form = h(`<form class="rejilla" novalidate>
    <div class="campo"><label for="f-persona">Nombre de la persona</label>
      <select id="f-persona" class="entrada" ${existente ? 'disabled' : ''}>${opciones(sinCuenta.map(p => [p.id, nombreCompleto(p)]), inicial.idPersonal)}</select></div>
    <div class="rejilla dos">
      <div class="campo"><label for="f-rol">Rol</label><select id="f-rol" class="entrada">${opciones(ROLES, inicial.rol)}</select></div>
      <div class="campo"><label for="f-sede">Sede asignada</label><select id="f-sede" class="entrada">${opciones(estado.terminales.map(t => [t.id, t.nombre]), inicial.idTerminal)}</select></div>
    </div>
    <div class="rejilla dos">
      <div class="campo"><label>Usuario</label><div id="f-usuario" class="entrada mono" style="display:flex;align-items:center;background:var(--cabeza)"></div></div>
      <div class="campo"><label for="f-clave">${existente ? 'Nueva contraseña (opcional)' : 'Contraseña temporal'}</label><input id="f-clave" class="entrada" type="password" autocomplete="new-password"><span class="error" hidden></span></div>
    </div>
    <p class="sec chico">Solo podrá trabajar en la sede asignada: el taquillero abre turno en una ventanilla de esa sede y el despachador ve los viajes que pasan por ella.</p>
  </form>`);
  const sel = form.querySelector('#f-persona');
  const usuario = () => existente ? existente.username : generarUsuario(estado, porId(estado.personal, Number(sel.value)));
  const mostrarUsuario = () => { form.querySelector('#f-usuario').textContent = usuario(); };
  sel.onchange = () => { form.querySelector('#f-rol').value = rolPorPuesto(porId(estado.personal, Number(sel.value))); mostrarUsuario(); };
  mostrarUsuario();

  const cerrar = abrirVentana({ titulo: existente ? `Editar cuenta ${existente.username}` : 'Nueva cuenta', ancho: 620, cuerpo: form, pie: [
    boton('Cancelar', () => cerrar(), 'secundario'),
    boton(existente ? 'Guardar cambios' : 'Crear cuenta', () => {
      const clave = form.querySelector('#f-clave');
      const error = clave.parentElement.querySelector('.error');
      const msg = !existente && !clave.value ? 'Campo obligatorio' : clave.value && clave.value.length < 8 ? 'Mínimo 8 caracteres' : '';
      clave.classList.toggle('invalida', !!msg);
      error.hidden = !msg;
      error.textContent = msg;
      if (msg) return;
      const nuevoUsuario = usuario();
      estado = cargar();
      const datos = { rol: form.querySelector('#f-rol').value, idTerminal: Number(form.querySelector('#f-sede').value) };
      if (clave.value) datos.password = clave.value;
      if (existente) Object.assign(porId(estado.cuentas, existente.id), datos);
      else estado.cuentas.push({ id: Math.max(0, ...estado.cuentas.map(c => c.id)) + 1, idPersonal: Number(sel.value), username: nuevoUsuario, activo: true, ...datos });
      guardar(estado);
      cerrar();
      toast(existente ? 'Cuenta actualizada.' : `Cuenta ${nuevoUsuario} creada. Entregue la contraseña temporal a la persona.`);
      refrescar();
    })
  ] });
}
