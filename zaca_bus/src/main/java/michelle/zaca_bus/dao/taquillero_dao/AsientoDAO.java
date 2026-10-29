package michelle.zaca_bus.dao.taquillero_dao;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

import michelle.zaca_bus.model.Autobus;
import michelle.zaca_bus.util.ConexionBD;

public class AsientoDAO {

    public Autobus autobus(int idViaje) throws SQLException {
        try (PreparedStatement ps = ConexionBD.getInstancia().getConexion().prepareStatement(
                "SELECT nombre_layout, capacidad FROM viajes.fn_obtener_autobus_viaje(?)")) {
            ps.setInt(1, idViaje);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("No existe el viaje " + idViaje);
                }
                return new Autobus(rs.getString(1), rs.getInt(2));
            }
        }
    }

    public Set<Integer> ocupados(int idViaje, int idOrigen, int idDestino) throws SQLException {
        Set<Integer> ocupados = new HashSet<>();
        try (PreparedStatement ps = ConexionBD.getInstancia().getConexion().prepareStatement(
                "SELECT num_asiento FROM ventas.fn_obtener_asientos_ocupados(?, ?, ?)")) {
            ps.setInt(1, idViaje);
            ps.setInt(2, idOrigen);
            ps.setInt(3, idDestino);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ocupados.add(rs.getInt(1));
                }
            }
        }
        return ocupados;
    }
}
