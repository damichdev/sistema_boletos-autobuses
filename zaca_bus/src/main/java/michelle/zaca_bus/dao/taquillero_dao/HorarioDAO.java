package michelle.zaca_bus.dao.taquillero_dao;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import michelle.zaca_bus.model.Horario;
import michelle.zaca_bus.util.ConexionBD;
import michelle.zaca_bus.util.Formato;

public class HorarioDAO {

    public List<Horario> buscar(int idOrigen, int idDestino, LocalDate fecha) throws SQLException {
        List<Horario> horarios = new ArrayList<>();
        try (PreparedStatement ps = ConexionBD.getInstancia().getConexion().prepareStatement(
                "SELECT id_viaje, fecha_salida, tipo_bus, servicio, placa, libres, precio FROM viajes.fn_buscar_salidas(?, ?, ?)")) {
            ps.setInt(1, idOrigen);
            ps.setInt(2, idDestino);
            ps.setObject(3, fecha);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    horarios.add(new Horario(rs.getInt(1), rs.getObject(2, OffsetDateTime.class).atZoneSameInstant(Formato.ZONA),
                            rs.getString(3), rs.getString(4), rs.getString(5), rs.getInt(6), rs.getBigDecimal(7)));
                }
            }
        }
        return horarios;
    }
}
