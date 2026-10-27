package michelle.zaca_bus.controller;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import michelle.zaca_bus.App;
import michelle.zaca_bus.dao.SalidaDAO;
import michelle.zaca_bus.model.Catalogo;
import michelle.zaca_bus.model.Salida;
import michelle.zaca_bus.util.Formato;
import michelle.zaca_bus.util.Mensaje;

/** Pantalla Programar salida: aquí nacen los viajes; al guardar aparecen en la venta de las taquillas. */
public class ProgramarSalidaController {

    @FXML private ComboBox<Catalogo> cbRuta, cbUnidad, cbChofer;
    @FXML private DatePicker dpFecha;
    @FXML private TextField txtHora, txtBuscar;
    @FXML private Label lblMensaje;
    @FXML private TableView<Salida> tabla;
    @FXML private TableColumn<Salida, String> colHora, colRuta, colUnidad, colChofer, colEstado;

    private final SalidaDAO dao = new SalidaDAO();
    private final ObservableList<Salida> salidas = FXCollections.observableArrayList();

    @FXML
    private void initialize() {
        colHora.setCellValueFactory(c -> texto(Formato.hora(c.getValue().fecha())));
        colRuta.setCellValueFactory(c -> texto(c.getValue().ruta()));
        colUnidad.setCellValueFactory(c -> texto(c.getValue().placa()));
        colChofer.setCellValueFactory(c -> texto(c.getValue().chofer()));
        colEstado.setCellValueFactory(c -> texto(c.getValue().estado()));

        FilteredList<Salida> visibles = new FilteredList<>(salidas);
        txtBuscar.textProperty().addListener((obs, antes, ahora) -> visibles.setPredicate(
                s -> (Formato.hora(s.fecha()) + " " + s.ruta() + " " + s.placa() + " " + s.chofer() + " " + s.estado())
                        .toLowerCase().contains(ahora.trim().toLowerCase())));
        tabla.setItems(visibles);

        dpFecha.setValue(LocalDate.now(Formato.ZONA));
        try {
            cbRuta.getItems().setAll(dao.rutas());
            cbUnidad.getItems().setAll(dao.unidades());
            cbChofer.getItems().setAll(dao.choferes());
        } catch (SQLException e) {
            Mensaje.error(lblMensaje, "No se pudieron cargar las listas: " + e.getMessage());
        }
        cargar();
    }

    @FXML
    private void guardar() {
        try {
            ZonedDateTime salida = leerSalida();
            dao.programar(cbRuta.getValue().id(), cbUnidad.getValue().id(), cbChofer.getValue().id(), salida);
            Mensaje.exito(lblMensaje, "Salida guardada. Ya aparece en la venta de las taquillas.");
            txtHora.clear();
            cargar();
        } catch (IllegalArgumentException e) {
            Mensaje.error(lblMensaje, e.getMessage());
        } catch (SQLException e) {
            Mensaje.error(lblMensaje, Mensaje.deViaje(e));
        }
    }

    @FXML
    private void verDetalle() {
        Salida salida = tabla.getSelectionModel().getSelectedItem();
        if (salida == null) {
            Mensaje.error(lblMensaje, "Elija una salida de la tabla para ver su detalle.");
            return;
        }
        try {
            var recorrido = dao.recorrido(salida);
            App.<DetalleSalidaController>abrirVentana("detalle-salida", tabla.getScene().getWindow(),
                    detalle -> detalle.preparar(salida, recorrido));
        } catch (SQLException e) {
            Mensaje.error(lblMensaje, "No se pudo abrir el detalle: " + e.getMessage());
        }
    }

    private void cargar() {
        try {
            salidas.setAll(dao.listar(LocalDate.now(Formato.ZONA)));
        } catch (SQLException e) {
            Mensaje.error(lblMensaje, "No se pudieron cargar las salidas: " + e.getMessage());
        }
    }

    private ZonedDateTime leerSalida() {
        if (cbRuta.getValue() == null || cbUnidad.getValue() == null || cbChofer.getValue() == null || dpFecha.getValue() == null) {
            throw new IllegalArgumentException("Elija la ruta, la fecha, la unidad y el chofer.");
        }
        try {
            return dpFecha.getValue().atTime(LocalTime.parse(txtHora.getText().trim())).atZone(Formato.ZONA);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Escriba la hora de salida con el formato hh:mm, por ejemplo 08:30.");
        }
    }

    private static ObservableValue<String> texto(String valor) {
        return new SimpleStringProperty(valor);
    }
}
