package michelle.zaca_bus.controller;

import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import michelle.zaca_bus.App;
import michelle.zaca_bus.dao.admin.CuentaDAO;
import michelle.zaca_bus.model.Cuenta;
import michelle.zaca_bus.util.Mensaje;
import michelle.zaca_bus.util.Sesion;

/** Inicio de sesión: valida usuario y contraseña, guarda la cuenta en la sesión y abre la primera pantalla de su rol. */
public class LoginController {

    /** Primera vista de cada rol (nombre_rol de la base). */
    private static final Map<String, String> INICIO = Map.of(
            "ADMINISTRADOR", "rutas",
            "DESPACHADOR", "programar-salida",
            "TAQUILLERO", "abrir-turno",
            "JEFE_TALLER", "ordenes-jefe");

    @FXML private TextField txtUsuario;
    @FXML private PasswordField txtPassword;
    @FXML private Label lblMensaje;

    private final CuentaDAO dao = new CuentaDAO();

    @FXML
    private void entrar() {
        String usuario = txtUsuario.getText().trim();
        String password = txtPassword.getText();
        if (usuario.isEmpty() || password.isEmpty()) {
            Mensaje.error(lblMensaje, "Escriba el usuario y la contraseña.");
            return;
        }
        try {
            Optional<Cuenta> cuenta = dao.autenticar(usuario, password);
            if (cuenta.isEmpty()) {
                Mensaje.error(lblMensaje, "Usuario o contraseña incorrectos.");
                txtPassword.clear();
                return;
            }
            String vista = INICIO.get(cuenta.get().rol().nombre());
            if (vista == null) {
                Mensaje.error(lblMensaje, "El rol " + cuenta.get().rol().nombre() + " no tiene pantalla de inicio.");
                return;
            }
            Sesion.iniciar(cuenta.get());
            App.irA(vista);
        } catch (SQLException e) {
            Mensaje.error(lblMensaje, "No se pudo consultar la cuenta: " + e.getMessage());
        }
    }
}
