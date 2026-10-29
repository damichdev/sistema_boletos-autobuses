package michelle.zaca_bus.dao.taquillero_dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import michelle.zaca_bus.model.Tarifa;
import michelle.zaca_bus.util.ConexionBD;

public class TarifaDAO {

    public List<Tarifa> listar() throws SQLException {
        List<Tarifa> tarifas = new ArrayList<>();
        try (Statement st = ConexionBD.getInstancia().getConexion().createStatement();
                ResultSet rs = st.executeQuery("SELECT id_tarifa, nombre_tarifa, descuento FROM ventas.v_tarifas")) {
            while (rs.next()) {
                tarifas.add(new Tarifa(rs.getInt(1), rs.getString(2), rs.getBigDecimal(3)));
            }
        }
        return tarifas;
    }
}
