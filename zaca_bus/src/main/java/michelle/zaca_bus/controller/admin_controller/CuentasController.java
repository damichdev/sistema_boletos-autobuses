package michelle.zaca_bus.controller.admin_controller;

import static michelle.zaca_bus.util.Tablas.botonChico;
import static michelle.zaca_bus.util.Tablas.texto;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.util.StringConverter;
import michelle.zaca_bus.App;
import michelle.zaca_bus.dao.admin_dao.CuentaDAO;
import michelle.zaca_bus.dao.admin_dao.PersonalDAO;
import michelle.zaca_bus.dao.admin_dao.TaquillaDAO;
import michelle.zaca_bus.dao.admin_dao.TerminalDAO;
import michelle.zaca_bus.model.Catalogo;
import michelle.zaca_bus.model.Cuenta;
import michelle.zaca_bus.model.Empleado;
import michelle.zaca_bus.model.Taquilla;
import michelle.zaca_bus.util.Mensaje;
import michelle.zaca_bus.util.NombreUsuario;
import michelle.zaca_bus.util.Seguridad;
import michelle.zaca_bus.util.Tablas;

/**
 * Módulo Cuentas: una cuenta personal por empleado; activar o desactivar no la borra.
 * Controla la pantalla (cuentas.fxml) y su formulario (cuenta-form.fxml); cada FXML recibe su propia instancia.
 */
public class CuentasController {

    private static final String REPETIDO = "23505";
    private static final String TAQUILLERO = "TAQUILLERO";

    // Pantalla de lista
    @FXML private TableView<Cuenta> tabla;
    @FXML private TableColumn<Cuenta, String> colUsuario, colNombre, colRol, colSede, colEstado;
    @FXML private TableColumn<Cuenta, Void> colAcciones;
    @FXML private TextField txtBuscar;
    @FXML private Label lblMensaje;

    // Formulario
    @FXML private Label lblTitulo, lblUsuario, lblContrasena, lblError;
    @FXML private ComboBox<Empleado> cbPersona;
    @FXML private TextField txtPersona;
    @FXML private ComboBox<Catalogo> cbRol, cbSede;
    @FXML private ComboBox<Taquilla> cbTaquilla;
    @FXML private PasswordField txtContrasena;
    @FXML private Button btnGuardar;

    private final CuentaDAO cuentaDAO = new CuentaDAO();
    private final PersonalDAO personalDAO = new PersonalDAO();
    private final TerminalDAO terminalDAO = new TerminalDAO();
    private final TaquillaDAO taquillaDAO = new TaquillaDAO();
    private final ObservableList<Cuenta> cuentas = FXCollections.observableArrayList();

    private List<Taquilla> taquillas;
    private Set<String> usuarios;
    private Cuenta cuentaEditada;

    @FXML
    private void initialize() {
        if (tabla == null) {
            return; // se está cargando cuenta-form.fxml: no hay lista que armar
        }
        colUsuario.setCellValueFactory(c -> texto(c.getValue().username()));
        colNombre.setCellValueFactory(c -> texto(c.getValue().nombreCompleto()));
        colRol.setCellValueFactory(c -> texto(c.getValue().rol().nombre()));
        colSede.setCellValueFactory(c -> texto(c.getValue().sede() == null ? "—" : c.getValue().sede().nombre()));
        colEstado.setCellValueFactory(c -> texto(c.getValue().activa() ? "Activa" : "Inactiva"));
        colAcciones.setCellFactory(columna -> new TableCell<>() {
            private final Button editar = botonChico("Editar");
            private final Button estado = botonChico("");
            private final HBox botones = new HBox(6, editar, estado);

            {
                editar.setOnAction(e -> abrirEditor(cuenta()));
                estado.setOnAction(e -> cambiarEstado(cuenta()));
            }

            private Cuenta cuenta() {
                return getTableView().getItems().get(getIndex());
            }

            @Override
            protected void updateItem(Void vacio, boolean esVacia) {
                super.updateItem(vacio, esVacia);
                if (esVacia) {
                    setGraphic(null);
                    return;
                }
                estado.setText(cuenta().activa() ? "Desactivar" : "Activar");
                setGraphic(botones);
            }
        });
        tabla.setItems(Tablas.filtrar(cuentas, txtBuscar, c -> c.username() + " " + c.nombreCompleto() + " "
                + c.rol().nombre() + " " + (c.sede() == null ? "" : c.sede().nombre())));
        cargar();
    }

    // ---- Pantalla de lista ----

    @FXML
    private void nuevaCuenta() {
        abrirEditor(null);
    }

