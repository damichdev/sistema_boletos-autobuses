package michelle.zaca_bus.controller.admin_controller;

import java.sql.SQLException;
import java.util.function.Consumer;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import michelle.zaca_bus.model.Catalogo;
import michelle.zaca_bus.util.Mensaje;
import michelle.zaca_bus.util.Validar;

/** Ventana genérica de alta rápida para cualquier catálogo simple (ciudad, tipo de bus, acomodo…). */
public class CatalogoController {

    @FXML private Label lblTitulo, lblEtiqueta, lblEtiquetaExtra, lblError;
    @FXML private TextField txtNombre, txtExtra;
    @FXML private VBox boxExtra;
    @FXML private Button btnGuardar;

    /** Guarda el catálogo en la base; `extra` es null cuando el catálogo no usa segundo campo. */
    public interface Crear {
        Catalogo crear(String nombre, String extra) throws SQLException;
    }

    private Crear crear;
    private Consumer<Catalogo> alCrear;
    private String etiquetaExtra;

    /** `etiquetaExtra` null oculta el segundo campo. `alCrear` recibe el catálogo ya guardado. */
    public void preparar(String titulo, String etiquetaNombre, String etiquetaExtra, String textoBoton,
            Crear crear, Consumer<Catalogo> alCrear) {
        this.crear = crear;
        this.alCrear = alCrear;
        this.etiquetaExtra = etiquetaExtra;
        lblTitulo.setText(titulo);
        lblEtiqueta.setText(etiquetaNombre);
        btnGuardar.setText(textoBoton);
        lblEtiquetaExtra.setText(etiquetaExtra);
        mostrar(boxExtra, etiquetaExtra != null);
    }

    @FXML
    private void guardar() {
        try {
            String nombre = Validar.texto(txtNombre.getText(), "Escriba " + lblEtiqueta.getText().toLowerCase() + ".");
            String extra = etiquetaExtra == null ? null : Validar.texto(txtExtra.getText(), "Escriba " + etiquetaExtra.toLowerCase() + ".");
            alCrear.accept(crear.crear(nombre, extra));
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

    private static void mostrar(Node nodo, boolean visible) {
        nodo.setVisible(visible);
        nodo.setManaged(visible);
    }
}
