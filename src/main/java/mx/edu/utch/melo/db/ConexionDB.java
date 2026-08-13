package mx.edu.utch.melo.db;

import mx.edu.utch.melo.config.Configuracion;
import mx.edu.utch.melo.dao.PersistenciaException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;


public final class ConexionDB implements Transaccionador {

    private static final String DRIVER_MYSQL = "com.mysql.cj.jdbc.Driver";

    /**
     * La URL sí tiene un valor por defecto razonable (localhost) -- no es una credencial.
     * {@code sslMode=PREFERRED} (ver auditoría de Fase 5) intenta cifrar la conexión con MySQL y
     * solo cae a texto plano si el servidor no soporta TLS -- a diferencia del antiguo
     * {@code useSSL=false}, que la deshabilitaba siempre, incluso contra un MySQL que sí la soporta.
     * No rompe conexiones locales sin TLS configurado: simplemente no cifra en ese caso, igual que
     * antes.
     */
    private static final String URL_POR_DEFECTO = "jdbc:mysql://localhost:3306/melo_pos?sslMode=PREFERRED&serverTimezone=UTC";

    private static volatile ConexionDB instancia;

    private final String url;
    private final String usuario;
    private final String contrasena;

    /**
     * Deliberadamente SIN valor por defecto para usuario/contraseña (antes eran "root"/"" -- ver
     * auditoría de Fase 4): que este objeto se construya no implica que ya se pueda hablar con MySQL,
     * así que la validación de credenciales se hace en {@link #obtenerConexion()}, no aquí. Esto deja
     * construir un ConexionDB sin lanzar (p. ej. para inyectarlo en un AppContext de pruebas que nunca
     * llega a abrir una conexión real).
     */
    private ConexionDB() {
        this.url = Configuracion.obtenerConVariableEntorno("db.url", "DB_URL", URL_POR_DEFECTO);
        this.usuario = Configuracion.obtenerConVariableEntorno("db.usuario", "DB_USER", null);
        this.contrasena = Configuracion.obtenerConVariableEntorno("db.contrasena", "DB_PASSWORD", null);
        cargarDriver();
    }

    public static ConexionDB getInstancia() {
        ConexionDB resultado = instancia;
        if (resultado == null) {
            synchronized (ConexionDB.class) {
                resultado = instancia;
                if (resultado == null) {
                    instancia = resultado = new ConexionDB();
                }
            }
        }
        return resultado;
    }


    public Connection obtenerConexion() throws SQLException {
        validarCredenciales();
        return DriverManager.getConnection(url, usuario, contrasena);
    }

    /**
     * Corre varias operaciones DAO como una sola transacción: abre una conexión, desactiva
     * autocommit, corre {@code trabajo} y hace commit si todo sale bien -- o rollback si algo lanza
     * una excepción (ver VentaService/PagoService, "Registrar venta" en la auditoría de Fase 4).
     */
    @Override
    public <T> T ejecutarEnTransaccion(TrabajoTransaccional<T> trabajo) {
        try (Connection conexion = obtenerConexion()) {
            conexion.setAutoCommit(false);
            try {
                T resultado = trabajo.ejecutar(conexion);
                conexion.commit();
                return resultado;
            } catch (Exception e) {
                conexion.rollback();
                if (e instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw new PersistenciaException("No se pudo completar la operación (se revirtió por completo)", e);
            }
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo abrir la conexión para la transacción", e);
        }
    }

    private void validarCredenciales() {
        if (usuario == null || usuario.isBlank() || contrasena == null) {
            throw new IllegalStateException(
                    "Faltan las credenciales de MySQL. Define las variables de entorno DB_USER y DB_PASSWORD, "
                            + "o agrega db.usuario/db.contrasena en tu .env local (nunca en database.properties, "
                            + "que sí se sube al repositorio -- ver .gitignore).");
        }
    }

    private void cargarDriver() {
        try {
            Class.forName(DRIVER_MYSQL);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(
                    "No se encontró el driver JDBC de MySQL (" + DRIVER_MYSQL + ") en el classpath. "
                            + "Verifica la dependencia mysql-connector-j en el pom.xml.", e);
        }
    }
}
