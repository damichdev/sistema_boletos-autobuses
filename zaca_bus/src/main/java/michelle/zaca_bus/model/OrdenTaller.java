package michelle.zaca_bus.model;

/** Una orden de taller (mantenimiento de un autobús). `salida` es "—" mientras la orden sigue abierta. */
public record OrdenTaller(int idOrden, String numero, String taller, String unidad, String servicio, String mecanico,
                          String ingreso, String salida, String estado, String descripcion, String costo) {
}
