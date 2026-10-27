package michelle.zaca_bus.util;

/** Lectura de campos de formulario: si el texto no sirve, lanza IllegalArgumentException con el aviso para el usuario. */
public final class Validar {

    private Validar() {
    }

    public static String texto(String valor, String aviso) {
        String limpio = valor == null ? "" : valor.trim();
        if (limpio.isEmpty()) {
            throw new IllegalArgumentException(aviso);
        }
        return limpio;
    }

    /** Entero con `minimo` o más; `maximo` inclusive. */
    public static int entero(String valor, int minimo, int maximo, String aviso) {
        try {
            int numero = Integer.parseInt(valor.trim());
            if (numero >= minimo && numero <= maximo) {
                return numero;
            }
        } catch (NumberFormatException e) {
            // cae al aviso de abajo
        }
        throw new IllegalArgumentException(aviso);
    }
}
