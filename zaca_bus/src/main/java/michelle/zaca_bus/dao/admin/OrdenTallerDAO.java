package michelle.zaca_bus.dao.admin;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import michelle.zaca_bus.model.OrdenTaller;
import michelle.zaca_bus.util.ConexionBD;
import michelle.zaca_bus.util.Formato;
import michelle.zaca_bus.util.Sql;


public class OrdenTallerDAO {

    
    public List<OrdenTaller> listar(Integer idTaller) throws SQLException {
        List<OrdenTaller> ordenes = new ArrayList<>();
        try (PreparedStatement ps = ConexionBD.getInstancia().getConexion().prepareStatement("""
                SELECT id_orden, nombre_taller, placa, modelo, tipo_servicio, mecanico, fecha_ingreso, fecha_salida,
                       abierta, diagnostico, costo
                FROM viajes.v_ordenes_taller
                WHERE (?::int IS NULL OR id_taller = ?)""")) {
            ps.setObject(1, idTaller);
            ps.setObject(2, idTaller);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ordenes.add(new OrdenTaller(rs.getInt(1), String.format("OT-%04d", rs.getInt(1)), rs.getString(2),
                            rs.getString(3) + " · " + rs.getString(4), rs.getString(5), rs.getString(6),
                            fecha(rs.getObject(7, OffsetDateTime.class)), fecha(rs.getObject(8, OffsetDateTime.class)),
                            rs.getBoolean(9) ? "En Reparación" : "Completado", rs.getString(10),
                            Formato.dinero(rs.getBigDecimal(11))));
                }
            }
        }
        return ordenes;
    }

    /**
     * Mete un autobús a taller. `tipo`: 2 preventivo, 3 correctivo. Z0050 taller inactivo; Z0053 unidad inválida;
     * Z0054 ya tiene orden abierta; Z0055 mecánico que no es del taller; Z0056 tipo inválido; Z0058 costo inválido.
     */
    public void registrarIngreso(String placa, int idTaller, int idMecanico, int tipo, ZonedDateTime ingreso,
            String diagnostico, BigDecimal costo, String observaciones) throws SQLException {
        Sql.transaccion(con -> Sql.ejecutar(con, "CALL viajes.sp_registrar_ingreso(?, ?, ?, ?, ?, ?, ?, ?)",
                placa, idTaller, idMecanico, tipo, ingreso.toOffsetDateTime(), diagnostico, costo, observaciones));
    }

    /** Cierra una orden abierta del taller. `estadoFinal`: 1 operativo, 4 de baja. Z0057 orden inválida; Z0058 dato inválido. */
    public void cerrarOrden(int idOrden, int idTaller, ZonedDateTime salida, String diagnostico, BigDecimal costo,
            int estadoFinal) throws SQLException {
        Sql.transaccion(con -> Sql.ejecutar(con, "CALL viajes.sp_cerrar_orden(?, ?, ?, ?, ?, ?)",
                idOrden, idTaller, salida.toOffsetDateTime(), diagnostico, costo, estadoFinal));
    }

    private static String fecha(OffsetDateTime fecha) {
        return fecha == null ? "—" : Formato.fechaHora(fecha.atZoneSameInstant(Formato.ZONA));
    }
}
