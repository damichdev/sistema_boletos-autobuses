package michelle.zaca_bus.controller;

import static michelle.zaca_bus.util.Formato.dinero;
import static michelle.zaca_bus.util.Tablas.texto;

import java.math.BigDecimal;
import java.sql.SQLException;
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
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import michelle.zaca_bus.App;
import michelle.zaca_bus.dao.RutaDAO;
import michelle.zaca_bus.dao.TerminalDAO;
import michelle.zaca_bus.model.Catalogo;
import michelle.zaca_bus.model.Parada;
import michelle.zaca_bus.model.Ruta;
import michelle.zaca_bus.model.Tramo;
import michelle.zaca_bus.util.Mensaje;
import michelle.zaca_bus.util.Tablas;
import michelle.zaca_bus.util.Validar;

/**
 * Módulo Rutas: lista con buscador y editor (paradas en orden y un precio por cada par subida-bajada).
 * Controla rutas.fxml y ruta-editor.fxml; cada FXML recibe su propia instancia.
 */
public class RutasController {

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

    private final RutaDAO rutaDAO = new RutaDAO();
    private final TerminalDAO terminalDAO = new TerminalDAO();
    private final ObservableList<Ruta> rutas = FXCollections.observableArrayList();
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
        Tablas.columnaEditar(colAcciones, this::abrirEditor);
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
            for (Parada p : ruta.paradas()) {
                agregarFila(p.terminal(), String.valueOf(p.km()), String.valueOf(p.min()));
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
            Mensaje.error(lblError, "No se pudo guardar: " + e.getMessage());
        }
    }

    @FXML
    private void cerrar() {
        lblError.getScene().getWindow().hide();
    }

    /** Reglas del script: nombre obligatorio, terminal sin repetir, km y minutos >= 0, precios >= 0. */
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
        for (Fila f : filas) {
            Catalogo terminal = f.terminal.getValue();
            if (terminal == null) {
                throw new IllegalArgumentException("Elija la terminal de cada parada.");
            }
            if (!usadas.add(terminal.id())) {
                throw new IllegalArgumentException("Una terminal aparece dos veces en la ruta.");
            }
            paradas.add(new Parada(terminal, entero(f.km.getText()), entero(f.min.getText())));
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
            filas.get(i).numero.setText(String.valueOf(i + 1));
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
            km.setPromptText("km");
            km.setText(kmInicial);
            min.setPromptText("min");
            min.setText(minInicial);
            for (TextField campo : List.of(km, min)) {
                campo.getStyleClass().add("mono");
                campo.setPrefWidth(100);
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
}
