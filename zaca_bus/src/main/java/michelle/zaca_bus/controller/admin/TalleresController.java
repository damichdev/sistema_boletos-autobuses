package michelle.zaca_bus.controller.admin;

import static michelle.zaca_bus.util.Tablas.texto;

import java.sql.SQLException;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import michelle.zaca_bus.App;
import michelle.zaca_bus.dao.admin.CiudadDAO;
import michelle.zaca_bus.dao.admin.TallerDAO;
import michelle.zaca_bus.model.Catalogo;
import michelle.zaca_bus.model.JefeTaller;
import michelle.zaca_bus.model.Taller;
import michelle.zaca_bus.util.Mensaje;
import michelle.zaca_bus.util.Tablas;
import michelle.zaca_bus.util.Validar;

/**
 * Módulo Talleres: lista (taller.fxml) y formulario de taller (taller-form.fxml); cada FXML recibe su propia instancia.
 * Guardar usa sp_guardar_taller. Autobuses y encargado salen de la base externa (FDW): pendientes.
 */
public class TalleresController {

    private static final String TODAS = "Todas las ciudades";

    // Pantalla de lista
    @FXML private TableView<Taller> tabla;
    @FXML private TableColumn<Taller, String> colClave, colNombre, colDireccion, colAutobuses, colMecanicos, colEncargado, colEstado;
    @FXML private TableColumn<Taller, Void> colAcciones;
    @FXML private TextField txtBuscar;

    // Formulario
    @FXML private Label lblTitulo, lblError;
    @FXML private ComboBox<JefeTaller> cbEncargado;
    @FXML private TextField txtClave, txtNombre;
    @FXML private TextArea txtDireccion;

    // En la lista filtra por ciudad; en el formulario es la ciudad del taller
    @FXML private ComboBox<String> cbCiudad;

    private final TallerDAO dao = new TallerDAO();
    private final CiudadDAO ciudadDAO = new CiudadDAO();
    private final ObservableList<Taller> talleres = FXCollections.observableArrayList();
    private List<Catalogo> ciudades = List.of();
    private int idTaller;

    @FXML
    private void initialize() {
        if (tabla == null) {
            return; // se está cargando taller-form.fxml: no hay lista que armar
        }
        colClave.setCellValueFactory(c -> texto(c.getValue().clave()));
        colNombre.setCellValueFactory(c -> texto(c.getValue().nombre()));
        colDireccion.setCellValueFactory(c -> texto(c.getValue().direccion()));
        colAutobuses.setCellValueFactory(c -> texto(c.getValue().autobuses() + " autobuses"));
        colMecanicos.setCellValueFactory(c -> texto(plural(c.getValue().mecanicos(), "mecánico")));
        colEncargado.setCellValueFactory(c -> texto(c.getValue().encargado() == null ? "Sin asignar" : c.getValue().encargado()));
        colEstado.setCellValueFactory(c -> texto(c.getValue().activo() ? "Activo" : "Inactivo"));
        Tablas.columnaGestion(colAcciones, Taller::activo, this::abrirEditor,
                "Ver Órdenes", t -> App.irA("ordenes-taller"), "Desactivar", t -> cambiarEstado(t, false),
                t -> cambiarEstado(t, true));

        FilteredList<Taller> visibles = new FilteredList<>(talleres);
        Runnable filtrar = () -> {
            String texto = txtBuscar.getText().trim().toLowerCase();
            visibles.setPredicate(t -> (TODAS.equals(cbCiudad.getValue()) || t.ciudad().equals(cbCiudad.getValue()))
                    && (t.nombre() + " " + (t.encargado() == null ? "" : t.encargado())).toLowerCase().contains(texto));
        };
        cbCiudad.valueProperty().addListener((obs, a, b) -> filtrar.run());
        txtBuscar.textProperty().addListener((obs, a, b) -> filtrar.run());
        tabla.setItems(visibles);
        recargar();
    }

    /** Vuelve a leer los talleres y las ciudades del filtro, conservando la ciudad elegida. */
    private void recargar() {
        try {
            talleres.setAll(dao.listar());
        } catch (SQLException e) {
            Mensaje.error(lblError, "No se pudieron cargar los talleres: " + e.getMessage());
            return;
        }
        String elegida = cbCiudad.getValue();
        cbCiudad.getItems().setAll(TODAS);
        talleres.stream().map(Taller::ciudad).distinct().forEach(cbCiudad.getItems()::add);
        cbCiudad.setValue(cbCiudad.getItems().contains(elegida) ? elegida : TODAS);
    }

    /** Z0052: no se desactiva un taller con personal activo; el mensaje de la base lo explica. */
    private void cambiarEstado(Taller taller, boolean activo) {
        try {
            dao.cambiarEstado(taller.idTaller(), activo);
            Mensaje.ocultar(lblError);
            recargar();
        } catch (SQLException e) {
            Mensaje.error(lblError, "Z0052".equals(e.getSQLState()) ? e.getMessage() : "No se pudo cambiar el estado: " + e.getMessage());
        }
    }

    private static String plural(int n, String palabra) {
        return n + " " + palabra + (n == 1 ? "" : "s");
    }

    @FXML
    private void nuevoTaller() {
        abrirEditor(null);
    }

    /** `taller` es null para un alta. */
    private void abrirEditor(Taller taller) {
        App.<TalleresController>abrirVentana("taller-form", tabla.getScene().getWindow(), f -> f.prepararFormulario(taller));
        recargar();
    }

    // ---- Formulario ----

    /** `taller` es null para un alta. */
    public void prepararFormulario(Taller taller) {
        try {
            ciudades = ciudadDAO.listar();
        } catch (SQLException e) {
            ciudades = List.of();
            Mensaje.error(lblError, "No se pudieron cargar las ciudades: " + e.getMessage());
        }
        cbCiudad.getItems().setAll(ciudades.stream().map(Catalogo::nombre).toList());
        idTaller = taller == null ? 0 : taller.idTaller();
        try {
            cbEncargado.getItems().setAll(dao.encargadosDisponibles(idTaller));
        } catch (SQLException e) {
            Mensaje.error(lblError, "No se pudieron cargar los jefes de taller: " + e.getMessage());
        }
        if (taller == null) {
            return;
        }
        lblTitulo.setText("EDITAR TALLER");
        cbCiudad.setValue(taller.ciudad());
        txtClave.setText(taller.clave());
        txtNombre.setText(taller.nombre());
        txtDireccion.setText(taller.direccion());
        cbEncargado.getItems().stream().filter(j -> j.idTaller() != null).findFirst().ifPresent(cbEncargado::setValue);
    }

    @FXML
    private void guardar() {
        try {
            Catalogo ciudad = ciudades.stream().filter(c -> c.nombre().equals(cbCiudad.getValue())).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Elija la ciudad."));
            dao.guardar(idTaller, ciudad.id(), Validar.texto(txtClave.getText(), "Escriba la clave."),
                    Validar.texto(txtNombre.getText(), "Escriba el nombre del taller."),
                    Validar.texto(txtDireccion.getText(), "Escriba la dirección."),
                    cbEncargado.getValue() == null ? null : cbEncargado.getValue().idPersonal());
            cerrar();
        } catch (IllegalArgumentException e) {
            Mensaje.error(lblError, e.getMessage());
        } catch (SQLException e) {
            Mensaje.error(lblError, "23505".equals(e.getSQLState())
                    ? "Ya existe un taller con esa clave o ese nombre." : "No se pudo guardar: " + e.getMessage());
        }
    }

    @FXML
    private void cerrar() {
        lblTitulo.getScene().getWindow().hide();
    }
}
