package michelle.zaca_bus.dao.admin;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import michelle.zaca_bus.model.Catalogo;
import michelle.zaca_bus.model.Terminal;
import michelle.zaca_bus.util.ConexionBD;
import michelle.zaca_bus.util.Sql;

public class TerminalDAO {

    public List<Catalogo> listar() throws SQLException {
        return Sql.catalogo("SELECT id_terminal, nombre_terminal FROM administracion.v_terminales");
    }

    /** Todas las terminales, activas o no, con su ciudad. */
    public List<Terminal> listarTodas() throws SQLException {
        List<Terminal> terminales = new ArrayList<>();
        try (Statement st = ConexionBD.getInstancia().getConexion().createStatement();
                ResultSet rs = st.executeQuery("""
                        SELECT id_terminal, nombre_terminal, activo, id_ciudad, nombre_ciudad, clave, direccion, telefono
                        FROM administracion.v_terminales_detalle""")) {
            while (rs.next()) {
                terminales.add(new Terminal(rs.getInt(1), rs.getString(2), rs.getBoolean(3), rs.getInt(4), rs.getString(5),
                        rs.getString(6), rs.getString(7), rs.getString(8)));
            }
        }
        return terminales;
    }

    public void cambiarEstado(int idTerminal, boolean activa) throws SQLException {
        Sql.transaccion(con -> Sql.ejecutar(con, "CALL administracion.sp_cambiar_estado_terminal(?, ?)", idTerminal, activa));
    }

    /**
     * Terminales a las que se puede viajar desde `idOrigen` el día `fecha`: las que van después en el orden de alguna
     * salida de ese día que pasa por el origen, con tramo con precio. Sin sentidos inversos ni duplicados.
     */
    public List<Catalogo> destinosPorSalida(int idOrigen, LocalDate fecha) throws SQLException {
        List<Catalogo> destinos = new ArrayList<>();
        try (PreparedStatement ps = ConexionBD.getInstancia().getConexion().prepareStatement(
                "SELECT id_terminal, nombre_terminal FROM viajes.fn_destinos_por_salida(?, ?)")) {
            ps.setInt(1, idOrigen);
            ps.setObject(2, fecha);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    destinos.add(new Catalogo(rs.getInt(1), rs.getString(2)));
                }
            }
        }
        return destinos;
    }

    /**
     * Alta (idTerminal 0) o edición. Devuelve el id. Una terminal nueva queda DESCONECTADA (id_conexion 4) hasta que se
     * enlace su nodo. Z0032: la ciudad ya tiene su terminal; 23505: clave o nombre repetidos; Z0033: no existe.
     */
    public int guardar(int idTerminal, int idCiudad, String clave, String nombre, String direccion, String telefono) throws SQLException {
        int[] id = new int[1];
        Sql.transaccion(con -> {
            // p_id_terminal es INOUT: con executeQuery el procedure devuelve el id en una fila.
            try (PreparedStatement ps = con.prepareStatement("CALL administracion.sp_guardar_terminal(?, ?, ?, ?, ?, ?)")) {
                ps.setInt(1, idTerminal);
                ps.setInt(2, idCiudad);
                ps.setString(3, clave);
                ps.setString(4, nombre);
                ps.setString(5, direccion);
                ps.setString(6, telefono);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    id[0] = rs.getInt(1);
                }
            }
        });
        return id[0];
    }
}
