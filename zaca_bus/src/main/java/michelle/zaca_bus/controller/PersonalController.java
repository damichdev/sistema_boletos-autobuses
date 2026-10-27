package michelle.zaca_bus.controller;

import static michelle.zaca_bus.util.Tablas.botonChico;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.regex.Pattern;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import michelle.zaca_bus.App;
import michelle.zaca_bus.dao.PersonalDAO;
import michelle.zaca_bus.model.Catalogo;
import michelle.zaca_bus.model.Empleado;
import michelle.zaca_bus.util.Mensaje;
import michelle.zaca_bus.util.Validar;

/**
 * Módulo Personal: un panel por puesto, cada uno con su buscador, su lista y su botón de alta.
 * Controla personal.fxml, empleado-form.fxml y eliminar-empleado.fxml; cada FXML recibe su propia instancia.
 */
public class PersonalController {

    private static final int DIAS_AVISO_LICENCIA = 30;
    private static final String TIENE_REGISTROS = "23503";
    private static final String CURP_REPETIDA = "23505";
    private static final String CONDUCTOR = "CONDUCTOR";
    // Igual que check_curp_valido y check_telefono de administracion.personal
    private static final Pattern CURP = Pattern.compile("^[A-Z]{4}[0-9]{6}[HM][A-Z]{5}[A-Z0-9][0-9]$");
    private static final Pattern TELEFONO = Pattern.compile("^[0-9]{10}$");

    // Pantalla de lista
    @FXML private GridPane rejilla;
    @FXML private Label lblMensaje;

    // Formulario de empleado
    @FXML private Label lblTitulo;
    @FXML private TextField txtNombre, txtApPaterno, txtApMaterno, txtCurp, txtTelefono, txtLicencia;
    @FXML private ComboBox<Catalogo> cbPuesto;
    @FXML private DatePicker dpVigencia;

    // Confirmación de eliminar
    @FXML private Label lblPregunta, lblCuenta;

    // Aviso de error: lo usan el formulario y la confirmación
    @FXML private Label lblError;

    private final PersonalDAO dao = new PersonalDAO();
    private final ObservableList<Empleado> personal = FXCollections.observableArrayList();
    private int idPersonal;
    private Empleado empleadoAEliminar;

    @FXML
    private void initialize() {
        if (rejilla == null) {
            return; // se está cargando el formulario o la confirmación: no hay paneles que armar
        }
        agregarPanel("Taquilla", "TAQUILLERO", "taquillero", 0, 0, 1);
        agregarPanel("Despacho", "DESPACHADOR", "despachador", 1, 0, 1);
        agregarPanel("Administración", "ADMINISTRADOR", "administrador", 2, 0, 1);
        agregarPanel("Choferes", CONDUCTOR, "chofer", 0, 1, 2);
        agregarPanel("Mecánicos", "MECANICO", "mecánico", 2, 1, 1);
        cargar();
    }

    // ---- Pantalla de lista ----

    private void cargar() {
        try {
            personal.setAll(dao.listar());
            Mensaje.ocultar(lblMensaje);
        } catch (SQLException e) {
            Mensaje.error(lblMensaje, "No se pudo cargar el personal: " + e.getMessage());
        }
    }

