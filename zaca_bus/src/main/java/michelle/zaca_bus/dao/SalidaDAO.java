package michelle.zaca_bus.dao;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import michelle.zaca_bus.model.Catalogo;
import michelle.zaca_bus.model.ParadaViaje;
import michelle.zaca_bus.model.Salida;
import michelle.zaca_bus.util.ConexionBD;
import michelle.zaca_bus.util.Formato;
import michelle.zaca_bus.util.Sql;

public class SalidaDAO {

    public List<Catalogo> rutas() throws SQLException {
        return Sql.catalogo("SELECT id_ruta, nombre_ruta FROM viajes.ruta ORDER BY nombre_ruta");
    }

    public List<Catalogo> unidades() throws SQLException {
        return Sql.catalogo("""
                SELECT u.id_unidad, u.placa || ' · ' || u.modelo || ' · ' || tb.capacidad || ' asientos'
                FROM viajes.unidad u JOIN viajes.tipo_bus tb ON tb.id_tipo_bus = u.id_tipo_bus
                ORDER BY u.placa""");
    }

    public List<Catalogo> choferes() throws SQLException {
        return Sql.catalogo("""
                SELECT p.id_personal, p.nombre_personal || ' ' || p.ap_paterno || ' ' || p.ap_materno
                FROM administracion.personal p JOIN administracion.puesto pu ON pu.id_puesto = p.id_puesto
                WHERE pu.nombre_puesto = 'CONDUCTOR'
                ORDER BY p.ap_paterno, p.ap_materno, p.nombre_personal""");
    }

    /** Salidas del día `dia` (en la zona de las terminales), por hora. */
    public List<Salida> listar(LocalDate dia) throws SQLException {
        List<Salida> salidas = new ArrayList<>();
        try (PreparedStatement ps = ConexionBD.getInstancia().getConexion().prepareStatement("""
                SELECT v.id_viaje, r.id_ruta, r.nombre_ruta, ts.nombre_servicio, v.fecha_salida, ev.nombre_estado_viaje,
                       u.placa, u.modelo, tb.capacidad,
                       p.nombre_personal || ' ' || p.ap_paterno || ' ' || p.ap_materno, p.vigencia
                FROM viajes.viaje_programado v
                JOIN viajes.ruta r ON r.id_ruta = v.id_ruta
                JOIN viajes.tipo_servicio ts ON ts.id_servicio = r.id_servicio
                JOIN viajes.estado_viaje ev ON ev.id_estado_viaje = v.id_estado_viaje
                JOIN viajes.unidad u ON u.id_unidad = v.id_unidad
                JOIN viajes.tipo_bus tb ON tb.id_tipo_bus = u.id_tipo_bus
                JOIN administracion.personal p ON p.id_personal = v.id_conductor
                WHERE v.fecha_salida >= ? AND v.fecha_salida < ?
                ORDER BY v.fecha_salida""")) {
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
        Sql.transaccion(con -> Sql.ejecutar(con, """
                INSERT INTO viajes.viaje_programado (id_ruta, id_estado_viaje, id_unidad, id_conductor, fecha_salida)
                VALUES (?, (SELECT id_estado_viaje FROM viajes.estado_viaje WHERE nombre_estado_viaje = 'PROGRAMADO'), ?, ?, ?)""",
                idRuta, idUnidad, idChofer, salida.toOffsetDateTime()));
    }

    /** Recorrido del viaje: hora estimada por parada, quiénes suben y bajan, y la ocupación hacia la siguiente parada. */
    public List<ParadaViaje> recorrido(Salida salida) throws SQLException {
        List<ParadaViaje> paradas = new ArrayList<>();
        try (PreparedStatement ps = ConexionBD.getInstancia().getConexion().prepareStatement("""
                SELECT t.nombre_terminal, rp.min_desde_origen,
                       (SELECT count(*) FROM ventas.boleto b WHERE b.id_viaje = ? AND b.id_estado_boleto <> 4 AND b.id_terminal_origen = rp.id_terminal),
                       (SELECT count(*) FROM ventas.boleto b WHERE b.id_viaje = ? AND b.id_estado_boleto <> 4 AND b.id_terminal_destino = rp.id_terminal),
                       (SELECT count(*) FROM ventas.boleto b WHERE b.id_viaje = ? AND b.id_estado_boleto <> 4 AND b.tramo_ocupacion @> rp.orden)
                FROM viajes.ruta_paradas rp
                JOIN administracion.terminal t ON t.id_terminal = rp.id_terminal
                WHERE rp.id_ruta = ?
                ORDER BY rp.orden""")) {
            ps.setInt(1, salida.idViaje());
            ps.setInt(2, salida.idViaje());
            ps.setInt(3, salida.idViaje());
            ps.setInt(4, salida.idRuta());
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
