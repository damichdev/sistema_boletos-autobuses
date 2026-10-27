package michelle.zaca_bus.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.stage.Window;
import michelle.zaca_bus.App;

/**
 * Solo para recorrer las vistas: el id del botón es el nombre de la vista (view/<id>.fxml).
 * Se reemplaza por un controlador real en cada pantalla cuando se hace funcional.
 */
public class VistaController {

    @FXML
    private void irA(ActionEvent evento) {
        Node boton = (Node) evento.getSource();
        Window ventana = boton.getScene().getWindow();
        if (ventana != App.ventanaPrincipal()) {
            ventana.hide();
        }
        App.irA(boton.getId());
    }

    @FXML
    private void abrir(ActionEvent evento) {
        Node boton = (Node) evento.getSource();
        App.abrirVentana(boton.getId(), boton.getScene().getWindow(), c -> { });
    }

    @FXML
    private void cerrar(ActionEvent evento) {
        ((Node) evento.getSource()).getScene().getWindow().hide();
    }
}
