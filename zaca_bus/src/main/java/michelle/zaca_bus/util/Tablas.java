package michelle.zaca_bus.util;

import java.util.function.Consumer;
import java.util.function.Function;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.Button;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;

/** Piezas que repiten las pantallas de lista: celdas de texto, botón Editar y buscador. */
public final class Tablas {

    private Tablas() {
    }

    public static ObservableValue<String> texto(String valor) {
        return new SimpleStringProperty(valor);
    }

    public static Button botonChico(String texto) {
        Button boton = new Button(texto);
        boton.getStyleClass().addAll("boton", "boton-secundario", "boton-chico");
        return boton;
    }

    /** Columna de acciones con un solo botón "Editar" que entrega la fila de esa celda. */
    public static <T> void columnaEditar(TableColumn<T, Void> columna, Consumer<T> alEditar) {
        columna.setCellFactory(c -> new TableCell<>() {
            private final Button editar = botonChico("Editar");

            {
                editar.setOnAction(e -> alEditar.accept(getTableView().getItems().get(getIndex())));
            }

            @Override
            protected void updateItem(Void vacio, boolean esVacia) {
                super.updateItem(vacio, esVacia);
                setGraphic(esVacia ? null : editar);
            }
        });
    }

    /** Columna de acciones con un solo botón de texto libre que entrega la fila de esa celda. */
    public static <T> void columnaEditar(TableColumn<T, Void> columna, Consumer<T> alPulsar, String texto) {
        columna.setCellFactory(c -> new TableCell<>() {
            private final Button boton = botonChico(texto);

            {
                boton.setOnAction(e -> alPulsar.accept(getTableView().getItems().get(getIndex())));
            }

            @Override
            protected void updateItem(Void vacio, boolean esVacia) {
                super.updateItem(vacio, esVacia);
                setGraphic(esVacia ? null : boton);
            }
        });
    }

    /** Columna de acciones con "Editar" y un segundo botón (por ejemplo "Horarios"); ambos entregan la fila de esa celda. */
    public static <T> void columnaEditar(TableColumn<T, Void> columna, Consumer<T> alEditar, String otro, Consumer<T> alOtro) {
        columna.setCellFactory(c -> new TableCell<>() {
            private final Button editar = botonChico("Editar");
            private final Button segundo = botonChico(otro);
            private final HBox cajas = new HBox(6, editar, segundo);

            {
                editar.setOnAction(e -> alEditar.accept(getTableView().getItems().get(getIndex())));
                segundo.setOnAction(e -> alOtro.accept(getTableView().getItems().get(getIndex())));
            }

            @Override
            protected void updateItem(Void vacio, boolean esVacia) {
                super.updateItem(vacio, esVacia);
                setGraphic(esVacia ? null : cajas);
            }
        });
    }

    /** Lista que se filtra mientras se escribe en `buscador`; `texto` arma el texto donde se busca de cada fila. */
    public static <T> FilteredList<T> filtrar(ObservableList<T> datos, TextField buscador, Function<T, String> texto) {
        FilteredList<T> visibles = new FilteredList<>(datos);
        buscador.textProperty().addListener((obs, antes, ahora) -> visibles.setPredicate(
                fila -> texto.apply(fila).toLowerCase().contains(ahora.trim().toLowerCase())));
        return visibles;
    }
}
