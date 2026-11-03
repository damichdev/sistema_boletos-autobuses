package michelle.zaca_bus.controller.taquillero;

import static michelle.zaca_bus.util.Tablas.texto;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.print.PrinterJob;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DateCell;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.Node;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import michelle.zaca_bus.App;
import michelle.zaca_bus.controller.VistaController;
import michelle.zaca_bus.dao.admin.HorarioDAO;
import michelle.zaca_bus.dao.admin.TarifaDAO;
import michelle.zaca_bus.dao.admin.TerminalDAO;
import michelle.zaca_bus.dao.taquillero.AsientoDAO;
import michelle.zaca_bus.dao.taquillero.VentaDAO;
import michelle.zaca_bus.model.Autobus;
import michelle.zaca_bus.model.Catalogo;
import michelle.zaca_bus.model.Cuenta;
import michelle.zaca_bus.model.Horario;
import michelle.zaca_bus.model.Pase;
import michelle.zaca_bus.model.Tarifa;
import michelle.zaca_bus.model.Ticket;
import michelle.zaca_bus.model.Titular;
import michelle.zaca_bus.model.VentaRegistrada;
import michelle.zaca_bus.util.Formato;
import michelle.zaca_bus.util.Mensaje;
import michelle.zaca_bus.util.Qr;
import michelle.zaca_bus.util.Sesion;
import michelle.zaca_bus.util.Tablas;
import michelle.zaca_bus.util.Validar;
import michelle.zaca_bus.view.CroquisAsientos;
import michelle.zaca_bus.view.TarifasAsientos;
import michelle.zaca_bus.view.TarjetaHorario;

/**
 * Venta del taquillero. Controla la pantalla (venta.fxml): origen fijo por la sesión, fecha, destino, horario, asientos,
 * titular, tarifas y cobro; el boleto con su QR (ticket.fxml) que se muestra al confirmar; y Boletos vendidos
 * (boletos.fxml) con su cancelación (cancelar-boleto.fxml). Cada FXML recibe su propia instancia. Hereda de
 * VistaController los botones que aún abren vistas de muestra.
 */
public class VentaController extends VistaController {

    @FXML private ComboBox<Catalogo> cbOrigen, cbDestino;
    @FXML private DatePicker dpFecha;
    @FXML private Label lblMensaje, lblSinHorarios, lblSinAsientos, lblTotal, lblCambio;
    @FXML private TextField txtNombre, txtApPaterno, txtApMaterno, txtCurp, txtRecibido;
    @FXML private GridPane gridHorarios, gridAsientos;
    @FXML private VBox boxAutobus, boxTarifas;
    @FXML private Button btnTodos;

    // Boleto
    @FXML private Label lblTotales;
    @FXML private VBox boxPases;

    // Boletos vendidos
    @FXML private TableView<VentaRegistrada> tablaBoletos;
    @FXML private TableColumn<VentaRegistrada, String> colFolio, colSalida, colTramo, colTitular, colAsientos, colTotal, colVendio, colEstado;
    @FXML private TextField txtBuscar;

    // Cancelación (lblError es el aviso de esa ventana)
    @FXML private Label lblDevolver, lblError;
    @FXML private VBox boxAsientos;

    /** Cuántos horarios caben en el panel; el resto se ve en la ventana "todos los horarios". */
    private static final int VISIBLES = 4;
    private static final int LADO_QR = 160;
    // Igual que check_curp_valido de ventas.pasajero
    private static final Pattern CURP = Pattern.compile("^[A-Z]{4}[0-9]{6}[HMX][A-Z]{5}[A-Z0-9][0-9]$");

    private final TerminalDAO terminalDAO = new TerminalDAO();
    private final HorarioDAO horarioDAO = new HorarioDAO();
    private final AsientoDAO asientoDAO = new AsientoDAO();
    private final TarifaDAO tarifaDAO = new TarifaDAO();
    private final VentaDAO ventaDAO = new VentaDAO();
    private CroquisAsientos croquis;
    private TarifasAsientos tarifas;
    private List<Horario> horarios = List.of();
    private Horario elegido;
    private final ObservableList<VentaRegistrada> ventas = FXCollections.observableArrayList();
    private VentaRegistrada porCancelar;

