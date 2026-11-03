package michelle.zaca_bus.dao.taquillero;

import java.math.BigDecimal;
import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import michelle.zaca_bus.model.Catalogo;
import michelle.zaca_bus.model.Horario;
import michelle.zaca_bus.model.Pase;
import michelle.zaca_bus.model.Tarifa;
import michelle.zaca_bus.model.Ticket;
import michelle.zaca_bus.model.Titular;
import michelle.zaca_bus.model.VentaRegistrada;
import michelle.zaca_bus.util.ConexionBD;
import michelle.zaca_bus.util.Formato;
import michelle.zaca_bus.util.Sql;

public class VentaDAO {

    /**
     * Registra la venta de los asientos de `tarifas` (asiento y su tarifa) entre `origen` y `destino` en el viaje de
     * `horario`, con ventas.sp_registrar_venta: valida al vendedor, el folio de su taquilla, el dinero recibido y que
     * ningún asiento esté ya vendido en ese tramo. `nombres` trae quien viaja en cada asiento. Cada asiento recibe su
     * propio folio consecutivo; devuelve un {@link Pase} por asiento (folio impreso, el mismo que lee el abordaje).
     * Códigos: Z0040 vendedor sin permiso o sin taquilla; Z0041 folios agotados; Z0042 viaje o tramo no
     * vendible; Z0043 dinero insuficiente; Z0044 asientos, tarifas o nombres inválidos; 23P01 asiento ya vendido.
     */
    public List<Pase> registrar(int idApp, Horario horario, Catalogo origen, Catalogo destino, Titular titular,
            Map<Integer, Tarifa> tarifas, Map<Integer, String> nombres, BigDecimal recibido) throws SQLException {
        List<Pase> pases = new ArrayList<>();
        Sql.transaccion(con -> {
            Array asientos = con.createArrayOf("integer", tarifas.keySet().toArray());
            Array idsTarifa = con.createArrayOf("integer", tarifas.values().stream().map(Tarifa::id).toArray());
            // Mismo orden que los asientos: tarifas es un TreeMap y sus llaves salen por número de asiento
            Array pasajeros = con.createArrayOf("text", tarifas.keySet().stream().map(nombres::get).toArray());
            int idVenta;
            // p_id_venta y p_folio son INOUT: con executeQuery el procedure devuelve sus valores en una fila.
            try (PreparedStatement ps = con.prepareStatement("CALL ventas.sp_registrar_venta(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setInt(1, 0);
                ps.setInt(2, 0);
                ps.setInt(3, idApp);
                ps.setInt(4, turnoActual());
                ps.setInt(5, horario.idViaje());
                ps.setInt(6, origen.id());
                ps.setInt(7, destino.id());
                ps.setString(8, titular.nombre());
                ps.setString(9, titular.apPaterno());
                ps.setString(10, titular.apMaterno());
                ps.setString(11, titular.curp());
                ps.setArray(12, asientos);
                ps.setArray(13, idsTarifa);
                ps.setArray(14, pasajeros);
                ps.setBigDecimal(15, recibido);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    idVenta = rs.getInt(1);
                }
            }
            // Mismo formato que ventas.v_boletos_abordaje: así el QR del boleto coincide con lo que busca el abordaje.
            try (PreparedStatement ps = con.prepareStatement("""
                    SELECT upper(q.serie_folio || '-' || lpad(b.folio::text, 6, '0')), b.num_asiento, b.nombre_pasajero,
                           b.id_boleto, b.precio_unitario, b.id_estado_boleto
                    FROM ventas.boleto b
                    INNER JOIN administracion.taquilla q ON q.id_taquilla = b.id_taquilla
                    WHERE b.id_venta = ?
                    ORDER BY b.num_asiento""")) {
                ps.setInt(1, idVenta);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        pases.add(new Pase(rs.getInt(4), rs.getString(1), rs.getInt(2), rs.getString(3), rs.getBigDecimal(5), rs.getInt(6)));
                    }
                }
            }
        });
        return pases;
    }

    /**
     * Turno de la venta según la hora (catálogo administracion.turno: 1 matutino, 2 vespertino, 3 nocturno).
     * pendiente backend: cuando exista la pantalla de abrir turno, se usará el turno que abrió el taquillero.
     */
    private static int turnoActual() {
        int hora = LocalTime.now(Formato.ZONA).getHour();
        return hora < 14 ? 1 : hora < 20 ? 2 : 3;
    }

    /**
     * Ventas de todas las taquillas, las más recientes primero. Cada venta junta sus boletos (mismo viaje y tramo);
     * la salida es la hora a la que el autobús pasa por el origen. Se lee de las tablas porque la base no tiene vista de ventas.
     */
    public List<VentaRegistrada> listar() throws SQLException {
        List<VentaRegistrada> ventas = new ArrayList<>();
        try (Statement st = ConexionBD.getInstancia().getConexion().createStatement();
                ResultSet rs = st.executeQuery("""
                        SELECT v.id_venta,
                               upper(q.serie_folio || '-' || lpad(v.folio::text, 6, '0')),
                               tor.nombre_terminal, tde.nombre_terminal,
                               vp.fecha_salida + make_interval(mins => rp.min_desde_origen),
                               u.placa,
                               p.nombre_pasajero || ' ' || p.ap_paterno || ' ' || p.ap_materno,
                               b.asientos, b.folios, b.pasajeros, b.ids, b.precios, b.estados, v.total_pagado, v.recibido,
                               pe.nombre_personal || ' ' || pe.ap_paterno || ' ' || pe.ap_materno,
                               CASE WHEN v.cancelada_en IS NOT NULL THEN 'CANCELADA'
                                    WHEN b.abordado THEN 'ABORDADO' ELSE 'VIGENTE' END
                        FROM ventas.venta v
                        INNER JOIN administracion.taquilla q ON q.id_taquilla = v.id_taquilla
                        INNER JOIN ventas.pasajero p ON p.id_pasajero = v.id_titular
                        INNER JOIN administracion.personal_app pa ON pa.id_app = v.id_vendedor
                        INNER JOIN administracion.personal pe ON pe.id_personal = pa.id_personal
                        CROSS JOIN LATERAL (
                            SELECT b.id_viaje, b.id_terminal_origen, b.id_terminal_destino,
                                   array_agg(b.num_asiento ORDER BY b.num_asiento) AS asientos,
                                   array_agg(upper(q.serie_folio || '-' || lpad(b.folio::text, 6, '0')) ORDER BY b.num_asiento) AS folios,
                                   array_agg(b.nombre_pasajero ORDER BY b.num_asiento) AS pasajeros,
                                   array_agg(b.id_boleto ORDER BY b.num_asiento) AS ids,
                                   array_agg(b.precio_unitario ORDER BY b.num_asiento) AS precios,
                                   array_agg(b.id_estado_boleto ORDER BY b.num_asiento) AS estados,
                                   bool_and(b.id_estado_boleto = 3) AS abordado
                            FROM ventas.boleto b WHERE b.id_venta = v.id_venta
                            GROUP BY b.id_viaje, b.id_terminal_origen, b.id_terminal_destino) b
                        INNER JOIN viajes.viaje_programado vp ON vp.id_viaje = b.id_viaje
                        INNER JOIN viajes.unidad u ON u.id_unidad = vp.id_unidad
                        INNER JOIN viajes.ruta_paradas rp ON rp.id_ruta = vp.id_ruta AND rp.id_terminal = b.id_terminal_origen
                        INNER JOIN administracion.terminal tor ON tor.id_terminal = b.id_terminal_origen
                        INNER JOIN administracion.terminal tde ON tde.id_terminal = b.id_terminal_destino
                        ORDER BY v.fecha DESC""")) {
            while (rs.next()) {
                Ticket ticket = new Ticket(rs.getString(2), rs.getString(3) + " → " + rs.getString(4),
                        rs.getObject(5, OffsetDateTime.class).atZoneSameInstant(Formato.ZONA), rs.getString(6), rs.getString(7),
                        pases(rs), rs.getBigDecimal(14), rs.getBigDecimal(15), rs.getString(16));
                ventas.add(new VentaRegistrada(rs.getInt(1), rs.getString(17), ticket));
            }
        }
        return ventas;
    }

    private static List<Pase> pases(ResultSet rs) throws SQLException {
        Integer[] asientos = (Integer[]) rs.getArray(8).getArray();
        String[] folios = (String[]) rs.getArray(9).getArray();
        String[] pasajeros = (String[]) rs.getArray(10).getArray();
        Integer[] ids = (Integer[]) rs.getArray(11).getArray();
        BigDecimal[] precios = (BigDecimal[]) rs.getArray(12).getArray();
        Integer[] estados = (Integer[]) rs.getArray(13).getArray();
        List<Pase> pases = new ArrayList<>();
        for (int i = 0; i < asientos.length; i++) {
            pases.add(new Pase(ids[i], folios[i], asientos[i], pasajeros[i], precios[i], estados[i]));
        }
        return pases;
    }

    /**
     * Cancela los boletos (asientos) indicados con ventas.sp_cancelar_boleto, todos en una transacción; los demás de la
     * venta siguen vigentes. Z0045: ya cancelado o con el pasajero abordado.
     */
    public void cancelarBoletos(List<Integer> idsBoleto, int idApp) throws SQLException {
        Sql.transaccion(con -> {
            for (int id : idsBoleto) {
                Sql.ejecutar(con, "CALL ventas.sp_cancelar_boleto(?, ?)", id, idApp);
            }
        });
    }

    /** Cancela la venta con ventas.sp_cancelar_venta: sus asientos quedan libres. Z0045: ya cancelada o con pasajeros abordados. */
    public void cancelar(int idVenta, int idApp) throws SQLException {
        Sql.transaccion(con -> Sql.ejecutar(con, "CALL ventas.sp_cancelar_venta(?, ?)", idVenta, idApp));
    }
}
