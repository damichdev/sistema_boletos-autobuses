package michelle.zaca_bus.model;

/**
 * Una terminal por ciudad: la ciudad identifica a la terminal. Los campos extra son null mientras la vista de la
 * base no los traiga; entonces el nombre de la terminal hace las veces de ciudad.
 */
public record Terminal(int id, String nombre, boolean activa, int idCiudad, String ciudad, String clave, String direccion, String telefono) {

    public Catalogo catalogo() {
        return new Catalogo(id, nombre);
    }

    /** Ciudad a la que pertenece la terminal. */
    public String ciudadVisible() {
        return ciudad != null ? ciudad : nombre;
    }
}
