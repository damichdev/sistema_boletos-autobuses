package michelle.zaca_bus.util;

import java.text.Normalizer;
import java.util.Collection;

public final class NombreUsuario {

    private NombreUsuario() {
    }

    /** Primer nombre + "." + apellido paterno, sin acentos: dana.constantino, y dana.constantino2 si ya existe. */
    public static String generar(String nombre, String apPaterno, Collection<String> existentes) {
        String base = Normalizer.normalize(nombre.trim().split("\\s+")[0] + "." + apPaterno, Normalizer.Form.NFD)
                .toLowerCase().replaceAll("[^a-z.]", "");
        String usuario = base;
        for (int n = 2; existentes.contains(usuario); n++) {
            usuario = base + n;
        }
        return usuario;
    }
}
