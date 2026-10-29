package michelle.zaca_bus.dao.despachador_dao;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import michelle.zaca_bus.model.Catalogo;
import michelle.zaca_bus.model.Escala;
import michelle.zaca_bus.model.PasajeroManifiesto;
import michelle.zaca_bus.model.Salida;
import michelle.zaca_bus.util.ConexionBD;
import michelle.zaca_bus.util.Sql;

public class ManifiestoDAO {

    /** Paradas donde el autobús recoge pasajeros: todas menos la última, que es solo de llegada. */
    public List<Escala> escalas(Salida salida) throws SQLException {
        List<Escala> escalas = new ArrayList<>();
        try (PreparedStatement ps = ConexionBD.getInstancia().getConexion().prepareStatement(
                "SELECT id_terminal, nombre_terminal, min_desde_origen FROM viajes.fn_obtener_escalas(?)")) {
            ps.setInt(1, salida.idRuta());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    escalas.add(new Escala(new Catalogo(rs.getInt(1), rs.getString(2)), salida.fecha().plusMinutes(rs.getInt(3))));
                }
            }
        }
        return escalas;
    }

    /**
     * Quienes suben en `idTerminal` en este viaje, sin los boletos cancelados. Un boleto no abordado pasa a
     * "No se presentó" cuando el viaje ya salió y la hora de la escala ya pasó; su asiento sigue ocupado.
     */
    public List<PasajeroManifiesto> pasajeros(int idViaje, int idTerminal) throws SQLException {
        List<PasajeroManifiesto> pasajeros = new ArrayList<>();
        try (PreparedStatement ps = ConexionBD.getInstancia().getConexion().prepareStatement(
                "SELECT num_asiento, nombre_completo, destino, nombre_tarifa, folio_impreso, estatus_abordaje FROM ventas.fn_buscar_pasajeros_manifiesto(?, ?)")) {
            ps.setInt(1, idViaje);
            ps.setInt(2, idTerminal);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    pasajeros.add(new PasajeroManifiesto(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4),
                            rs.getString(5), rs.getString(6)));
                }
            }
        }
        return pasajeros;
    }

    /** El viaje sale de su origen: pasa a EN RUTA. */
    public void marcarEnRuta(int idViaje) throws SQLException {
        Sql.transaccion(con -> Sql.ejecutar(con, "CALL viajes.sp_marcar_viaje_en_ruta(?)", idViaje));
    }
}