    /** `puesto` es el nombre del puesto en la base; el panel solo muestra a quienes lo tienen. */
    private void agregarPanel(String titulo, String puesto, String singular, int columna, int fila, int ancho) {
        FilteredList<Empleado> visibles = new FilteredList<>(personal);
        visibles.setPredicate(e -> e.puesto().nombre().equals(puesto));

        TextField buscar = new TextField();
        buscar.setPromptText("Buscar en " + titulo.toLowerCase() + "…");
        buscar.getStyleClass().add("buscador");
        buscar.setMinHeight(Region.USE_PREF_SIZE);
        buscar.textProperty().addListener((obs, antes, ahora) -> visibles.setPredicate(e -> e.puesto().nombre().equals(puesto)
                && (e.nombreCompleto() + " " + e.curp() + " " + e.telefono()).toLowerCase().contains(ahora.trim().toLowerCase())));

        ListView<Empleado> lista = new ListView<>(visibles);
        lista.getStyleClass().add("lista-personas");
        Label vacio = new Label("Sin personal en " + titulo.toLowerCase() + ".");
        vacio.getStyleClass().add("tabla-vacia");
        lista.setPlaceholder(vacio);
        lista.setCellFactory(l -> new FilaEmpleado());

        Button agregar = new Button("+ Agregar " + singular);
        agregar.getStyleClass().addAll("boton", "boton-primario");
        agregar.setMaxWidth(Double.MAX_VALUE);
        agregar.setMinHeight(Region.USE_PREF_SIZE);
        agregar.setOnAction(e -> abrirEditor(null, puesto));

        Label etiqueta = new Label(titulo);
        etiqueta.getStyleClass().add("panel-titulo");
        VBox panel = new VBox(12, etiqueta, buscar, lista, agregar);
        panel.getStyleClass().add("panel");
        panel.setMinHeight(300);
        panel.setPrefHeight(300);
        VBox.setVgrow(lista, Priority.ALWAYS);
        rejilla.add(panel, columna, fila, ancho, 1);
    }

    /** `empleado` es null para un alta; `puestoInicial` es el puesto que queda elegido. */
    private void abrirEditor(Empleado empleado, String puestoInicial) {
        try {
            List<Catalogo> puestos = dao.puestos();
            App.<PersonalController>abrirVentana("empleado-form", rejilla.getScene().getWindow(),
                    formulario -> formulario.prepararFormulario(empleado, puestoInicial, puestos));
            cargar();
        } catch (SQLException e) {
            Mensaje.error(lblMensaje, "No se pudo abrir el formulario: " + e.getMessage());
        }
    }

    private void abrirEliminar(Empleado empleado) {
        App.<PersonalController>abrirVentana("eliminar-empleado", rejilla.getScene().getWindow(),
                confirmacion -> confirmacion.prepararEliminar(empleado));
        cargar();
    }

    /** Una fila de la lista: nombre, CURP y teléfono, licencia si es chofer, y los botones Editar y Eliminar. */
    private final class FilaEmpleado extends ListCell<Empleado> {

        FilaEmpleado() {
            setPrefWidth(0); // la fila se ajusta al ancho de la lista, sin barra horizontal
        }

        @Override
        protected void updateItem(Empleado e, boolean vacia) {
            super.updateItem(e, vacia);
            if (vacia || e == null) {
                setGraphic(null);
                return;
            }
            Label nombre = new Label(e.nombreCompleto());
            nombre.getStyleClass().add("persona-nombre");
            Label detalle = new Label(e.telefono() == null ? e.curp() : e.curp() + " · " + e.telefono());
            detalle.getStyleClass().addAll("mono", "texto-sec", "chico");
            nombre.setMinWidth(0);
            detalle.setMinWidth(0);
            VBox datos = new VBox(2, nombre, detalle);
            datos.setMinWidth(0);
            if (e.puesto().nombre().equals(CONDUCTOR)) {
                datos.getChildren().add(chipLicencia(e.vigencia()));
            }
            Button editar = botonChico("Editar");
            editar.setOnAction(ev -> abrirEditor(e, e.puesto().nombre()));
            Button eliminar = botonChico("Eliminar");
            eliminar.setOnAction(ev -> abrirEliminar(e));
            // Los botones van debajo: el panel es angosto y el nombre completo vale más que verlos al lado.
            HBox botones = new HBox(8, editar, eliminar);
            setGraphic(new VBox(6, datos, botones));
        }
    }

