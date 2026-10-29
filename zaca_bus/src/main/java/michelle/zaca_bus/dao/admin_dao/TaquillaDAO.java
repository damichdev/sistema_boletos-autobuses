package michelle.zaca_bus.dao.admin_dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import michelle.zaca_bus.model.Catalogo;
import michelle.zaca_bus.model.Taquilla;
import michelle.zaca_bus.util.ConexionBD;
import michelle.zaca_bus.util.Sql;

public class TaquillaDAO {

    public List<Taquilla> listar() throws SQLException {
        List<Taquilla> taquillas = new ArrayList<>();
        try (Statement st = ConexionBD.getInstancia().getConexion().createStatement();
                ResultSet rs = st.executeQuery("""
                        SELECT id_taquilla, id_terminal, nombre_terminal, identificador, serie_folio,
                               rango_inicial, rango_final, folios_usados, activo
                        FROM administracion.v_taquillas""")) {
            while (rs.next()) {
                taquillas.add(new Taquilla(rs.getInt(1), new Catalogo(rs.getInt(2), rs.getString(3)), rs.getString(4),
                        rs.getString(5), rs.getInt(6), rs.getInt(7), rs.getInt(8), rs.getBoolean(9)));
            }
        }
        return taquillas;
    }

    /** Alta (idTaquilla 0) o edición. Un SQLException 23505 es identificador o serie repetidos; 23514, rango al revés. */
    public void guardar(Taquilla t) throws SQLException {
        Sql.transaccion(con -> Sql.ejecutar(con, "CALL administracion.sp_guardar_taquilla(?, ?, ?, ?, ?, ?, ?)",
                t.idTaquilla(), t.terminal().id(), t.identificador(), t.serie(), t.rangoInicial(), t.rangoFinal(), t.activa()));
    }
}
