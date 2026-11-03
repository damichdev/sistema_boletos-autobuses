package michelle.zaca_bus.controller.admin;

import static michelle.zaca_bus.util.Tablas.texto;

import java.sql.SQLException;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import michelle.zaca_bus.App;
import michelle.zaca_bus.dao.admin.OrdenTallerDAO;
import michelle.zaca_bus.model.OrdenTaller;
import michelle.zaca_bus.util.Mensaje;
import michelle.zaca_bus.util.Tablas;


public class OrdenesTallerController {

    private static final String TODAS = "Todas";
    @FXML private TableView<OrdenTaller> tabla;
    @FXML private TableColumn<OrdenTaller, String> colOrden, colUnidad, colServicio, colMecanico, colIngreso, colEstado;
    @FXML private TableColumn<OrdenTaller, Void> colAcciones;
    @FXML private ComboBox<String> cbTaller, cbEstado;
    @FXML private TextField txtBuscar;
    
    @FXML private TextField txtNumero, txtUnidad, txtMecanico, txtServicio, txtIngreso;
    @FXML private TextArea txtDescripcion;
    @FXML private HBox boxEstado;
    @FXML private Label lblPie, lblError;

    private final OrdenTallerDAO dao = new OrdenTallerDAO();
    private final ObservableList<OrdenTaller> ordenes = FXCollections.observableArrayList();

    @FXML
    private void initialize() {
        if (tabla == null) {
            return; // se está cargando orden-detalle.fxml: no hay lista que armar
        }
        try {
            ordenes.setAll(dao.listar(null));
        } catch (SQLException e) {
            Mensaje.error(lblError, "No se pudieron cargar las órdenes: " + e.getMessage());
        }
        colOrden.setCellValueFactory(c -> texto(c.getValue().numero()));
        colUnidad.setCellValueFactory(c -> texto(c.getValue().unidad()));
        colServicio.setCellValueFactory(c -> texto(c.getValue().servicio()));
        colMecanico.setCellValueFactory(c -> texto(c.getValue().mecanico()));
        colIngreso.setCellValueFactory(c -> texto(c.getValue().ingreso()));
        colEstado.setCellValueFactory(c -> texto(c.getValue().estado()));
        colEstado.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(String estado, boolean vacia) {
                super.updateItem(estado, vacia);
                setGraphic(vacia ? null : chipEstado(estado));
            }
        });
        Tablas.columnaEditar(colAcciones, this::abrirDetalle, "Ver/Detalle");

        cbTaller.getItems().setAll(ordenes.stream().map(OrdenTaller::taller).distinct().toList());
        if (!cbTaller.getItems().isEmpty()) { // sin órdenes (la base de taller aún no está conectada) no hay taller que elegir
            cbTaller.setValue(cbTaller.getItems().get(0));
        }
        cbEstado.getItems().setAll(TODAS, "En Espera", "En Reparación", "Completado");
        cbEstado.setValue(TODAS);

        FilteredList<OrdenTaller> visibles = new FilteredList<>(ordenes);
        Runnable filtrar = () -> {
            String texto = txtBuscar.getText().trim().toLowerCase();
            visibles.setPredicate(o -> o.taller().equals(cbTaller.getValue())
                    && (TODAS.equals(cbEstado.getValue()) || o.estado().equals(cbEstado.getValue()))
                    && (o.numero() + " " + o.unidad() + " " + o.mecanico()).toLowerCase().contains(texto));
        };
        cbTaller.valueProperty().addListener((obs, a, b) -> filtrar.run());
        cbEstado.valueProperty().addListener((obs, a, b) -> filtrar.run());
        txtBuscar.textProperty().addListener((obs, a, b) -> filtrar.run());
        filtrar.run();
        tabla.setItems(visibles);
    }

    private static Label chipEstado(String estado) {
        Label chip = new Label(estado);
        chip.getStyleClass().addAll("chip", switch (estado) {
            case "Completado" -> "chip-ok";
            case "En Reparación" -> "chip-aviso";
            default -> "chip-neutro";
        });
        return chip;
    }

    private void abrirDetalle(OrdenTaller orden) {
        App.<OrdenesTallerController>abrirVentana("orden-detalle", tabla.getScene().getWindow(), d -> d.prepararDetalle(orden));
    }

    // ---- Detalle ----

    public void prepararDetalle(OrdenTaller orden) {
        txtNumero.setText(orden.numero());
        txtUnidad.setText(orden.unidad());
        txtMecanico.setText(orden.mecanico());
        txtServicio.setText(orden.servicio());
        txtIngreso.setText(orden.ingreso());
        txtDescripcion.setText(orden.descripcion());
        boxEstado.getChildren().setAll(chipEstado(orden.estado()));
        lblPie.setText("Taller: " + orden.taller() + " | Salida: " + orden.salida() + " | Costo: " + orden.costo());
    }

    @FXML
    private void cerrar() {
        lblPie.getScene().getWindow().hide();
    }
}
