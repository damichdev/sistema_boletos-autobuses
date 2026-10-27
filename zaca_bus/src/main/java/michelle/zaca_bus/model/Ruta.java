package michelle.zaca_bus.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/** `idRuta` es 0 mientras la ruta no se ha guardado. */
public record Ruta(int idRuta, String nombre, Catalogo servicio, List<Parada> paradas, List<Tramo> tramos) {

    public String paradasTexto() {
        return paradas.stream().map(p -> p.terminal().nombre()).collect(Collectors.joining(" → "));
    }

    public int distanciaKm() {
        return paradas.isEmpty() ? 0 : paradas.get(paradas.size() - 1).km();
    }

    /** Precio de la primera a la última parada, o null si no está capturado. */
    public BigDecimal precioCompleto() {
        return tramos.stream()
                .filter(t -> t.origen() == 1 && t.destino() == paradas.size())
                .map(Tramo::precio).findFirst().orElse(null);
    }
}
