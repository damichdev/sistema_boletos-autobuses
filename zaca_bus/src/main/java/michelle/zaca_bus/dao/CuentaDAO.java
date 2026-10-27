package michelle.zaca_bus.dao;

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
        return Sql.catalogo("SELECT id_rol, nombre_rol FROM administracion.rol ORDER BY id_rol");
    }

    public List<Cuenta> listar() throws SQLException {
        List<Cuenta> cuentas = new ArrayList<>();
        try (Statement st = ConexionBD.getInstancia().getConexion().createStatement();
                ResultSet rs = st.executeQuery("""
                        SELECT pa.id_app, pa.id_personal, pa.username,
                               p.nombre_personal || ' ' || p.ap_paterno || ' ' || p.ap_materno,
                               r.id_rol, r.nombre_rol, t.id_terminal, t.nombre_terminal, q.id_taquilla, q.identificador, pa.activo
                        FROM administracion.personal_app pa
                        JOIN administracion.personal p ON p.id_personal = pa.id_personal
                        JOIN administracion.rol r ON r.id_rol = pa.id_rol
                        LEFT JOIN administracion.terminal t ON t.id_terminal = pa.id_terminal
                        LEFT JOIN administracion.taquilla q ON q.id_taquilla = pa.id_taquilla
                        ORDER BY pa.username""")) {
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
            if (c.idApp() == 0) {
                Sql.ejecutar(con, "INSERT INTO administracion.personal_app (id_personal, id_rol, id_terminal, id_taquilla, username, password_hash, activo) VALUES (?, ?, ?, ?, ?, ?, ?)",
                        c.idPersonal(), c.rol().id(), sede, taquilla, c.username(), hash, c.activa());
            } else {
                Sql.ejecutar(con, "UPDATE administracion.personal_app SET id_rol = ?, id_terminal = ?, id_taquilla = ?, activo = ? WHERE id_app = ?",
                        c.rol().id(), sede, taquilla, c.activa(), c.idApp());
                if (hash != null) {
                    Sql.ejecutar(con, "UPDATE administracion.personal_app SET password_hash = ? WHERE id_app = ?", hash, c.idApp());
                }
            }
        });
    }

    public void cambiarEstado(int idApp, boolean activa) throws SQLException {
        Sql.transaccion(con -> Sql.ejecutar(con, "UPDATE administracion.personal_app SET activo = ? WHERE id_app = ?", activa, idApp));
    }

    /** Un elemento de catálogo cuyo id puede ser NULL en la base (sede, taquilla). */
    private static Catalogo opcional(ResultSet rs, int columna) throws SQLException {
        Integer id = rs.getObject(columna, Integer.class);
        return id == null ? null : new Catalogo(id, rs.getString(columna + 1));
    }
}