    @FXML
    private void initialize() {
        if (tablaBoletos != null) {
            armarBoletos();
            return;
        }
        if (cbOrigen == null) {
            return; // ticket.fxml y cancelar-boleto.fxml no tienen pantalla que armar
        }
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
        // Cascada: fecha -> destinos (solo con salidas ese día) -> horarios -> asientos. El origen lo fija la sesión.
        cbOrigen.valueProperty().addListener((obs, antes, ahora) -> llenarDestinos());
        dpFecha.valueProperty().addListener((obs, antes, ahora) -> llenarDestinos());
        cbDestino.setDisable(true);
        cbDestino.valueProperty().addListener((obs, antes, ahora) -> cargarHorarios());
        fijarOrigen();
    }

    private List<Tarifa> cargarTarifas() {
        try {
            return tarifaDAO.listar();
        } catch (SQLException e) {
            Mensaje.error(lblMensaje, "No se pudieron cargar las tarifas: " + e.getMessage());
            return List.of();
        }
    }

    /**
     * El origen es la terminal (sede) de la cuenta en sesión y no se puede cambiar. Si la cuenta no tiene sede
     * (por ejemplo un administrador), se ofrecen todas las terminales para poder elegir.
     */
    private void fijarOrigen() {
        Cuenta cuenta = Sesion.actual();
        try {
            if (cuenta != null && cuenta.sede() != null) {
                Catalogo sede = new Catalogo(cuenta.sede().id(), cuenta.sede().nombre());
                cbOrigen.getItems().setAll(sede);
                cbOrigen.setValue(sede);
                cbOrigen.setDisable(true);
            } else {
                cbOrigen.getItems().setAll(terminalDAO.listar());
            }
            Mensaje.ocultar(lblMensaje);
        } catch (SQLException e) {
            Mensaje.error(lblMensaje, "No se pudieron cargar las terminales: " + e.getMessage());
        }
    }

    /**
     * Destinos de las salidas de la fecha elegida que pasan por el origen y siguen después de él; no hay catálogo de
     * ciudades fijo. El destino ya elegido se conserva solo si sigue en la lista.
     */
    private void llenarDestinos() {
        Catalogo origen = cbOrigen.getValue();
        Catalogo destino = cbDestino.getValue();
        List<Catalogo> validos = List.of();
        if (origen != null && dpFecha.getValue() != null) {
            try {
                validos = terminalDAO.destinosPorSalida(origen.id(), dpFecha.getValue());
                Mensaje.ocultar(lblMensaje);
            } catch (SQLException e) {
                Mensaje.error(lblMensaje, "No se pudieron cargar los destinos: " + e.getMessage());
            }
        }
        cbDestino.getItems().setAll(validos);
        cbDestino.setValue(destino != null && validos.contains(destino) ? destino : null);
        cbDestino.setDisable(validos.isEmpty());
        cbDestino.setPromptText(origen != null && validos.isEmpty() ? "No hay salidas ese día desde este origen" : "Elija el destino");
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
            gridHorarios.add(TarjetaHorario.crear(h, h.equals(elegido), () -> elegir(h)), 0, i);
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
        BigDecimal recibido = recibido();
        return recibido != null && total.signum() > 0 && recibido.compareTo(total) >= 0
                ? Formato.dinero(recibido.subtract(total)) : "—";
    }

