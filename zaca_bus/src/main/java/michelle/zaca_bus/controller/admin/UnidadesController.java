package michelle.zaca_bus.controller.admin;

import static michelle.zaca_bus.util.Tablas.texto;

import java.sql.SQLException;
import java.text.NumberFormat;
import java.time.Year;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.Window;
import michelle.zaca_bus.App;
import michelle.zaca_bus.dao.admin.UnidadDAO;
import michelle.zaca_bus.model.Acomodo;
import michelle.zaca_bus.model.Catalogo;
import michelle.zaca_bus.model.TipoBus;
import michelle.zaca_bus.model.Unidad;
import michelle.zaca_bus.util.Mensaje;
import michelle.zaca_bus.util.Tablas;
import michelle.zaca_bus.util.Validar;

/**
 * Módulo Unidades: lista con buscador, su formulario en ventana emergente y los acomodos (plantillas de asientos).
 * Controla unidades.fxml, unidad-form.fxml, acomodos-lista.fxml y acomodo-form.fxml; cada FXML recibe su propia
 * instancia. Un acomodo es una fila de viajes.tipo_bus: tipo de servicio, distribución de asientos y capacidad.
 */
public class UnidadesController {

    private static final String PLACA_REPETIDA = "23505";

    // Pantalla de lista
    @FXML private TableView<Unidad> tabla;
    @FXML private TableColumn<Unidad, String> colPlaca, colModelo, colAnio, colTipo, colAcomodo, colKm, colTaller;
    @FXML private TableColumn<Unidad, Void> colAcciones;
    @FXML private TextField txtBuscar;
    @FXML private Label lblMensaje;

    // Formulario
    @FXML private Label lblTitulo, lblError;
    @FXML private TextField txtPlaca, txtModelo, txtAnio, txtKm, txtCapacidad;
    @FXML private ComboBox<Catalogo> cbTipo;
    @FXML private ComboBox<Acomodo> cbAcomodo;

    // Lista de acomodos (lblMensaje es el aviso de las listas)
    @FXML private TableView<TipoBus> tablaAcomodos;
    @FXML private TableColumn<TipoBus, String> colServicio, colDistribucion, colCapacidad;

    // Formulario de acomodo (txtCapacidad y lblError se llaman igual que en el formulario de unidad)
    @FXML private ComboBox<Catalogo> cbServicio, cbDistribucion;
    @FXML private Button btnCerrar;

    private final UnidadDAO dao = new UnidadDAO();
    private final ObservableList<Unidad> unidades = FXCollections.observableArrayList();
    private final ObservableList<TipoBus> acomodos = FXCollections.observableArrayList();
    private final Map<String, String> estatusTaller = new HashMap<>();
    private int idUnidad;

    @FXML
    private void initialize() {
        if (tablaAcomodos != null) {
            armarListaAcomodos();
            return;
        }
        if (cbDistribucion != null) {
            armarFormularioAcomodo();
            return;
        }
        if (tabla == null) {
            return; // se está cargando unidad-form.fxml: no hay lista que armar
        }
        colPlaca.setCellValueFactory(c -> texto(c.getValue().placa()));
        colModelo.setCellValueFactory(c -> texto(c.getValue().modelo()));
        colAnio.setCellValueFactory(c -> texto(String.valueOf(c.getValue().anio())));
        colTipo.setCellValueFactory(c -> texto(c.getValue().tipo().nombre()));
        colAcomodo.setCellValueFactory(c -> texto(c.getValue().acomodo().nombre() + " · " + c.getValue().capacidad() + " asientos"));
        colKm.setCellValueFactory(c -> texto(NumberFormat.getIntegerInstance().format(c.getValue().km())));
        colTaller.setCellValueFactory(c -> texto(estatusTaller.getOrDefault(c.getValue().placa(), "—")));
        Tablas.columnaEditarBaja(colAcciones, this::abrirEditor, this::darDeBaja);
        tabla.setItems(Tablas.filtrar(unidades, txtBuscar, u -> u.placa() + " " + u.modelo()));
        cargar();
    }

