package michelle.zaca_bus.util;

import michelle.zaca_bus.model.Cuenta;

/** Cuenta que inició sesión. El login debe llamar a `iniciar` al validar las credenciales; mientras tanto es null. */
public final class Sesion {

    private static Cuenta actual;

    private Sesion() {
    }

    public static void iniciar(Cuenta cuenta) {
        actual = cuenta;
    }

    public static Cuenta actual() {
        return actual;
    }
}
