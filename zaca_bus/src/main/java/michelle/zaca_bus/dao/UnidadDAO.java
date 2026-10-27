package michelle.zaca_bus.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import michelle.zaca_bus.model.Catalogo;
import michelle.zaca_bus.model.Unidad;
import michelle.zaca_bus.util.ConexionBD;
import michelle.zaca_bus.util.Sql;

public class UnidadDAO {

    public List<Catalogo> tipos() throws SQLException {
        return Sql.catalogo("SELECT id_nombre_tipo_bus, nombre_tipo_bus FROM viajes.nombre_tipo_bus ORDER BY id_nombre_tipo_bus");
    }

    public List<Catalogo> acomodos() throws SQLException {
        return Sql.catalogo("SELECT id_layout, nombre_layout FROM viajes.layout ORDER BY id_layout");
    }

    public List<Unidad> listar() throws SQLException {
        List<Unidad> unidades = new ArrayList<>();
        try (Statement st = ConexionBD.getInstancia().getConexion().createStatement();
                ResultSet rs = st.executeQuery("""
                        SELECT u.id_unidad, u.placa, u.modelo, u.anio, u.kilometraje,
                               nt.id_nombre_tipo_bus, nt.nombre_tipo_bus, l.id_layout, l.nombre_layout, tb.capacidad
                        FROM viajes.unidad u
                        JOIN viajes.tipo_bus tb ON tb.id_tipo_bus = u.id_tipo_bus
                        JOIN viajes.nombre_tipo_bus nt ON nt.id_nombre_tipo_bus = tb.id_nombre_tipo_bus
                        JOIN viajes.layout l ON l.id_layout = tb.id_layout
                        ORDER BY u.placa""")) {
            while (rs.next()) {
                unidades.add(new Unidad(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getInt(4), rs.getInt(5),
                        new Catalogo(rs.getInt(6), rs.getString(7)), new Catalogo(rs.getInt(8), rs.getString(9)), rs.getInt(10)));
            }
        }
        return unidades;
    }

    /** Alta (idUnidad 0) o edición. Un SQLException con estado 23505 significa placa repetida. */
    public void guardar(Unidad unidad) throws SQLException {
        Sql.transaccion(con -> {
            int idTipoBus = buscarOCrearTipoBus(con, unidad);
            if (unidad.idUnidad() == 0) {
                Sql.ejecutar(con, "INSERT INTO viajes.unidad (id_tipo_bus, placa, modelo, anio, kilometraje) VALUES (?, ?, ?, ?, ?)",
                        idTipoBus, unidad.placa(), unidad.modelo(), unidad.anio(), unidad.km());
            } else {
                Sql.ejecutar(con, "UPDATE viajes.unidad SET id_tipo_bus = ?, placa = ?, modelo = ?, anio = ?, kilometraje = ? WHERE id_unidad = ?",
                        idTipoBus, unidad.placa(), unidad.modelo(), unidad.anio(), unidad.km(), unidad.idUnidad());
            }
        });
    }

    /** `tipo_bus` no tiene datos de inicio: la combinación (tipo, acomodo, capacidad) se crea la primera vez que se usa. */
    private int buscarOCrearTipoBus(Connection con, Unidad unidad) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT id_tipo_bus FROM viajes.tipo_bus WHERE id_nombre_tipo_bus = ? AND id_layout = ? AND capacidad = ?")) {
            ps.setInt(1, unidad.tipo().id());
            ps.setInt(2, unidad.acomodo().id());
            ps.setInt(3, unidad.capacidad());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO viajes.tipo_bus (id_nombre_tipo_bus, id_layout, capacidad) VALUES (?, ?, ?) RETURNING id_tipo_bus")) {
            ps.setInt(1, unidad.tipo().id());
            ps.setInt(2, unidad.acomodo().id());
            ps.setInt(3, unidad.capacidad());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }
}
