package michelle.zaca_bus.model;

/** Acomodo de asientos (layout) ya registrado para un tipo de servicio, con su capacidad. */
public record Acomodo(int idLayout, String nombre, int capacidad) {

    @Override
    public String toString() {
        return nombre + " · " + capacidad + " asientos";
    }
}
