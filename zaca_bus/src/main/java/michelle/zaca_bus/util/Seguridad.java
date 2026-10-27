package michelle.zaca_bus.util;

import org.mindrot.jbcrypt.BCrypt;

/** Contraseñas con BCrypt, igual que en la biblioteca: en la base solo se guarda el hash. */
public final class Seguridad {

    private Seguridad() {
    }

    public static String generarHash(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt());
    }

    public static boolean verificar(String password, String hash) {
        if (password == null || hash == null) {
            return false;
        }
        try {
            return BCrypt.checkpw(password, hash);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
