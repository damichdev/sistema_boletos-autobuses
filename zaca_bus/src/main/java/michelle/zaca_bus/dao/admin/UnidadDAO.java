package michelle.zaca_bus.dao.admin;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import michelle.zaca_bus.model.Acomodo;
import michelle.zaca_bus.model.Catalogo;
import michelle.zaca_bus.model.TipoBus;
import michelle.zaca_bus.model.Unidad;
import michelle.zaca_bus.util.ConexionBD;
import michelle.zaca_bus.util.Sql;

public class UnidadDAO {

    public List<Catalogo> tipos() throws SQLException {
        return Sql.catalogo("SELECT id_nombre_tipo_bus, nombre_tipo_bus FROM viajes.v_nombre_tipo_bus");
    }

    /** Acomodos ya registrados para un tipo de servicio: las combinaciones (layout, capacidad) de viajes.tipo_bus. */
    public List<Acomodo> acomodosDe(int idTipo) throws SQLException {
        List<Acomodo> acomodos = new ArrayList<>();
        try (PreparedStatement ps = ConexionBD.getInstancia().getConexion().prepareStatement("""
                SELECT id_layout, nombre_layout, capacidad FROM viajes.fn_obtener_acomodos(?)""")) {
            ps.setInt(1, idTipo);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    acomodos.add(new Acomodo(rs.getInt(1), rs.getString(2), rs.getInt(3)));
                }
            }
        }
        return acomodos;
    }

    /** Distribuciones de asientos que existen en el script: cuántos asientos a cada lado del pasillo, como "2+2". */
    public List<Catalogo> distribuciones() throws SQLException {
        return Sql.catalogo("SELECT id_layout, nombre_layout FROM viajes.v_layouts");
    }

    /** Todos los acomodos registrados, de cualquier tipo de servicio. */
    public List<TipoBus> acomodos() throws SQLException {
        List<TipoBus> acomodos = new ArrayList<>();
        try (Statement st = ConexionBD.getInstancia().getConexion().createStatement();
                ResultSet rs = st.executeQuery("""
                        SELECT n.id_nombre_tipo_bus, n.nombre_tipo_bus, l.id_layout, l.nombre_layout, tb.capacidad
                        FROM viajes.tipo_bus tb
                        INNER JOIN viajes.nombre_tipo_bus n ON n.id_nombre_tipo_bus = tb.id_nombre_tipo_bus
                        INNER JOIN viajes.layout l ON l.id_layout = tb.id_layout
                        ORDER BY n.nombre_tipo_bus, l.nombre_layout, tb.capacidad""")) {
            while (rs.next()) {
                acomodos.add(new TipoBus(new Catalogo(rs.getInt(1), rs.getString(2)),
                        new Acomodo(rs.getInt(3), rs.getString(4), rs.getInt(5))));
            }
        }
        return acomodos;
    }

    /**
     * Registra un acomodo (tipo de servicio, distribución y capacidad) en viajes.tipo_bus, que es de donde salen los
     * acomodos al dar de alta una unidad. Devuelve false si ya estaba registrado.
     */
    public boolean guardarAcomodo(int idTipo, int idLayout, int capacidad) throws SQLException {
        boolean[] nuevo = new boolean[1];
        Sql.transaccion(con -> {
            try (PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO viajes.tipo_bus (id_nombre_tipo_bus, id_layout, capacidad)
                    SELECT ?, ?, ?
                    WHERE NOT EXISTS (SELECT 1 FROM viajes.tipo_bus
                                      WHERE id_nombre_tipo_bus = ? AND id_layout = ? AND capacidad = ?)""")) {
                for (int i = 0; i < 2; i++) {
                    ps.setInt(1 + 3 * i, idTipo);
                    ps.setInt(2 + 3 * i, idLayout);
                    ps.setInt(3 + 3 * i, capacidad);
                }
                nuevo[0] = ps.executeUpdate() > 0;
            }
        });
        return nuevo[0];
    }

    public List<Unidad> listar() throws SQLException {
        List<Unidad> unidades = new ArrayList<>();
        try (Statement st = ConexionBD.getInstancia().getConexion().createStatement();
                ResultSet rs = st.executeQuery("""
                        SELECT id_unidad, placa, modelo, anio, kilometraje, id_nombre_tipo_bus, nombre_tipo_bus,
                               id_layout, nombre_layout, capacidad
                        FROM viajes.v_unidades""")) {
            while (rs.next()) {
                unidades.add(new Unidad(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getInt(4), rs.getInt(5),
                        new Catalogo(rs.getInt(6), rs.getString(7)), new Catalogo(rs.getInt(8), rs.getString(9)), rs.getInt(10)));
            }
        }
        return unidades;
    }

    /** Estatus de cada unidad activa en el taller (placa -> estatus), de la base `taller` por FDW. */
    public Map<String, String> estatusTaller() throws SQLException {
        Map<String, String> estatus = new HashMap<>();
        try (Statement st = ConexionBD.getInstancia().getConexion().createStatement();
                ResultSet rs = st.executeQuery("SELECT placa, estatus FROM viajes.v_flota_taller")) {
            while (rs.next()) {
                estatus.put(rs.getString(1), rs.getString(2));
            }
        }
        return estatus;
    }

    /** Baja o reactivación. Z0062: la unidad tiene salidas programadas o en ruta. */
    public void cambiarEstado(int idUnidad, boolean activa) throws SQLException {
        Sql.transaccion(con -> Sql.ejecutar(con, "CALL viajes.sp_cambiar_estado_unidad(?, ?)", idUnidad, activa));
    }

    /** Alta (idUnidad 0) o edición. Un SQLException con estado 23505 significa placa repetida. */
    public void guardar(Unidad unidad) throws SQLException {
        // sp_guardar_unidad busca o crea el tipo_bus (tipo, acomodo, capacidad) y luego inserta o actualiza la unidad.
        Sql.transaccion(con -> Sql.ejecutar(con, "CALL viajes.sp_guardar_unidad(?, ?, ?, ?, ?, ?, ?, ?)",
                unidad.idUnidad(), unidad.placa(), unidad.modelo(), unidad.anio(), unidad.km(),
                unidad.tipo().id(), unidad.acomodo().id(), unidad.capacidad()));
    }
}
