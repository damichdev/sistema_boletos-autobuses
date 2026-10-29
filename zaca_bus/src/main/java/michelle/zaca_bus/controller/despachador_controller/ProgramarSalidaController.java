package michelle.zaca_bus.controller.despachador_controller;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
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
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import michelle.zaca_bus.App;
import michelle.zaca_bus.dao.despachador_dao.SalidaDAO;
import michelle.zaca_bus.model.Catalogo;
import michelle.zaca_bus.model.Parada;
import michelle.zaca_bus.model.Salida;
import michelle.zaca_bus.util.Formato;
import michelle.zaca_bus.util.Mensaje;

/** Pantalla Programar salida: aquí nacen los viajes; al guardar aparecen en la venta de las taquillas. */
public class ProgramarSalidaController {

    @FXML private ComboBox<Catalogo> cbRuta, cbUnidad, cbChofer;
    @FXML private ComboBox<LocalTime> cbHorario;
    @FXML private DatePicker dpFecha;
    @FXML private TextField txtBuscar;
    @FXML private Label lblMensaje;
    @FXML private VBox boxPrevia, boxPasos;
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
        cbRuta.valueProperty().addListener((obs, antes, ruta) -> cargarHorarios(ruta));
        cbHorario.valueProperty().addListener((obs, antes, hora) -> mostrarPrevia());
        cargar();
    }

    /** Ofrece los horarios base de la ruta; la hora solo se elige de esa lista. */
    private void cargarHorarios(Catalogo ruta) {
        cbHorario.getItems().clear();
        if (ruta == null) {
            return;
        }
        try {
            cbHorario.getItems().setAll(dao.horarios(ruta.id()));
        } catch (SQLException e) {
            Mensaje.error(lblMensaje, "No se pudieron cargar los horarios de la ruta: " + e.getMessage());
        }
    }

    /** Con la ruta y el horario elegidos, calcula la hora de paso por cada parada: salida + minutos desde el origen. */
    private void mostrarPrevia() {
        boxPasos.getChildren().clear();
        Catalogo ruta = cbRuta.getValue();
        LocalTime salida = cbHorario.getValue();
        boolean hayPrevia = false;
        if (ruta != null && salida != null) {
            try {
                for (Parada p : dao.paradas(ruta.id())) {
                    int minutos = salida.toSecondOfDay() / 60 + p.min();
                    Label hora = new Label(LocalTime.ofSecondOfDay(minutos % 1440 * 60L).toString());
                    hora.getStyleClass().addAll("mono", "hora-parada");
                    String sufijo = minutos >= 1440 ? " (día siguiente)" : "";
                    boxPasos.getChildren().add(new HBox(8, hora, new Label(p.terminal().nombre() + sufijo)));
                }
                hayPrevia = !boxPasos.getChildren().isEmpty();
            } catch (SQLException e) {
                Mensaje.error(lblMensaje, "No se pudo calcular el recorrido: " + e.getMessage());
            }
        }
        boxPrevia.setVisible(hayPrevia);
        boxPrevia.setManaged(hayPrevia);
    }

    @FXML
    private void guardar() {
        try {
            ZonedDateTime salida = leerSalida();
            dao.programar(cbRuta.getValue().id(), cbUnidad.getValue().id(), cbChofer.getValue().id(), salida);
            Mensaje.exito(lblMensaje, "Salida guardada. Ya aparece en la venta de las taquillas.");
            cbHorario.setValue(null);
            mostrarPrevia();
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
        if (cbHorario.getValue() == null) {
            throw new IllegalArgumentException("Elija el horario de salida de la ruta.");
        }
        return dpFecha.getValue().atTime(cbHorario.getValue()).atZone(Formato.ZONA);
    }

    private static ObservableValue<String> texto(String valor) {
        return new SimpleStringProperty(valor);
    }
}
