package michelle.zaca_bus.controller.jefetaller;

import static michelle.zaca_bus.util.Tablas.texto;

import java.sql.SQLException;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import michelle.zaca_bus.dao.jefetaller.TallerJefeDAO;
import michelle.zaca_bus.model.BusTaller;
import michelle.zaca_bus.util.Mensaje;
import michelle.zaca_bus.util.Tablas;

/** Semáforo de la flota (flota-taller.fxml): lo mismo que ve el despacho al programar una salida. */
public class FlotaTallerController {

    @FXML private TableView<BusTaller> tabla;
    @FXML private TableColumn<BusTaller, String> colUnidad, colEstatus, colIngreso, colObservaciones;
    @FXML private TextField txtBuscar;
    @FXML private Label lblError;

    private final TallerJefeDAO dao = new TallerJefeDAO();

    @FXML
    private void initialize() {
        colUnidad.setCellValueFactory(c -> texto(c.getValue().placa() + "  " + c.getValue().modelo()));
        colEstatus.setCellValueFactory(c -> texto(c.getValue().estatus()));
        colEstatus.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(String estatus, boolean vacia) {
                super.updateItem(estatus, vacia);
                if (vacia) {
                    setGraphic(null);
                    return;
                }
                BusTaller bus = getTableView().getItems().get(getIndex());
                Label chip = new Label(estatus);
                chip.getStyleClass().addAll("chip", bus.aptoParaViajar() ? "chip-ok" : "chip-aviso");
                setGraphic(chip);
            }
        });
        colIngreso.setCellValueFactory(c -> texto(c.getValue().ultimoIngreso() == null ? "—" : c.getValue().ultimoIngreso()));
        colObservaciones.setCellValueFactory(c -> texto(c.getValue().observaciones() == null ? "—" : c.getValue().observaciones()));
        List<BusTaller> flota = List.of();
        try {
            flota = dao.flota();
        } catch (SQLException e) {
            Mensaje.error(lblError, "No se pudo cargar la flota: " + e.getMessage());
        }
        tabla.setItems(Tablas.filtrar(FXCollections.observableArrayList(flota), txtBuscar,
                b -> b.placa() + " " + b.modelo() + " " + b.estatus()));
    }
}
