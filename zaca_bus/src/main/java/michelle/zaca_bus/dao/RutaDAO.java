package michelle.zaca_bus.dao;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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
        return Sql.catalogo("SELECT id_servicio, nombre_servicio FROM viajes.tipo_servicio ORDER BY id_servicio");
    }

    /** Rutas con sus paradas y precios; la posición de cada parada es su `orden` (1, 2, 3…). */
    public List<Ruta> listar() throws SQLException {
        Map<Integer, List<Parada>> paradas = new HashMap<>();
        Map<Integer, List<Tramo>> tramos = new HashMap<>();
        List<Ruta> rutas = new ArrayList<>();
        try (Statement st = ConexionBD.getInstancia().getConexion().createStatement()) {
            try (ResultSet rs = st.executeQuery("""
                    SELECT rp.id_ruta, t.id_terminal, t.nombre_terminal, rp.km_desde_origen, rp.min_desde_origen
                    FROM viajes.ruta_paradas rp
                    JOIN administracion.terminal t ON t.id_terminal = rp.id_terminal
                    ORDER BY rp.id_ruta, rp.orden""")) {
                while (rs.next()) {
                    paradas.computeIfAbsent(rs.getInt(1), k -> new ArrayList<>())
                            .add(new Parada(new Catalogo(rs.getInt(2), rs.getString(3)), rs.getInt(4), rs.getInt(5)));
                }
            }
            try (ResultSet rs = st.executeQuery("SELECT id_ruta, orden_origen, orden_destino, precio FROM viajes.precio_tramo")) {
                while (rs.next()) {
                    tramos.computeIfAbsent(rs.getInt(1), k -> new ArrayList<>())
                            .add(new Tramo(rs.getInt(2), rs.getInt(3), rs.getBigDecimal(4)));
                }
            }
            try (ResultSet rs = st.executeQuery("""
                    SELECT r.id_ruta, r.nombre_ruta, s.id_servicio, s.nombre_servicio
                    FROM viajes.ruta r
                    JOIN viajes.tipo_servicio s ON s.id_servicio = r.id_servicio
                    ORDER BY r.nombre_ruta""")) {
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
     * Al editar se borran las paradas y se vuelven a insertar; sus precios se borran en cascada.
     */
    public void guardar(Ruta ruta) throws SQLException {
        Sql.transaccion(con -> {
            int id = ruta.idRuta();
            if (id == 0) {
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO viajes.ruta (id_servicio, nombre_ruta) VALUES (?, ?) RETURNING id_ruta")) {
                    ps.setInt(1, ruta.servicio().id());
                    ps.setString(2, ruta.nombre());
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        id = rs.getInt(1);
                    }
                }
            } else {
                Sql.ejecutar(con, "UPDATE viajes.ruta SET id_servicio = ?, nombre_ruta = ? WHERE id_ruta = ?",
                        ruta.servicio().id(), ruta.nombre(), id);
                Sql.ejecutar(con, "DELETE FROM viajes.ruta_paradas WHERE id_ruta = ?", id);
            }
            int orden = 1;
            for (Parada p : ruta.paradas()) {
                Sql.ejecutar(con, "INSERT INTO viajes.ruta_paradas (id_ruta, id_terminal, orden, min_desde_origen, km_desde_origen) VALUES (?, ?, ?, ?, ?)",
                        id, p.terminal().id(), orden++, p.min(), p.km());
            }
            for (Tramo t : ruta.tramos()) {
                Sql.ejecutar(con, "INSERT INTO viajes.precio_tramo (id_ruta, orden_origen, orden_destino, precio) VALUES (?, ?, ?, ?)",
                        id, t.origen(), t.destino(), t.precio());
            }
        });
    }
}
