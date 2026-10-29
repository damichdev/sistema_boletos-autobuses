package michelle.zaca_bus.controller.taquillero_controller;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.shape.SVGPath;
import michelle.zaca_bus.model.Autobus;

/** Dibuja el autobús visto desde arriba en un GridPane y lleva la cuenta de los asientos que el taquillero elige. */
final class CroquisAsientos {

    private static final double ANCHO = 44;
    private static final double ALTO = 36;
    private static final double ANCHO_PASILLO = 26;
    private static final String VOLANTE = "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM12 10.5a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3zM3 12h6M15 12h6M12 15v6";

    private final GridPane grid;
    private final Set<Integer> elegidos = new TreeSet<>();
    private final Runnable alCambiar;

    CroquisAsientos(GridPane grid, Runnable alCambiar) {
        this.grid = grid;
        this.alCambiar = alCambiar;
    }

    /** Asientos elegidos, en orden; solo lectura. */
    Set<Integer> elegidos() {
        return Collections.unmodifiableSet(elegidos);
    }

    void limpiar() {
        elegidos.clear();
        grid.getChildren().clear();
        grid.getColumnConstraints().clear();
        alCambiar.run();
    }

    /** Numera de izquierda a derecha, fila por fila; la última fila puede quedar incompleta. */
    void mostrar(Autobus autobus, Set<Integer> ocupados) {
        limpiar();
        int izquierda = autobus.izquierda();
        int derecha = autobus.derecha();
        int porFila = izquierda + derecha;
        for (int columna = 0; columna <= porFila; columna++) {
            ColumnConstraints restriccion = new ColumnConstraints();
            if (columna == izquierda) {
                restriccion.setPrefWidth(ANCHO_PASILLO);
            }
            grid.getColumnConstraints().add(restriccion);
        }
        grid.add(volante(), 0, 0);
        Label frente = new Label("FRENTE");
        frente.getStyleClass().add("frente");
        grid.add(frente, izquierda + 1, 0, derecha, 1);

        for (int numero = 1; numero <= autobus.capacidad(); numero++) {
            int posicion = (numero - 1) % porFila;
            int columna = posicion < izquierda ? posicion : posicion + 1;
            int fila = (numero - 1) / porFila + 1;
            grid.add(asiento(numero, ocupados.contains(numero)), columna, fila);
        }
    }

    private Button volante() {
        SVGPath icono = new SVGPath();
        icono.setContent(VOLANTE);
        icono.getStyleClass().add("icono");
        Button chofer = tamano(new Button());
        chofer.setGraphic(icono);
        chofer.getStyleClass().addAll("asiento", "asiento-chofer");
        chofer.setDisable(true);
        return chofer;
    }

    private Button asiento(int numero, boolean ocupado) {
        Button boton = tamano(new Button(String.valueOf(numero)));
        boton.getStyleClass().add("asiento");
        if (ocupado) {
            boton.getStyleClass().add("asiento-ocupado");
            boton.setDisable(true);
        } else {
            boton.setOnAction(e -> alternar(numero, boton));
        }
        return boton;
    }

    private void alternar(int numero, Button boton) {
        if (elegidos.remove(numero)) {
            boton.getStyleClass().remove("asiento-elegido");
        } else {
            elegidos.add(numero);
            boton.getStyleClass().add("asiento-elegido");
        }
        alCambiar.run();
    }

    private static Button tamano(Button boton) {
        boton.setPrefSize(ANCHO, ALTO);
        boton.setMinSize(ANCHO, ALTO);
        boton.setMnemonicParsing(false);
        return boton;
    }
}
