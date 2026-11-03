package michelle.zaca_bus.model;

import java.math.BigDecimal;

/**
 * El boleto de un asiento: su propio folio (serie y número, también va en el QR), quien viaja en él, lo que costó y su
 * estado en la base (1 vigente, 3 abordado, 4 cancelado).
 */
public record Pase(int idBoleto, String folio, int asiento, String pasajero, BigDecimal precio, int estado) {

    public boolean vigente() {
        return estado == 1;
    }
}
