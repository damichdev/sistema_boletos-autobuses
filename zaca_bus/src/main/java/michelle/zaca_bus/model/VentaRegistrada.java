package michelle.zaca_bus.model;

/** Una venta ya hecha, tal como se lista en Boletos vendidos. `estado` es VIGENTE, ABORDADO o CANCELADA. */
public record VentaRegistrada(int idVenta, String estado, Ticket ticket) {
}
