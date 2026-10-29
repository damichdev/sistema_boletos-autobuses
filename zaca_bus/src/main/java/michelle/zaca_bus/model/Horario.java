package michelle.zaca_bus.model;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

/** Una salida que se puede vender entre el origen y el destino elegidos, con sus lugares libres y el precio del tramo. */
public record Horario(int idViaje, ZonedDateTime fecha, String tipoBus, String servicio, String placa, int libres,
        BigDecimal precio) {
}