    /** Efectivo capturado, o null si está vacío o no es un número. */
    private BigDecimal recibido() {
        try {
            return new BigDecimal(txtRecibido.getText().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @FXML
    private void confirmar() {
        try {
            Ticket ticket = vender();
            limpiarVenta();
            App.<VentaController>abrirVentana("ticket", btnTodos.getScene().getWindow(), t -> t.prepararTicket(ticket));
        } catch (IllegalArgumentException e) {
            Mensaje.error(lblMensaje, e.getMessage());
        } catch (SQLException e) {
            Mensaje.error(lblMensaje, Mensaje.deVenta(e));
            cargarHorarios(); // si otro taquillero vendió esos asientos, el croquis debe mostrarlos ocupados
        }
    }

    /** Valida lo capturado y registra la venta; devuelve lo que lleva el boleto. */
    private Ticket vender() throws SQLException {
        Cuenta cuenta = Sesion.actual();
        if (cuenta == null) {
            throw new IllegalArgumentException("Inicie sesión para vender.");
        }
        if (elegido == null) {
            throw new IllegalArgumentException("Elija el horario.");
        }
        Map<Integer, Tarifa> asientos = tarifas.elegidas();
        if (asientos.isEmpty()) {
            throw new IllegalArgumentException("Elija al menos un asiento.");
        }
        Titular titular = new Titular(Validar.texto(txtNombre.getText(), "Escriba el nombre del titular."),
                Validar.texto(txtApPaterno.getText(), "Escriba el apellido paterno del titular."),
                Validar.texto(txtApMaterno.getText(), "Escriba el apellido materno del titular."), leerCurp());
        BigDecimal total = tarifas.total();
        BigDecimal recibido = recibido();
        if (recibido == null || recibido.compareTo(total) < 0) {
            throw new IllegalArgumentException("El efectivo recibido debe cubrir el total de " + Formato.dinero(total) + ".");
        }
        Map<Integer, String> nombres = new TreeMap<>();
        tarifas.nombres().forEach((asiento, nombre) -> {
            if (nombre == null || nombre.isBlank()) {
                throw new IllegalArgumentException("Escriba el nombre del pasajero del asiento " + asiento + ".");
            }
            nombres.put(asiento, nombre.trim());
        });
        List<Pase> pases = ventaDAO.registrar(cuenta.idApp(), elegido, cbOrigen.getValue(), cbDestino.getValue(), titular, asientos, nombres, recibido);
        return new Ticket(pases.get(0).folio(), cbOrigen.getValue().nombre() + " → " + cbDestino.getValue().nombre(), elegido.fecha(),
                elegido.placa(), titular.nombreCompleto(), pases, total, recibido, cuenta.nombreCompleto());
    }

    /** La CURP es opcional; si se escribe debe cumplir el formato que exige la base. */
    private String leerCurp() {
        String curp = txtCurp.getText() == null ? "" : txtCurp.getText().trim().toUpperCase();
        if (curp.isEmpty()) {
            return null;
        }
        if (!CURP.matcher(curp).matches()) {
            throw new IllegalArgumentException("La CURP no tiene un formato válido.");
        }
        return curp;
    }

    /** Deja la pantalla lista para la siguiente venta; recargar los horarios actualiza los lugares libres. */
    private void limpiarVenta() {
        for (TextField campo : List.of(txtNombre, txtApPaterno, txtApMaterno, txtCurp, txtRecibido)) {
            campo.clear();
        }
        Mensaje.ocultar(lblMensaje);
        cargarHorarios();
    }

    // ---- Boleto ----

    private void prepararTicket(Ticket t) {
        lblTotales.setText("Total " + Formato.dinero(t.total()) + " · Recibido " + Formato.dinero(t.recibido())
                + " · Cambio " + Formato.dinero(t.cambio()));
        boxPases.getChildren().clear();
        for (Pase pase : t.pases().stream().filter(Pase::vigente).toList()) {
            boxPases.getChildren().add(boletoDe(t, pase));
        }
    }

    /** Un boleto impreso por asiento: su folio, su pasajero y su QR. */
    private static HBox boletoDe(Ticket t, Pase pase) {
        Label marca = new Label("ZACA · BOLETO");
        marca.getStyleClass().add("ticket-marca");
        Label asiento = new Label("Asiento " + pase.asiento() + " · " + pase.pasajero());
        asiento.getStyleClass().add("ticket-asientos");
        asiento.setWrapText(true);
        VBox datos = new VBox(3, marca, new Label("Folio: " + pase.folio()), new Label("Ruta: " + t.trayecto()),
                new Label("Salida: " + Formato.fechaHora(t.salida())), new Label("Unidad: " + t.unidad()), asiento,
                new Label("Titular: " + t.titular()), new Label("Atendió: " + t.atendio()));
        datos.getChildren().stream().filter(n -> n instanceof Label && n != marca && n != asiento)
                .forEach(n -> n.getStyleClass().add("mono"));
        HBox.setHgrow(datos, Priority.ALWAYS);
        ImageView qr = new ImageView(Qr.generar(pase.folio(), LADO_QR));
        qr.setFitWidth(130);
        qr.setFitHeight(130);
        qr.setPreserveRatio(true);
        HBox boleto = new HBox(16, datos, new VBox(qr));
        boleto.getStyleClass().add("ticket");
        return boleto;
    }

    /** Imprime cada boleto en su propia hoja. */
    @FXML
    private void imprimir() {
        PrinterJob trabajo = PrinterJob.createPrinterJob();
        if (trabajo == null || !trabajo.showPrintDialog(boxPases.getScene().getWindow())) {
            return;
        }
        for (Node boleto : boxPases.getChildren()) {
            if (!trabajo.printPage(boleto)) {
                return;
            }
        }
        trabajo.endJob();
    }

    // ---- Boletos vendidos ----

    private void armarBoletos() {
        colFolio.setCellValueFactory(c -> texto(c.getValue().ticket().folio()));
        colSalida.setCellValueFactory(c -> texto(Formato.fechaHora(c.getValue().ticket().salida())));
        colTramo.setCellValueFactory(c -> texto(c.getValue().ticket().trayecto()));
        colTitular.setCellValueFactory(c -> texto(c.getValue().ticket().titular()));
        colAsientos.setCellValueFactory(c -> texto(asientos(c.getValue().ticket())));
        colTotal.setCellValueFactory(c -> texto(Formato.dinero(c.getValue().ticket().total())));
        colVendio.setCellValueFactory(c -> texto(c.getValue().ticket().atendio()));
        colEstado.setCellValueFactory(c -> texto(c.getValue().estado()));
        tablaBoletos.setItems(Tablas.filtrar(ventas, txtBuscar, v -> v.ticket().folio() + " " + v.ticket().titular()));
        cargarBoletos();
    }

    private void cargarBoletos() {
        try {
            ventas.setAll(ventaDAO.listar());
            Mensaje.ocultar(lblMensaje);
        } catch (SQLException e) {
            Mensaje.error(lblMensaje, "No se pudieron cargar los boletos: " + e.getMessage());
        }
    }

    private static String asientos(Ticket ticket) {
        return ticket.asientos().stream().map(String::valueOf).collect(Collectors.joining(", "));
    }

    /** La venta elegida en la tabla; si no hay ninguna, avisa y devuelve null. */
    private VentaRegistrada seleccionada() {
        VentaRegistrada venta = tablaBoletos.getSelectionModel().getSelectedItem();
        if (venta == null) {
            Mensaje.error(lblMensaje, "Elija un boleto de la tabla.");
        } else {
            Mensaje.ocultar(lblMensaje);
        }
        return venta;
    }

    @FXML
    private void verTicket() {
        VentaRegistrada venta = seleccionada();
        if (venta != null) {
            App.<VentaController>abrirVentana("ticket", tablaBoletos.getScene().getWindow(), t -> t.prepararTicket(venta.ticket()));
        }
    }

    @FXML
    private void cancelarVenta() {
        VentaRegistrada venta = seleccionada();
        if (venta == null) {
            return;
        }
        if (!"VIGENTE".equals(venta.estado())) {
            Mensaje.error(lblMensaje, "Solo se cancela un boleto vigente; este está " + venta.estado().toLowerCase() + ".");
            return;
        }
        App.<VentaController>abrirVentana("cancelar-boleto", tablaBoletos.getScene().getWindow(), c -> c.prepararCancelacion(venta));
        cargarBoletos();
    }

    /** Un CheckBox por asiento vigente (los abordados y los ya cancelados no se pueden cancelar); todos marcados al abrir. */
    private void prepararCancelacion(VentaRegistrada venta) {
        porCancelar = venta;
        for (Pase pase : venta.ticket().pases()) {
            if (!pase.vigente()) {
                continue;
            }
            CheckBox marca = new CheckBox("Asiento " + pase.asiento() + " · " + pase.pasajero() + " · " + Formato.dinero(pase.precio()));
            marca.setSelected(true);
            marca.setUserData(pase);
            marca.selectedProperty().addListener((obs, antes, ahora) -> actualizarDevolucion());
            boxAsientos.getChildren().add(marca);
        }
        actualizarDevolucion();
    }

    /** Asientos marcados en la ventana de cancelar. */
    private List<Pase> marcados() {
        return boxAsientos.getChildren().stream().map(n -> (CheckBox) n).filter(CheckBox::isSelected)
                .map(c -> (Pase) c.getUserData()).toList();
    }

    private void actualizarDevolucion() {
        lblDevolver.setText(Formato.dinero(marcados().stream().map(Pase::precio).reduce(BigDecimal.ZERO, BigDecimal::add)));
    }

    @FXML
    private void confirmarCancelacion() {
        List<Pase> elegidos = marcados();
        if (elegidos.isEmpty()) {
            Mensaje.error(lblError, "Marque al menos un asiento.");
            return;
        }
        try {
            ventaDAO.cancelarBoletos(elegidos.stream().map(Pase::idBoleto).toList(), Sesion.actual().idApp());
            lblError.getScene().getWindow().hide();
        } catch (SQLException e) {
            Mensaje.error(lblError, e.getMessage());
        }
    }

    @FXML
    private void verTodos() {
        App.<HorariosDiaController>abrirVentana("horarios-dia", btnTodos.getScene().getWindow(),
                c -> c.preparar(horarios, elegido, this::elegir));
    }
}
