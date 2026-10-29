package michelle.zaca_bus.controller.admin_controller;

import java.sql.SQLException;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import michelle.zaca_bus.dao.admin_dao.RutaDAO;
import michelle.zaca_bus.model.Ruta;
import michelle.zaca_bus.util.Mensaje;

/** Ventana de horarios de una ruta: el administrador agrega o quita las horas de salida oficiales. */
public class HorariosRutaController {

    private static final String HORA_REPETIDA = "23505";

    @FXML private Label lblTitulo, lblError;
    @FXML private ListView<LocalTime> lstHorarios;
    @FXML private TextField txtHora;

    private final RutaDAO dao = new RutaDAO();
    private final ObservableList<LocalTime> horarios = FXCollections.observableArrayList();
    private int idRuta;

    public void preparar(Ruta ruta, List<LocalTime> horas) {
        idRuta = ruta.idRuta();
        lblTitulo.setText("Horarios de " + ruta.nombre());
        lstHorarios.setItems(horarios);
        horarios.setAll(horas);
    }

    @FXML
    private void agregar() {
        try {
            LocalTime hora = LocalTime.parse(txtHora.getText().trim());
            dao.agregarHorario(idRuta, hora);
            txtHora.clear();
            recargar();
        } catch (DateTimeParseException e) {
            Mensaje.error(lblError, "Escriba la hora con el formato hh:mm, por ejemplo 06:00.");
        } catch (SQLException e) {
            Mensaje.error(lblError, HORA_REPETIDA.equals(e.getSQLState())
                    ? "La ruta ya tiene ese horario." : "No se pudo agregar: " + e.getMessage());
        }
    }

    @FXML
    private void quitar() {
        LocalTime hora = lstHorarios.getSelectionModel().getSelectedItem();
        if (hora == null) {
            Mensaje.error(lblError, "Elija el horario que quiere quitar.");
            return;
        }
        try {
            dao.quitarHorario(idRuta, hora);
            recargar();
        } catch (SQLException e) {
            Mensaje.error(lblError, "No se pudo quitar: " + e.getMessage());
        }
    }

    @FXML
    private void cerrar() {
        lblError.getScene().getWindow().hide();
    }

    private void recargar() throws SQLException {
        horarios.setAll(dao.horarios(idRuta));
        Mensaje.ocultar(lblError);
    }
}
