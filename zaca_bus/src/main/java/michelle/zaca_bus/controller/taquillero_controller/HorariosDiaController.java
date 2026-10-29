package michelle.zaca_bus.controller.taquillero_controller;

import java.util.List;
import java.util.function.Consumer;
import javafx.fxml.FXML;
import javafx.scene.layout.VBox;
import michelle.zaca_bus.model.Horario;

/** Ventana con todos los horarios del día; al elegir uno se cierra y el panel Horario lo recibe. */
public class HorariosDiaController {

    @FXML private VBox boxHorarios;

    public void preparar(List<Horario> horarios, Horario elegido, Consumer<Horario> alElegir) {
        for (Horario h : horarios) {
            boxHorarios.getChildren().add(TarjetaHorario.crear(h, h.equals(elegido), () -> {
                alElegir.accept(h);
                cerrar();
            }));
        }
    }

    @FXML
    private void cerrar() {
        boxHorarios.getScene().getWindow().hide();
    }
}
