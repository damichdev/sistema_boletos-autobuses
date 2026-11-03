package michelle.zaca_bus.controller.admin;

import static michelle.zaca_bus.controller.admin.EtiquetasPersonal.REPETIDO;
import static michelle.zaca_bus.controller.admin.EtiquetasPersonal.convertidor;
import static michelle.zaca_bus.controller.admin.EtiquetasPersonal.etiquetaPuesto;
import static michelle.zaca_bus.controller.admin.EtiquetasPersonal.mostrar;

import java.sql.SQLException;
import java.util.List;
import java.util.function.Function;
import java.util.Set;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import michelle.zaca_bus.dao.admin.CuentaDAO;
import michelle.zaca_bus.model.Catalogo;
import michelle.zaca_bus.model.Cuenta;
import michelle.zaca_bus.model.Empleado;
import michelle.zaca_bus.util.Mensaje;
import michelle.zaca_bus.util.NombreUsuario;
import michelle.zaca_bus.util.Seguridad;
import michelle.zaca_bus.util.Validar;

/** Formulario cuenta-form.fxml: el rol sale del puesto del empleado y la sede de su registro; solo se muestran. */
public class CuentaFormController {

    @FXML private Label lblTitulo, lblContrasena, lblError;
    @FXML private ComboBox<Empleado> cbPersona;
    @FXML private TextField txtPersona, txtRol, txtSede, txtUsuario;
    @FXML private PasswordField txtContrasena;
    @FXML private Button btnGuardar;

    private final CuentaDAO cuentaDAO = new CuentaDAO();
    private List<Catalogo> roles = List.of();
    private Set<String> usuarios = Set.of();
    private Cuenta cuentaEditada;
    private Catalogo rolHeredado;
    private Function<Empleado, String> sedeDe;

    /** `cuenta` es null para un alta; entonces se elige entre `sinCuenta`. */
    public void preparar(Cuenta cuenta, List<Empleado> sinCuenta, List<Catalogo> roles, Set<String> usuarios,
            Function<Empleado, String> sedeDe) {
        this.sedeDe = sedeDe;
        this.cuentaEditada = cuenta;
        this.roles = roles;
        this.usuarios = usuarios;
        cbPersona.setConverter(convertidor(e -> e.nombreCompleto() + " · " + etiquetaPuesto(e.puesto().nombre())));

        if (cuenta == null) {
            mostrar(txtPersona, false);
            cbPersona.getItems().setAll(sinCuenta);
            cbPersona.valueProperty().addListener((obs, antes, ahora) -> personaElegida());
            cbPersona.setValue(sinCuenta.get(0));
        } else {
            mostrar(cbPersona, false);
            lblTitulo.setText("EDITAR CUENTA");
            lblContrasena.setText("NUEVA CONTRASEÑA (OPCIONAL)");
            btnGuardar.setText("Guardar cambios");
            txtPersona.setText(cuenta.nombreCompleto());
            txtUsuario.setText(cuenta.username());
            txtUsuario.setDisable(true);
            rolHeredado = cuenta.rol();
            txtRol.setText(etiquetaPuesto(cuenta.rol().nombre()));
            txtSede.setText(CuentasController.asignacion(cuenta));
        }
    }

    @FXML
    private void guardarCuenta() {
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

    /** Al elegir al empleado se propone su usuario y se hereda su rol; la sede es la que Personal le guardó. */
    private void personaElegida() {
        Empleado persona = cbPersona.getValue();
        if (persona == null) {
            return;
        }
        txtUsuario.setText(NombreUsuario.generar(persona.nombre(), persona.apPaterno(), usuarios));
        // El rol sale del puesto: quien es taquillero entra como taquillero
        rolHeredado = roles.stream().filter(r -> r.nombre().equals(persona.puesto().nombre())).findFirst().orElse(null);
        txtRol.setText(etiquetaPuesto(persona.puesto().nombre()));
        txtSede.setText(sedeDe.apply(persona));
    }

    /** El script no pide largo mínimo: solo que la contraseña exista al crear la cuenta. */
    private Cuenta leerFormulario() {
        if (rolHeredado == null) {
            throw new IllegalArgumentException("No hay un rol de cuenta para el puesto de este empleado.");
        }
        if (cuentaEditada != null) {
            return new Cuenta(cuentaEditada.idApp(), cuentaEditada.idPersonal(), cuentaEditada.username(),
                    cuentaEditada.nombreCompleto(), rolHeredado, cuentaEditada.sede(), cuentaEditada.taquilla(), cuentaEditada.activa(), cuentaEditada.taller());
        }
        String usuario = Validar.texto(txtUsuario.getText(), "Escriba el usuario.");
        if (usuarios.contains(usuario)) {
            throw new IllegalArgumentException("Ese usuario ya existe.");
        }
        Empleado persona = cbPersona.getValue();
        return new Cuenta(0, persona.idPersonal(), usuario, persona.nombreCompleto(), rolHeredado, null, null, true, null);
    }

    @FXML
    private void cerrar() {
        lblError.getScene().getWindow().hide();
    }
}
