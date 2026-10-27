package michelle.zaca_bus.util;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class Formato {

    /** Zona de las terminales; es la misma que usa la base para comparar fechas de viaje. */
    public static final ZoneId ZONA = ZoneId.of("America/Mexico_City");

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private Formato() {
    }

    /** $1,250.00, o un guion si el monto no está capturado. */
    public static String dinero(BigDecimal monto) {
        return monto == null ? "—" : String.format(Locale.forLanguageTag("es-MX"), "$%,.2f", monto);
    }

    /** 08:30 */
    public static String hora(ZonedDateTime fecha) {
        return fecha.format(HORA);
    }
}
