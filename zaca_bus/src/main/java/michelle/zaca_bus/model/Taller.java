package michelle.zaca_bus.model;

/** Un taller de mantenimiento. `encargado` es null mientras no tenga jefe de taller asignado. */
public record Taller(int idTaller, int idCiudad, String clave, String nombre, String ciudad, String direccion, int autobuses, int mecanicos,
                      String encargado, boolean activo) {
}
