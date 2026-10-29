package michelle.zaca_bus.dao.admin_dao;

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
        return Sql.catalogo("SELECT id_nombre_tipo_bus, nombre_tipo_bus FROM viajes.v_nombre_tipo_bus");
    }

    public List<Catalogo> acomodos() throws SQLException {
        return Sql.catalogo("SELECT id_layout, nombre_layout FROM viajes.v_layouts");
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

    /** Alta (idUnidad 0) o edición. Un SQLException con estado 23505 significa placa repetida. */
    public void guardar(Unidad unidad) throws SQLException {
        // sp_guardar_unidad busca o crea el tipo_bus (tipo, acomodo, capacidad) y luego inserta o actualiza la unidad.
        Sql.transaccion(con -> Sql.ejecutar(con, "CALL viajes.sp_guardar_unidad(?, ?, ?, ?, ?, ?, ?, ?)",
                unidad.idUnidad(), unidad.placa(), unidad.modelo(), unidad.anio(), unidad.km(),
                unidad.tipo().id(), unidad.acomodo().id(), unidad.capacidad()));
    }
}