    private void cargar() {
        try {
            cuentas.setAll(cuentaDAO.listar());
            Mensaje.ocultar(lblMensaje);
        } catch (SQLException e) {
            Mensaje.error(lblMensaje, "No se pudieron cargar las cuentas: " + e.getMessage());
        }
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
    private void abrirEditor(Cuenta cuenta) {
        try {
            List<Empleado> sinCuenta = personalDAO.listar().stream().filter(p -> !p.tieneCuenta()).toList();
            if (cuenta == null && sinCuenta.isEmpty()) {
                Mensaje.error(lblMensaje, "Todo el personal ya tiene cuenta. Dé de alta al empleado en Personal.");
                return;
            }
            Set<String> existentes = new HashSet<>(cuentas.stream().map(Cuenta::username).toList());
            List<Catalogo> roles = cuentaDAO.roles();
            List<Catalogo> sedes = terminalDAO.listar();
            List<Taquilla> todasTaquillas = taquillaDAO.listar();
            App.<CuentasController>abrirVentana("cuenta-form", tabla.getScene().getWindow(),
                    formulario -> formulario.prepararFormulario(cuenta, sinCuenta, roles, sedes, todasTaquillas, existentes));
            cargar();
        } catch (SQLException e) {
            Mensaje.error(lblMensaje, "No se pudo abrir el formulario: " + e.getMessage());
        }
    }

    // ---- Formulario ----

    /** `cuenta` es null para un alta; entonces se elige entre `sinCuenta`. */
    public void prepararFormulario(Cuenta cuenta, List<Empleado> sinCuenta, List<Catalogo> roles, List<Catalogo> sedes,
            List<Taquilla> taquillas, Set<String> usuarios) {
        this.cuentaEditada = cuenta;
        this.taquillas = taquillas;
        this.usuarios = usuarios;
        cbRol.getItems().setAll(roles);
        cbSede.getItems().setAll(sedes);
        cbPersona.setConverter(convertidor(e -> e.nombreCompleto() + " · " + e.puesto().nombre().toLowerCase()));
        cbTaquilla.setConverter(convertidor(Taquilla::identificador));
        cbSede.valueProperty().addListener((obs, antes, ahora) -> llenarTaquillas());
        cbRol.valueProperty().addListener((obs, antes, ahora) -> llenarTaquillas());

        if (cuenta == null) {
            mostrar(txtPersona, false);
            cbPersona.getItems().setAll(sinCuenta);
            cbPersona.valueProperty().addListener((obs, antes, ahora) -> personaElegida());
            cbPersona.setValue(sinCuenta.get(0));
        } else {
            mostrar(cbPersona, false);
            lblTitulo.setText("Editar cuenta " + cuenta.username());
            lblContrasena.setText("NUEVA CONTRASEÑA (OPCIONAL)");
            btnGuardar.setText("Guardar cambios");
            txtPersona.setText(cuenta.nombreCompleto());
            lblUsuario.setText(cuenta.username());
            cbRol.setValue(cuenta.rol());
            cbSede.setValue(cuenta.sede());
            llenarTaquillas();
            cbTaquilla.setValue(taquillas.stream()
                    .filter(t -> cuenta.taquilla() != null && t.idTaquilla() == cuenta.taquilla().id())
                    .findFirst().orElse(null));
        }
    }

    @FXML
    private void guardar() {
        try {
            String clave = txtContrasena.getText() == null ? "" : txtContrasena.getText();
            if (cuentaEditada == null && clave.isEmpty()) {
                throw new IllegalArgumentException("Escriba la contraseña.");
            }
            // Al editar, una contraseña vacía significa "no cambiarla"
            cuentaDAO.guardar(leerFormulario(), clave.isEmpty() ? null : Seguridad.generarHash(clave));
            cerrar();
        } catch (IllegalArgumentException e) {
            Mensaje.error(lblError, e.getMessage());
        } catch (SQLException e) {
            Mensaje.error(lblError, REPETIDO.equals(e.getSQLState())
                    ? "Ese usuario o esa persona ya tienen cuenta." : "No se pudo guardar: " + e.getMessage());
        }
    }

    @FXML
    private void cerrar() {
        lblError.getScene().getWindow().hide();
    }

    private void personaElegida() {
        Empleado persona = cbPersona.getValue();
        if (persona == null) {
            return;
        }
        lblUsuario.setText(NombreUsuario.generar(persona.nombre(), persona.apPaterno(), usuarios));
        // El rol se sugiere por el puesto: quien es taquillero entra como taquillero
        cbRol.getItems().stream().filter(r -> r.nombre().equals(persona.puesto().nombre())).findFirst()
                .or(() -> cbRol.getItems().stream().filter(r -> r.nombre().equals(TAQUILLERO)).findFirst())
                .ifPresent(cbRol::setValue);
    }

    /** La taquilla solo se asigna a taquilleros, y se ofrecen las de la sede elegida. */
    private void llenarTaquillas() {
        boolean taquillero = cbRol.getValue() != null && TAQUILLERO.equals(cbRol.getValue().nombre());
        cbTaquilla.setDisable(!taquillero || cbSede.getValue() == null);
        cbTaquilla.getItems().setAll(cbSede.getValue() == null ? List.of()
                : taquillas.stream().filter(t -> t.terminal().id() == cbSede.getValue().id()).toList());
        cbTaquilla.setValue(null);
    }

    /** El script no pide largo mínimo: solo que la contraseña exista al crear la cuenta. */
    private Cuenta leerFormulario() {
        if (cbRol.getValue() == null || cbSede.getValue() == null) {
            throw new IllegalArgumentException("Elija el rol y la sede.");
        }
        Taquilla taquilla = cbTaquilla.isDisabled() ? null : cbTaquilla.getValue();
        Catalogo taquillaElegida = taquilla == null ? null : new Catalogo(taquilla.idTaquilla(), taquilla.identificador());
        if (cuentaEditada == null) {
            Empleado persona = cbPersona.getValue();
            return new Cuenta(0, persona.idPersonal(), lblUsuario.getText(), persona.nombreCompleto(), cbRol.getValue(),
                    cbSede.getValue(), taquillaElegida, true);
        }
        return new Cuenta(cuentaEditada.idApp(), cuentaEditada.idPersonal(), cuentaEditada.username(),
                cuentaEditada.nombreCompleto(), cbRol.getValue(), cbSede.getValue(), taquillaElegida, cuentaEditada.activa());
    }

    private static <T> StringConverter<T> convertidor(java.util.function.Function<T, String> aTexto) {
        return new StringConverter<>() {
            @Override
            public String toString(T valor) {
                return valor == null ? "" : aTexto.apply(valor);
            }

            @Override
            public T fromString(String texto) {
                return null;
            }
        };
    }

    private static void mostrar(Node nodo, boolean visible) {
        nodo.setVisible(visible);
        nodo.setManaged(visible);
    }
}
