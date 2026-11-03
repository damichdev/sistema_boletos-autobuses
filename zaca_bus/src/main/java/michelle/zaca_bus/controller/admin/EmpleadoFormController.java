package michelle.zaca_bus.controller.admin;

import static michelle.zaca_bus.controller.admin.EtiquetasPersonal.CONDUCTOR;
import static michelle.zaca_bus.controller.admin.EtiquetasPersonal.DESPACHADOR;
import static michelle.zaca_bus.controller.admin.EtiquetasPersonal.NOMBRE_PUESTO;
import static michelle.zaca_bus.controller.admin.EtiquetasPersonal.REPETIDO;
import static michelle.zaca_bus.controller.admin.EtiquetasPersonal.TAQUILLERO;
import static michelle.zaca_bus.controller.admin.EtiquetasPersonal.mostrar;

import java.sql.SQLException;
import java.util.List;
import java.util.regex.Pattern;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import michelle.zaca_bus.dao.admin.PersonalDAO;
import michelle.zaca_bus.dao.admin.TallerDAO;
import michelle.zaca_bus.model.Catalogo;
import michelle.zaca_bus.model.Empleado;
import michelle.zaca_bus.model.Taquilla;
import michelle.zaca_bus.util.Mensaje;
import michelle.zaca_bus.util.Validar;

/** Formulario empleado-form.fxml: alta y edición; los campos cambian según el puesto. */
public class EmpleadoFormController {

    // Igual que check_curp_valido y check_telefono de administracion.personal
    private static final Pattern CURP = Pattern.compile("^[A-Z]{4}[0-9]{6}[HM][A-Z]{5}[A-Z0-9][0-9]$");
    private static final Pattern TELEFONO = Pattern.compile("^[0-9]{10}$");

    @FXML private Label lblTitulo, lblTerminal, lblNotaTerminal, lblError;
    @FXML private TextField txtNombre, txtApPaterno, txtApMaterno, txtCurp, txtTelefono, txtLicencia;
    @FXML private ComboBox<Catalogo> cbPuesto, cbTerminal, cbTaquilla, cbTaller;
    @FXML private ComboBox<String> cbArea;
    @FXML private DatePicker dpVigencia;
    @FXML private HBox boxLicencia;
    @FXML private VBox boxTerminal, boxTaquilla, boxArea, boxTaller;

    private final PersonalDAO dao = new PersonalDAO();
    private final TallerDAO tallerDAO = new TallerDAO();
    private List<Taquilla> taquillas = List.of();
    private int idPersonal;

    @FXML
    private void initialize() {
        cbPuesto.setConverter(NOMBRE_PUESTO);
        cbPuesto.valueProperty().addListener((obs, antes, ahora) -> actualizarCamposPorPuesto());
        cbTerminal.valueProperty().addListener((obs, antes, ahora) -> cargarTaquillas(ahora));
    }

    /** `empleado` es null para un alta. Terminal y taquilla solo se muestran; aún no se guardan (fase de backend). */
    public void preparar(Empleado empleado, List<Catalogo> puestos, List<Catalogo> terminales, List<Taquilla> todasTaquillas) {
        taquillas = todasTaquillas;
        cbPuesto.getItems().setAll(puestos);
        cbTerminal.getItems().setAll(terminales);
        cbArea.getItems().setAll("Rutas / Flota (Sin Terminal Fija)");
        cbArea.setValue(cbArea.getItems().get(0));
        try {
            cbTaller.getItems().setAll(tallerDAO.activos());
        } catch (SQLException e) {
            Mensaje.error(lblError, "No se pudo cargar la lista de talleres: " + e.getMessage());
        }
        if (empleado != null) {
            idPersonal = empleado.idPersonal();
            lblTitulo.setText("EDITAR EMPLEADO");
            txtNombre.setText(empleado.nombre());
            txtApPaterno.setText(empleado.apPaterno());
            txtApMaterno.setText(empleado.apMaterno());
            txtCurp.setText(empleado.curp());
            txtTelefono.setText(empleado.telefono());
            cbPuesto.setValue(empleado.puesto());
            txtLicencia.setText(empleado.licencia());
            dpVigencia.setValue(empleado.vigencia());
            cbTaller.getItems().stream().filter(t -> empleado.idTaller() != null && t.id() == empleado.idTaller())
                    .findFirst().ifPresent(cbTaller::setValue);
            cbTerminal.getItems().stream().filter(t -> empleado.idTerminal() != null && t.id() == empleado.idTerminal())
                    .findFirst().ifPresent(cbTerminal::setValue); // carga las taquillas de esa terminal
            cbTaquilla.getItems().stream().filter(t -> empleado.idTaquilla() != null && t.id() == empleado.idTaquilla())
                    .findFirst().ifPresent(cbTaquilla::setValue);
        }
        actualizarCamposPorPuesto();
    }

