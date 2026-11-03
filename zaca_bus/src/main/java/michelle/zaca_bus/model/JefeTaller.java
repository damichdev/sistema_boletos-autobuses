package michelle.zaca_bus.model;

/** Un jefe de taller activo. `idTaller` es null mientras no encargue ningún taller; `toString` es lo que muestra el ComboBox. */
public record JefeTaller(int idPersonal, String nombre, Integer idTaller) {

    @Override
    public String toString() {
        return nombre;
    }
}
