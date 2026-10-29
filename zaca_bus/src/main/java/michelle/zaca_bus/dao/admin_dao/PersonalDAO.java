package michelle.zaca_bus.dao.admin_dao;

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
        return Sql.catalogo("SELECT id_puesto, nombre_puesto FROM administracion.v_puestos");
    }

    public List<Empleado> listar() throws SQLException {
        List<Empleado> personal = new ArrayList<>();
        try (Statement st = ConexionBD.getInstancia().getConexion().createStatement();
                ResultSet rs = st.executeQuery("""
                        SELECT id_personal, id_puesto, nombre_puesto, nombre_personal, ap_paterno, ap_materno,
                               curp, telefono, licencia, vigencia, tiene_cuenta
                        FROM administracion.v_personal""")) {
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
            Sql.ejecutar(con, "CALL administracion.sp_guardar_personal(?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    e.idPersonal(), e.puesto().id(), e.nombre(), e.apPaterno(), e.apMaterno(), e.curp(), e.telefono(), e.licencia(), e.vigencia());
        });
    }

    /** Borra al empleado y, en cascada, su cuenta. Un SQLException 23503 es un chofer con viajes asignados. */
    public void eliminar(int idPersonal) throws SQLException {
        Sql.transaccion(con -> Sql.ejecutar(con, "CALL administracion.sp_eliminar_personal(?)", idPersonal));
    }
}
