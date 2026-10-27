package michelle.zaca_bus.controller;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DateCell;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import michelle.zaca_bus.dao.TerminalDAO;
import michelle.zaca_bus.model.Catalogo;
import michelle.zaca_bus.model.Cuenta;
import michelle.zaca_bus.util.Formato;
import michelle.zaca_bus.util.Mensaje;
import michelle.zaca_bus.util.Sesion;

/**
 * Pantalla Venta del taquillero. Por ahora solo el panel Destino (origen, destino y fecha); los paneles
 * Horario, Asientos y Titular se suman aquí. Hereda de VistaController los botones que aún abren vistas de muestra.
 */
public class VentaController extends VistaController {

    @FXML private ComboBox<Catalogo> cbOrigen, cbDestino;
    @FXML private DatePicker dpFecha;
    @FXML private Label lblMensaje;

    private final TerminalDAO terminalDAO = new TerminalDAO();

    @FXML
    private void initialize() {
        dpFecha.setValue(LocalDate.now(Formato.ZONA));
        // No se vende para días pasados.
        dpFecha.setDayCellFactory(selector -> new DateCell() {
            @Override
            public void updateItem(LocalDate dia, boolean vacio) {
                super.updateItem(dia, vacio);
                setDisable(!vacio && dia.isBefore(LocalDate.now(Formato.ZONA)));
            }
        });
        // Menús en cascada: el destino empieza vacío y se llena según el origen.
        cbOrigen.valueProperty().addListener((obs, antes, ahora) -> llenarDestinos());
        cbDestino.setDisable(true);
        try {
            cbOrigen.getItems().setAll(terminalDAO.listar());
            Mensaje.ocultar(lblMensaje);
            preseleccionarSede();
        } catch (SQLException e) {
            Mensaje.error(lblMensaje, "No se pudieron cargar las terminales: " + e.getMessage());
        }
    }

    /** El origen por defecto es la sede de la cuenta en sesión (si la hay). */
    private void preseleccionarSede() {
        Cuenta cuenta = Sesion.actual();
        if (cuenta != null && cuenta.sede() != null) {
            cbOrigen.getItems().stream().filter(t -> t.id() == cuenta.sede().id()).findFirst().ifPresent(cbOrigen::setValue);
        }
    }

    /** Destinos reales desde el origen elegido; el destino ya elegido se conserva solo si sigue siendo válido. */
    private void llenarDestinos() {
        Catalogo origen = cbOrigen.getValue();
        Catalogo destino = cbDestino.getValue();
        List<Catalogo> validos = List.of();
        if (origen != null) {
            try {
                validos = terminalDAO.obtenerDestinosValidos(origen.id());
                Mensaje.ocultar(lblMensaje);
            } catch (SQLException e) {
                Mensaje.error(lblMensaje, "No se pudieron cargar los destinos: " + e.getMessage());
            }
        }
        cbDestino.getItems().setAll(validos);
        cbDestino.setValue(destino != null && validos.contains(destino) ? destino : null);
        cbDestino.setDisable(validos.isEmpty());
        cbDestino.setPromptText(origen != null && validos.isEmpty() ? "Sin destinos desde este origen" : "Elija el destino");
    }
}
