package michelle.zaca_bus.model;

/** Quien viaja o compra: la venta se registra a su nombre. Los tres nombres son obligatorios; `curp` es null si no se capturó. */
public record Titular(String nombre, String apPaterno, String apMaterno, String curp) {

    public String nombreCompleto() {
        return nombre + " " + apPaterno + " " + apMaterno;
    }
}
