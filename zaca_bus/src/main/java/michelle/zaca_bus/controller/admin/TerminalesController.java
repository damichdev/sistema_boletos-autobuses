package michelle.zaca_bus.controller.admin;

import static michelle.zaca_bus.util.Tablas.texto;

import java.sql.SQLException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import michelle.zaca_bus.App;
import michelle.zaca_bus.dao.admin.CiudadDAO;
import michelle.zaca_bus.dao.admin.TaquillaDAO;
import michelle.zaca_bus.dao.admin.TerminalDAO;
import michelle.zaca_bus.model.Catalogo;
import michelle.zaca_bus.model.Taquilla;
import michelle.zaca_bus.model.Terminal;
import michelle.zaca_bus.util.Mensaje;
import michelle.zaca_bus.util.Tablas;
import michelle.zaca_bus.util.Validar;

/**
 * Módulo Terminales, ventanas de una terminal:
 * - Formulario (terminal-form.fxml). Regla: una terminal por ciudad. Al crear, solo se ofrecen las ciudades que aún
 *   no tienen terminal; al editar, la ciudad queda fija. Guarda con sp_guardar_terminal.
 * - Nueva ciudad (ciudad-form.fxml), que se abre desde el formulario con el botón +.
 * - Taquillas de la terminal (terminal-detalle.fxml): tabla con rango de folios, estado, edición y baja.
 * Cada FXML recibe su propia instancia.
 */
public class TerminalesController {

    private static final Pattern TELEFONO = Pattern.compile("^[0-9]{10}$");

    @FXML private Label lblTitulo, lblNotaCiudad, lblError;
    @FXML private ComboBox<Catalogo> cbCiudad;
    @FXML private Button btnNuevaCiudad;
    @FXML private TextField txtClave, txtSucursal, txtTelefono;
    @FXML private TextArea txtDireccion;

    // Ventana de nueva ciudad
    @FXML private ListView<String> lista;

    // Taquillas de la terminal (lblTitulo y lblMensaje también los usa esta ventana)
    @FXML private Label lblMensaje;
    @FXML private TableView<Taquilla> tabla;
    @FXML private TableColumn<Taquilla, String> colNumero, colIdentificador, colPrefijo, colFolios, colEstatus;
    @FXML private TableColumn<Taquilla, Void> colAcciones;

    private final CiudadDAO ciudadDAO = new CiudadDAO();
    private final TerminalDAO terminalDAO = new TerminalDAO();
    private final TaquillaDAO taquillaDAO = new TaquillaDAO();
    private final ObservableList<Taquilla> taquillas = FXCollections.observableArrayList();
    private final List<Catalogo> registradas = new ArrayList<>();
    private List<Integer> conTerminal = List.of();
    private int idTerminal;
    private boolean edicion;
    private Consumer<Catalogo> alAgregar;
    private Catalogo terminal;

    @FXML
    private void initialize() {
        if (tabla == null) {
            return; // terminal-form.fxml y ciudad-form.fxml no tienen tabla que armar
        }
        colNumero.setCellValueFactory(c -> texto(String.format("%02d", c.getValue().idTaquilla())));
        colIdentificador.setCellValueFactory(c -> texto(c.getValue().identificador()));
        colPrefijo.setCellValueFactory(c -> texto(c.getValue().serie()));
        colFolios.setCellValueFactory(c -> texto(c.getValue().foliosUsados() + " de " + c.getValue().totalFolios()
                + " (" + c.getValue().rangoInicial() + " - " + c.getValue().rangoFinal() + ")"));
        colEstatus.setCellValueFactory(c -> texto(c.getValue().activa() ? "Activa" : "Inactiva"));
        Tablas.columnaGestion(colAcciones, Taquilla::activa, this::abrirFormularioTaquilla, null, q -> { },
                this::cambiarEstadoTaquilla, this::cambiarEstadoTaquilla);
        tabla.setItems(taquillas);
    }

    // ---- Formulario de terminal ----

    /** `terminal` es null para una terminal nueva; `ciudadesConTerminal` son los ids de las ciudades que ya tienen la suya. */
    public void preparar(Terminal terminal, List<Integer> ciudadesConTerminal) {
        conTerminal = ciudadesConTerminal;
        edicion = terminal != null;
        try {
            registradas.addAll(ciudadDAO.listar());
        } catch (SQLException e) {
            Mensaje.error(lblError, "No se pudieron cargar las ciudades: " + e.getMessage());
        }
        if (!edicion) {
            mostrarCiudadesLibres(null);
            return;
        }
        idTerminal = terminal.id();
        lblTitulo.setText("EDITAR TERMINAL");
        Catalogo ciudad = new Catalogo(terminal.idCiudad(), terminal.ciudad());
        cbCiudad.getItems().setAll(ciudad);
        cbCiudad.setValue(ciudad);
        cbCiudad.setDisable(true); // la ciudad no se cambia: las rutas y los boletos ya la usan
        btnNuevaCiudad.setDisable(true);
        lblNotaCiudad.setText("La ciudad de una terminal no se puede cambiar");
        txtClave.setText(terminal.clave());
        txtSucursal.setText(terminal.nombre());
        txtDireccion.setText(terminal.direccion());
        txtTelefono.setText(terminal.telefono());
    }

