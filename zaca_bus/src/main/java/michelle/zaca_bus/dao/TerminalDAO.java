package michelle.zaca_bus.dao;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import michelle.zaca_bus.model.Catalogo;
import michelle.zaca_bus.util.ConexionBD;
import michelle.zaca_bus.util.Sql;

public class TerminalDAO {

    public List<Catalogo> listar() throws SQLException {
        return Sql.catalogo("SELECT id_terminal, nombre_terminal FROM administracion.terminal ORDER BY nombre_terminal");
    }

    /**
     * Terminales a las que se puede viajar desde `idOrigen`: las que van después en el orden de alguna ruta y cuyo
     * tramo tiene precio. Así no se ofrecen sentidos inversos ni combinaciones que ninguna ruta recorre.
     */
    public List<Catalogo> obtenerDestinosValidos(int idOrigen) throws SQLException {
        List<Catalogo> destinos = new ArrayList<>();
        try (PreparedStatement ps = ConexionBD.getInstancia().getConexion().prepareStatement("""
                SELECT DISTINCT t.id_terminal, t.nombre_terminal
                FROM viajes.ruta_paradas o
                JOIN viajes.ruta_paradas d ON d.id_ruta = o.id_ruta AND d.orden > o.orden
                JOIN viajes.precio_tramo p ON p.id_ruta = o.id_ruta AND p.orden_origen = o.orden AND p.orden_destino = d.orden
                JOIN administracion.terminal t ON t.id_terminal = d.id_terminal
                WHERE o.id_terminal = ?
                ORDER BY t.nombre_terminal""")) {
            ps.setInt(1, idOrigen);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    destinos.add(new Catalogo(rs.getInt(1), rs.getString(2)));
                }
            }
        }
        return destinos;
    }

    /** `id_conexion` es obligatorio: una terminal nueva queda DESCONECTADA hasta que se enlace su nodo. */
    public Catalogo crear(String nombre, String direccion) throws SQLException {
        try (PreparedStatement ps = ConexionBD.getInstancia().getConexion().prepareStatement("""
                INSERT INTO administracion.terminal (id_conexion, nombre_terminal, direccion)
                VALUES ((SELECT id_conexion FROM administracion.estado_conexion WHERE nombre_conexion = 'DESCONECTADO'), ?, ?)
                RETURNING id_terminal""")) {
            ps.setString(1, nombre);
            ps.setString(2, direccion);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return new Catalogo(rs.getInt(1), nombre);
            }
        }
    }
}
