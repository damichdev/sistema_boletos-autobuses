package michelle.zaca_bus.model;

import java.time.LocalDate;

/**
 * `idPersonal` es 0 mientras no se ha guardado. Teléfono, licencia y vigencia pueden ser null.
 * `idTaller` (mecánico o jefe), `idTerminal` y `idTaquilla` (taquillero; despachador solo terminal) pueden ser null. `tieneCuenta` solo se lee: al eliminar al empleado, la base también elimina su cuenta.
 */
public record Empleado(int idPersonal, Catalogo puesto, String nombre, String apPaterno, String apMaterno,
        String curp, String telefono, String licencia, LocalDate vigencia, boolean tieneCuenta,
        Integer idTaller, Integer idTerminal, Integer idTaquilla) {

    public String nombreCompleto() {
        return nombre + " " + apPaterno + " " + apMaterno;
    }
}
