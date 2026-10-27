package michelle.zaca_bus.controller;

import static michelle.zaca_bus.util.Tablas.texto;

import java.sql.SQLException;
import java.text.NumberFormat;
import java.time.Year;
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
import michelle.zaca_bus.dao.UnidadDAO;
import michelle.zaca_bus.model.Catalogo;
import michelle.zaca_bus.model.Unidad;
import michelle.zaca_bus.util.Mensaje;
import michelle.zaca_bus.util.Tablas;
import michelle.zaca_bus.util.Validar;

/**
 * Módulo Unidades: lista con buscador y su formulario en ventana emergente.
 * Controla unidades.fxml y unidad-form.fxml; cada FXML recibe su propia instancia.
 */
public class UnidadesController {

    private static final String PLACA_REPETIDA = "23505";

    // Pantalla de lista
    @FXML private TableView<Unidad> tabla;
    @FXML private TableColumn<Unidad, String> colPlaca, colModelo, colAnio, colTipo, colAcomodo, colKm;
    @FXML private TableColumn<Unidad, Void> colAcciones;
    @FXML private TextField txtBuscar;
    @FXML private Label lblMensaje;

    // Formulario
    @FXML private Label lblTitulo, lblError;
    @FXML private TextField txtPlaca, txtModelo, txtAnio, txtKm, txtCapacidad;
    @FXML private ComboBox<Catalogo> cbTipo, cbAcomodo;

    private final UnidadDAO dao = new UnidadDAO();
    private final ObservableList<Unidad> unidades = FXCollections.observableArrayList();
    private int idUnidad;

    @FXML
    private void initialize() {
        if (tabla == null) {
            return; // se está cargando unidad-form.fxml: no hay lista que armar
        }
        colPlaca.setCellValueFactory(c -> texto(c.getValue().placa()));
        colModelo.setCellValueFactory(c -> texto(c.getValue().modelo()));
        colAnio.setCellValueFactory(c -> texto(String.valueOf(c.getValue().anio())));
        colTipo.setCellValueFactory(c -> texto(c.getValue().tipo().nombre()));
        colAcomodo.setCellValueFactory(c -> texto(c.getValue().acomodo().nombre() + " · " + c.getValue().capacidad() + " asientos"));
        colKm.setCellValueFactory(c -> texto(NumberFormat.getIntegerInstance().format(c.getValue().km())));
        Tablas.columnaEditar(colAcciones, this::abrirEditor);
        tabla.setItems(Tablas.filtrar(unidades, txtBuscar, u -> u.placa() + " " + u.modelo()));
        cargar();
    }

    // ---- Pantalla de lista ----

    @FXML
    private void nuevaUnidad() {
        abrirEditor(null);
    }

    private void cargar() {
        try {
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
            List<Catalogo> acomodos = dao.acomodos();
            App.<UnidadesController>abrirVentana("unidad-form", tabla.getScene().getWindow(),
                    formulario -> formulario.prepararFormulario(unidad, tipos, acomodos));
            cargar();
        } catch (SQLException e) {
            Mensaje.error(lblMensaje, "No se pudo abrir el formulario: " + e.getMessage());
        }
    }

    // ---- Formulario ----

    /** `unidad` es null para una unidad nueva. */
    public void prepararFormulario(Unidad unidad, List<Catalogo> tipos, List<Catalogo> acomodos) {
        cbTipo.getItems().setAll(tipos);
        cbAcomodo.getItems().setAll(acomodos);
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
        cbAcomodo.setValue(unidad.acomodo());
        txtCapacidad.setText(String.valueOf(unidad.capacidad()));
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
                cbTipo.getValue(), cbAcomodo.getValue(),
                Validar.entero(txtCapacidad.getText(), 1, Integer.MAX_VALUE, "La capacidad debe ser un entero mayor que 0."));
    }
}