    // ---- Pantalla de lista ----

    @FXML
    private void nuevaUnidad() {
        abrirEditor(null);
    }

    @FXML
    private void verAcomodos() {
        App.<UnidadesController>abrirVentana("acomodos-lista", tabla.getScene().getWindow(), c -> { });
    }

    private void darDeBaja(Unidad unidad) {
        try {
            dao.cambiarEstado(unidad.idUnidad(), false);
            Mensaje.exito(lblMensaje, "Unidad " + unidad.placa() + " dada de baja.");
            cargar();
        } catch (SQLException e) {
            Mensaje.error(lblMensaje, "No se pudo dar de baja: " + e.getMessage());
        }
    }

    private void cargar() {
        try {
            estatusTaller.clear();
            estatusTaller.putAll(dao.estatusTaller());
            unidades.setAll(dao.listar());
            Mensaje.ocultar(lblMensaje);
        } catch (SQLException e) {
            Mensaje.error(lblMensaje, "No se pudieron cargar las unidades: " + e.getMessage());
        }
    }

    /** `unidad` es null para una unidad nueva. */
    private void abrirEditor(Unidad unidad) {
        try {
            List<Catalogo> tipos = dao.tipos();
            App.<UnidadesController>abrirVentana("unidad-form", tabla.getScene().getWindow(),
                    formulario -> formulario.prepararFormulario(unidad, tipos));
            cargar();
        } catch (SQLException e) {
            Mensaje.error(lblMensaje, "No se pudo abrir el formulario: " + e.getMessage());
        }
    }

    // ---- Formulario ----

    /** `unidad` es null para una unidad nueva. */
    public void prepararFormulario(Unidad unidad, List<Catalogo> tipos) {
        cbTipo.getItems().setAll(tipos);
        // Acomodo espera al tipo de servicio y capacidad espera al acomodo; la capacidad sale del acomodo elegido.
        cbAcomodo.disableProperty().bind(cbTipo.valueProperty().isNull());
        txtCapacidad.disableProperty().bind(cbAcomodo.valueProperty().isNull());
        cbTipo.valueProperty().addListener((obs, antes, tipo) -> cargarAcomodos(tipo));
        cbAcomodo.valueProperty().addListener((obs, antes, acomodo) ->
                txtCapacidad.setText(acomodo == null ? "" : String.valueOf(acomodo.capacidad())));
        if (unidad == null) {
            return;
        }
        idUnidad = unidad.idUnidad();
        lblTitulo.setText("Editar unidad");
        txtPlaca.setText(unidad.placa());
        txtModelo.setText(unidad.modelo());
        txtAnio.setText(String.valueOf(unidad.anio()));
        txtKm.setText(String.valueOf(unidad.km()));
        cbTipo.setValue(unidad.tipo());
        cbAcomodo.getItems().stream()
                .filter(a -> a.idLayout() == unidad.acomodo().id() && a.capacidad() == unidad.capacidad())
                .findFirst().ifPresent(cbAcomodo::setValue);
    }

    /** Llena el combo con los acomodos registrados para ese tipo de servicio. */
    private void cargarAcomodos(Catalogo tipo) {
        cbAcomodo.setValue(null);
        cbAcomodo.getItems().clear();
        if (tipo == null) {
            return;
        }
        try {
            cbAcomodo.getItems().setAll(dao.acomodosDe(tipo.id()));
        } catch (SQLException e) {
            Mensaje.error(lblError, "No se pudieron cargar los acomodos: " + e.getMessage());
        }
    }

    @FXML
    private void guardar() {
        try {
            dao.guardar(leerFormulario());
            cerrar();
        } catch (IllegalArgumentException e) {
            Mensaje.error(lblError, e.getMessage());
        } catch (SQLException e) {
            Mensaje.error(lblError, PLACA_REPETIDA.equals(e.getSQLState())
                    ? "Ya existe una unidad con esa placa." : "No se pudo guardar: " + e.getMessage());
        }
    }

