package michelle.zaca_bus.controller.admin;

import static michelle.zaca_bus.util.Tablas.texto;

import java.sql.SQLException;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import michelle.zaca_bus.App;
import michelle.zaca_bus.dao.admin.TaquillaDAO;
import michelle.zaca_bus.dao.admin.TerminalDAO;
import michelle.zaca_bus.model.Catalogo;
import michelle.zaca_bus.model.Taquilla;
import michelle.zaca_bus.model.Terminal;
import michelle.zaca_bus.util.Mensaje;
import michelle.zaca_bus.util.Tablas;
import michelle.zaca_bus.util.Validar;

/**
 * Módulo Taquillas: lista de terminales (cada una es una ciudad) y, por terminal, sus taquillas con serie y rango de folios.
 * Controla taquillas.fxml (terminales) y taquilla-form.fxml; cada FXML recibe su propia instancia.
 */
public class TaquillasController {

    private static final String REPETIDO = "23505";
    private static final String ACTIVA = "Activa";
    private static final String INACTIVA = "Inactiva";
    private static final String TODAS = "Todas las ciudades";

    // Pantalla de terminales
    @FXML private TableView<Terminal> tabla;
    @FXML private TableColumn<Terminal, String> colTerminal, colSucursal, colDireccion, colTaquillas, colEstado;
    @FXML private TableColumn<Terminal, Void> colAcciones;
    @FXML private ComboBox<String> cbCiudad;
    @FXML private Label lblMensaje;

    // Formulario de taquilla
    @FXML private Label lblTitulo, lblError;
    @FXML private CheckBox chkActiva;
    @FXML private TextField txtTerminal, txtNumero, txtIdentificador, txtSerie, txtInicial, txtFinal;

    private final TaquillaDAO taquillaDAO = new TaquillaDAO();
    private final TerminalDAO terminalDAO = new TerminalDAO();
    private final ObservableList<Terminal> terminales = FXCollections.observableArrayList();
    private List<Taquilla> taquillas = List.of();
    private Catalogo terminalForm;
    private int idTaquilla;
    private int foliosUsados;

    @FXML
    private void initialize() {
        if (tabla == null) {
            // taquilla-form.fxml: el identificador sale del número
            txtNumero.textProperty().addListener((obs, antes, n) ->
                    txtIdentificador.setText(n.isBlank() ? "" : "TAQ-" + n.trim()));
            return;
        }
        // una terminal por ciudad: la columna identifica la terminal por su ciudad
        colTerminal.setCellValueFactory(c -> texto(c.getValue().ciudadVisible()));
        // pendiente backend: la vista de terminales aún no trae ciudad, sucursal ni dirección
        colSucursal.setCellValueFactory(c -> texto(c.getValue().ciudad() == null ? "—" : c.getValue().nombre()));
        colDireccion.setCellValueFactory(c -> texto(c.getValue().direccion() == null ? "—" : c.getValue().direccion()));
        colTaquillas.setCellValueFactory(c -> texto(resumen(c.getValue())));
        colEstado.setCellValueFactory(c -> texto(c.getValue().activa() ? ACTIVA : INACTIVA));
        Tablas.columnaGestion(colAcciones, Terminal::activa, this::abrirEditor,
                "Ver Taquillas", this::abrirDetalle, t -> cambiarEstado(t), t -> cambiarEstado(t));
        FilteredList<Terminal> visibles = new FilteredList<>(terminales);
        cbCiudad.valueProperty().addListener((obs, antes, ciudad) ->
                visibles.setPredicate(t -> TODAS.equals(ciudad) || t.ciudadVisible().equals(ciudad)));
        tabla.setItems(visibles);
        cargar();
    }

    // ---- Pantalla de terminales ----

    @FXML
    private void nuevaTerminal() {
        abrirEditor(null);
    }

    /**
     * `terminal` es null para una terminal nueva. Al formulario se le pasan las ciudades que ya tienen terminal
     * para que aplique la regla de una terminal por ciudad. El formulario aún no guarda (fase de frontend).
     */
    private void abrirEditor(Terminal terminal) {
        List<Integer> ciudadesConTerminal = terminales.stream().map(Terminal::idCiudad).toList();
        App.<TerminalesController>abrirVentana("terminal-form", tabla.getScene().getWindow(),
                f -> f.preparar(terminal, ciudadesConTerminal));
        cargar();
    }

