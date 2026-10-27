package michelle.zaca_bus.dao;

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
        try (PreparedStatement ps = ConexionBD.getInstancia().getConexion().prepareStatement("""
                SELECT t.id_terminal, t.nombre_terminal, rp.min_desde_origen
                FROM viajes.ruta_paradas rp
                JOIN administracion.terminal t ON t.id_terminal = rp.id_terminal
                WHERE rp.id_ruta = ? AND rp.orden < (SELECT max(orden) FROM viajes.ruta_paradas WHERE id_ruta = ?)
                ORDER BY rp.orden""")) {
            ps.setInt(1, salida.idRuta());
            ps.setInt(2, salida.idRuta());
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
        try (PreparedStatement ps = ConexionBD.getInstancia().getConexion().prepareStatement("""
                SELECT b.num_asiento, p.nombre_pasajero || ' ' || p.ap_paterno || ' ' || p.ap_materno, td.nombre_terminal,
                       tar.nombre_tarifa, q.serie_folio || '-' || lpad(v.folio::text, 6, '0'),
                       CASE WHEN eb.nombre_estado_boleto = 'ABORDADO' THEN 'Abordó'
                            WHEN ev.nombre_estado_viaje IN ('EN RUTA', 'CERRADO')
                                 AND vp.fecha_salida + make_interval(mins => rp.min_desde_origen) <= now() THEN 'No se presentó'
                            ELSE 'Pendiente' END
                FROM ventas.boleto b
                JOIN ventas.venta v ON v.id_venta = b.id_venta
                JOIN ventas.pasajero p ON p.id_pasajero = v.id_titular
                JOIN administracion.taquilla q ON q.id_taquilla = v.id_taquilla
                JOIN administracion.terminal td ON td.id_terminal = b.id_terminal_destino
                JOIN ventas.tarifa tar ON tar.id_tarifa = b.id_tarifa
                JOIN ventas.estado_boleto eb ON eb.id_estado_boleto = b.id_estado_boleto
                JOIN viajes.viaje_programado vp ON vp.id_viaje = b.id_viaje
                JOIN viajes.estado_viaje ev ON ev.id_estado_viaje = vp.id_estado_viaje
                JOIN viajes.ruta_paradas rp ON rp.id_ruta = vp.id_ruta AND rp.id_terminal = b.id_terminal_origen
                WHERE b.id_viaje = ? AND b.id_terminal_origen = ? AND eb.nombre_estado_boleto <> 'CANCELADO'
                ORDER BY b.num_asiento""")) {
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
        Sql.transaccion(con -> Sql.ejecutar(con,
                "UPDATE viajes.viaje_programado SET id_estado_viaje = (SELECT id_estado_viaje FROM viajes.estado_viaje WHERE nombre_estado_viaje = 'EN RUTA') WHERE id_viaje = ?",
                idViaje));
    }
}
