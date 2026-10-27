package michelle.zaca_bus.model;

/** Un asiento de un boleto escaneado. `estado` es el nombre del estado en la base (VIGENTE, ABORDADO…). */
public record BoletoAbordaje(int idBoleto, int idViaje, int asiento, String estado) {
}