    @FXML
    private void cerrar() {
        lblError.getScene().getWindow().hide();
    }

    /** Reglas del script: placa y modelo obligatorios, año entre 1901 y el siguiente, km >= 0, capacidad > 0. */
    private Unidad leerFormulario() {
        if (cbTipo.getValue() == null || cbAcomodo.getValue() == null) {
            throw new IllegalArgumentException("Elija el tipo de servicio y el acomodo.");
        }
        int anioMaximo = Year.now().getValue() + 1;
        return new Unidad(idUnidad,
                Validar.texto(txtPlaca.getText(), "Escriba la placa."),
                Validar.texto(txtModelo.getText(), "Escriba el modelo."),
                Validar.entero(txtAnio.getText(), 1901, anioMaximo, "El año debe estar entre 1901 y " + anioMaximo + "."),
                Validar.entero(txtKm.getText(), 0, Integer.MAX_VALUE, "El kilometraje debe ser un entero de 0 o más."),
                cbTipo.getValue(), new Catalogo(cbAcomodo.getValue().idLayout(), cbAcomodo.getValue().nombre()),
                cbAcomodo.getValue().capacidad());
    }

    // ---- Acomodos ----

    private void armarListaAcomodos() {
        colServicio.setCellValueFactory(c -> texto(c.getValue().servicio().nombre()));
        colDistribucion.setCellValueFactory(c -> texto(c.getValue().acomodo().nombre()));
        colCapacidad.setCellValueFactory(c -> texto(c.getValue().acomodo().capacidad() + " asientos"));
        tablaAcomodos.setItems(acomodos);
        cargarListaAcomodos();
    }

    private void cargarListaAcomodos() {
        try {
            acomodos.setAll(dao.acomodos());
            Mensaje.ocultar(lblMensaje);
        } catch (SQLException e) {
            Mensaje.error(lblMensaje, "No se pudieron cargar los acomodos: " + e.getMessage());
        }
    }

    private void armarFormularioAcomodo() {
        try {
            cbServicio.getItems().setAll(dao.tipos());
            cbDistribucion.getItems().setAll(dao.distribuciones());
        } catch (SQLException e) {
            Mensaje.error(lblError, "No se pudieron cargar las opciones: " + e.getMessage());
        }
    }

    /** Se abre desde la lista de unidades y desde la lista de acomodos; en esta última se refresca al volver. */
    @FXML
    private void nuevoAcomodo() {
        Window dueno = (tabla != null ? tabla : btnCerrar).getScene().getWindow();
        App.<UnidadesController>abrirVentana("acomodo-form", dueno, c -> { });
        if (tablaAcomodos != null) {
            cargarListaAcomodos();
        }
    }

    @FXML
    private void guardarAcomodo() {
        try {
            if (cbServicio.getValue() == null || cbDistribucion.getValue() == null) {
                throw new IllegalArgumentException("Elija el tipo de servicio y la distribución de asientos.");
            }
            int capacidad = Validar.entero(txtCapacidad.getText(), 1, Integer.MAX_VALUE, "La capacidad debe ser un entero de 1 o más.");
            if (!dao.guardarAcomodo(cbServicio.getValue().id(), cbDistribucion.getValue().id(), capacidad)) {
                throw new IllegalArgumentException("Ese acomodo ya está registrado para ese tipo de servicio.");
            }
            cerrarAcomodo();
        } catch (IllegalArgumentException e) {
            Mensaje.error(lblError, e.getMessage());
        } catch (SQLException e) {
            Mensaje.error(lblError, "No se pudo guardar: " + e.getMessage());
        }
    }

    @FXML
    private void cerrarAcomodo() {
        btnCerrar.getScene().getWindow().hide();
    }
}
