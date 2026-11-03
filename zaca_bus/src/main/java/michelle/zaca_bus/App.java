package michelle.zaca_bus;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.util.function.Consumer;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import michelle.zaca_bus.util.Sesion;

public class App extends Application {

    private static final String CSS = App.class.getResource("css/estilos.css").toExternalForm();

    private static Stage principal;

    private static Scene escena;

    @Override
    public void start(Stage stage) {
        principal = stage;
        escena = new Scene(cargar("login").getRoot(), 1280, 760);
        escena.getStylesheets().add(CSS);
        stage.setTitle("Zaca - Terminal de autobuses");
        stage.setMinWidth(1100);
        stage.setMinHeight(650);
        stage.setScene(escena);
        stage.show();
    }

    public static Window ventanaPrincipal() {
        return principal;
    }

    /** Cambia la pantalla de la ventana principal y marca en la barra lateral el botón de esa vista. */
    public static void irA(String vista) {
        if ("login".equals(vista)) {
            Sesion.iniciar(null);
        }
        Parent raiz = cargar(vista).getRoot();
        for (Node boton : raiz.lookupAll(".nav-boton")) {
            if (vista.equals(boton.getId())) {
                boton.getStyleClass().add("activo");
            }
        }
        escena.setRoot(raiz);
    }

    /**
     * Abre una vista como ventana emergente encima de `dueno`; Esc la cierra. `preparar` recibe el
     * controlador de la vista antes de mostrarla, para pasarle los datos que necesita.
     */
    public static <T> void abrirVentana(String vista, Window dueno, Consumer<T> preparar) {
        FXMLLoader loader = cargar(vista);
        preparar.accept(loader.getController());
        Stage ventana = new Stage(StageStyle.TRANSPARENT);
        ventana.initModality(Modality.APPLICATION_MODAL);
        ventana.initOwner(dueno);
        Scene scene = new Scene(loader.getRoot());
        scene.setFill(Color.TRANSPARENT);
        scene.getStylesheets().add(CSS);
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                ventana.hide();
            }
        });
        ventana.setScene(scene);
        ventana.showAndWait();
    }

    /** Carpetas de la capa Vista (resources/.../view): una por rol, más `comun` para lo compartido (login). */
    private static final String[] CARPETAS_VISTA = { "admin", "despachador", "taquillero", "jefetaller", "comun" };

    /** El nombre de una vista es único en todo el proyecto, así que se busca en las carpetas de la capa Vista. */
    private static URL ubicar(String vista) {
        for (String carpeta : CARPETAS_VISTA) {
            URL url = App.class.getResource("view/" + carpeta + "/" + vista + ".fxml");
            if (url != null) {
                return url;
            }
        }
        throw new IllegalArgumentException("No existe la vista " + vista);
    }

    private static FXMLLoader cargar(String vista) {
        FXMLLoader loader = new FXMLLoader(ubicar(vista));
        try {
            loader.load();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo cargar la vista " + vista, e);
        }
        return loader;
    }

    public static void main(String[] args) {
        launch();
    }
}
