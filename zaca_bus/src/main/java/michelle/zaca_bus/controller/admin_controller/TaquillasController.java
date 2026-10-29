package michelle.zaca_bus.controller.admin_controller;

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
import michelle.zaca_bus.dao.admin_dao.TaquillaDAO;
import michelle.zaca_bus.dao.admin_dao.TerminalDAO;
import michelle.zaca_bus.model.Catalogo;
import michelle.zaca_bus.model.Taquilla;
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

    // Pantalla de terminales
    @FXML private TableView<Catalogo> tabla;
    @FXML private TableColumn<Catalogo, String> colTerminal, colTaquillas;
    @FXML private TableColumn<Catalogo, Void> colAcciones;
    @FXML private TextField txtBuscar;
    @FXML private Label lblMensaje;

    // Formulario de taquilla
    @FXML private Label lblTitulo, lblError;
    @FXML private ComboBox<String> cbEstado;
    @FXML private TextField txtIdentificador, txtSerie, txtInicial, txtFinal;

    private final TaquillaDAO taquillaDAO = new TaquillaDAO();
    private final TerminalDAO terminalDAO = new TerminalDAO();
    private final ObservableList<Catalogo> terminales = FXCollections.observableArrayList();
    private List<Taquilla> taquillas = List.of();
    private Catalogo terminalForm;
    private int idTaquilla;
    private int foliosUsados;

    @FXML
    private void initialize() {
        if (tabla == null) {
            return; // se está cargando taquilla-form.fxml: no hay lista que armar
        }
        colTerminal.setCellValueFactory(c -> texto(c.getValue().nombre()));
        colTaquillas.setCellValueFactory(c -> texto(resumen(c.getValue())));
        Tablas.columnaEditar(colAcciones, this::abrirDetalle, "Detalle");
        tabla.setItems(Tablas.filtrar(terminales, txtBuscar, Catalogo::nombre));
        cargar();
    }

    // ---- Pantalla de terminales ----

    /** Terminal y ciudad son uno: el alta pide nombre y dirección, igual que el alta rápida de ciudad. */
    @FXML
    private void nuevaTerminal() {
        App.<CatalogoController>abrirVentana("catalogo-form", tabla.getScene().getWindow(),
                c -> c.preparar("Nueva terminal", "Nombre (ciudad)", "Dirección", "Agregar terminal",
                        terminalDAO::crear, t -> cargar()));
    }

    private void cargar() {
        try {
            taquillas = taquillaDAO.listar();
            terminales.setAll(terminalDAO.listar());
            Mensaje.ocultar(lblMensaje);
        } catch (SQLException e) {
            Mensaje.error(lblMensaje, "No se pudieron cargar las terminales: " + e.getMessage());
        }
    }

    private String resumen(Catalogo terminal) {
        long total = taquillas.stream().filter(t -> t.terminal().id() == terminal.id()).count();
        long activas = taquillas.stream().filter(t -> t.terminal().id() == terminal.id() && t.activa()).count();
        return activas + " activas de " + total;
    }

    private void abrirDetalle(Catalogo terminal) {
        App.<TerminalDetalleController>abrirVentana("terminal-detalle", tabla.getScene().getWindow(),
                d -> d.preparar(terminal));
        cargar();
    }

    // ---- Formulario de taquilla ----

    /** `taquilla` es null para una taquilla nueva en `terminal`. */
    public void prepararFormulario(Catalogo terminal, Taquilla taquilla) {
        terminalForm = terminal;
        cbEstado.getItems().setAll(ACTIVA, INACTIVA);
        cbEstado.setValue(ACTIVA);
        lblTitulo.setText("Nueva taquilla · " + terminal.nombre());
        if (taquilla == null) {
            return;
        }
        idTaquilla = taquilla.idTaquilla();
        foliosUsados = taquilla.foliosUsados();
        lblTitulo.setText("Editar taquilla · " + terminal.nombre());
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
                inicial, fin, foliosUsados, ACTIVA.equals(cbEstado.getValue()));
    }

    private static String mensajeRepetido(SQLException e) {
        return e.getMessage().contains("serie_folio") ? "Ese prefijo ya lo usa otra taquilla." : "Ya existe una taquilla con ese identificador.";
    }
}