    private void cargar() {
        try {
            taquillas = taquillaDAO.listar();
            terminales.setAll(terminalDAO.listarTodas());
            Object elegida = cbCiudad.getValue();
            cbCiudad.getItems().setAll(TODAS);
            terminales.forEach(t -> cbCiudad.getItems().add(t.ciudadVisible()));
            cbCiudad.setValue(cbCiudad.getItems().contains(elegida) ? (String) elegida : TODAS);
            Mensaje.ocultar(lblMensaje);
        } catch (SQLException e) {
            Mensaje.error(lblMensaje, "No se pudieron cargar las terminales: " + e.getMessage());
        }
    }

    private String resumen(Terminal terminal) {
        long activas = taquillas.stream().filter(t -> t.terminal().id() == terminal.id() && t.activa()).count();
        return activas + (activas == 1 ? " taquilla" : " taquillas");
    }

    private void abrirDetalle(Terminal terminal) {
        App.<TerminalesController>abrirVentana("terminal-detalle", tabla.getScene().getWindow(),
                d -> d.prepararTaquillas(terminal.catalogo()));
        cargar();
    }

    /** Desactivar no borra: la terminal deja de ofrecerse en rutas, taquillas y personal, y conserva su historial. */
    private void cambiarEstado(Terminal terminal) {
        try {
            terminalDAO.cambiarEstado(terminal.id(), !terminal.activa());
            cargar();
        } catch (SQLException e) {
            Mensaje.error(lblMensaje, "No se pudo cambiar el estado: " + e.getMessage());
        }
    }

    // ---- Formulario de taquilla ----

    /** `taquilla` es null para una taquilla nueva en `terminal`. */
    public void prepararFormulario(Catalogo terminal, Taquilla taquilla) {
        terminalForm = terminal;
        txtTerminal.setText(terminal.nombre());
        chkActiva.setSelected(true);
        if (taquilla == null) {
            return;
        }
        idTaquilla = taquilla.idTaquilla();
        foliosUsados = taquilla.foliosUsados();
        lblTitulo.setText("EDITAR TAQUILLA");
        txtNumero.setText(taquilla.identificador().replaceAll("\\D", ""));
        txtIdentificador.setText(taquilla.identificador()); // conserva el identificador guardado aunque no siga el patrón TAQ-n
        txtSerie.setText(taquilla.serie());
        txtInicial.setText(String.valueOf(taquilla.rangoInicial()));
        txtFinal.setText(String.valueOf(taquilla.rangoFinal()));
        chkActiva.setSelected(taquilla.activa());
    }

    @FXML
    private void guardar() {
        try {
            taquillaDAO.guardar(leerFormulario());
            cerrar();
        } catch (IllegalArgumentException e) {
            Mensaje.error(lblError, e.getMessage());
        } catch (SQLException e) {
            Mensaje.error(lblError, REPETIDO.equals(e.getSQLState()) ? mensajeRepetido(e) : "No se pudo guardar: " + e.getMessage());
        }
    }

    @FXML
    private void cerrar() {
        lblError.getScene().getWindow().hide();
    }

    /** Reglas del script: identificador y serie únicos, folio inicial menor o igual al final. */
    private Taquilla leerFormulario() {
        int inicial = Validar.entero(txtInicial.getText(), 1, Integer.MAX_VALUE, "La cantidad inicial debe ser un entero mayor que 0.");
        int fin = Validar.entero(txtFinal.getText(), 1, Integer.MAX_VALUE, "La cantidad final debe ser un entero mayor que 0.");
        if (inicial > fin) {
            throw new IllegalArgumentException("La cantidad final debe ser mayor o igual a la inicial.");
        }
        return new Taquilla(idTaquilla, terminalForm,
                Validar.texto(txtIdentificador.getText(), "Escriba el identificador."),
                Validar.texto(txtSerie.getText(), "Escriba el prefijo de folios."),
                inicial, fin, foliosUsados, chkActiva.isSelected());
    }

    private static String mensajeRepetido(SQLException e) {
        return e.getMessage().contains("serie_folio") ? "Ese prefijo ya lo usa otra taquilla." : "Ya existe una taquilla con ese identificador.";
    }
}
