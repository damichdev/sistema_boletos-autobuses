package michelle.zaca_bus.controller;

import java.sql.SQLException;
import java.time.LocalDate;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import michelle.zaca_bus.dao.ManifiestoDAO;
import michelle.zaca_bus.dao.SalidaDAO;
import michelle.zaca_bus.model.Escala;
import michelle.zaca_bus.model.PasajeroManifiesto;
import michelle.zaca_bus.model.Salida;
import michelle.zaca_bus.util.Formato;
import michelle.zaca_bus.util.Mensaje;

/** Pantalla Manifiesto: quienes suben en una escala de una salida de hoy, y el botón para marcarla en ruta. */
public class ManifiestoController {

    private static final String ABORDO = "Abordó";

    @FXML private ComboBox<Salida> cbSalida;
    @FXML private ComboBox<Escala> cbEscala;
    @FXML private Label lblHora, lblAbordaron, lblMensaje;
    @FXML private TextField txtBuscar;
    @FXML private TableView<PasajeroManifiesto> tabla;
    @FXML private TableColumn<PasajeroManifiesto, String> colAsiento, colTitular, colDestino, colTarifa, colFolio, colEstado;

    private final SalidaDAO salidaDAO = new SalidaDAO();
    private final ManifiestoDAO dao = new ManifiestoDAO();
    private final ObservableList<PasajeroManifiesto> pasajeros = FXCollections.observableArrayList();

    @FXML
    private void initialize() {
        colAsiento.setCellValueFactory(c -> texto(String.valueOf(c.getValue().asiento())));
        colTitular.setCellValueFactory(c -> texto(c.getValue().titular()));
        colDestino.setCellValueFactory(c -> texto(c.getValue().destino()));
        colTarifa.setCellValueFactory(c -> texto(c.getValue().tarifa()));
        colFolio.setCellValueFactory(c -> texto(c.getValue().folio()));
        colEstado.setCellValueFactory(c -> texto(c.getValue().estado()));

        FilteredList<PasajeroManifiesto> visibles = new FilteredList<>(pasajeros);
        txtBuscar.textProperty().addListener((obs, antes, ahora) -> visibles.setPredicate(
                p -> (p.titular() + " " + p.asiento() + " " + p.folio()).toLowerCase().contains(ahora.trim().toLowerCase())));
        tabla.setItems(visibles);

        cbSalida.valueProperty().addListener((obs, antes, ahora) -> cargarEscalas());
        cbEscala.valueProperty().addListener((obs, antes, ahora) -> cargarPasajeros());
        cargarSalidas(0);
    }

    @FXML
    private void marcarEnRuta() {
        Salida salida = cbSalida.getValue();
        if (salida == null) {
            Mensaje.error(lblMensaje, "Elija una salida.");
        } else if (salida.estado().equals("EN RUTA") || salida.estado().equals("CERRADO")) {
            Mensaje.error(lblMensaje, "Esta salida ya está " + salida.estado().toLowerCase() + ".");
        } else {
            try {
                dao.marcarEnRuta(salida.idViaje());
                cargarSalidas(salida.idViaje());
                Mensaje.exito(lblMensaje, "La salida de las " + Formato.hora(salida.fecha()) + " quedó en ruta.");
            } catch (SQLException e) {
                Mensaje.error(lblMensaje, Mensaje.deViaje(e));
            }
        }
    }

    /** Carga las salidas de hoy y deja elegida la de `idViaje` (o la primera si es 0). */
    private void cargarSalidas(int idViaje) {
        try {
            cbSalida.getItems().setAll(salidaDAO.listar(LocalDate.now(Formato.ZONA)));
            cbSalida.getItems().stream().filter(s -> s.idViaje() == idViaje).findFirst()
                    .or(() -> cbSalida.getItems().stream().findFirst()).ifPresent(cbSalida::setValue);
        } catch (SQLException e) {
            Mensaje.error(lblMensaje, "No se pudieron cargar las salidas: " + e.getMessage());
        }
    }

    private void cargarEscalas() {
        cbEscala.getItems().clear();
        try {
            if (cbSalida.getValue() != null) {
                cbEscala.getItems().setAll(dao.escalas(cbSalida.getValue()));
                cbEscala.getItems().stream().findFirst().ifPresent(cbEscala::setValue);
            }
        } catch (SQLException e) {
            Mensaje.error(lblMensaje, "No se pudieron cargar las escalas: " + e.getMessage());
        }
        cargarPasajeros();
    }

    private void cargarPasajeros() {
        Salida salida = cbSalida.getValue();
        Escala escala = cbEscala.getValue();
        pasajeros.clear();
        lblHora.setText(escala == null ? "" : Formato.hora(escala.hora()));
        if (salida != null && escala != null) {
            try {
                pasajeros.setAll(dao.pasajeros(salida.idViaje(), escala.terminal().id()));
            } catch (SQLException e) {
                Mensaje.error(lblMensaje, "No se pudo cargar el manifiesto: " + e.getMessage());
            }
        }
        long abordaron = pasajeros.stream().filter(p -> p.estado().equals(ABORDO)).count();
        lblAbordaron.setText(abordaron + " de " + pasajeros.size() + " pasajeros");
    }

    private static ObservableValue<String> texto(String valor) {
        return new SimpleStringProperty(valor);
    }
}
