package michelle.zaca_bus.controller.jefetaller;

import static michelle.zaca_bus.util.Tablas.texto;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import michelle.zaca_bus.App;
import michelle.zaca_bus.dao.admin.OrdenTallerDAO;
import michelle.zaca_bus.dao.jefetaller.TallerJefeDAO;
import michelle.zaca_bus.model.Catalogo;
import michelle.zaca_bus.model.OrdenTaller;
import michelle.zaca_bus.util.Formato;
import michelle.zaca_bus.util.Mensaje;
import michelle.zaca_bus.util.Sesion;
import michelle.zaca_bus.util.Tablas;

/**
 * Órdenes del jefe de taller: lista (ordenes-jefe.fxml) y los formularios de ingreso y cierre; cada FXML recibe su
 * propia instancia. Siempre trabaja con el taller de la cuenta que inició sesión.
 */
public class OrdenesJefeController {

    private static final String COMPLETADO = "Completado";
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    // Lista
    @FXML private TableView<OrdenTaller> tabla;
    @FXML private TableColumn<OrdenTaller, String> colOrden, colUnidad, colServicio, colMecanico, colIngreso, colEstado;
    @FXML private TableColumn<OrdenTaller, Void> colAcciones;
    @FXML private TextField txtBuscar;

    // Formularios (ingreso y cierre)
    @FXML private Label lblTitulo, lblOrden, lblIngreso, lblError, lblAviso;
    @FXML private ComboBox<String> cbUnidad, cbTipo, cbEstadoFinal;
    @FXML private ComboBox<Catalogo> cbMecanico;
    @FXML private DatePicker dpIngreso, dpSalida;
    @FXML private TextField txtHoraIngreso, txtHoraSalida, txtCosto, txtDiagnostico, txtObservaciones;

    private final OrdenTallerDAO ordenDAO = new OrdenTallerDAO();
    private final TallerJefeDAO dao = new TallerJefeDAO();
    private final ObservableList<OrdenTaller> ordenes = FXCollections.observableArrayList();
    private OrdenTaller ordenACerrar;

    /** El taller del jefe sale de su cuenta; sin taller no hay nada que mostrar ni registrar. */
    private static Integer idTaller() {
        Catalogo taller = Sesion.actual() == null ? null : Sesion.actual().taller();
        return taller == null ? null : taller.id();
    }

