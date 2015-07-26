package me.johnnywoof.databases;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.md_5.bungee.api.ProxyServer;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MySQLDatabase implements Database {
    private static final String selectSQLStatement = "SELECT uuid,ip FROM always_online WHERE name = ?";
    private static final String insertSQLStatement = "INSERT INTO always_online (name,ip,uuid) VALUES(?,?,?) ON DUPLICATE KEY UPDATE ip = VALUES(ip), uuid = VALUES(uuid)";

    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;

    private HikariDataSource pool;

    public MySQLDatabase(String host, int port, String database, String username, String password) throws SQLException {
        this.host = host;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password;

        this.connect();
    }

    private void connect() throws SQLException {
        try {
            HikariConfig e = new HikariConfig();
            e.setDataSourceClassName("com.mysql.jdbc.jdbc2.optional.MysqlDataSource");
            e.setMaximumPoolSize(5);
            e.addDataSourceProperty("serverName", host);
            e.addDataSourceProperty("port", Integer.toString(port));
            e.addDataSourceProperty("databaseName", database);
            e.addDataSourceProperty("user", username);
            e.addDataSourceProperty("password", password);

            pool = new HikariDataSource(e);
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (!this.doesTableExist("always_online")) {
            try (Connection conn = pool.getConnection()) {
                conn.createStatement()
                        .executeUpdate("CREATE TABLE `always_online` ( `name` CHAR(16) NOT NULL , `ip` CHAR(15) NOT NULL " +
                                ", `uuid` CHAR(36) NOT NULL , PRIMARY KEY (`name`)) ENGINE = MyISAM; ");
            }
        }
    }

    @Override
    public PlayerData getData(String username) {
        PlayerData playerData = null;
        try (Connection conn = pool.getConnection()) {
            PreparedStatement preparedStatement = conn.prepareStatement(selectSQLStatement);
            preparedStatement.setString(1, username);
            ResultSet rs = preparedStatement.executeQuery();
            if (rs.next()) {
                playerData = new PlayerData(rs.getString(2), UUID.fromString(rs.getString(1)));

            }
            rs.close();
            preparedStatement.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return playerData;
    }


    @Override
    public void updatePlayer(String username, PlayerData data) {
        try (Connection conn = pool.getConnection()) {
            PreparedStatement preparedStatement = conn.prepareStatement(insertSQLStatement);

            preparedStatement.setString(1, username);
            preparedStatement.setString(2, data.ipAddress);
            preparedStatement.setString(3, data.uuid.toString());

            preparedStatement.execute();
            preparedStatement.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void shutdown() {
        pool.close();
    }

    /**
     * Determines if a table exists
     *
     * @param tableName The table name
     * @return If the table exists
     */
    private boolean doesTableExist(String tableName) throws SQLException {
        try (Connection conn = pool.getConnection()) {
            ResultSet rs = conn.getMetaData().getTables(null, null, tableName, null);
            if (rs.next()) {
                rs.close();
                return true;
            }
            rs.close();
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
