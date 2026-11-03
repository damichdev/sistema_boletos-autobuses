package michelle.zaca_bus.view;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import michelle.zaca_bus.model.Tarifa;
import michelle.zaca_bus.util.Formato;

/** Una fila por asiento elegido, con el nombre de quien viaja, su tarifa y lo que paga; la primera tarifa de la lista es la de partida. */
public final class TarifasAsientos {

    private final VBox filas;
    private final List<Tarifa> tarifas;
    private final Runnable alCambiar;
    private final Map<Integer, Tarifa> elegidas = new TreeMap<>();
    private final Map<Integer, String> nombres = new TreeMap<>();
    private BigDecimal precioBase = BigDecimal.ZERO;

    public TarifasAsientos(VBox filas, List<Tarifa> tarifas, Runnable alCambiar) {
        this.filas = filas;
        this.tarifas = tarifas;
        this.alCambiar = alCambiar;
    }

    /** Tarifa de cada asiento, por número de asiento; solo lectura. */
    public Map<Integer, Tarifa> elegidas() {
        return Collections.unmodifiableMap(elegidas);
    }

    /** Nombre de quien viaja en cada asiento elegido (puede estar vacío mientras no se capture); solo lectura. */
    public Map<Integer, String> nombres() {
        return Collections.unmodifiableMap(nombres);
    }

    public BigDecimal total() {
        return elegidas.values().stream().map(t -> t.precio(precioBase)).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Rehace las filas para `asientos`; los que ya tenían tarifa la conservan. */
    public void mostrar(Set<Integer> asientos, BigDecimal precioBase) {
        this.precioBase = precioBase;
        elegidas.keySet().retainAll(asientos);
        nombres.keySet().retainAll(asientos);
        filas.getChildren().clear();
        if (!asientos.isEmpty() && tarifas.isEmpty()) {
            filas.getChildren().add(new Label("No hay tarifas registradas."));
            elegidas.clear();
            nombres.clear();
        } else {
            asientos.forEach(this::agregarFila);
        }
        alCambiar.run();
    }

    private void agregarFila(int numero) {
        Tarifa actual = elegidas.computeIfAbsent(numero, n -> tarifas.get(0));
        ComboBox<Tarifa> combo = new ComboBox<>(FXCollections.observableArrayList(tarifas));
        combo.setValue(actual);
        Label importe = new Label(Formato.dinero(actual.precio(precioBase)));
        importe.getStyleClass().add("horario-precio");
        combo.valueProperty().addListener((obs, antes, nueva) -> {
            elegidas.put(numero, nueva);
            importe.setText(Formato.dinero(nueva.precio(precioBase)));
            alCambiar.run();
        });
        TextField pasajero = new TextField(nombres.getOrDefault(numero, ""));
        pasajero.setPromptText("Nombre del pasajero");
        nombres.put(numero, pasajero.getText());
        pasajero.textProperty().addListener((obs, antes, nuevo) -> nombres.put(numero, nuevo));
        HBox.setHgrow(pasajero, Priority.ALWAYS);
        Region espacio = new Region();
        HBox fila = new HBox(10, new Label("Asiento " + numero), pasajero, combo, espacio, importe);
        fila.setAlignment(Pos.CENTER_LEFT);
        filas.getChildren().add(fila);
    }
}
