package michelle.zaca_bus.controller.taquillero_controller;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DateCell;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import michelle.zaca_bus.App;
import michelle.zaca_bus.controller.VistaController;
import michelle.zaca_bus.dao.admin_dao.TerminalDAO;
import michelle.zaca_bus.dao.taquillero_dao.AsientoDAO;
import michelle.zaca_bus.dao.taquillero_dao.HorarioDAO;
import michelle.zaca_bus.dao.taquillero_dao.TarifaDAO;
import michelle.zaca_bus.model.Autobus;
import michelle.zaca_bus.model.Catalogo;
import michelle.zaca_bus.model.Cuenta;
import michelle.zaca_bus.model.Horario;
import michelle.zaca_bus.model.Tarifa;
import michelle.zaca_bus.util.Formato;
import michelle.zaca_bus.util.Mensaje;
import michelle.zaca_bus.util.Sesion;

/**
 * Pantalla Venta del taquillero. Por ahora los paneles Destino (origen, destino y fecha), Horario,
 * Asientos y Titular y tarifas (hasta el total y el cambio); falta confirmar la venta. Hereda de VistaController los botones que aún abren vistas de muestra.
 */
public class VentaController extends VistaController {

    @FXML private ComboBox<Catalogo> cbOrigen, cbDestino;
    @FXML private DatePicker dpFecha;
    @FXML private Label lblMensaje, lblSinHorarios, lblSinAsientos, lblTotal, lblCambio;
    @FXML private TextField txtNombre, txtApPaterno, txtApMaterno, txtCurp, txtRecibido;
    @FXML private GridPane gridHorarios, gridAsientos;
    @FXML private VBox boxAutobus, boxTarifas;
    @FXML private Button btnTodos;

    /** Cuántos horarios caben en el panel; el resto se ve en la ventana "todos los horarios". */
    private static final int VISIBLES = 4;

    private final TerminalDAO terminalDAO = new TerminalDAO();
    private final HorarioDAO horarioDAO = new HorarioDAO();
    private final AsientoDAO asientoDAO = new AsientoDAO();
    private final TarifaDAO tarifaDAO = new TarifaDAO();
    private CroquisAsientos croquis;
    private TarifasAsientos tarifas;
    private List<Horario> horarios = List.of();
    private Horario elegido;

    @FXML
    private void initialize() {
        tarifas = new TarifasAsientos(boxTarifas, cargarTarifas(), this::actualizarCobro);
        croquis = new CroquisAsientos(gridAsientos, this::mostrarTarifas);
        txtRecibido.textProperty().addListener((obs, antes, ahora) -> actualizarCobro());
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
        cbDestino.valueProperty().addListener((obs, antes, ahora) -> cargarHorarios());
        dpFecha.valueProperty().addListener((obs, antes, ahora) -> cargarHorarios());
        try {
            cbOrigen.getItems().setAll(terminalDAO.listar());
            Mensaje.ocultar(lblMensaje);
            preseleccionarSede();
        } catch (SQLException e) {
            Mensaje.error(lblMensaje, "No se pudieron cargar las terminales: " + e.getMessage());
        }
    }

    private List<Tarifa> cargarTarifas() {
        try {
            return tarifaDAO.listar();
        } catch (SQLException e) {
            Mensaje.error(lblMensaje, "No se pudieron cargar las tarifas: " + e.getMessage());
            return List.of();
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
        cargarHorarios();
    }

    /** Salidas de la fecha entre el origen y el destino; el horario ya elegido se conserva solo si sigue en la lista. */
    private void cargarHorarios() {
        Catalogo origen = cbOrigen.getValue();
        Catalogo destino = cbDestino.getValue();
        horarios = List.of();
        if (origen != null && destino != null && dpFecha.getValue() != null) {
            try {
                horarios = horarioDAO.buscar(origen.id(), destino.id(), dpFecha.getValue());
                Mensaje.ocultar(lblMensaje);
            } catch (SQLException e) {
                Mensaje.error(lblMensaje, "No se pudieron cargar los horarios: " + e.getMessage());
            }
        }
        elegido = null;
        lblSinHorarios.setText(destino == null ? "Elija el destino para ver los horarios." : "No hay salidas para ese tramo en esa fecha.");
        pintarHorarios();
        cargarAsientos();
    }

    /** Las primeras salidas en dos columnas; si el horario elegido quedó fuera, ocupa el último lugar para que siga a la vista. */
    private void pintarHorarios() {
        List<Horario> visibles = new ArrayList<>(horarios.subList(0, Math.min(VISIBLES, horarios.size())));
        if (elegido != null && !visibles.contains(elegido)) {
            visibles.set(visibles.size() - 1, elegido);
        }
        gridHorarios.getChildren().clear();
        for (int i = 0; i < visibles.size(); i++) {
            Horario h = visibles.get(i);
            gridHorarios.add(TarjetaHorario.crear(h, h.equals(elegido), () -> elegir(h)), i % 2, i / 2);
        }
        lblSinHorarios.setVisible(horarios.isEmpty());
        lblSinHorarios.setManaged(horarios.isEmpty());
        btnTodos.setVisible(horarios.size() > VISIBLES);
        btnTodos.setManaged(horarios.size() > VISIBLES);
        btnTodos.setText("Ver los " + horarios.size() + " horarios del día");
    }

    private void elegir(Horario horario) {
        if (horario.equals(elegido)) {
            return;
        }
        elegido = horario;
        pintarHorarios();
        cargarAsientos();
    }

    /** Dibuja el autobús del horario elegido con los asientos ya vendidos en el tramo; sin horario, lo oculta. */
    private void cargarAsientos() {
        croquis.limpiar();
        boolean hayHorario = elegido != null;
        lblSinAsientos.setVisible(!hayHorario);
        lblSinAsientos.setManaged(!hayHorario);
        boxAutobus.setVisible(hayHorario);
        boxAutobus.setManaged(hayHorario);
        if (!hayHorario) {
            return;
        }
        try {
            Autobus autobus = asientoDAO.autobus(elegido.idViaje());
            Set<Integer> ocupados = asientoDAO.ocupados(elegido.idViaje(), cbOrigen.getValue().id(), cbDestino.getValue().id());
            croquis.mostrar(autobus, ocupados);
            Mensaje.ocultar(lblMensaje);
        } catch (SQLException e) {
            Mensaje.error(lblMensaje, "No se pudieron cargar los asientos: " + e.getMessage());
        }
    }

    /** Una fila de tarifa por cada asiento elegido, con el precio del tramo del horario elegido. */
    private void mostrarTarifas() {
        BigDecimal precio = elegido == null ? BigDecimal.ZERO : elegido.precio();
        tarifas.mostrar(croquis.elegidos(), precio);
    }

    /** Total de los asientos y cambio; sin total o con efectivo menor al total no hay cambio que mostrar. */
    private void actualizarCobro() {
        BigDecimal total = tarifas.total();
        lblTotal.setText(Formato.dinero(total));
        lblCambio.setText(cambio(total));
    }

    private String cambio(BigDecimal total) {
        try {
            BigDecimal recibido = new BigDecimal(txtRecibido.getText().trim());
            return total.signum() > 0 && recibido.compareTo(total) >= 0 ? Formato.dinero(recibido.subtract(total)) : "—";
        } catch (NumberFormatException e) {
            return "—";
        }
    }

    @FXML
    private void verTodos() {
        App.<HorariosDiaController>abrirVentana("horarios-dia", btnTodos.getScene().getWindow(),
                c -> c.preparar(horarios, elegido, this::elegir));
    }
}
