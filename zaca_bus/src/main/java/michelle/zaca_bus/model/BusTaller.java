package michelle.zaca_bus.model;

/** Estado de una unidad según el taller (semáforo). `ultimoIngreso` y `observaciones` pueden ser null. */
public record BusTaller(String placa, String modelo, String estatus, boolean aptoParaViajar, String ultimoIngreso,
                        String observaciones) {
}
