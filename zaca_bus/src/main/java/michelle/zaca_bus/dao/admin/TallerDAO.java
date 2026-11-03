package michelle.zaca_bus.dao.admin;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import michelle.zaca_bus.model.Catalogo;
import michelle.zaca_bus.model.JefeTaller;
import michelle.zaca_bus.model.Taller;
import michelle.zaca_bus.util.ConexionBD;
import michelle.zaca_bus.util.Sql;

/**
 * Talleres (catálogo de la base principal); sus autobuses salen de las órdenes abiertas (base `taller` por FDW).
 */
public class TallerDAO {

    public List<Taller> listar() throws SQLException {
        List<Taller> talleres = new ArrayList<>();
        Map<Integer, Integer> enTaller = new HashMap<>();
        try (Statement st = ConexionBD.getInstancia().getConexion().createStatement();
                ResultSet rs = st.executeQuery(
                        "SELECT id_taller, COUNT(*) FROM viajes.v_ordenes_taller WHERE abierta GROUP BY id_taller")) {
            while (rs.next()) {
                enTaller.put(rs.getInt(1), rs.getInt(2));
            }
        }
        try (Statement st = ConexionBD.getInstancia().getConexion().createStatement();
                ResultSet rs = st.executeQuery("""
                        SELECT id_taller, id_ciudad, clave, nombre_taller, nombre_ciudad, direccion, mecanicos, encargado, activo
                        FROM administracion.v_talleres""")) {
            while (rs.next()) {
                talleres.add(new Taller(rs.getInt(1), rs.getInt(2), rs.getString(3), rs.getString(4), rs.getString(5),
                        rs.getString(6), enTaller.getOrDefault(rs.getInt(1), 0), rs.getInt(7), rs.getString(8),
                        rs.getBoolean(9)));
            }
        }
        return talleres;
    }

    /**
     * Alta (idTaller 0) o edición, y asigna al encargado si `idEncargado` no es null. 23505: clave o nombre repetidos;
     * Z0050: el taller no existe; Z0051: el jefe no es válido o el encargado actual tiene cuenta.
     */
    public void guardar(int idTaller, int idCiudad, String clave, String nombre, String direccion, Integer idEncargado)
            throws SQLException {
        Sql.transaccion(con -> {
            // p_id_taller es INOUT: el procedure devuelve una fila, así que va con executeQuery.
            try (PreparedStatement ps = con.prepareStatement("CALL administracion.sp_guardar_taller(?, ?, ?, ?, ?)")) {
                ps.setInt(1, idTaller);
                ps.setInt(2, idCiudad);
                ps.setString(3, clave);
                ps.setString(4, nombre);
                ps.setString(5, direccion);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    if (idEncargado != null) {
                        Sql.ejecutar(con, "CALL administracion.sp_asignar_encargado(?, ?)", rs.getInt(1), idEncargado);
                    }
                }
            }
        });
    }

    /** Baja o reactivación. Z0052: el taller tiene personal activo asignado. */
    public void cambiarEstado(int idTaller, boolean activo) throws SQLException {
        Sql.transaccion(con -> Sql.ejecutar(con, "CALL administracion.sp_cambiar_estado_taller(?, ?)", idTaller, activo));
    }

    /** Talleres activos (catálogo de la base principal), para asignar a un mecánico o jefe de taller. */
    public List<Catalogo> activos() throws SQLException {
        return Sql.catalogo("SELECT id_taller, nombre_taller FROM administracion.v_talleres_activos");
    }

    /** Jefes de taller que pueden encargar el taller `idTaller` (0 en un alta): los libres y el que ya lo encarga. */
    public List<JefeTaller> encargadosDisponibles(int idTaller) throws SQLException {
        List<JefeTaller> jefes = new ArrayList<>();
        try (Statement st = ConexionBD.getInstancia().getConexion().createStatement();
                ResultSet rs = st.executeQuery("SELECT id_personal, nombre_completo, id_taller FROM administracion.v_jefes_taller")) {
            while (rs.next()) {
                Integer taller = rs.getObject(3, Integer.class);
                if (taller == null || taller == idTaller) {
                    jefes.add(new JefeTaller(rs.getInt(1), rs.getString(2), taller));
                }
            }
        }
        return jefes;
    }
}
