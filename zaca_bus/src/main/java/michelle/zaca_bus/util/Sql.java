package michelle.zaca_bus.util;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import michelle.zaca_bus.model.Catalogo;

/** Lo que repiten todos los DAO: catálogos, ejecutar una sentencia y transacciones. */
public final class Sql {

    @FunctionalInterface
    public interface Accion {
        void ejecutar(Connection con) throws SQLException;
    }

    private Sql() {
    }

    /** Consulta de dos columnas (id, nombre) convertida en lista de catálogo. */
    public static List<Catalogo> catalogo(String consulta) throws SQLException {
        List<Catalogo> elementos = new ArrayList<>();
        try (Statement st = ConexionBD.getInstancia().getConexion().createStatement();
                ResultSet rs = st.executeQuery(consulta)) {
            while (rs.next()) {
                elementos.add(new Catalogo(rs.getInt(1), rs.getString(2)));
            }
        }
        return elementos;
    }

    /** INSERT, UPDATE o DELETE con parámetros. */
    public static void ejecutar(Connection con, String sentencia, Object... valores) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(sentencia)) {
            for (int i = 0; i < valores.length; i++) {
                ps.setObject(i + 1, valores[i]);
            }
            ps.executeUpdate();
        }
    }

    /** Todo lo de `accion` se guarda junto o no se guarda nada. */
    public static void transaccion(Accion accion) throws SQLException {
        Connection con = ConexionBD.getInstancia().getConexion();
        con.setAutoCommit(false);
        try {
            accion.ejecutar(con);
            con.commit();
        } catch (SQLException | RuntimeException e) {
            con.rollback();
            throw e;
        } finally {
            con.setAutoCommit(true);
        }
    }
}
