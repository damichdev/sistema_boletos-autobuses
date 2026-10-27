package michelle.zaca_bus.controller;

import static michelle.zaca_bus.util.Tablas.texto;

import java.sql.SQLException;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import michelle.zaca_bus.App;
import michelle.zaca_bus.dao.TaquillaDAO;
import michelle.zaca_bus.dao.TerminalDAO;
import michelle.zaca_bus.model.Catalogo;
import michelle.zaca_bus.model.Taquilla;
import michelle.zaca_bus.util.Mensaje;
import michelle.zaca_bus.util.Tablas;
import michelle.zaca_bus.util.Validar;

/**
 * Módulo Taquillas: ventanillas por terminal, cada una con su serie y rango de folios.
 * Controla taquillas.fxml y taquilla-form.fxml; cada FXML recibe su propia instancia.
 */
public class TaquillasController {

    private static final String REPETIDO = "23505";
    private static final String ACTIVA = "Activa";
    private static final String INACTIVA = "Inactiva";

    // Pantalla de lista
    @FXML private TableView<Taquilla> tabla;
    @FXML private TableColumn<Taquilla, String> colIdentificador, colTerminal, colSerie, colRango, colUsados, colEstado;
    @FXML private TableColumn<Taquilla, Void> colAcciones;
    @FXML private TextField txtBuscar;
    @FXML private Label lblMensaje;

    // Formulario
    @FXML private Label lblTitulo, lblError;
    @FXML private ComboBox<Catalogo> cbTerminal;
    @FXML private ComboBox<String> cbEstado;
    @FXML private TextField txtIdentificador, txtSerie, txtInicial, txtFinal;

    private final TaquillaDAO taquillaDAO = new TaquillaDAO();
    private final TerminalDAO terminalDAO = new TerminalDAO();
    private final ObservableList<Taquilla> taquillas = FXCollections.observableArrayList();
    private int idTaquilla;
    private int foliosUsados;

    @FXML
    private void initialize() {
        if (tabla == null) {
            return; // se está cargando taquilla-form.fxml: no hay lista que armar
        }
        colIdentificador.setCellValueFactory(c -> texto(c.getValue().identificador()));
        colTerminal.setCellValueFactory(c -> texto(c.getValue().terminal().nombre()));
        colSerie.setCellValueFactory(c -> texto(c.getValue().serie()));
        colRango.setCellValueFactory(c -> texto(c.getValue().rangoInicial() + " – " + c.getValue().rangoFinal()));
        colUsados.setCellValueFactory(c -> texto(c.getValue().foliosUsados() + " de " + c.getValue().totalFolios()));
        colEstado.setCellValueFactory(c -> texto(c.getValue().activa() ? ACTIVA : INACTIVA));
        Tablas.columnaEditar(colAcciones, this::abrirEditor);
        tabla.setItems(Tablas.filtrar(taquillas, txtBuscar,
                t -> t.identificador() + " " + t.serie() + " " + t.terminal().nombre()));
        cargar();
    }

    // ---- Pantalla de lista ----

    @FXML
    private void nuevaTaquilla() {
        abrirEditor(null);
    }

    private void cargar() {
        try {
            taquillas.setAll(taquillaDAO.listar());
            Mensaje.ocultar(lblMensaje);
        } catch (SQLException e) {
            Mensaje.error(lblMensaje, "No se pudieron cargar las taquillas: " + e.getMessage());
        }
    }

    /** `taquilla` es null para una taquilla nueva. */
    private void abrirEditor(Taquilla taquilla) {
        try {
            List<Catalogo> terminales = terminalDAO.listar();
            App.<TaquillasController>abrirVentana("taquilla-form", tabla.getScene().getWindow(),
                    formulario -> formulario.prepararFormulario(taquilla, terminales));
            cargar();
        } catch (SQLException e) {
            Mensaje.error(lblMensaje, "No se pudo abrir el formulario: " + e.getMessage());
        }
    }

    // ---- Formulario ----

    /** `taquilla` es null para una taquilla nueva. */
    public void prepararFormulario(Taquilla taquilla, List<Catalogo> terminales) {
        cbTerminal.getItems().setAll(terminales);
        cbEstado.getItems().setAll(ACTIVA, INACTIVA);
        cbEstado.setValue(ACTIVA);
        if (taquilla == null) {
            return;
        }
        idTaquilla = taquilla.idTaquilla();
        foliosUsados = taquilla.foliosUsados();
        lblTitulo.setText("Editar taquilla");
        cbTerminal.setValue(taquilla.terminal());
        txtIdentificador.setText(taquilla.identificador());
        txtSerie.setText(taquilla.serie());
        txtInicial.setText(String.valueOf(taquilla.rangoInicial()));
        txtFinal.setText(String.valueOf(taquilla.rangoFinal()));
        cbEstado.setValue(taquilla.activa() ? ACTIVA : INACTIVA);
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

    /** Alta rápida de terminal sin salir del formulario; al guardarla queda elegida. */
    @FXML
    private void nuevaCiudad() {
        App.<CatalogoController>abrirVentana("catalogo-form", lblError.getScene().getWindow(),
                ciudad -> ciudad.preparar("Nueva ciudad", "Ciudad o municipio", "Dirección de la terminal", "Agregar ciudad",
                        terminalDAO::crear, terminal -> {
                            cbTerminal.getItems().add(terminal);
                            cbTerminal.setValue(terminal);
                        }));
    }

    /** Reglas del script: identificador y serie únicos, folio inicial menor o igual al final. */
    private Taquilla leerFormulario() {
        if (cbTerminal.getValue() == null) {
            throw new IllegalArgumentException("Elija la terminal.");
        }
        int inicial = Validar.entero(txtInicial.getText(), 1, Integer.MAX_VALUE, "El folio inicial debe ser un entero mayor que 0.");
        int fin = Validar.entero(txtFinal.getText(), 1, Integer.MAX_VALUE, "El folio final debe ser un entero mayor que 0.");
        if (inicial > fin) {
            throw new IllegalArgumentException("El folio final debe ser mayor o igual al inicial.");
        }
        return new Taquilla(idTaquilla, cbTerminal.getValue(),
                Validar.texto(txtIdentificador.getText(), "Escriba el identificador."),
                Validar.texto(txtSerie.getText(), "Escriba la serie de folios."),
                inicial, fin, foliosUsados, ACTIVA.equals(cbEstado.getValue()));
    }

    private static String mensajeRepetido(SQLException e) {
        return e.getMessage().contains("serie_folio") ? "Esa serie ya la usa otra taquilla." : "Ya existe una taquilla con ese identificador.";
    }
}
