package michelle.zaca_bus.dao.despachador_dao;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import michelle.zaca_bus.model.Catalogo;
import michelle.zaca_bus.model.Parada;
import michelle.zaca_bus.model.ParadaViaje;
import michelle.zaca_bus.model.Salida;
import michelle.zaca_bus.util.ConexionBD;
import michelle.zaca_bus.util.Formato;
import michelle.zaca_bus.util.Sql;

public class SalidaDAO {

    public List<Catalogo> rutas() throws SQLException {
        return Sql.catalogo("SELECT id_ruta, nombre_ruta FROM viajes.v_rutas");
    }

    public List<Catalogo> unidades() throws SQLException {
        return Sql.catalogo("SELECT id_unidad, descripcion_unidad FROM viajes.v_catalogo_unidades");
    }

    public List<Catalogo> choferes() throws SQLException {
        return Sql.catalogo("SELECT id_personal, chofer FROM administracion.v_choferes");
    }

    /** Horarios base de la ruta (fn_obtener_horarios_ruta); vacío si la ruta aún no tiene. */
    public List<LocalTime> horarios(int idRuta) throws SQLException {
        List<LocalTime> horas = new ArrayList<>();
        try (PreparedStatement ps = ConexionBD.getInstancia().getConexion().prepareStatement(
                "SELECT hora_salida FROM viajes.fn_obtener_horarios_ruta(?)")) {
            ps.setInt(1, idRuta);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    horas.add(rs.getObject(1, LocalTime.class));
                }
            }
        }
        return horas;
    }

    /** Paradas de la ruta en orden, con los minutos que tarda el bus en llegar a cada una desde el origen. */
    public List<Parada> paradas(int idRuta) throws SQLException {
        List<Parada> paradas = new ArrayList<>();
        try (PreparedStatement ps = ConexionBD.getInstancia().getConexion().prepareStatement(
                "SELECT id_terminal, nombre_terminal, km_desde_origen, min_desde_origen FROM viajes.v_ruta_paradas WHERE id_ruta = ?")) {
            ps.setInt(1, idRuta);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    paradas.add(new Parada(new Catalogo(rs.getInt(1), rs.getString(2)), rs.getInt(3), rs.getInt(4)));
                }
            }
        }
        return paradas;
    }

    /** Salidas del día `dia` (en la zona de las terminales), por hora. */
    public List<Salida> listar(LocalDate dia) throws SQLException {
        List<Salida> salidas = new ArrayList<>();
        try (PreparedStatement ps = ConexionBD.getInstancia().getConexion().prepareStatement("""
                SELECT id_viaje, id_ruta, nombre_ruta, nombre_servicio, fecha_salida, nombre_estado_viaje,
                       placa, modelo, capacidad, nombre_conductor, vigencia
                FROM viajes.fn_buscar_salidas_por_dia(?, ?)""")) {
            ps.setObject(1, dia.atStartOfDay(Formato.ZONA).toOffsetDateTime());
            ps.setObject(2, dia.plusDays(1).atStartOfDay(Formato.ZONA).toOffsetDateTime());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    salidas.add(new Salida(rs.getInt(1), rs.getInt(2), rs.getString(3), rs.getString(4),
                            rs.getObject(5, OffsetDateTime.class).atZoneSameInstant(Formato.ZONA), rs.getString(6),
                            rs.getString(7), rs.getString(8), rs.getInt(9), rs.getString(10), rs.getObject(11, LocalDate.class)));
                }
            }
        }
        return salidas;
    }

    /**
     * Crea el viaje en estado PROGRAMADO. La base valida que el chofer sea CONDUCTOR (Z0007), que su licencia esté
     * vigente ese día (Z0006) y que ni la unidad ni el chofer tengan otro viaje en ese horario (Z0012).
     */
    public void programar(int idRuta, int idUnidad, int idChofer, ZonedDateTime salida) throws SQLException {
        Sql.transaccion(con -> Sql.ejecutar(con, "CALL viajes.sp_programar_viaje(?, ?, ?, ?)",
                idRuta, idUnidad, idChofer, salida.toOffsetDateTime()));
    }

    /** Recorrido del viaje: hora estimada por parada, quiénes suben y bajan, y la ocupación hacia la siguiente parada. */
    public List<ParadaViaje> recorrido(Salida salida) throws SQLException {
        List<ParadaViaje> paradas = new ArrayList<>();
        try (PreparedStatement ps = ConexionBD.getInstancia().getConexion().prepareStatement(
                "SELECT nombre_terminal, min_desde_origen, suben, bajan, ocupacion_en_tramo FROM viajes.fn_obtener_recorrido_viaje(?, ?)")) {
            ps.setInt(1, salida.idViaje());
            ps.setInt(2, salida.idRuta());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    paradas.add(new ParadaViaje(rs.getString(1), salida.fecha().plusMinutes(rs.getInt(2)),
                            rs.getInt(3), rs.getInt(4), rs.getInt(5)));
                }
            }
        }
        return paradas;
    }
}
