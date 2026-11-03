package michelle.zaca_bus.controller.admin;

import static michelle.zaca_bus.controller.admin.EtiquetasPersonal.CONDUCTOR;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import michelle.zaca_bus.dao.admin.TallerDAO;
import michelle.zaca_bus.dao.admin.TaquillaDAO;
import michelle.zaca_bus.dao.admin.TerminalDAO;
import michelle.zaca_bus.model.Empleado;

/** Texto de la asignación de un empleado (lista de Personal y alta de cuenta); lee los nombres al crearse. */
final class AsignacionPersonal {

    private final Map<Integer, String> talleres = new HashMap<>();
    private final Map<Integer, String> terminales = new HashMap<>();
    private final Map<Integer, String> taquillas = new HashMap<>();

    AsignacionPersonal() throws SQLException {
        new TallerDAO().listar().forEach(t -> talleres.put(t.idTaller(), t.nombre()));
        new TerminalDAO().listar().forEach(t -> terminales.put(t.id(), t.nombre()));
        new TaquillaDAO().listar().forEach(t -> taquillas.put(t.idTaquilla(), t.identificador()));
    }

    /** Chofer: rutas. Mecánico o jefe: su taller. Despachador: su terminal. Taquillero: terminal y taquilla. */
    String de(Empleado e) {
        if (CONDUCTOR.equals(e.puesto().nombre())) {
            return "N/A (Rutas)";
        }
        if (e.idTaller() != null) {
            return talleres.getOrDefault(e.idTaller(), "—");
        }
        if (e.idTerminal() == null) {
            return "—";
        }
        String terminal = terminales.getOrDefault(e.idTerminal(), "—");
        return e.idTaquilla() == null ? terminal : terminal + " · " + taquillas.getOrDefault(e.idTaquilla(), "—");
    }
}
