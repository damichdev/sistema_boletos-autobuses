package michelle.zaca_bus.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Una tarifa de `ventas.tarifa`; `descuento` va de 0 a 1 (0.5 es 50 %). */
public record Tarifa(int id, String nombre, BigDecimal descuento) {

    /** Lo que paga un asiento con esta tarifa si el tramo cuesta `precioBase`. */
    public BigDecimal precio(BigDecimal precioBase) {
        return precioBase.multiply(BigDecimal.ONE.subtract(descuento)).setScale(2, RoundingMode.HALF_UP);
    }

    /** Texto del ComboBox: Adulto, Estudiante… */
    @Override
    public String toString() {
        return nombre.substring(0, 1) + nombre.substring(1).toLowerCase();
    }
}
