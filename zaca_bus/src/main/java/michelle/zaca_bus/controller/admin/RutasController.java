package michelle.zaca_bus.controller.admin;

import static michelle.zaca_bus.util.Formato.dinero;
import static michelle.zaca_bus.util.Tablas.texto;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import michelle.zaca_bus.App;
import michelle.zaca_bus.dao.admin.RutaDAO;
import michelle.zaca_bus.dao.admin.TerminalDAO;
import michelle.zaca_bus.model.Catalogo;
import michelle.zaca_bus.model.Parada;
import michelle.zaca_bus.model.Ruta;
import michelle.zaca_bus.model.Tramo;
import michelle.zaca_bus.util.Mensaje;
import michelle.zaca_bus.util.Tablas;
import michelle.zaca_bus.util.Validar;

/**
 * Módulo Rutas: lista con buscador, editor (paradas en orden y un precio por cada par subida-bajada) y ventana de
 * horarios de salida de una ruta. Controla rutas.fxml, ruta-editor.fxml y ruta-horarios.fxml;
 * cada FXML recibe su propia instancia.
 */
public class RutasController {

    private static final String HORA_REPETIDA = "23505";

    // Pantalla de lista
    @FXML private TableView<Ruta> tabla;
    @FXML private TableColumn<Ruta, String> colNombre, colServicio, colParadas, colDistancia, colPrecio;
    @FXML private TableColumn<Ruta, Void> colAcciones;
    @FXML private TextField txtBuscar;
    @FXML private Label lblMensaje;

    // Editor
    @FXML private Label lblTitulo, lblError;
    @FXML private TextField txtNombre;
    @FXML private ComboBox<Catalogo> cbServicio;
    @FXML private VBox boxParadas;
    @FXML private FlowPane boxPrecios;

    // Horarios de la ruta
    @FXML private ListView<LocalTime> lstHorarios;
    @FXML private TextField txtHora;

    private final RutaDAO rutaDAO = new RutaDAO();
    private final TerminalDAO terminalDAO = new TerminalDAO();
    private final ObservableList<Ruta> rutas = FXCollections.observableArrayList();
    private final ObservableList<LocalTime> horarios = FXCollections.observableArrayList();
    private final List<Fila> filas = new ArrayList<>();
    /** Campo de precio de cada tramo, con la clave "origen-destino" (posiciones desde 1). */
    private final Map<String, TextField> camposPrecio = new HashMap<>();
    /** Lo ya escrito, para no perderlo cuando se agrega o quita una parada y se redibujan los campos. */
    private final Map<String, String> textosPrecio = new HashMap<>();
    private List<Catalogo> terminales;
    private int idRuta;

    @FXML
    private void initialize() {
        if (tabla == null) {
            return; // se está cargando ruta-editor.fxml: no hay lista que armar
        }
        colNombre.setCellValueFactory(c -> texto(c.getValue().nombre()));
        colServicio.setCellValueFactory(c -> texto(c.getValue().servicio().nombre()));
        colParadas.setCellValueFactory(c -> texto(c.getValue().paradasTexto()));
        colDistancia.setCellValueFactory(c -> texto(c.getValue().distanciaKm() + " km"));
        colPrecio.setCellValueFactory(c -> texto(dinero(c.getValue().precioCompleto())));
        Tablas.columnaEditar(colAcciones, this::abrirEditor, "Horarios", this::abrirHorarios);
        tabla.setItems(Tablas.filtrar(rutas, txtBuscar, r -> r.nombre() + " " + r.paradasTexto()));
        cargar();
    }

    // ---- Pantalla de lista ----

    @FXML
    private void nuevaRuta() {
        abrirEditor(null);
    }

    private void cargar() {
        try {
            rutas.setAll(rutaDAO.listar());
            Mensaje.ocultar(lblMensaje);
        } catch (SQLException e) {
            Mensaje.error(lblMensaje, "No se pudieron cargar las rutas: " + e.getMessage());
        }
    }

    /** `ruta` es null para una ruta nueva. */
    private void abrirEditor(Ruta ruta) {
        try {
            List<Catalogo> terminalesDisponibles = terminalDAO.listar();
            List<Catalogo> servicios = rutaDAO.servicios();
            App.<RutasController>abrirVentana("ruta-editor", tabla.getScene().getWindow(),
                    editor -> editor.prepararEditor(ruta, terminalesDisponibles, servicios));
            cargar();
        } catch (SQLException e) {
            Mensaje.error(lblMensaje, "No se pudo abrir el editor: " + e.getMessage());
        }
    }

