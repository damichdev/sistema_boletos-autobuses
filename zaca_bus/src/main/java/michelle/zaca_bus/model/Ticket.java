package michelle.zaca_bus.model;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Lo que se imprime de una venta: un boleto por asiento (`pases`) y los datos comunes. `folio` es el del primer
 * boleto y sirve para identificar la venta en la tabla.
 */
public record Ticket(String folio, String trayecto, ZonedDateTime salida, String unidad, String titular,
        List<Pase> pases, BigDecimal total, BigDecimal recibido, String atendio) {

    public BigDecimal cambio() {
        return recibido.subtract(total);
    }

    public List<Integer> asientos() {
        return pases.stream().filter(Pase::vigente).map(Pase::asiento).toList();
    }
}
