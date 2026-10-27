package michelle.zaca_bus.model;

import java.time.LocalDate;
import java.time.ZonedDateTime;

import michelle.zaca_bus.util.Formato;

/** Un viaje programado con los datos que se muestran en pantalla. `vigenciaLicencia` es la del chofer. */
public record Salida(int idViaje, int idRuta, String ruta, String servicio, ZonedDateTime fecha, String estado,
        String placa, String modelo, int capacidad, String chofer, LocalDate vigenciaLicencia) {

    /** Texto de los combos de salida. */
    @Override
    public String toString() {
        return Formato.hora(fecha) + " · " + ruta + " · " + placa;
    }
}
