package mx.edu.utch.melo.db;

import mx.edu.utch.melo.config.Configuracion;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;


public final class ConexionDB {

    private static final String DRIVER_MYSQL = "com.mysql.cj.jdbc.Driver";

    private static final String URL_POR_DEFECTO = "jdbc:mysql://localhost:3306/melo_pos?useSSL=false&serverTimezone=UTC";
    private static final String USUARIO_POR_DEFECTO = "root";
    private static final String CONTRASENA_POR_DEFECTO = "";

    private static volatile ConexionDB instancia;

    private final String url;
    private final String usuario;
    private final String contrasena;

    private ConexionDB() {
        this.url = Configuracion.obtener("db.url", URL_POR_DEFECTO);
        this.usuario = Configuracion.obtener("db.usuario", USUARIO_POR_DEFECTO);
        this.contrasena = Configuracion.obtener("db.contrasena", CONTRASENA_POR_DEFECTO);
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
        return DriverManager.getConnection(url, usuario, contrasena);
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