    /** Las horas de salida oficiales se vinculan a una ruta ya creada, no al crearla. */
    private void abrirHorarios(Ruta ruta) {
        try {
            List<LocalTime> horas = rutaDAO.horarios(ruta.idRuta());
            App.<RutasController>abrirVentana("ruta-horarios", tabla.getScene().getWindow(),
                    ventana -> ventana.prepararHorarios(ruta, horas));
        } catch (SQLException e) {
            Mensaje.error(lblMensaje, "No se pudieron cargar los horarios: " + e.getMessage());
        }
    }

    // ---- Editor ----

    /** `ruta` es null para una ruta nueva. */
    public void prepararEditor(Ruta ruta, List<Catalogo> terminales, List<Catalogo> servicios) {
        this.terminales = terminales;
        cbServicio.getItems().setAll(servicios);
        if (ruta == null) {
            agregarFila(null, "", "");
            agregarFila(null, "", "");
        } else {
            idRuta = ruta.idRuta();
            lblTitulo.setText("Editar ruta");
            txtNombre.setText(ruta.nombre());
            cbServicio.setValue(ruta.servicio());
            // La base guarda km y minutos desde el origen; el editor los muestra por tramo (desde la parada anterior).
            int kmAnterior = 0;
            int minAnterior = 0;
            for (Parada p : ruta.paradas()) {
                agregarFila(p.terminal(), String.valueOf(p.km() - kmAnterior), String.valueOf(p.min() - minAnterior));
                kmAnterior = p.km();
                minAnterior = p.min();
            }
            for (Tramo t : ruta.tramos()) {
                textosPrecio.put(t.origen() + "-" + t.destino(), t.precio().toPlainString());
            }
        }
        actualizar();
    }

    @FXML
    private void agregarParada() {
        agregarFila(null, "", "");
        actualizar();
    }

    @FXML
    private void guardar() {
        try {
            rutaDAO.guardar(leerFormulario());
            cerrar();
        } catch (IllegalArgumentException e) {
            Mensaje.error(lblError, e.getMessage());
        } catch (SQLException e) {
            Mensaje.error(lblError, Mensaje.deRuta(e));
        }
    }

    @FXML
    private void cerrar() {
        lblError.getScene().getWindow().hide();
    }

    /** Reglas del script: nombre obligatorio, terminal sin repetir, km y minutos >= 0, precios >= 0. Km y minutos se capturan por tramo y aquí se acumulan desde el origen. */
    private Ruta leerFormulario() {
        String nombre = txtNombre.getText().trim();
        if (nombre.isEmpty()) {
            throw new IllegalArgumentException("Escriba el nombre de la ruta.");
        }
        if (cbServicio.getValue() == null) {
            throw new IllegalArgumentException("Elija el servicio.");
        }
        if (filas.size() < 2) {
            throw new IllegalArgumentException("La ruta necesita al menos dos paradas.");
        }
        List<Parada> paradas = new ArrayList<>();
        Set<Integer> usadas = new HashSet<>();
        int kmDesdeOrigen = 0;
        int minutosDesdeOrigen = 0;
        for (Fila f : filas) {
            Catalogo terminal = f.terminal.getValue();
            if (terminal == null) {
                throw new IllegalArgumentException("Elija la terminal de cada parada.");
            }
            if (!usadas.add(terminal.id())) {
                throw new IllegalArgumentException("Una terminal aparece dos veces en la ruta.");
            }
            int minutosDelTramo = entero(f.min.getText());
            if (!paradas.isEmpty() && minutosDelTramo == 0) {
                throw new IllegalArgumentException("Cada tramo debe durar al menos 1 minuto.");
            }
            kmDesdeOrigen += entero(f.km.getText());
            minutosDesdeOrigen += minutosDelTramo;
            paradas.add(new Parada(terminal, kmDesdeOrigen, minutosDesdeOrigen));
        }
        List<Tramo> tramos = new ArrayList<>();
        for (int i = 1; i <= filas.size(); i++) {
            for (int j = i + 1; j <= filas.size(); j++) {
                tramos.add(new Tramo(i, j, precio(camposPrecio.get(i + "-" + j).getText())));
            }
        }
        return new Ruta(idRuta, nombre, cbServicio.getValue(), paradas, tramos);
    }

    private static BigDecimal precio(String texto) {
        try {
            BigDecimal valor = new BigDecimal(texto.trim());
            if (valor.signum() >= 0) {
                return valor;
            }
        } catch (NumberFormatException e) {
            // cae al mensaje de abajo
        }
        throw new IllegalArgumentException("Escriba el precio de cada tramo (0 o más).");
    }

    private void agregarFila(Catalogo terminal, String km, String min) {
        Fila fila = new Fila(terminal, km, min);
        filas.add(fila);
        boxParadas.getChildren().add(fila.caja);
    }

