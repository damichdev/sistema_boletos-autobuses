package michelle.zaca_bus.dao.despachador_dao;

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
        try (PreparedStatement ps = ConexionBD.getInstancia().getConexion().prepareStatement(
                "SELECT id_boleto, id_viaje, num_asiento, estado_boleto FROM ventas.fn_buscar_boletos_por_folio(?)")) {
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
                Sql.ejecutar(con, "CALL ventas.sp_marcar_abordado(?)", id);
            }
        });
    }
}
