package michelle.zaca_bus.util;

import java.sql.SQLException;

import javafx.scene.control.Label;

/** Franja de aviso de las pantallas: un Label oculto que se muestra solo cuando hay algo que decir. */
public final class Mensaje {

    private static final String ERROR = "alerta-error";
    private static final String EXITO = "alerta-ok";

    private Mensaje() {
    }

    public static void error(Label franja, String texto) {
        mostrar(franja, texto, ERROR, EXITO);
    }

    public static void exito(Label franja, String texto) {
        mostrar(franja, texto, EXITO, ERROR);
    }

    /** Errores del trigger fn_asignar_conductor al guardar o cambiar un viaje; los demás se muestran con el texto de la base. */
    public static String deViaje(SQLException e) {
        return switch (String.valueOf(e.getSQLState())) {
            case "Z0006" -> "La licencia del chofer no está vigente el día de la salida.";
            case "Z0007" -> "Esa persona no es conductor.";
            case "Z0012" -> "La unidad o el chofer ya tienen otro viaje en ese horario.";
            default -> "No se pudo guardar: " + e.getMessage();
        };
    }

    public static void ocultar(Label franja) {
        franja.setVisible(false);
        franja.setManaged(false);
    }

    private static void mostrar(Label franja, String texto, String clase, String otra) {
        franja.getStyleClass().remove(otra);
        if (!franja.getStyleClass().contains(clase)) {
            franja.getStyleClass().add(clase);
        }
        franja.setText(texto);
        franja.setVisible(true);
        franja.setManaged(true);
    }
}
