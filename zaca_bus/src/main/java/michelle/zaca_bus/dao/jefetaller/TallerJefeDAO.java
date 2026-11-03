package michelle.zaca_bus.dao.jefetaller;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import michelle.zaca_bus.model.BusTaller;
import michelle.zaca_bus.model.Catalogo;
import michelle.zaca_bus.util.ConexionBD;
import michelle.zaca_bus.util.Formato;
import michelle.zaca_bus.util.Sql;

/**
 * Datos de la pantalla del jefe de taller: flota y catálogos de los formularios. Las órdenes están en OrdenTallerDAO.
 * El estado de las unidades viene de la base `taller` por FDW (viajes.v_flota_taller).
 */
public class TallerJefeDAO {

    /** Semáforo de la flota. */
    public List<BusTaller> flota() throws SQLException {
        List<BusTaller> flota = new ArrayList<>();
        try (Statement st = ConexionBD.getInstancia().getConexion().createStatement();
                ResultSet rs = st.executeQuery("""
                        SELECT placa, modelo, estatus, apto_para_viajar, ultima_fecha_ingreso, observaciones
                        FROM viajes.v_flota_taller""")) {
            while (rs.next()) {
                OffsetDateTime ingreso = rs.getObject(5, OffsetDateTime.class);
                flota.add(new BusTaller(rs.getString(1), rs.getString(2), rs.getString(3), rs.getBoolean(4),
                        ingreso == null ? null : Formato.fechaHora(ingreso.atZoneSameInstant(Formato.ZONA)),
                        rs.getString(6)));
            }
        }
        return flota;
    }

    /** Placas de las unidades que pueden ingresar (aptas: sin orden abierta y no de baja). */
    public List<String> unidadesLibres() throws SQLException {
        List<String> placas = new ArrayList<>();
        try (Statement st = ConexionBD.getInstancia().getConexion().createStatement();
                ResultSet rs = st.executeQuery("SELECT placa FROM viajes.v_unidades_disponibles ORDER BY placa")) {
            while (rs.next()) {
                placas.add(rs.getString(1));
            }
        }
        return placas;
    }

    /** Mecánicos activos del taller. */
    public List<Catalogo> mecanicos(int idTaller) throws SQLException {
        List<Catalogo> mecanicos = new ArrayList<>();
        try (PreparedStatement ps = ConexionBD.getInstancia().getConexion().prepareStatement(
                "SELECT id_personal, nombre_completo FROM administracion.v_mecanicos_taller WHERE id_taller = ?")) {
            ps.setInt(1, idTaller);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    mecanicos.add(new Catalogo(rs.getInt(1), rs.getString(2)));
                }
            }
        }
        return mecanicos;
    }

    /** Cuántas salidas programadas a futuro tiene la unidad (el despacho deberá cambiarles la unidad). */
    public int salidasPendientes(String placa) throws SQLException {
        try (PreparedStatement ps = ConexionBD.getInstancia().getConexion().prepareStatement("""
                SELECT COUNT(*) FROM viajes.viaje_programado v
                INNER JOIN viajes.unidad u ON u.id_unidad = v.id_unidad
                WHERE u.placa = ? AND v.id_estado_viaje = 1 AND v.fecha_salida > now()""")) {
            ps.setString(1, placa);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }
}
