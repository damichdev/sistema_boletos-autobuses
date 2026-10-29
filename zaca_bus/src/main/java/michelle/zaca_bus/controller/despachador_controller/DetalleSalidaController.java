package michelle.zaca_bus.controller.despachador_controller;

import java.time.LocalDate;
import java.util.List;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import michelle.zaca_bus.App;
import michelle.zaca_bus.model.ParadaViaje;
import michelle.zaca_bus.model.Salida;
import michelle.zaca_bus.util.Formato;

/** Detalle de una salida, solo consulta: no permite editar ni cancelar el viaje. */
public class DetalleSalidaController {

    @FXML private Label lblHora, lblRuta, lblEstado, lblUnidad, lblChofer, lblLicencia;
    @FXML private VBox boxRecorrido, boxOcupacion;

    public void preparar(Salida salida, List<ParadaViaje> recorrido) {
        lblHora.setText(Formato.hora(salida.fecha()));
        lblRuta.setText(salida.ruta() + " · " + salida.servicio());
        lblEstado.setText(salida.estado());
        lblUnidad.setText(salida.placa() + " · " + salida.modelo());
        lblChofer.setText(salida.chofer());
        lblLicencia.setText(textoLicencia(salida.vigenciaLicencia()));

        for (int i = 0; i < recorrido.size(); i++) {
            ParadaViaje p = recorrido.get(i);
            Label hora = new Label(Formato.hora(p.hora()));
            hora.getStyleClass().addAll("mono", "hora-parada");
            boxRecorrido.getChildren().add(new HBox(8, hora, new Label(p.terminal() + " · suben " + p.suben() + ", bajan " + p.bajan())));
            if (i < recorrido.size() - 1) {
                boxOcupacion.getChildren().addAll(tramo(p.terminal() + " → " + recorrido.get(i + 1).terminal(),
                        p.ocupadosHaciaSiguiente(), salida.capacidad()));
            }
        }
    }

    @FXML
    private void verManifiesto() {
        cerrar();
        App.irA("manifiesto");
    }

    @FXML
    private void cerrar() {
        lblHora.getScene().getWindow().hide();
    }

    private static VBox tramo(String nombre, int ocupados, int capacidad) {
        Label etiqueta = new Label(nombre);
        Label cifra = new Label(ocupados + " de " + capacidad);
        cifra.getStyleClass().add("mono");
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        ProgressBar barra = new ProgressBar(capacidad == 0 ? 0 : (double) ocupados / capacidad);
        barra.setMaxWidth(Double.MAX_VALUE);
        return new VBox(4, new HBox(etiqueta, espacio, cifra), barra);
    }

    /** El aviso de la licencia es solo visual; quien la valida al programar es la base. */
    private static String textoLicencia(LocalDate vigencia) {
        return vigencia == null ? "Sin licencia registrada" : "Licencia vigente a " + vigencia;
    }
}
