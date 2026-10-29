package michelle.zaca_bus.controller.admin_controller;

import java.sql.SQLException;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import michelle.zaca_bus.App;
import michelle.zaca_bus.dao.admin_dao.TaquillaDAO;
import michelle.zaca_bus.model.Catalogo;
import michelle.zaca_bus.model.Taquilla;
import michelle.zaca_bus.util.Mensaje;
import michelle.zaca_bus.util.Tablas;

/** Ventana de detalle de una terminal: sus taquillas con estado, edición y baja. */
public class TerminalDetalleController {

    @FXML private Label lblTitulo, lblMensaje;
    @FXML private VBox listaTaquillas;

    private final TaquillaDAO taquillaDAO = new TaquillaDAO();
    private Catalogo terminal;

    public void preparar(Catalogo terminal) {
        this.terminal = terminal;
        lblTitulo.setText(terminal.nombre());
        cargar();
    }

    @FXML
    private void nuevaTaquilla() {
        abrirFormulario(null);
    }

    @FXML
    private void cerrar() {
        lblTitulo.getScene().getWindow().hide();
    }

    private void cargar() {
        listaTaquillas.getChildren().clear();
        try {
            for (Taquilla t : taquillaDAO.listar()) {
                if (t.terminal().id() == terminal.id()) {
                    listaTaquillas.getChildren().add(fila(t));
                }
            }
            if (listaTaquillas.getChildren().isEmpty()) {
                listaTaquillas.getChildren().add(new Label("Esta terminal aún no tiene taquillas."));
            }
            Mensaje.ocultar(lblMensaje);
        } catch (SQLException e) {
            Mensaje.error(lblMensaje, "No se pudieron cargar las taquillas: " + e.getMessage());
        }
    }

    private HBox fila(Taquilla t) {
        Label nombre = new Label(t.identificador() + " · " + t.serie() + " " + t.rangoInicial() + "–" + t.rangoFinal());
        Label estado = new Label(t.activa() ? "Activa" : "Inactiva");
        estado.getStyleClass().addAll("chip", t.activa() ? "chip-ok" : "chip-neutro");
        Button editar = Tablas.botonChico("Editar");
        editar.setOnAction(e -> abrirFormulario(t));
        Button baja = Tablas.botonChico(t.activa() ? "Dar de baja" : "Reactivar");
        baja.setOnAction(e -> cambiarEstado(t));
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox fila = new HBox(10, nombre, estado, espacio, editar, baja);
        fila.getStyleClass().add("fila-taquilla");
        fila.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        return fila;
    }

    private void abrirFormulario(Taquilla taquilla) {
        App.<TaquillasController>abrirVentana("taquilla-form", lblTitulo.getScene().getWindow(),
                f -> f.prepararFormulario(terminal, taquilla));
        cargar();
    }

    /** Dar de baja no borra: la taquilla conserva sus folios usados y solo cambia su estado. */
    private void cambiarEstado(Taquilla t) {
        try {
            taquillaDAO.guardar(new Taquilla(t.idTaquilla(), t.terminal(), t.identificador(), t.serie(),
                    t.rangoInicial(), t.rangoFinal(), t.foliosUsados(), !t.activa()));
            cargar();
        } catch (SQLException e) {
            Mensaje.error(lblMensaje, "No se pudo cambiar el estado: " + e.getMessage());
        }
    }
}
