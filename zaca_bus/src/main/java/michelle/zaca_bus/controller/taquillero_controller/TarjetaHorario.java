package michelle.zaca_bus.controller.taquillero_controller;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import michelle.zaca_bus.model.Horario;
import michelle.zaca_bus.util.Formato;

/** El botón de un horario (hora, tipo, lugares libres y precio); lo usan el panel Horario y la ventana de todos los horarios. */
final class TarjetaHorario {

    private TarjetaHorario() {
    }

    static Button crear(Horario h, boolean elegida, Runnable alElegir) {
        Label hora = new Label(Formato.hora(h.fecha()));
        hora.getStyleClass().add("horario-hora");
        Label tipo = new Label(capital(h.tipoBus()) + " · " + h.servicio().toLowerCase());
        tipo.getStyleClass().add("horario-tipo");
        Label libres = new Label(h.libres() > 0 ? h.libres() + " libres" : "Lleno");
        libres.getStyleClass().add("texto-sec");
        Label precio = new Label(Formato.dinero(h.precio()));
        precio.getStyleClass().add("horario-precio");
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);

        Button boton = new Button();
        boton.setGraphic(new HBox(12, hora, new VBox(2, tipo, libres), espacio, precio));
        ((HBox) boton.getGraphic()).setAlignment(Pos.CENTER_LEFT);
        boton.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        boton.getStyleClass().add("horario");
        if (elegida) {
            boton.getStyleClass().add("elegida");
        }
        boton.setDisable(h.libres() == 0);
        boton.setMaxWidth(Double.MAX_VALUE);
        boton.setOnAction(e -> alElegir.run());
        return boton;
    }

    private static String capital(String texto) {
        return texto.substring(0, 1) + texto.substring(1).toLowerCase();
    }
}
