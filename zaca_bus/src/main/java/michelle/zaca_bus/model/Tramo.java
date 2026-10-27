package michelle.zaca_bus.model;

import java.math.BigDecimal;

/** Precio entre dos paradas; origen y destino son posiciones en la ruta (la primera parada es 1). */
public record Tramo(int origen, int destino, BigDecimal precio) {
}
