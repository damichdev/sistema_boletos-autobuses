package michelle.zaca_bus.model;

import java.time.ZonedDateTime;

/** Una parada de un viaje: hora estimada, cuántos suben y bajan allí, y cuántos asientos van ocupados hacia la siguiente. */
public record ParadaViaje(String terminal, ZonedDateTime hora, int suben, int bajan, int ocupadosHaciaSiguiente) {
}
