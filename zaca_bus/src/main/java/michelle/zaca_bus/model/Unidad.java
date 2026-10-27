package michelle.zaca_bus.model;

/** `idUnidad` es 0 mientras la unidad no se ha guardado. Tipo, acomodo y capacidad forman el `tipo_bus` de la base. */
public record Unidad(
        int idUnidad, String placa, String modelo, int anio, int km, Catalogo tipo, Catalogo acomodo, int capacidad) {
}
