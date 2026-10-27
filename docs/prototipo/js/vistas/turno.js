import { exigirRol, pintarBarra } from '../sesion.js';
import { cargar, guardar } from '../estado.js';
import { porId, taquillasDisponibles, abrirTurno, turnoAbierto, nombreCompleto, horaLocal } from '../logica.js';
import { h, esc, boton, toast } from '../ui.js';

const cuenta = await exigirRol('TAQUILLERO');
if (turnoAbierto(cargar(), cuenta.id)) location.replace('/taquilla/venta.html');
pintarBarra(cuenta);

const app = document.getElementById('app');

function pintar() {
  const estado = cargar();
  const sede = porId(estado.terminales, cuenta.idTerminal);
  const persona = porId(estado.personal, cuenta.idPersonal);
  const libres = new Set(taquillasDisponibles(estado, cuenta.idTerminal).map(t => t.id));
  const deSede = estado.taquillas.filter(t => t.idTerminal === cuenta.idTerminal);
  const ocupadaPor = t => {
    const turno = estado.turnos.find(x => x.idTaquilla === t.id && !x.fin);
    const c = turno && porId(estado.cuentas, turno.idCuenta);
    return c ? `${nombreCompleto(porId(estado.personal, c.idPersonal))} desde ${horaLocal(new Date(turno.inicio))}` : '';
  };
  const lista = h('<div class="ventanillas"></div>');
  for (const t of deSede) {
    const libre = libres.has(t.id);
    const tarjeta = h(`<section class="tarjeta ventanilla ${libre ? '' : 'apagado'}">
      <div class="mono" style="font-size:24px;font-weight:600">${esc(t.identificador)}</div>
      <div class="sec chico">Serie ${esc(t.serie)} · folio siguiente ${t.rangoInicial + t.foliosUsados}</div>
      <div>${!t.activo ? '<span class="chip neutro">Inactiva</span>' : libre ? '<span class="chip ok">Disponible</span>' : `<span class="chip aviso">Ocupada</span><div class="sec chico" style="margin-top:6px">${esc(ocupadaPor(t))}</div>`}</div>
    </section>`);
    const abrir = boton('Abrir turno aquí', () => {
      const e = cargar();
      try {
        abrirTurno(e, cuenta.id, t.id, new Date());
        guardar(e);
        location.href = '/taquilla/venta.html';
      } catch {
        toast(`${t.identificador} acaba de ser ocupada por otra persona. Elija otra ventanilla.`, 'error');
        pintar();
      }
    }, 'grande');
    abrir.disabled = !libre;
    tarjeta.append(abrir);
    lista.append(tarjeta);
  }
  app.replaceChildren(
    h(`<div class="encabezado"><div><h1>Abrir turno</h1><p>${esc(persona.nombre)}, elija la ventanilla de ${esc(sede.nombre)} donde va a trabajar. Todo lo que venda o cancele quedará registrado a su nombre.</p></div></div>`),
    deSede.length ? lista : h('<div class="tarjeta vacio">Su sede no tiene ventanillas registradas. Consulte al administrador.</div>')
  );
}
pintar();
