package michelle.zaca_bus.controller.admin;

import java.util.function.Function;
import javafx.scene.Node;
import javafx.util.StringConverter;
import michelle.zaca_bus.model.Catalogo;

/** Lo que comparten las pantallas de Personal y Cuentas: nombres de puesto, filtros "Todos" y utilidades de formulario. */
final class EtiquetasPersonal {

    static final String CONDUCTOR = "CONDUCTOR";
    static final String TAQUILLERO = "TAQUILLERO";
    static final String DESPACHADOR = "DESPACHADOR";
    static final String JEFE_TALLER = "JEFE_TALLER"; // nombre del puesto en administracion.puesto
    static final String REPETIDO = "23505"; // CURP, usuario o persona que ya existen
    static final String TODAS_CIUDADES = "Todas las ciudades";
    static final Catalogo TODAS_TERMINALES = new Catalogo(0, "Todas las terminales");
    static final Catalogo TODOS_LOS_PUESTOS = new Catalogo(0, "Todos los puestos");

    static final StringConverter<Catalogo> NOMBRE_PUESTO = new StringConverter<>() {
        @Override
        public String toString(Catalogo puesto) {
            return puesto == null ? "" : puesto == TODOS_LOS_PUESTOS ? puesto.nombre() : etiquetaPuesto(puesto.nombre());
        }

        @Override
        public Catalogo fromString(String texto) {
            return null;
        }
    };

    private EtiquetasPersonal() {
    }

    /** Nombre del puesto como se muestra: la base lo guarda en mayúsculas y CONDUCTOR se llama Chofer. */
    static String etiquetaPuesto(String puesto) {
        return switch (puesto) {
            case CONDUCTOR -> "Chofer";
            case "MECANICO" -> "Mecánico";
            case JEFE_TALLER -> "Jefe de taller";
            default -> puesto.charAt(0) + puesto.substring(1).toLowerCase();
        };
    }

    static void mostrar(Node nodo, boolean visible) {
        nodo.setVisible(visible);
        nodo.setManaged(visible);
    }

    static <T> StringConverter<T> convertidor(Function<T, String> aTexto) {
        return new StringConverter<>() {
            @Override
            public String toString(T valor) {
                return valor == null ? "" : aTexto.apply(valor);
            }

            @Override
            public T fromString(String texto) {
                return null;
            }
        };
    }
}
