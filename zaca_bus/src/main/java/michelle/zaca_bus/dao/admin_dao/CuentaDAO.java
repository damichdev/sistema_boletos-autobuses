package michelle.zaca_bus.dao.admin_dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import michelle.zaca_bus.model.Catalogo;
import michelle.zaca_bus.model.Cuenta;
import michelle.zaca_bus.util.ConexionBD;
import michelle.zaca_bus.util.Sql;

public class CuentaDAO {

    public List<Catalogo> roles() throws SQLException {
        return Sql.catalogo("SELECT id_rol, nombre_rol FROM administracion.v_roles");
    }

    public List<Cuenta> listar() throws SQLException {
        List<Cuenta> cuentas = new ArrayList<>();
        try (Statement st = ConexionBD.getInstancia().getConexion().createStatement();
                ResultSet rs = st.executeQuery("""
                        SELECT id_app, id_personal, username, nombre_completo, id_rol, nombre_rol,
                               id_terminal, nombre_terminal, id_taquilla, identificador, activo
                        FROM administracion.v_cuentas""")) {
            while (rs.next()) {
                cuentas.add(new Cuenta(rs.getInt(1), rs.getInt(2), rs.getString(3), rs.getString(4),
                        new Catalogo(rs.getInt(5), rs.getString(6)), opcional(rs, 7), opcional(rs, 9), rs.getBoolean(11)));
            }
        }
        return cuentas;
    }

    /** Alta (idApp 0) o edición; `hash` es el de la contraseña nueva, o null para no cambiarla. 23505: usuario o persona repetidos. */
    public void guardar(Cuenta c, String hash) throws SQLException {
        Sql.transaccion(con -> {
            Object sede = c.sede() == null ? null : c.sede().id();
            Object taquilla = c.taquilla() == null ? null : c.taquilla().id();
            Sql.ejecutar(con, "CALL administracion.sp_guardar_cuenta(?, ?, ?, ?, ?, ?, ?, ?)",
                    c.idApp(), c.idPersonal(), c.rol().id(), sede, taquilla, c.username(), hash, c.activa());
        });
    }

    public void cambiarEstado(int idApp, boolean activa) throws SQLException {
        Sql.transaccion(con -> Sql.ejecutar(con, "CALL administracion.sp_cambiar_estado_cuenta(?, ?)", idApp, activa));
    }

    /** Un elemento de catálogo cuyo id puede ser NULL en la base (sede, taquilla). */
    private static Catalogo opcional(ResultSet rs, int columna) throws SQLException {
        Integer id = rs.getObject(columna, Integer.class);
        return id == null ? null : new Catalogo(id, rs.getString(columna + 1));
    }
}
