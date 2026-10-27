package michelle.zaca_bus.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import michelle.zaca_bus.model.Catalogo;
import michelle.zaca_bus.model.Empleado;
import michelle.zaca_bus.util.ConexionBD;
import michelle.zaca_bus.util.Sql;

public class PersonalDAO {

    public List<Catalogo> puestos() throws SQLException {
        return Sql.catalogo("SELECT id_puesto, nombre_puesto FROM administracion.puesto ORDER BY id_puesto");
    }

    public List<Empleado> listar() throws SQLException {
        List<Empleado> personal = new ArrayList<>();
        try (Statement st = ConexionBD.getInstancia().getConexion().createStatement();
                ResultSet rs = st.executeQuery("""
                        SELECT p.id_personal, pu.id_puesto, pu.nombre_puesto, p.nombre_personal, p.ap_paterno, p.ap_materno,
                               p.curp, p.telefono, p.licencia, p.vigencia,
                               EXISTS (SELECT 1 FROM administracion.personal_app pa WHERE pa.id_personal = p.id_personal)
                        FROM administracion.personal p
                        JOIN administracion.puesto pu ON pu.id_puesto = p.id_puesto
                        ORDER BY p.ap_paterno, p.ap_materno, p.nombre_personal""")) {
            while (rs.next()) {
                personal.add(new Empleado(rs.getInt(1), new Catalogo(rs.getInt(2), rs.getString(3)), rs.getString(4),
                        rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9),
                        rs.getObject(10, LocalDate.class), rs.getBoolean(11)));
            }
        }
        return personal;
    }

    /** Alta (idPersonal 0) o edición. Un SQLException 23505 es CURP repetida. */
    public void guardar(Empleado e) throws SQLException {
        Sql.transaccion(con -> {
            if (e.idPersonal() == 0) {
                Sql.ejecutar(con, "INSERT INTO administracion.personal (id_puesto, nombre_personal, ap_paterno, ap_materno, curp, telefono, licencia, vigencia) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                        e.puesto().id(), e.nombre(), e.apPaterno(), e.apMaterno(), e.curp(), e.telefono(), e.licencia(), e.vigencia());
            } else {
                Sql.ejecutar(con, "UPDATE administracion.personal SET id_puesto = ?, nombre_personal = ?, ap_paterno = ?, ap_materno = ?, curp = ?, telefono = ?, licencia = ?, vigencia = ? WHERE id_personal = ?",
                        e.puesto().id(), e.nombre(), e.apPaterno(), e.apMaterno(), e.curp(), e.telefono(), e.licencia(), e.vigencia(), e.idPersonal());
            }
        });
    }

    /** Borra al empleado y, en cascada, su cuenta. Un SQLException 23503 es un chofer con viajes asignados. */
    public void eliminar(int idPersonal) throws SQLException {
        Sql.transaccion(con -> Sql.ejecutar(con, "DELETE FROM administracion.personal WHERE id_personal = ?", idPersonal));
    }
}
