package michelle.zaca_bus.dao;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import michelle.zaca_bus.model.BoletoAbordaje;
import michelle.zaca_bus.util.ConexionBD;
import michelle.zaca_bus.util.Sql;

public class AbordajeDAO {

    /** Asientos de la venta con ese folio (serie-000000, el mismo que lleva el QR del ticket), de cualquier viaje. */
    public List<BoletoAbordaje> boletos(String folio) throws SQLException {
        List<BoletoAbordaje> boletos = new ArrayList<>();
        try (PreparedStatement ps = ConexionBD.getInstancia().getConexion().prepareStatement("""
                SELECT b.id_boleto, b.id_viaje, b.num_asiento, eb.nombre_estado_boleto
                FROM ventas.boleto b
                JOIN ventas.venta v ON v.id_venta = b.id_venta
                JOIN administracion.taquilla q ON q.id_taquilla = v.id_taquilla
                JOIN ventas.estado_boleto eb ON eb.id_estado_boleto = b.id_estado_boleto
                WHERE upper(q.serie_folio || '-' || lpad(v.folio::text, 6, '0')) = upper(?)
                ORDER BY b.num_asiento""")) {
            ps.setString(1, folio);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    boletos.add(new BoletoAbordaje(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getString(4)));
                }
            }
        }
        return boletos;
    }

    /** Marca como ABORDADO los boletos dados, todos juntos. */
    public void abordar(List<Integer> idsBoleto) throws SQLException {
        Sql.transaccion(con -> {
            for (int id : idsBoleto) {
                Sql.ejecutar(con, "UPDATE ventas.boleto SET id_estado_boleto = (SELECT id_estado_boleto FROM ventas.estado_boleto WHERE nombre_estado_boleto = 'ABORDADO') WHERE id_boleto = ?", id);
            }
        });
    }
}
