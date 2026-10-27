package michelle.zaca_bus;

import java.io.IOException;
import java.io.UncheckedIOException;
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

    private static FXMLLoader cargar(String vista) {
        FXMLLoader loader = new FXMLLoader(App.class.getResource("view/" + vista + ".fxml"));
        try {
            loader.load();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo cargar view/" + vista + ".fxml", e);
        }
        return loader;
    }

    public static void main(String[] args) {
        launch();
    }
}
