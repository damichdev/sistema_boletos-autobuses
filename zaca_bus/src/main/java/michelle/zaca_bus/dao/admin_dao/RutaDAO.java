package michelle.zaca_bus.dao.admin_dao;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import michelle.zaca_bus.model.Catalogo;
import michelle.zaca_bus.model.Parada;
import michelle.zaca_bus.model.Ruta;
import michelle.zaca_bus.model.Tramo;
import michelle.zaca_bus.util.ConexionBD;
import michelle.zaca_bus.util.Sql;

public class RutaDAO {

    public List<Catalogo> servicios() throws SQLException {
        return Sql.catalogo("SELECT id_servicio, nombre_servicio FROM viajes.v_servicios");
    }

    /** Horas de salida oficiales de la ruta, de la más temprana a la más tarde. */
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

    /** Un SQLException con estado 23505 significa que la ruta ya tiene esa hora. */
    public void agregarHorario(int idRuta, LocalTime hora) throws SQLException {
        Sql.transaccion(con -> Sql.ejecutar(con, "CALL viajes.sp_agregar_horario_ruta(?, ?)", idRuta, hora));
    }

    public void quitarHorario(int idRuta, LocalTime hora) throws SQLException {
        Sql.transaccion(con -> Sql.ejecutar(con, "CALL viajes.sp_quitar_horario_ruta(?, ?)", idRuta, hora));
    }

    /** Rutas con sus paradas y precios; la posición de cada parada es su `orden` (1, 2, 3…). */
    public List<Ruta> listar() throws SQLException {
        Map<Integer, List<Parada>> paradas = new HashMap<>();
        Map<Integer, List<Tramo>> tramos = new HashMap<>();
        List<Ruta> rutas = new ArrayList<>();
        try (Statement st = ConexionBD.getInstancia().getConexion().createStatement()) {
            try (ResultSet rs = st.executeQuery("""
                    SELECT id_ruta, id_terminal, nombre_terminal, km_desde_origen, min_desde_origen
                    FROM viajes.v_ruta_paradas""")) {
                while (rs.next()) {
                    paradas.computeIfAbsent(rs.getInt(1), k -> new ArrayList<>())
                            .add(new Parada(new Catalogo(rs.getInt(2), rs.getString(3)), rs.getInt(4), rs.getInt(5)));
                }
            }
            try (ResultSet rs = st.executeQuery("SELECT id_ruta, orden_origen, orden_destino, precio FROM viajes.v_precio_tramo")) {
                while (rs.next()) {
                    tramos.computeIfAbsent(rs.getInt(1), k -> new ArrayList<>())
                            .add(new Tramo(rs.getInt(2), rs.getInt(3), rs.getBigDecimal(4)));
                }
            }
            try (ResultSet rs = st.executeQuery("""
                    SELECT id_ruta, nombre_ruta, id_servicio, nombre_servicio
                    FROM viajes.v_rutas""")) {
                while (rs.next()) {
                    int id = rs.getInt(1);
                    rutas.add(new Ruta(id, rs.getString(2), new Catalogo(rs.getInt(3), rs.getString(4)),
                            paradas.getOrDefault(id, List.of()), tramos.getOrDefault(id, List.of())));
                }
            }
        }
        return rutas;
    }

    /**
     * Alta (idRuta 0) o edición. Todo en una transacción: o se guarda la ruta completa o nada.
     * Al editar, sp_guardar_ruta borra las paradas (y sus precios en cascada) y aquí se vuelven a insertar.
     */
    public void guardar(Ruta ruta) throws SQLException {
        Sql.transaccion(con -> {
            int id;
            // p_id_ruta es INOUT: con executeQuery el procedure devuelve el id en una fila.
            try (PreparedStatement ps = con.prepareStatement("CALL viajes.sp_guardar_ruta(?, ?, ?)")) {
                ps.setInt(1, ruta.idRuta());
                ps.setInt(2, ruta.servicio().id());
                ps.setString(3, ruta.nombre());
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    id = rs.getInt(1);
                }
            }
            int orden = 1;
            for (Parada p : ruta.paradas()) {
                Sql.ejecutar(con, "CALL viajes.sp_guardar_parada(?, ?, ?, ?, ?)",
                        id, p.terminal().id(), orden++, p.min(), p.km());
            }
            for (Tramo t : ruta.tramos()) {
                Sql.ejecutar(con, "CALL viajes.sp_guardar_tramo(?, ?, ?, ?)",
                        id, t.origen(), t.destino(), t.precio());
            }
        });
    }
}
