package michelle.zaca_bus.model;

import java.time.LocalDate;

/**
 * `idPersonal` es 0 mientras no se ha guardado. Teléfono, licencia y vigencia pueden ser null.
 * `tieneCuenta` solo se lee: al eliminar al empleado, la base también elimina su cuenta.
 */
public record Empleado(int idPersonal, Catalogo puesto, String nombre, String apPaterno, String apMaterno,
        String curp, String telefono, String licencia, LocalDate vigencia, boolean tieneCuenta) {

    public String nombreCompleto() {
        return nombre + " " + apPaterno + " " + apMaterno;
    }
}
