package michelle.zaca_bus.util;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Única conexión de la aplicación, y apunta a Pgpool-II (nunca directo a un nodo): Pgpool decide si la
 * consulta va al nodo principal o a la réplica. Los DAO usan la conexión pero NO la cierran.
 */
public final class ConexionBD {

    private static final ConexionBD INSTANCIA = new ConexionBD();

    private final Properties config = cargarEnv();

    private Connection conexion;

    private ConexionBD() {
    }

    public static ConexionBD getInstancia() {
        return INSTANCIA;
    }

    /** Si Pgpool reinició o la conexión se cayó, abre una nueva. */
    public synchronized Connection getConexion() throws SQLException {
        if (conexion == null || !conexion.isValid(2)) {
            conexion = DriverManager.getConnection(
                    config.getProperty("DB_URL"),
                    config.getProperty("DB_USER"),
                    config.getProperty("DB_PASSWORD"));
        }
        return conexion;
    }

    private static Properties cargarEnv() {
        Properties propiedades = new Properties();
        try (Reader lector = Files.newBufferedReader(Path.of(".env"))) {
            propiedades.load(lector);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer el archivo .env (copia .env.example y completa los datos)", e);
        }
        return propiedades;
    }
}
