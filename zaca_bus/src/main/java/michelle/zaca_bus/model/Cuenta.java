package michelle.zaca_bus.model;

/** `idApp` es 0 mientras no se ha guardado. `sede`, `taquilla` y `taller` (jefe de taller) pueden ser null; la taquilla solo aplica a taquilleros. */
public record Cuenta(int idApp, int idPersonal, String username, String nombreCompleto, Catalogo rol,
        Catalogo sede, Catalogo taquilla, boolean activa, Catalogo taller) {
}
