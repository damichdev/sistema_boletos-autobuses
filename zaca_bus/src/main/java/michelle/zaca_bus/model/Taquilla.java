package michelle.zaca_bus.model;

/** `idTaquilla` es 0 mientras no se ha guardado. `foliosUsados` lo lleva la base; aquí solo se muestra. */
public record Taquilla(int idTaquilla, Catalogo terminal, String identificador, String serie,
        int rangoInicial, int rangoFinal, int foliosUsados, boolean activa) {

    public int totalFolios() {
        return rangoFinal - rangoInicial + 1;
    }
}
