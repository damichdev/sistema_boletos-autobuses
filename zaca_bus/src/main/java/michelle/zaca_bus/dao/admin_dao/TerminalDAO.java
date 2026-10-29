package michelle.zaca_bus.dao.admin_dao;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import michelle.zaca_bus.model.Catalogo;
import michelle.zaca_bus.util.ConexionBD;
import michelle.zaca_bus.util.Sql;

public class TerminalDAO {

    public List<Catalogo> listar() throws SQLException {
        return Sql.catalogo("SELECT id_terminal, nombre_terminal FROM administracion.v_terminales");
    }

    /**
     * Terminales a las que se puede viajar desde `idOrigen`: las que van después en el orden de alguna ruta y cuyo
     * tramo tiene precio. Así no se ofrecen sentidos inversos ni combinaciones que ninguna ruta recorre.
     */
    public List<Catalogo> obtenerDestinosValidos(int idOrigen) throws SQLException {
        List<Catalogo> destinos = new ArrayList<>();
        try (PreparedStatement ps = ConexionBD.getInstancia().getConexion().prepareStatement(
                "SELECT id_terminal, nombre_terminal FROM viajes.fn_obtener_destinos_validos(?)")) {
            ps.setInt(1, idOrigen);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    destinos.add(new Catalogo(rs.getInt(1), rs.getString(2)));
                }
            }
        }
        return destinos;
    }

    /** `id_conexion` es obligatorio: una terminal nueva queda DESCONECTADA hasta que se enlace su nodo. */
    public Catalogo crear(String nombre, String direccion) throws SQLException {
        try (PreparedStatement ps = ConexionBD.getInstancia().getConexion().prepareStatement("CALL administracion.sp_crear_terminal(?, ?, ?)")) {
            // p_id_terminal es INOUT: con executeQuery el procedure devuelve el id en una fila.
            ps.setInt(1, 0);
            ps.setString(2, nombre);
            ps.setString(3, direccion);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return new Catalogo(rs.getInt(1), nombre);
            }
        }
    }
}
