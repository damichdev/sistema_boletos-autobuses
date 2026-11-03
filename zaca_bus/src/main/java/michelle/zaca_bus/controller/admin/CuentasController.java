package michelle.zaca_bus.controller.admin;

import static michelle.zaca_bus.controller.admin.EtiquetasPersonal.CONDUCTOR;
import static michelle.zaca_bus.controller.admin.EtiquetasPersonal.TODAS_CIUDADES;
import static michelle.zaca_bus.controller.admin.EtiquetasPersonal.TODAS_TERMINALES;
import static michelle.zaca_bus.controller.admin.EtiquetasPersonal.etiquetaPuesto;
import static michelle.zaca_bus.util.Tablas.texto;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import michelle.zaca_bus.App;
import michelle.zaca_bus.dao.admin.CuentaDAO;
import michelle.zaca_bus.dao.admin.PersonalDAO;
import michelle.zaca_bus.dao.admin.TerminalDAO;
import michelle.zaca_bus.model.Catalogo;
import michelle.zaca_bus.model.Cuenta;
import michelle.zaca_bus.model.Empleado;
import michelle.zaca_bus.util.Mensaje;
import michelle.zaca_bus.util.Tablas;

/**
 * Pantalla cuentas.fxml: cuentas de acceso, una por empleado; activarla o desactivarla no la borra.
 * Choferes y mecánicos no entran al sistema, así que no se les crea cuenta.
 */
public class CuentasController {

    private static final Set<String> SIN_CUENTA = Set.of(CONDUCTOR, "MECANICO");

    @FXML private TableView<Cuenta> tablaCuentas;
    @FXML private TableColumn<Cuenta, String> colUsuario, colNombreCuenta, colRol, colSede, colEstado;
    @FXML private TableColumn<Cuenta, Void> colAccionesCuenta;
    @FXML private ComboBox<String> cbCiudad;
    @FXML private ComboBox<Catalogo> cbTerminalFiltro;
    @FXML private TextField txtBuscar;
    @FXML private Label lblMensaje;

    private final PersonalDAO dao = new PersonalDAO();
    private final CuentaDAO cuentaDAO = new CuentaDAO();
    private final TerminalDAO terminalDAO = new TerminalDAO();
    private final ObservableList<Cuenta> cuentas = FXCollections.observableArrayList();
    private final FilteredList<Cuenta> cuentasVisibles = new FilteredList<>(cuentas);

    /** Sede o taller de la cuenta y, si es taquillero, su taquilla ("Tapachula · TAP-01"); la usa también el formulario. */
    static String asignacion(Cuenta c) {
        if (c.taller() != null) {
            return c.taller().nombre();
        }
        if (c.sede() != null) {
            return c.taquilla() == null ? c.sede().nombre() : c.sede().nombre() + " · " + c.taquilla().nombre();
        }
        return CONDUCTOR.equals(c.rol().nombre()) ? "Rutas / Flota" : "—";
    }

    @FXML
    private void initialize() {
        colUsuario.setCellValueFactory(c -> texto(c.getValue().username()));
        colNombreCuenta.setCellValueFactory(c -> texto(c.getValue().nombreCompleto()));
        colRol.setCellValueFactory(c -> texto(etiquetaPuesto(c.getValue().rol().nombre())));
        colSede.setCellValueFactory(c -> texto(asignacion(c.getValue())));
        colEstado.setCellValueFactory(c -> texto(c.getValue().activa() ? "Activa" : "Inactiva"));
        Tablas.columnaGestion(colAccionesCuenta, Cuenta::activa, this::abrirEditorCuenta, null, c -> { },
                "Desactivar", this::cambiarEstado, this::cambiarEstado);
        cbTerminalFiltro.valueProperty().addListener((obs, antes, ahora) -> filtrar());
        txtBuscar.textProperty().addListener((obs, antes, ahora) -> filtrar());
        tablaCuentas.setItems(cuentasVisibles);
        cargar();
    }

    @FXML
    private void nuevaCuenta() {
        abrirEditorCuenta(null);
    }

    private void cargar() {
        try {
            cuentas.setAll(cuentaDAO.listar());
            Catalogo elegida = cbTerminalFiltro.getValue();
            List<Catalogo> terminales = terminalDAO.listar();
            // pendiente backend: la ciudad aún no filtra; terminal y ciudad son uno en la base
            cbCiudad.getItems().setAll(TODAS_CIUDADES);
            cbCiudad.getItems().addAll(terminales.stream().map(Catalogo::nombre).toList());
            cbCiudad.setValue(TODAS_CIUDADES);
            cbTerminalFiltro.getItems().setAll(TODAS_TERMINALES);
            cbTerminalFiltro.getItems().addAll(terminales);
            cbTerminalFiltro.setValue(cbTerminalFiltro.getItems().stream()
                    .filter(t -> elegida != null && t.id() == elegida.id()).findFirst().orElse(TODAS_TERMINALES));
            Mensaje.ocultar(lblMensaje);
        } catch (SQLException e) {
            Mensaje.error(lblMensaje, "No se pudieron cargar las cuentas: " + e.getMessage());
        }
    }

    private void filtrar() {
        Catalogo terminal = cbTerminalFiltro.getValue();
        String buscado = txtBuscar.getText() == null ? "" : txtBuscar.getText().trim().toLowerCase();
        cuentasVisibles.setPredicate(c -> (terminal == null || terminal == TODAS_TERMINALES
                        || (c.sede() != null && c.sede().id() == terminal.id()))
                && (c.username() + " " + c.nombreCompleto() + " " + c.rol().nombre() + " " + asignacion(c))
                        .toLowerCase().contains(buscado));
    }

    private void cambiarEstado(Cuenta cuenta) {
        try {
            cuentaDAO.cambiarEstado(cuenta.idApp(), !cuenta.activa());
            cargar();
        } catch (SQLException e) {
            Mensaje.error(lblMensaje, "No se pudo cambiar el estado: " + e.getMessage());
        }
    }

    /** `cuenta` es null para un alta. */
    private void abrirEditorCuenta(Cuenta cuenta) {
        try {
            List<Empleado> sinCuenta = dao.listar().stream()
                    .filter(p -> !p.tieneCuenta() && !SIN_CUENTA.contains(p.puesto().nombre())).toList();
            if (cuenta == null && sinCuenta.isEmpty()) {
                Mensaje.error(lblMensaje, "Todo el personal ya tiene cuenta. Dé de alta al empleado en Personal.");
                return;
            }
            AsignacionPersonal asignaciones = new AsignacionPersonal();
            Set<String> existentes = new HashSet<>(cuentas.stream().map(Cuenta::username).toList());
            List<Catalogo> rolesBD = cuentaDAO.roles();
            App.<CuentaFormController>abrirVentana("cuenta-form", tablaCuentas.getScene().getWindow(),
                    formulario -> formulario.preparar(cuenta, sinCuenta, rolesBD, existentes, asignaciones::de));
            cargar();
        } catch (SQLException e) {
            Mensaje.error(lblMensaje, "No se pudo abrir el formulario: " + e.getMessage());
        }
    }
}
