package michelle.zaca_bus.util;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.Button;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;

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
        boton.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE); // en tablas, sin esto el texto se recorta ("Ed...")
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

    /** Columna de acciones con "Editar" y "Dar de baja" (rojo); ambos entregan la fila de esa celda. */
    public static <T> void columnaEditarBaja(TableColumn<T, Void> columna, Consumer<T> alEditar, Consumer<T> alBaja) {
        columna.setCellFactory(c -> new TableCell<>() {
            private final Button editar = botonChico("Editar");
            private final Button baja = botonChico("Dar de baja");
            private final HBox cajas = new HBox(6, editar, baja);

            {
                editar.setMinWidth(Region.USE_PREF_SIZE);
                baja.setMinWidth(Region.USE_PREF_SIZE); // sin esto el texto se recorta ("Dar de b…")
                baja.getStyleClass().remove("boton-secundario");
                baja.getStyleClass().add("boton-peligro");
                editar.setOnAction(e -> alEditar.accept(getTableView().getItems().get(getIndex())));
                baja.setOnAction(e -> alBaja.accept(getTableView().getItems().get(getIndex())));
            }

            @Override
            protected void updateItem(Void vacio, boolean esVacia) {
                super.updateItem(vacio, esVacia);
                setGraphic(esVacia ? null : cajas);
            }
        });
    }

    /**
     * Acciones de una fila con estado: activa muestra Editar, el botón extra (si `textoExtra` no es null) y
     * "Dar de baja" en rojo; inactiva muestra solo "Activar".
     */
    public static <T> void columnaGestion(TableColumn<T, Void> columna, Predicate<T> activa, Consumer<T> alEditar,
            String textoExtra, Consumer<T> alExtra, Consumer<T> alBaja, Consumer<T> alActivar) {
        columnaGestion(columna, activa, alEditar, textoExtra, alExtra, "Dar de baja", alBaja, alActivar);
    }

    /** Igual que la anterior, con el texto del botón rojo (por ejemplo "Desactivar"). */
    public static <T> void columnaGestion(TableColumn<T, Void> columna, Predicate<T> activa, Consumer<T> alEditar,
            String textoExtra, Consumer<T> alExtra, String textoBaja, Consumer<T> alBaja, Consumer<T> alActivar) {
        columna.setCellFactory(c -> new TableCell<>() {
            private final Button editar = botonChico("Editar");
            private final Button extra = botonChico(textoExtra == null ? "" : textoExtra);
            private final Button baja = botonChico(textoBaja);
            private final Button activar = botonChico("Activar");
            private final HBox cajas = new HBox(6);

            {
                for (Button b : new Button[] { editar, extra, baja, activar }) {
                    b.setMinWidth(Region.USE_PREF_SIZE); // sin esto el texto se recorta
                }
                baja.getStyleClass().remove("boton-secundario");
                baja.getStyleClass().add("boton-peligro");
                editar.setOnAction(e -> alEditar.accept(fila()));
                extra.setOnAction(e -> alExtra.accept(fila()));
                baja.setOnAction(e -> alBaja.accept(fila()));
                activar.setOnAction(e -> alActivar.accept(fila()));
            }

            private T fila() {
                return getTableView().getItems().get(getIndex());
            }

            @Override
            protected void updateItem(Void vacio, boolean esVacia) {
                super.updateItem(vacio, esVacia);
                if (esVacia) {
                    setGraphic(null);
                    return;
                }
                cajas.getChildren().clear();
                if (activa.test(fila())) {
                    cajas.getChildren().add(editar);
                    if (textoExtra != null) {
                        cajas.getChildren().add(extra);
                    }
                    cajas.getChildren().add(baja);
                } else {
                    cajas.getChildren().add(activar);
                }
                setGraphic(cajas);
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
