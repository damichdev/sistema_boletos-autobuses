package michelle.zaca_bus.dao.admin;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import michelle.zaca_bus.model.Catalogo;
import michelle.zaca_bus.model.Cuenta;
import michelle.zaca_bus.util.ConexionBD;
import michelle.zaca_bus.util.Seguridad;
import michelle.zaca_bus.util.Sql;

public class CuentaDAO {

    private static final String HASH_FALSO = Seguridad.generarHash("sin-usuario");

    public List<Catalogo> roles() throws SQLException {
        return Sql.catalogo("SELECT id_rol, nombre_rol FROM administracion.v_roles");
    }

    public List<Cuenta> listar() throws SQLException {
        List<Cuenta> cuentas = new ArrayList<>();
        try (Statement st = ConexionBD.getInstancia().getConexion().createStatement();
                ResultSet rs = st.executeQuery("""
                        SELECT id_app, id_personal, username, nombre_completo, id_rol, nombre_rol,
                               id_terminal, nombre_terminal, id_taquilla, identificador, activo, id_taller, nombre_taller
                        FROM administracion.v_cuentas""")) {
            while (rs.next()) {
                cuentas.add(new Cuenta(rs.getInt(1), rs.getInt(2), rs.getString(3), rs.getString(4),
                        new Catalogo(rs.getInt(5), rs.getString(6)), opcional(rs, 7), opcional(rs, 9), rs.getBoolean(11), opcional(rs, 12)));
            }
        }
        return cuentas;
    }

    /** Cuenta del usuario si la contraseña es correcta y la cuenta está activa; vacío en cualquier otro caso. */
    public Optional<Cuenta> autenticar(String username, String password) throws SQLException {
        int idApp;
        try (PreparedStatement ps = ConexionBD.getInstancia().getConexion().prepareStatement(
                "SELECT id_app, password_hash, activo FROM administracion.fn_obtener_credencial(?)")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                // Sin usuario se verifica contra un hash falso para que tardar lo mismo no delate si el usuario existe.
                if (!rs.next()) {
                    Seguridad.verificar(password, HASH_FALSO);
                    return Optional.empty();
                }
                if (!Seguridad.verificar(password, rs.getString(2)) || !rs.getBoolean(3)) {
                    return Optional.empty();
                }
                idApp = rs.getInt(1);
            }
        }
        return listar().stream().filter(c -> c.idApp() == idApp).findFirst();
    }

    /** Alta (idApp 0) o edición; `hash` es el de la contraseña nueva, o null para no cambiarla. 23505: usuario o persona repetidos; Z0026: puesto sin cuenta; Z0027: rol distinto al puesto; Z0025: jefe sin taller. */
    public void guardar(Cuenta c, String hash) throws SQLException {
        Sql.transaccion(con -> {
            // Terminal y taquilla ya no se guardan aquí: pertenecen al empleado (sp_guardar_personal).
            Sql.ejecutar(con, "CALL administracion.sp_guardar_cuenta(?, ?, ?, ?, ?, ?)",
                    c.idApp(), c.idPersonal(), c.rol().id(), c.username(), hash, c.activa());
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

