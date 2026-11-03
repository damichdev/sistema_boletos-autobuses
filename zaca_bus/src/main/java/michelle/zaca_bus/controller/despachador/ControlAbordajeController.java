package michelle.zaca_bus.controller.despachador;

import static michelle.zaca_bus.util.Tablas.texto;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import michelle.zaca_bus.dao.despachador.AbordajeDAO;
import michelle.zaca_bus.dao.despachador.SalidaDAO;
import michelle.zaca_bus.model.BoletoAbordaje;
import michelle.zaca_bus.model.Escala;
import michelle.zaca_bus.model.PasajeroManifiesto;
import michelle.zaca_bus.model.Salida;
import michelle.zaca_bus.util.Formato;
import michelle.zaca_bus.util.Mensaje;
import michelle.zaca_bus.view.EscanerQr;

/**
 * Control del abordaje de las salidas de hoy. Controla dos pantallas, cada una con su propia instancia:
 * - Manifiesto (manifiesto.fxml): quienes suben en una escala de una salida, y el botón para marcarla en ruta.
 * - Abordaje (abordaje.fxml): se escanea el QR del boleto con la cámara (o se teclea el folio; un lector USB lo
 *   teclea y manda Enter). Un solo folio ampara todos los asientos de la venta, así que un escaneo marca a todos
 *   como abordados.
 */
public class ControlAbordajeController {

    private static final String ABORDO = "Abordó";
    private static final String VIGENTE = "VIGENTE";
    private static final String ABORDADO = "ABORDADO";

    /** Lo que se muestra: `clase` es el color (resultado-ok, resultado-aviso o resultado-error). `aMarcar`, los boletos que abordan. */
    record Resultado(String clase, String titulo, String detalle, List<Integer> aMarcar) {
    }

    // Comunes a las dos pantallas
    @FXML private DatePicker dpDia;
    @FXML private ComboBox<Salida> cbSalida;
    @FXML private Label lblMensaje;

    // Manifiesto
    @FXML private ComboBox<Escala> cbEscala;
    @FXML private Label lblHora, lblAbordaron;
    @FXML private TextField txtBuscar;
    @FXML private TableView<PasajeroManifiesto> tabla;
    @FXML private TableColumn<PasajeroManifiesto, String> colAsiento, colTitular, colDestino, colTarifa, colFolio, colEstado;

    // Abordaje
    @FXML private TextField txtFolio;
    @FXML private Label lblTitulo, lblDetalle;
    @FXML private VBox boxResultado;
    @FXML private ImageView imgCamara;
    @FXML private Button btnCamara;

    private final SalidaDAO salidaDAO = new SalidaDAO();
    private final AbordajeDAO abordajeDAO = new AbordajeDAO();
    private final ObservableList<PasajeroManifiesto> pasajeros = FXCollections.observableArrayList();
    private EscanerQr escaner;

    @FXML
    private void initialize() {
        if (tabla != null) {
            armarManifiesto();
        } else {
            escaner = new EscanerQr(imgCamara, this::alEscanear, this::camaraNoDisponible);
        }
        dpDia.setValue(LocalDate.now(Formato.ZONA));
        dpDia.valueProperty().addListener((obs, antes, dia) -> cargarSalidas(0));
        cargarSalidas(0);
    }

    // ---- Manifiesto ----

