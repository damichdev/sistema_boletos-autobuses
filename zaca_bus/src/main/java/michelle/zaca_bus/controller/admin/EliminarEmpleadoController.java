package michelle.zaca_bus.controller.admin;

import static michelle.zaca_bus.controller.admin.EtiquetasPersonal.etiquetaPuesto;
import static michelle.zaca_bus.controller.admin.EtiquetasPersonal.mostrar;

import java.sql.SQLException;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import michelle.zaca_bus.dao.admin.PersonalDAO;
import michelle.zaca_bus.model.Empleado;
import michelle.zaca_bus.util.Mensaje;

/** Confirmación eliminar-empleado.fxml. */
public class EliminarEmpleadoController {

    private static final String TIENE_REGISTROS = "23503";

    @FXML private Label lblPregunta, lblCuenta, lblError;

    private final PersonalDAO dao = new PersonalDAO();
    private Empleado empleadoAEliminar;

    public void preparar(Empleado empleado) {
        empleadoAEliminar = empleado;
        lblPregunta.setText("Se eliminará a " + empleado.nombreCompleto() + " (" + etiquetaPuesto(empleado.puesto().nombre()).toLowerCase() + ").");
        mostrar(lblCuenta, empleado.tieneCuenta());
    }

    @FXML
    private void eliminar() {
        try {
            dao.eliminar(empleadoAEliminar.idPersonal());
            cerrar();
        } catch (SQLException e) {
            Mensaje.error(lblError, TIENE_REGISTROS.equals(e.getSQLState())
                    ? "No se puede eliminar: tiene viajes asignados. Asigne otro chofer a esas salidas primero."
                    : "No se pudo eliminar: " + e.getMessage());
        }
    }

    @FXML
    private void cerrar() {
        lblError.getScene().getWindow().hide();
    }
}
