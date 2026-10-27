package michelle.zaca_bus.model;

import java.time.ZonedDateTime;

import michelle.zaca_bus.util.Formato;

/** Una parada de la ruta donde el autobús recoge pasajeros, con su hora estimada de salida. */
public record Escala(Catalogo terminal, ZonedDateTime hora) {

    @Override
    public String toString() {
        return Formato.hora(hora) + " · " + terminal.nombre();
    }
}