    private void mostrarCiudadesLibres(Catalogo seleccion) {
        List<Catalogo> libres = registradas.stream().filter(c -> !conTerminal.contains(c.id())).toList();
        cbCiudad.getItems().setAll(libres);
        cbCiudad.setValue(seleccion);
        lblNotaCiudad.setText(libres.isEmpty()
                ? "No hay ciudades sin terminal. Use + para agregar una."
                : "Solo se muestran las ciudades que aún no tienen terminal");
    }

    @FXML
    private void nuevaCiudad() {
        App.<TerminalesController>abrirVentana("ciudad-form", lblTitulo.getScene().getWindow(), c -> c.prepararCiudad(registradas, ciudad -> {
            registradas.add(ciudad);
            mostrarCiudadesLibres(ciudad);
        }));
    }

    // ---- Ventana de nueva ciudad ----

    /** Sin acentos ni mayúsculas, para comparar "Tuxtla Gutiérrez" con "TUXTLA GUTIERREZ". */
    private static String claveCiudad(String ciudad) {
        return Normalizer.normalize(ciudad, Normalizer.Form.NFD).replaceAll("\\p{M}", "").trim().toLowerCase();
    }

    /** `yaRegistradas`: ciudades que ya existen; no se ofrecen otra vez. */
    public void prepararCiudad(List<Catalogo> yaRegistradas, Consumer<Catalogo> alAgregar) {
        this.alAgregar = alAgregar;
        try {
            for (String lugar : ciudadDAO.lugares()) {
                if (yaRegistradas.stream().noneMatch(c -> claveCiudad(c.nombre()).equals(claveCiudad(lugar)))) {
                    lista.getItems().add(lugar);
                }
            }
        } catch (SQLException e) {
            Mensaje.error(lblError, "No se pudieron cargar los lugares: " + e.getMessage());
        }
    }

    @FXML
    private void agregarCiudad() {
        try {
            String nombre = lista.getSelectionModel().getSelectedItem();
            if (nombre == null) {
                throw new IllegalArgumentException("Elija una ciudad de la lista.");
            }
            alAgregar.accept(ciudadDAO.crear(nombre));
            cerrar();
        } catch (IllegalArgumentException e) {
            Mensaje.error(lblError, e.getMessage());
        } catch (SQLException e) {
            Mensaje.error(lblError, "No se pudo guardar: " + e.getMessage());
        }
    }

    @FXML
    private void guardar() {
        try {
            Catalogo ciudad = cbCiudad.getValue();
            if (ciudad == null) {
                throw new IllegalArgumentException("Elija la ciudad de la terminal.");
            }
            if (!edicion && conTerminal.contains(ciudad.id())) {
                throw new IllegalArgumentException("Esa ciudad ya tiene su terminal.");
            }
            String clave = Validar.texto(txtClave.getText(), "Escriba la clave.");
            String sucursal = Validar.texto(txtSucursal.getText(), "Escriba el nombre de la sucursal.");
            String direccion = Validar.texto(txtDireccion.getText(), "Escriba la dirección.");
            String telefono = txtTelefono.getText() == null ? "" : txtTelefono.getText().trim();
            if (!telefono.isEmpty() && !TELEFONO.matcher(telefono).matches()) {
                throw new IllegalArgumentException("El teléfono debe tener 10 dígitos.");
            }
            terminalDAO.guardar(idTerminal, ciudad.id(), clave, sucursal, direccion, telefono);
            cerrar();
        } catch (IllegalArgumentException e) {
            Mensaje.error(lblError, e.getMessage());
        } catch (SQLException e) {
            Mensaje.error(lblError, "23505".equals(e.getSQLState())
                    ? "Ya existe una terminal con esa clave o ese nombre." : "No se pudo guardar: " + e.getMessage());
        }
    }

    @FXML
    private void cerrar() {
        lblError.getScene().getWindow().hide();
    }

    // ---- Taquillas de la terminal ----

    public void prepararTaquillas(Catalogo terminal) {
        this.terminal = terminal;
        lblTitulo.setText("VER TAQUILLAS DE LA TERMINAL - " + terminal.nombre());
        cargarTaquillas();
    }

    @FXML
    private void nuevaTaquilla() {
        abrirFormularioTaquilla(null);
    }

    @FXML
    private void cerrarTaquillas() {
        lblTitulo.getScene().getWindow().hide();
    }

    private void cargarTaquillas() {
        try {
            taquillas.setAll(taquillaDAO.listar().stream().filter(q -> q.terminal().id() == terminal.id()).toList());
            Mensaje.ocultar(lblMensaje);
        } catch (SQLException e) {
            Mensaje.error(lblMensaje, "No se pudieron cargar las taquillas: " + e.getMessage());
        }
    }

    private void abrirFormularioTaquilla(Taquilla taquilla) {
        App.<TaquillasController>abrirVentana("taquilla-form", lblTitulo.getScene().getWindow(),
                f -> f.prepararFormulario(terminal, taquilla));
        cargarTaquillas();
    }

    /** Dar de baja no borra: la taquilla conserva sus folios usados y solo cambia su estado. */
    private void cambiarEstadoTaquilla(Taquilla q) {
        try {
            taquillaDAO.guardar(new Taquilla(q.idTaquilla(), q.terminal(), q.identificador(), q.serie(),
                    q.rangoInicial(), q.rangoFinal(), q.foliosUsados(), !q.activa()));
            cargarTaquillas();
        } catch (SQLException e) {
            Mensaje.error(lblMensaje, "No se pudo cambiar el estado: " + e.getMessage());
        }
    }
}