    @FXML
    private void initialize() {
        if (tabla == null) {
            return; // se está cargando un formulario: no hay lista que armar
        }
        colOrden.setCellValueFactory(c -> texto(c.getValue().numero()));
        colUnidad.setCellValueFactory(c -> texto(c.getValue().unidad()));
        colServicio.setCellValueFactory(c -> texto(c.getValue().servicio()));
        colMecanico.setCellValueFactory(c -> texto(c.getValue().mecanico()));
        colIngreso.setCellValueFactory(c -> texto(c.getValue().ingreso()));
        colEstado.setCellValueFactory(c -> texto(c.getValue().estado()));
        colEstado.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(String estado, boolean vacia) {
                super.updateItem(estado, vacia);
                if (vacia) {
                    setGraphic(null);
                    return;
                }
                Label chip = new Label(estado);
                chip.getStyleClass().addAll("chip", COMPLETADO.equals(estado) ? "chip-ok" : "chip-aviso");
                setGraphic(chip);
            }
        });
        Tablas.columnaEditar(colAcciones, this::abrirCierre, "Cerrar orden");
        tabla.setItems(Tablas.filtrar(ordenes, txtBuscar, o -> o.numero() + " " + o.unidad() + " " + o.mecanico()));
        recargar();
    }

    private void recargar() {
        Integer taller = idTaller();
        if (taller == null) {
            Mensaje.error(lblError, "Su cuenta no tiene un taller asignado: pida al administrador que se lo asigne.");
            return;
        }
        try {
            ordenes.setAll(ordenDAO.listar(taller));
        } catch (SQLException e) {
            Mensaje.error(lblError, "No se pudieron cargar las órdenes: " + e.getMessage());
        }
    }

    @FXML
    private void nuevoIngreso() {
        App.<OrdenesJefeController>abrirVentana("orden-ingreso-form", tabla.getScene().getWindow(), f -> f.prepararIngreso());
        recargar();
    }

    private void abrirCierre(OrdenTaller orden) {
        if (COMPLETADO.equals(orden.estado())) {
            return; // ya está cerrada
        }
        App.<OrdenesJefeController>abrirVentana("orden-cerrar-form", tabla.getScene().getWindow(), f -> f.prepararCierre(orden));
        recargar();
    }

    // ---- Formulario de ingreso ----

    private void prepararIngreso() {
        Integer taller = idTaller();
        try {
            cbUnidad.getItems().setAll(dao.unidadesLibres());
            if (taller != null) {
                cbMecanico.getItems().setAll(dao.mecanicos(taller));
            }
        } catch (SQLException e) {
            Mensaje.error(lblError, "No se pudieron cargar los datos: " + e.getMessage());
        }
        cbTipo.getItems().setAll("Preventivo", "Correctivo");
        dpIngreso.setValue(LocalDate.now(Formato.ZONA));
        txtHoraIngreso.setText(LocalTime.now(Formato.ZONA).format(HORA));
        txtCosto.setText("0");
        cbUnidad.valueProperty().addListener((obs, antes, placa) -> avisarSalidas(placa));
    }

    /** Si la unidad ya tiene salidas programadas, el despacho tendrá que cambiarles la unidad. */
    private void avisarSalidas(String placa) {
        Mensaje.ocultar(lblAviso);
        try {
            int pendientes = placa == null ? 0 : dao.salidasPendientes(placa);
            if (pendientes > 0) {
                lblAviso.setText("Esa unidad tiene " + pendientes + " salida(s) programada(s): el despacho deberá cambiarles la unidad.");
                lblAviso.setVisible(true);
                lblAviso.setManaged(true);
            }
        } catch (SQLException e) {
            Mensaje.error(lblError, "No se pudieron revisar las salidas: " + e.getMessage());
        }
    }

    @FXML
    private void registrarIngreso() {
        try {
            if (cbUnidad.getValue() == null || cbMecanico.getValue() == null || cbTipo.getValue() == null) {
                throw new IllegalArgumentException("Elija la unidad, el mecánico y el tipo de mantenimiento.");
            }
            ZonedDateTime ingreso = fechaHora(dpIngreso, txtHoraIngreso);
            ordenDAO.registrarIngreso(cbUnidad.getValue(), idTaller(), cbMecanico.getValue().id(),
                    "Preventivo".equals(cbTipo.getValue()) ? 2 : 3, ingreso, txtDiagnostico.getText().trim(), costo(),
                    txtObservaciones.getText().trim());
            cerrar();
        } catch (IllegalArgumentException e) {
            Mensaje.error(lblError, e.getMessage());
        } catch (SQLException e) {
            Mensaje.error(lblError, mensaje(e));
        }
    }

    // ---- Formulario de cierre ----

    private void prepararCierre(OrdenTaller orden) {
        ordenACerrar = orden;
        lblOrden.setText(orden.numero() + " · " + orden.unidad());
        lblIngreso.setText("Ingresó " + orden.ingreso());
        cbEstadoFinal.getItems().setAll("Operativo", "De baja");
        cbEstadoFinal.setValue("Operativo");
        dpSalida.setValue(LocalDate.now(Formato.ZONA));
        txtHoraSalida.setText(LocalTime.now(Formato.ZONA).format(HORA));
        txtDiagnostico.setText(orden.descripcion());
        txtCosto.setText(orden.costo().replaceAll("[^0-9.]", ""));
    }

    @FXML
    private void registrarCierre() {
        try {
            if (cbEstadoFinal.getValue() == null) {
                throw new IllegalArgumentException("Elija el estado del bus al salir.");
            }
            ordenDAO.cerrarOrden(ordenACerrar.idOrden(), idTaller(), fechaHora(dpSalida, txtHoraSalida),
                    txtDiagnostico.getText().trim(), costo(), "Operativo".equals(cbEstadoFinal.getValue()) ? 1 : 4);
            cerrar();
        } catch (IllegalArgumentException e) {
            Mensaje.error(lblError, e.getMessage());
        } catch (SQLException e) {
            Mensaje.error(lblError, mensaje(e));
        }
    }

    // ---- Comunes de los formularios ----

    private static ZonedDateTime fechaHora(DatePicker fecha, TextField hora) {
        if (fecha.getValue() == null) {
            throw new IllegalArgumentException("Elija la fecha.");
        }
        try {
            return fecha.getValue().atTime(LocalTime.parse(hora.getText().trim())).atZone(Formato.ZONA);
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("La hora debe tener el formato hh:mm (por ejemplo 08:30).");
        }
    }

    private BigDecimal costo() {
        try {
            return new BigDecimal(txtCosto.getText().trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("El costo debe ser un número (por ejemplo 1500.50).");
        }
    }

    /** Las reglas de la base (Z0050 a Z0058) llegan con texto claro; lo demás se muestra con su causa. */
    private static String mensaje(SQLException e) {
        String estado = String.valueOf(e.getSQLState());
        return estado.startsWith("Z005") ? e.getMessage() : "No se pudo guardar: " + e.getMessage();
    }

    @FXML
    private void cerrar() {
        lblTitulo.getScene().getWindow().hide();
    }
}
