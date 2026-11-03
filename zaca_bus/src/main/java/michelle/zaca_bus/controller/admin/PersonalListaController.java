package michelle.zaca_bus.controller.admin;

import static michelle.zaca_bus.controller.admin.EtiquetasPersonal.CONDUCTOR;
import static michelle.zaca_bus.controller.admin.EtiquetasPersonal.NOMBRE_PUESTO;
import static michelle.zaca_bus.controller.admin.EtiquetasPersonal.TODAS_CIUDADES;
import static michelle.zaca_bus.controller.admin.EtiquetasPersonal.TODAS_TERMINALES;
import static michelle.zaca_bus.controller.admin.EtiquetasPersonal.TODOS_LOS_PUESTOS;
import static michelle.zaca_bus.controller.admin.EtiquetasPersonal.etiquetaPuesto;
import static michelle.zaca_bus.util.Tablas.texto;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import michelle.zaca_bus.App;
import michelle.zaca_bus.dao.admin.PersonalDAO;
import michelle.zaca_bus.dao.admin.TaquillaDAO;
import michelle.zaca_bus.dao.admin.TerminalDAO;
import michelle.zaca_bus.model.Catalogo;
import michelle.zaca_bus.model.Empleado;
import michelle.zaca_bus.model.Taquilla;
import michelle.zaca_bus.util.Mensaje;
import michelle.zaca_bus.util.Tablas;

/** Pantalla personal.fxml: tabla de empleados con filtro por puesto; abre el formulario y la baja. */
public class PersonalListaController {

    private static final int DIAS_AVISO_LICENCIA = 30;

    @FXML private TableView<Empleado> tabla;
    @FXML private TableColumn<Empleado, String> colNombre, colCurp, colTelefono, colPuesto, colAsignacion;
    @FXML private TableColumn<Empleado, Void> colAcciones;
    @FXML private ComboBox<String> cbCiudad;
    @FXML private ComboBox<Catalogo> cbTerminalFiltro, cbPuestoFiltro;
    @FXML private Label lblMensaje;

    private final PersonalDAO dao = new PersonalDAO();
    private final TerminalDAO terminalDAO = new TerminalDAO();
    private final TaquillaDAO taquillaDAO = new TaquillaDAO();
    private AsignacionPersonal asignaciones;
    private final ObservableList<Empleado> personal = FXCollections.observableArrayList();

    @FXML
    private void initialize() {
        colNombre.setCellValueFactory(c -> texto(c.getValue().nombreCompleto()));
        colCurp.setCellValueFactory(c -> texto(c.getValue().curp()));
        colTelefono.setCellValueFactory(c -> texto(c.getValue().telefono() == null ? "—" : c.getValue().telefono()));
        colPuesto.setCellValueFactory(c -> texto(etiquetaPuesto(c.getValue().puesto().nombre())));
        colAsignacion.setCellValueFactory(c -> texto(asignaciones == null ? "—" : asignaciones.de(c.getValue())));
        colAcciones.setCellFactory(c -> new CeldaAcciones());

        cbPuestoFiltro.setConverter(NOMBRE_PUESTO);
        FilteredList<Empleado> visibles = new FilteredList<>(personal);
        cbPuestoFiltro.valueProperty().addListener((obs, antes, puesto) -> visibles.setPredicate(
                e -> puesto == null || puesto == TODOS_LOS_PUESTOS || e.puesto().id() == puesto.id()));
        tabla.setItems(visibles);
        cargar();
    }

