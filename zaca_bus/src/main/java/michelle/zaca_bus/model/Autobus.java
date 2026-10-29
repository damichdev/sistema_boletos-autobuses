package michelle.zaca_bus.model;

/** Cómo se acomodan los asientos de la unidad de un viaje: `acomodo` es el layout del script, como "2+2". */
public record Autobus(String acomodo, int capacidad) {

    /** Asientos a la izquierda del pasillo. */
    public int izquierda() {
        return Integer.parseInt(acomodo.split("\\+")[0]);
    }

    /** Asientos a la derecha del pasillo. */
    public int derecha() {
        return Integer.parseInt(acomodo.split("\\+")[1]);
    }
}
