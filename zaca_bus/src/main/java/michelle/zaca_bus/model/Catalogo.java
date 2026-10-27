package michelle.zaca_bus.model;

/** Un elemento de un catálogo (terminal, tipo de servicio…). `toString` es lo que muestran los ComboBox. */
public record Catalogo(int id, String nombre) {

    @Override
    public String toString() {
        return nombre;
    }
}
