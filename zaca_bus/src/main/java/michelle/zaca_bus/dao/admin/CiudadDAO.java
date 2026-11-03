package michelle.zaca_bus.dao.admin;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import michelle.zaca_bus.model.Catalogo;
import michelle.zaca_bus.util.Sql;

public class CiudadDAO {

    public List<Catalogo> listar() throws SQLException {
        return Sql.catalogo("SELECT id_ciudad, nombre_ciudad FROM administracion.v_ciudades");
    }

    /**
     * Lugares del catálogo que se pueden dar de alta como ciudad. Pendiente en la base: el script aún no tiene
     * ese catálogo, así que la lista sale vacía hasta que exista (nombres en una vista con la columna `nombre_lugar`).
     */
    public List<String> lugares() throws SQLException {
        return List.of();
    }

    /** Da de alta la ciudad (la base la guarda en mayúsculas) o devuelve la que ya tenía ese nombre. Z0031: nombre vacío. */
    public Catalogo crear(String nombre) throws SQLException {
        Catalogo[] ciudad = new Catalogo[1];
        Sql.transaccion(con -> {
            // p_id_ciudad es INOUT: con executeQuery el procedure devuelve el id en una fila.
            try (PreparedStatement ps = con.prepareStatement("CALL administracion.sp_crear_ciudad(?, ?)")) {
                ps.setInt(1, 0);
                ps.setString(2, nombre);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    ciudad[0] = new Catalogo(rs.getInt(1), nombre.trim().toUpperCase());
                }
            }
        });
        return ciudad[0];
    }
}