    /** Aviso visual: vencida, por vencer en los próximos 30 días, o vigente. */
    private static Label chipLicencia(LocalDate vigencia) {
        LocalDate hoy = LocalDate.now();
        String texto;
        String tipo;
        if (vigencia == null) {
            texto = "Sin licencia";
            tipo = "chip-error";
        } else if (vigencia.isBefore(hoy)) {
            texto = "Vencida " + vigencia;
            tipo = "chip-error";
        } else if (!vigencia.isAfter(hoy.plusDays(DIAS_AVISO_LICENCIA))) {
            texto = "Vence " + vigencia;
            tipo = "chip-aviso";
        } else {
            texto = "Vigente a " + vigencia;
            tipo = "chip-ok";
        }
        Label chip = new Label(texto);
        chip.getStyleClass().addAll("chip", tipo);
        return chip;
    }

    // ---- Formulario de empleado ----

    /** `empleado` es null para un alta; `puestoInicial` es el puesto que queda elegido (nombre en la base). */
    public void prepararFormulario(Empleado empleado, String puestoInicial, List<Catalogo> puestos) {
        cbPuesto.getItems().setAll(puestos);
        cbPuesto.valueProperty().addListener((obs, antes, ahora) -> actualizarLicencia());
        if (empleado == null) {
            puestos.stream().filter(p -> p.nombre().equals(puestoInicial)).findFirst().ifPresent(cbPuesto::setValue);
        } else {
            idPersonal = empleado.idPersonal();
            lblTitulo.setText("Editar empleado");
            txtNombre.setText(empleado.nombre());
            txtApPaterno.setText(empleado.apPaterno());
            txtApMaterno.setText(empleado.apMaterno());
            txtCurp.setText(empleado.curp());
            txtTelefono.setText(empleado.telefono());
            cbPuesto.setValue(empleado.puesto());
            txtLicencia.setText(empleado.licencia());
            dpVigencia.setValue(empleado.vigencia());
        }
        actualizarLicencia();
    }

    @FXML
    private void guardar() {
        try {
            dao.guardar(leerFormulario());
            cerrar();
        } catch (IllegalArgumentException e) {
            Mensaje.error(lblError, e.getMessage());
        } catch (SQLException e) {
            Mensaje.error(lblError, CURP_REPETIDA.equals(e.getSQLState())
                    ? "Ya existe un empleado con esa CURP." : "No se pudo guardar: " + e.getMessage());
        }
    }

    private boolean esChofer() {
        return cbPuesto.getValue() != null && CONDUCTOR.equals(cbPuesto.getValue().nombre());
    }

    /** La licencia solo se pide a los choferes. */
    private void actualizarLicencia() {
        txtLicencia.setDisable(!esChofer());
        dpVigencia.setDisable(!esChofer());
    }

    /** Reglas del script: nombre y apellidos obligatorios, CURP con su formato, teléfono de 10 dígitos si se escribe. */
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
        // Licencia y vigencia se guardan solo para choferes
        String licencia = esChofer() ? txtLicencia.getText().trim() : "";
        return new Empleado(idPersonal, cbPuesto.getValue(),
                Validar.texto(txtNombre.getText(), "Escriba el nombre."),
                Validar.texto(txtApPaterno.getText(), "Escriba el apellido paterno."),
                Validar.texto(txtApMaterno.getText(), "Escriba el apellido materno."),
                curp, telefono.isEmpty() ? null : telefono,
                licencia.isEmpty() ? null : licencia, esChofer() ? dpVigencia.getValue() : null, false);
    }

    // ---- Confirmación de eliminar ----

    public void prepararEliminar(Empleado empleado) {
        empleadoAEliminar = empleado;
        lblPregunta.setText("Se eliminará a " + empleado.nombreCompleto() + " (" + empleado.puesto().nombre().toLowerCase() + ").");
        lblCuenta.setVisible(empleado.tieneCuenta());
        lblCuenta.setManaged(empleado.tieneCuenta());
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
