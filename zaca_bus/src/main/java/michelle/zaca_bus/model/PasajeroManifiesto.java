package michelle.zaca_bus.model;

/** Un asiento que sube en una escala. `estado` es Abordó, Pendiente o No se presentó. */
public record PasajeroManifiesto(int asiento, String titular, String destino, String tarifa, String folio, String estado) {
}