    private void armarManifiesto() {
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
                salidaDAO.marcarEnRuta(salida.idViaje());
                cargarSalidas(salida.idViaje());
                Mensaje.exito(lblMensaje, "La salida de las " + Formato.hora(salida.fecha()) + " quedó en ruta.");
            } catch (SQLException e) {
                Mensaje.error(lblMensaje, Mensaje.deViaje(e));
            }
        }
    }

    /** Carga las salidas del día elegido y deja elegida la de `idViaje` (o la primera si es 0). */
    private void cargarSalidas(int idViaje) {
        cbSalida.setValue(null);
        try {
            cbSalida.getItems().setAll(dpDia.getValue() == null ? List.of() : salidaDAO.listar(dpDia.getValue()));
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
                cbEscala.getItems().setAll(salidaDAO.escalas(cbSalida.getValue()));
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
                pasajeros.setAll(salidaDAO.generarManifiesto(salida.idViaje(), escala.terminal().id()));
            } catch (SQLException e) {
                Mensaje.error(lblMensaje, "No se pudo cargar el manifiesto: " + e.getMessage());
            }
        }
        long abordaron = pasajeros.stream().filter(p -> p.estado().equals(ABORDO)).count();
        lblAbordaron.setText(abordaron + " de " + pasajeros.size() + " pasajeros");
    }

    // ---- Abordaje ----

    @FXML
    private void validar() {
        String folio = txtFolio.getText().trim();
        if (cbSalida.getValue() == null || folio.isEmpty()) {
            Mensaje.error(lblMensaje, "Elija la salida y escanee o escriba el folio.");
            return;
        }
        try {
            Resultado resultado = resolver(cbSalida.getValue().idViaje(), abordajeDAO.boletos(folio));
            if (!resultado.aMarcar().isEmpty()) {
                abordajeDAO.abordar(resultado.aMarcar());
            }
            mostrar(resultado);
            Mensaje.ocultar(lblMensaje);
        } catch (SQLException e) {
            Mensaje.error(lblMensaje, "No se pudo validar el boleto: " + e.getMessage());
        }
        txtFolio.clear();
        txtFolio.requestFocus();
    }

    @FXML
    private void alternarCamara() {
        if (escaner.activo()) {
            escaner.detener();
            mostrarCamara(false);
        } else {
            escaner.iniciar();
            mostrarCamara(true);
        }
    }

    private void alEscanear(String folio) {
        txtFolio.setText(folio);
        validar();
    }

    private void camaraNoDisponible(String aviso) {
        mostrarCamara(false);
        Mensaje.error(lblMensaje, aviso);
    }

    private void mostrarCamara(boolean visible) {
        imgCamara.setVisible(visible);
        imgCamara.setManaged(visible);
        btnCamara.setText(visible ? "Detener cámara" : "Escanear con cámara");
    }

    /** Decide el resultado según los asientos de la venta; no toca la base. Solo un boleto VIGENTE puede abordar. */
    static Resultado resolver(int idViaje, List<BoletoAbordaje> boletos) {
        if (boletos.isEmpty()) {
            return new Resultado("resultado-error", "NO VÁLIDO", "Ese folio no existe.", List.of());
        }
        List<BoletoAbordaje> delViaje = boletos.stream().filter(b -> b.idViaje() == idViaje).collect(Collectors.toList());
        if (delViaje.isEmpty()) {
            return new Resultado("resultado-error", "NO VÁLIDO", "El boleto es de otro viaje.", List.of());
        }
        List<BoletoAbordaje> vigentes = delViaje.stream().filter(b -> b.estado().equals(VIGENTE)).collect(Collectors.toList());
        if (!vigentes.isEmpty()) {
            int n = vigentes.size();
            return new Resultado("resultado-ok", "PUEDE ABORDAR", (n == 1 ? "1 pasajero" : n + " pasajeros") + " · asientos " + asientos(vigentes),
                    vigentes.stream().map(BoletoAbordaje::idBoleto).collect(Collectors.toList()));
        }
        List<BoletoAbordaje> abordados = delViaje.stream().filter(b -> b.estado().equals(ABORDADO)).collect(Collectors.toList());
        if (!abordados.isEmpty()) {
            return new Resultado("resultado-aviso", "YA ESCANEADO", "Los asientos " + asientos(abordados) + " ya abordaron.", List.of());
        }
        return new Resultado("resultado-error", "NO VÁLIDO", "El boleto está " + delViaje.get(0).estado().toLowerCase() + ".", List.of());
    }

    private static String asientos(List<BoletoAbordaje> boletos) {
        return boletos.stream().map(b -> String.valueOf(b.asiento())).collect(Collectors.joining(", "));
    }

    private void mostrar(Resultado resultado) {
        boxResultado.getStyleClass().removeAll("resultado-ok", "resultado-aviso", "resultado-error");
        boxResultado.getStyleClass().add(resultado.clase());
        lblTitulo.setText(resultado.titulo());
        lblDetalle.setText(resultado.detalle());
        boxResultado.setVisible(true);
        boxResultado.setManaged(true);
    }
}