    /** Chofer: licencia y área. Taquillero: terminal y taquilla. Despachador: terminal (licencia deshabilitada). */
    private void actualizarCamposPorPuesto() {
        String puesto = cbPuesto.getValue() == null ? "" : cbPuesto.getValue().nombre();
        boolean chofer = CONDUCTOR.equals(puesto);
        boolean taquillero = TAQUILLERO.equals(puesto);
        boolean despachador = DESPACHADOR.equals(puesto);
        mostrar(boxLicencia, chofer || despachador);
        txtLicencia.setDisable(!chofer);
        dpVigencia.setDisable(!chofer);
        mostrar(boxArea, chofer);
        mostrar(boxTaller, conTaller(puesto));
        mostrar(boxTerminal, taquillero || despachador);
        mostrar(boxTaquilla, taquillero);
        lblTerminal.setText(despachador ? "TERMINAL / ADSCRIPCIÓN" : "TERMINAL");
        lblNotaTerminal.setText(despachador ? "Seleccione la terminal para el control de abordajes y salidas de viaje."
                : "Terminal o sucursal donde laborará");
    }

    private void cargarTaquillas(Catalogo terminal) {
        cbTaquilla.setValue(null);
        cbTaquilla.getItems().setAll(terminal == null ? List.of() : taquillas.stream()
                .filter(t -> t.terminal().id() == terminal.id() && t.activa())
                .map(t -> new Catalogo(t.idTaquilla(), t.identificador())).toList());
        cbTaquilla.setDisable(terminal == null);
    }

    @FXML
    private void guardar() {
        try {
            String puesto = cbPuesto.getValue() == null ? "" : cbPuesto.getValue().nombre();
            boolean taquillero = TAQUILLERO.equals(puesto);
            boolean despachador = DESPACHADOR.equals(puesto);
            Integer terminal = (taquillero || despachador) && cbTerminal.getValue() != null ? cbTerminal.getValue().id() : null;
            Integer taquilla = taquillero && cbTaquilla.getValue() != null ? cbTaquilla.getValue().id() : null;
            Integer taller = conTaller(puesto) && cbTaller.getValue() != null ? cbTaller.getValue().id() : null;
            dao.guardar(leerFormulario(), terminal, taquilla, taller);
            cerrar();
        } catch (IllegalArgumentException e) {
            Mensaje.error(lblError, e.getMessage());
        } catch (SQLException e) {
            Mensaje.error(lblError, REPETIDO.equals(e.getSQLState())
                    ? "Ya existe un empleado con esa CURP." : "No se pudo guardar: " + e.getMessage());
        }
    }

    /** Solo mecánicos y jefes de taller se asignan a un taller (trigger de personal). */
    private static boolean conTaller(String puesto) {
        return "MECANICO".equals(puesto) || "JEFE_TALLER".equals(puesto);
    }

    private boolean esChofer() {
        return cbPuesto.getValue() != null && CONDUCTOR.equals(cbPuesto.getValue().nombre());
    }

    private Empleado leerFormulario() {
        if (cbPuesto.getValue() == null) {
            throw new IllegalArgumentException("Elija el puesto.");
        }
        String curp = Validar.texto(txtCurp.getText(), "Escriba la CURP.").toUpperCase();
        if (!CURP.matcher(curp).matches()) {
            throw new IllegalArgumentException("La CURP no tiene un formato válido.");
        }
        String telefono = txtTelefono.getText() == null ? "" : txtTelefono.getText().trim();
        if (!telefono.isEmpty() && !TELEFONO.matcher(telefono).matches()) {
            throw new IllegalArgumentException("El teléfono debe tener 10 dígitos.");
        }

        String licencia = esChofer() ? txtLicencia.getText().trim() : "";
        return new Empleado(idPersonal, cbPuesto.getValue(),
                Validar.texto(txtNombre.getText(), "Escriba el nombre."),
                Validar.texto(txtApPaterno.getText(), "Escriba el apellido paterno."),
                Validar.texto(txtApMaterno.getText(), "Escriba el apellido materno."),
                curp, telefono.isEmpty() ? null : telefono,
                licencia.isEmpty() ? null : licencia, esChofer() ? dpVigencia.getValue() : null, false, null, null, null);
    }

    @FXML
    private void cerrar() {
        lblError.getScene().getWindow().hide();
    }
}