    private void cargar() {
        try {
            asignaciones = new AsignacionPersonal();
            personal.setAll(dao.listar());
            Catalogo elegido = cbPuestoFiltro.getValue();
            cbPuestoFiltro.getItems().setAll(TODOS_LOS_PUESTOS);
            cbPuestoFiltro.getItems().addAll(dao.puestos());
            cbPuestoFiltro.setValue(cbPuestoFiltro.getItems().stream()
                    .filter(p -> elegido != null && p.id() == elegido.id()).findFirst().orElse(TODOS_LOS_PUESTOS));
            // pendiente backend: ciudad y terminal aún no filtran porque el empleado no guarda su terminal
            cbCiudad.getItems().setAll(TODAS_CIUDADES);
            cbCiudad.getItems().addAll(terminalDAO.listar().stream().map(Catalogo::nombre).toList());
            cbCiudad.setValue(TODAS_CIUDADES);
            cbTerminalFiltro.getItems().setAll(TODAS_TERMINALES);
            cbTerminalFiltro.getItems().addAll(terminalDAO.listar());
            cbTerminalFiltro.setValue(cbTerminalFiltro.getItems().get(0));
            Mensaje.ocultar(lblMensaje);
        } catch (SQLException e) {
            Mensaje.error(lblMensaje, "No se pudo cargar el personal: " + e.getMessage());
        }
    }

    @FXML
    private void nuevoEmpleado() {
        abrirEditor(null);
    }

    /** `empleado` es null para un alta. */
    private void abrirEditor(Empleado empleado) {
        try {
            List<Catalogo> puestos = dao.puestos();
            List<Catalogo> terminales = terminalDAO.listar();
            List<Taquilla> todasTaquillas = taquillaDAO.listar();
            App.<EmpleadoFormController>abrirVentana("empleado-form", tabla.getScene().getWindow(),
                    formulario -> formulario.preparar(empleado, puestos, terminales, todasTaquillas));
            cargar();
        } catch (SQLException e) {
            Mensaje.error(lblMensaje, "No se pudo abrir el formulario: " + e.getMessage());
        }
    }

    private void abrirEliminar(Empleado empleado) {
        App.<EliminarEmpleadoController>abrirVentana("eliminar-empleado", tabla.getScene().getWindow(),
                confirmacion -> confirmacion.preparar(empleado));
        cargar();
    }

    /** Acciones de una fila: Editar y Dar de baja, y el aviso de licencia al lado si es chofer. */
    private final class CeldaAcciones extends TableCell<Empleado, Void> {

        private final Button editar = Tablas.botonChico("Editar");
        private final Button baja = Tablas.botonChico("Dar de baja");
        private final HBox caja = new HBox(8, editar, baja);

        CeldaAcciones() {
            editar.setMinWidth(Region.USE_PREF_SIZE);
            baja.setMinWidth(Region.USE_PREF_SIZE); // sin esto el texto se recorta
            baja.getStyleClass().remove("boton-secundario");
            baja.getStyleClass().add("boton-peligro");
            caja.setAlignment(Pos.CENTER_LEFT);
            editar.setOnAction(e -> abrirEditor(fila()));
            baja.setOnAction(e -> abrirEliminar(fila()));
        }

        private Empleado fila() {
            return getTableView().getItems().get(getIndex());
        }

        @Override
        protected void updateItem(Void vacio, boolean esVacia) {
            super.updateItem(vacio, esVacia);
            if (esVacia) {
                setGraphic(null);
                return;
            }
            caja.getChildren().setAll(editar, baja);
            if (CONDUCTOR.equals(fila().puesto().nombre())) {
                caja.getChildren().add(chipLicencia(fila().vigencia()));
            }
            setGraphic(caja);
        }
    }

    /** Aviso visual: vencida, por vencer en los próximos 30 días, o vigente. */
    private static Label chipLicencia(LocalDate vigencia) {
        LocalDate hoy = LocalDate.now();
        String texto;
        String tipo;
        if (vigencia == null) {
            texto = "Sin licencia";
            tipo = "chip-error";
        } else if (vigencia.isBefore(hoy)) {
            texto = "Licencia vencida " + vigencia;
            tipo = "chip-error";
        } else if (!vigencia.isAfter(hoy.plusDays(DIAS_AVISO_LICENCIA))) {
            texto = "Licencia vence " + vigencia;
            tipo = "chip-aviso";
        } else {
            texto = "Licencia Vigente";
            tipo = "chip-ok";
        }
        Label chip = new Label(texto);
        chip.getStyleClass().addAll("chip", tipo);
        return chip;
    }
}
