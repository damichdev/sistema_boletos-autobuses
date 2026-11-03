package michelle.zaca_bus.controller.despachador;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import michelle.zaca_bus.App;
import michelle.zaca_bus.dao.despachador.SalidaDAO;
import michelle.zaca_bus.model.Catalogo;
import michelle.zaca_bus.model.Parada;
import michelle.zaca_bus.model.ParadaViaje;
import michelle.zaca_bus.model.Salida;
import michelle.zaca_bus.util.Formato;
import michelle.zaca_bus.util.Mensaje;

/**
 * Módulo Salidas: la pantalla Programar salida (programar-salida.fxml), donde nacen los viajes y al guardar aparecen
 * en la venta de las taquillas, y la ventana de detalle de una salida (detalle-salida.fxml), que es solo consulta:
 * no permite editar ni cancelar el viaje. Cada FXML recibe su propia instancia.
 */
public class ProgramarSalidaController {

    private static final int MINUTOS_DIA = 1440;

    @FXML private ComboBox<Catalogo> cbRuta, cbUnidad, cbChofer;
    @FXML private ComboBox<LocalTime> cbHorario;
    @FXML private DatePicker dpFecha;
    @FXML private TextField txtBuscar;
    @FXML private Label lblMensaje;
    @FXML private VBox boxPrevia, boxPasos;
    @FXML private Label lblAviso;
    @FXML private TableView<Salida> tabla;
    @FXML private TableColumn<Salida, String> colHora, colRuta, colUnidad, colChofer, colEstado;

    // Detalle de una salida
    @FXML private Label lblHora, lblRuta, lblEstado, lblUnidad, lblChofer, lblLicencia;
    @FXML private VBox boxRecorrido, boxOcupacion;

    private final SalidaDAO dao = new SalidaDAO();
    private final ObservableList<Salida> salidas = FXCollections.observableArrayList();

    @FXML
    private void initialize() {
        if (tabla == null) {
            return; // se está cargando detalle-salida.fxml: no hay pantalla que armar
        }
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
        dpFecha.valueProperty().addListener((obs, antes, dia) -> cargar());
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
                List<Parada> paradas = dao.paradas(ruta.id());
                for (Parada p : paradas) {
                    int minutos = salida.toSecondOfDay() / 60 + p.min();
                    Label hora = new Label(LocalTime.ofSecondOfDay(minutos % MINUTOS_DIA * 60L).toString());
                    hora.getStyleClass().addAll("mono", "hora-parada");
                    String sufijo = minutos >= MINUTOS_DIA ? " (día siguiente)" : "";
                    boxPasos.getChildren().add(new HBox(8, hora, new Label(p.terminal().nombre() + sufijo)));
                }
                hayPrevia = !boxPasos.getChildren().isEmpty();
                avisarSiEsMuyLargo(hayPrevia ? paradas.get(paradas.size() - 1).min() : 0);
            } catch (SQLException e) {
                Mensaje.error(lblMensaje, "No se pudo calcular el recorrido: " + e.getMessage());
            }
        }
        boxPrevia.setVisible(hayPrevia);
        boxPrevia.setManaged(hayPrevia);
    }

    /** Un viaje de más de 24 horas casi siempre es un error al capturar los minutos de la ruta: se avisa antes de guardar. */
    private void avisarSiEsMuyLargo(int duracion) {
        boolean largo = duracion > MINUTOS_DIA;
        lblAviso.setVisible(largo);
        lblAviso.setManaged(largo);
        if (largo) {
            lblAviso.setText("Este viaje dura " + duracion / 60 + " h " + duracion % 60 + " min en total. "
                    + "Si no es correcto, corrija los minutos de la ruta en Rutas antes de guardar la salida.");
        }
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
            App.<ProgramarSalidaController>abrirVentana("detalle-salida", tabla.getScene().getWindow(),
                    detalle -> detalle.prepararDetalle(salida, recorrido));
        } catch (SQLException e) {
            Mensaje.error(lblMensaje, "No se pudo abrir el detalle: " + e.getMessage());
        }
    }

    private void cargar() {
        try {
            salidas.setAll(dpFecha.getValue() == null ? List.of() : dao.listar(dpFecha.getValue()));
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

    // ---- Detalle de una salida ----

    public void prepararDetalle(Salida salida, List<ParadaViaje> recorrido) {
        lblHora.setText(Formato.hora(salida.fecha()));
        lblRuta.setText(salida.ruta() + " · " + salida.servicio());
        lblEstado.setText(salida.estado());
        lblUnidad.setText(salida.placa() + " · " + salida.modelo());
        lblChofer.setText(salida.chofer());
        lblLicencia.setText(textoLicencia(salida.vigenciaLicencia()));

        for (int i = 0; i < recorrido.size(); i++) {
            ParadaViaje p = recorrido.get(i);
            Label hora = new Label(Formato.hora(p.hora()));
            hora.getStyleClass().addAll("mono", "hora-parada");
            boxRecorrido.getChildren().add(new HBox(8, hora, new Label(p.terminal() + " · suben " + p.suben() + ", bajan " + p.bajan())));
            if (i < recorrido.size() - 1) {
                boxOcupacion.getChildren().addAll(tramo(p.terminal() + " → " + recorrido.get(i + 1).terminal(),
                        p.ocupadosHaciaSiguiente(), salida.capacidad()));
            }
        }
    }

    @FXML
    private void verManifiesto() {
        cerrarDetalle();
        App.irA("manifiesto");
    }

    @FXML
    private void cerrarDetalle() {
        lblHora.getScene().getWindow().hide();
    }

    private static VBox tramo(String nombre, int ocupados, int capacidad) {
        Label etiqueta = new Label(nombre);
        Label cifra = new Label(ocupados + " de " + capacidad);
        cifra.getStyleClass().add("mono");
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        ProgressBar barra = new ProgressBar(capacidad == 0 ? 0 : (double) ocupados / capacidad);
        barra.setMaxWidth(Double.MAX_VALUE);
        return new VBox(4, new HBox(etiqueta, espacio, cifra), barra);
    }

    /** El aviso de la licencia es solo visual; quien la valida al programar es la base. */
    private static String textoLicencia(LocalDate vigencia) {
        return vigencia == null ? "Sin licencia registrada" : "Licencia vigente a " + vigencia;
    }
}
