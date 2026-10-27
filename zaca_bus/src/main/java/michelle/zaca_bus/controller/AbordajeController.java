package michelle.zaca_bus.controller;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import michelle.zaca_bus.dao.AbordajeDAO;
import michelle.zaca_bus.dao.SalidaDAO;
import michelle.zaca_bus.model.BoletoAbordaje;
import michelle.zaca_bus.model.Salida;
import michelle.zaca_bus.util.Formato;
import michelle.zaca_bus.util.Mensaje;

/**
 * Pantalla Abordaje: se escanea (o se teclea) el folio del ticket. Un solo folio ampara todos los asientos
 * de la venta, así que un escaneo marca a todos como abordados. Un lector USB teclea el folio y manda Enter.
 */
public class AbordajeController {

    private static final String VIGENTE = "VIGENTE";
    private static final String ABORDADO = "ABORDADO";

    /** Lo que se muestra: `clase` es el color (resultado-ok, resultado-aviso o resultado-error). `aMarcar`, los boletos que abordan. */
    record Resultado(String clase, String titulo, String detalle, List<Integer> aMarcar) {
    }

    @FXML private ComboBox<Salida> cbSalida;
    @FXML private TextField txtFolio;
    @FXML private Label lblMensaje, lblTitulo, lblDetalle;
    @FXML private VBox boxResultado;

    private final SalidaDAO salidaDAO = new SalidaDAO();
    private final AbordajeDAO dao = new AbordajeDAO();

    @FXML
    private void initialize() {
        try {
            cbSalida.getItems().setAll(salidaDAO.listar(LocalDate.now(Formato.ZONA)));
            cbSalida.getItems().stream().findFirst().ifPresent(cbSalida::setValue);
        } catch (SQLException e) {
            Mensaje.error(lblMensaje, "No se pudieron cargar las salidas: " + e.getMessage());
        }
    }

    @FXML
    private void validar() {
        String folio = txtFolio.getText().trim();
        if (cbSalida.getValue() == null || folio.isEmpty()) {
            Mensaje.error(lblMensaje, "Elija la salida y escanee o escriba el folio.");
            return;
        }
        try {
            Resultado resultado = resolver(cbSalida.getValue().idViaje(), dao.boletos(folio));
            if (!resultado.aMarcar().isEmpty()) {
                dao.abordar(resultado.aMarcar());
            }
            mostrar(resultado);
            Mensaje.ocultar(lblMensaje);
        } catch (SQLException e) {
            Mensaje.error(lblMensaje, "No se pudo validar el boleto: " + e.getMessage());
        }
        txtFolio.clear();
        txtFolio.requestFocus();
    }

    /** Decide el resultado según los asientos de la venta; no toca la base. Solo un boleto VIGENTE puede abordar. */
    static Resultado resolver(int idViaje, List<BoletoAbordaje> boletos) {
        if (boletos.isEmpty()) {
            return new Resultado("resultado-error", "NO VÁLIDO", "Ese folio no existe.", List.of());
        }
        List<BoletoAbordaje> delViaje = boletos.stream().filter(b -> b.idViaje() == idViaje).collect(Collectors.toList());
        if (delViaje.isEmpty()) {
            return new Resultado("resultado-error", "NO VÁLIDO", "El boleto es de otro viaje.", List.of());
        }
        List<BoletoAbordaje> vigentes = delViaje.stream().filter(b -> b.estado().equals(VIGENTE)).collect(Collectors.toList());
        if (!vigentes.isEmpty()) {
            int n = vigentes.size();
            return new Resultado("resultado-ok", "PUEDE ABORDAR", (n == 1 ? "1 pasajero" : n + " pasajeros") + " · asientos " + asientos(vigentes),
                    vigentes.stream().map(BoletoAbordaje::idBoleto).collect(Collectors.toList()));
        }
        List<BoletoAbordaje> abordados = delViaje.stream().filter(b -> b.estado().equals(ABORDADO)).collect(Collectors.toList());
        if (!abordados.isEmpty()) {
            return new Resultado("resultado-aviso", "YA ESCANEADO", "Los asientos " + asientos(abordados) + " ya abordaron.", List.of());
        }
        return new Resultado("resultado-error", "NO VÁLIDO", "El boleto está " + delViaje.get(0).estado().toLowerCase() + ".", List.of());
    }

    private static String asientos(List<BoletoAbordaje> boletos) {
        return boletos.stream().map(b -> String.valueOf(b.asiento())).collect(Collectors.joining(", "));
    }

    private void mostrar(Resultado resultado) {
        boxResultado.getStyleClass().removeAll("resultado-ok", "resultado-aviso", "resultado-error");
        boxResultado.getStyleClass().add(resultado.clase());
        lblTitulo.setText(resultado.titulo());
        lblDetalle.setText(resultado.detalle());
        boxResultado.setVisible(true);
        boxResultado.setManaged(true);
    }
}