    /** Renumera las paradas y redibuja los precios, que dependen de cuántas paradas hay y sus nombres. */
    private void actualizar() {
        for (int i = 0; i < filas.size(); i++) {
            Fila fila = filas.get(i);
            fila.numero.setText(String.valueOf(i + 1));
            // La base exige que la primera parada tenga 0 km y 0 min (el origen); las demás van aumentando.
            fila.km.setDisable(i == 0);
            fila.min.setDisable(i == 0);
            if (i == 0) {
                fila.km.setText("0");
                fila.min.setText("0");
            }
        }
        for (Map.Entry<String, TextField> campo : camposPrecio.entrySet()) {
            textosPrecio.put(campo.getKey(), campo.getValue().getText());
        }
        camposPrecio.clear();
        boxPrecios.getChildren().clear();
        for (int i = 1; i <= filas.size(); i++) {
            for (int j = i + 1; j <= filas.size(); j++) {
                String clave = i + "-" + j;
                Label etiqueta = new Label((nombreParada(i) + " → " + nombreParada(j)).toUpperCase());
                etiqueta.getStyleClass().add("campo-etiqueta");
                TextField campo = new TextField(textosPrecio.getOrDefault(clave, ""));
                campo.setPromptText("$");
                campo.getStyleClass().add("mono");
                camposPrecio.put(clave, campo);
                VBox caja = new VBox(6, etiqueta, campo);
                caja.setPrefWidth(240);
                boxPrecios.getChildren().add(caja);
            }
        }
    }

    private String nombreParada(int posicion) {
        Catalogo terminal = filas.get(posicion - 1).terminal.getValue();
        return terminal == null ? "Parada " + posicion : terminal.nombre();
    }

    private static int entero(String texto) {
        return Validar.entero(texto, 0, Integer.MAX_VALUE, "Kilómetros y minutos deben ser números enteros, 0 o más.");
    }

    /** Una parada en pantalla: número, terminal, km, minutos y botón para quitarla. */
    private final class Fila {

        final Label numero = new Label();
        final ComboBox<Catalogo> terminal = new ComboBox<>(FXCollections.observableArrayList(terminales));
        final TextField km = new TextField();
        final TextField min = new TextField();
        final HBox caja;

        Fila(Catalogo terminalInicial, String kmInicial, String minInicial) {
            numero.getStyleClass().add("mono");
            numero.setMinWidth(24);
            terminal.setPromptText("Terminal");
            terminal.setMaxWidth(Double.MAX_VALUE);
            terminal.setValue(terminalInicial);
            terminal.setOnAction(e -> actualizar());
            km.setPromptText("km del tramo");
            km.setText(kmInicial);
            min.setPromptText("min del tramo");
            min.setText(minInicial);
            for (TextField campo : List.of(km, min)) {
                campo.getStyleClass().add("mono");
                campo.setPrefWidth(120);
            }
            Button quitar = Tablas.botonChico("Quitar");
            // La parada se identifica por su caja: la lambda no puede usar `caja` antes de que se asigne.
            quitar.setOnAction(e -> {
                if (filas.size() > 2) {
                    filas.removeIf(f -> f.caja == quitar.getParent());
                    boxParadas.getChildren().remove(quitar.getParent());
                    actualizar();
                }
            });
            caja = new HBox(10, numero, terminal, km, min, quitar);
            caja.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(terminal, Priority.ALWAYS);
        }
    }

    // ---- Horarios de la ruta ----

    public void prepararHorarios(Ruta ruta, List<LocalTime> horas) {
        idRuta = ruta.idRuta();
        lblTitulo.setText("Horarios de " + ruta.nombre());
        lstHorarios.setItems(horarios);
        horarios.setAll(horas);
    }

    @FXML
    private void agregarHorario() {
        try {
            LocalTime hora = LocalTime.parse(txtHora.getText().trim());
            rutaDAO.agregarHorario(idRuta, hora);
            txtHora.clear();
            recargarHorarios();
        } catch (DateTimeParseException e) {
            Mensaje.error(lblError, "Escriba la hora con el formato hh:mm, por ejemplo 06:00.");
        } catch (SQLException e) {
            Mensaje.error(lblError, HORA_REPETIDA.equals(e.getSQLState())
                    ? "La ruta ya tiene ese horario." : "No se pudo agregar: " + e.getMessage());
        }
    }

    @FXML
    private void quitarHorario() {
        LocalTime hora = lstHorarios.getSelectionModel().getSelectedItem();
        if (hora == null) {
            Mensaje.error(lblError, "Elija el horario que quiere quitar.");
            return;
        }
        try {
            rutaDAO.quitarHorario(idRuta, hora);
            recargarHorarios();
        } catch (SQLException e) {
            Mensaje.error(lblError, "No se pudo quitar: " + e.getMessage());
        }
    }

    private void recargarHorarios() throws SQLException {
        horarios.setAll(rutaDAO.horarios(idRuta));
        Mensaje.ocultar(lblError);
    }
}
