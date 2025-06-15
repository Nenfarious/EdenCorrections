package dev.lsdmc.edencorrections.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.lsdmc.edencorrections.EdenCorrections;
import dev.lsdmc.edencorrections.managers.StorageManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class MySQLStorage implements StorageManager {
    private final EdenCorrections plugin;
    private HikariDataSource dataSource;
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private final String tablePrefix;

    public MySQLStorage(EdenCorrections plugin) {
        this.plugin = plugin;
        this.host = plugin.getConfig().getString("storage.mysql.host", "localhost");
        this.port = plugin.getConfig().getInt("storage.mysql.port", 3306);
        this.database = plugin.getConfig().getString("storage.mysql.database", "edencorrections");
        this.username = plugin.getConfig().getString("storage.mysql.username", "root");
        this.password = plugin.getConfig().getString("storage.mysql.password", "password");
        this.tablePrefix = plugin.getConfig().getString("storage.mysql.table-prefix", "ec_");
    }

    @Override
    public void initialize() {
        // Initialize connection pool
        try {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database);
            config.setUsername(username);
            config.setPassword(password);
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");

            // Connection pool settings with improved reliability
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(2);
            config.setIdleTimeout(30000);
            config.setConnectionTimeout(10000);
            config.setMaxLifetime(1800000);
            config.setPoolName("EdenCorrections-MySQL");
            config.setValidationTimeout(5000);
            config.setLeakDetectionThreshold(60000);

            // Set additional MySQL properties for better performance and reliability
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            config.addDataSourceProperty("useServerPrepStmts", "true");
            config.addDataSourceProperty("useLocalSessionState", "true");
            config.addDataSourceProperty("rewriteBatchedStatements", "true");
            config.addDataSourceProperty("cacheResultSetMetadata", "true");
            config.addDataSourceProperty("cacheServerConfiguration", "true");
            config.addDataSourceProperty("elideSetAutoCommits", "true");
            config.addDataSourceProperty("maintainTimeStats", "false");
            config.addDataSourceProperty("characterEncoding", "utf8mb4");
            config.addDataSourceProperty("useUnicode", "true");
            config.addDataSourceProperty("autoReconnect", "true");
            config.addDataSourceProperty("failOverReadOnly", "false");
            config.addDataSourceProperty("maxReconnects", "3");
            config.addDataSourceProperty("initialTimeout", "2");
            config.addDataSourceProperty("connectTimeout", "5000");
            config.addDataSourceProperty("socketTimeout", "30000");

            // Create data source
            dataSource = new HikariDataSource(config);

            // Test connection
            try (Connection conn = dataSource.getConnection()) {
                if (!conn.isValid(5)) {
                    throw new SQLException("Database connection test failed");
                }
            }

            // Create tables
            createTables();

            plugin.getLogger().info("MySQL connection established successfully");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize MySQL connection", e);
            // Attempt recovery
            try {
                Thread.sleep(1000); // Wait a bit before retry
                initialize(); // Retry initialization
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private Connection getConnection() throws SQLException {
        int maxRetries = 3;
        int retryDelay = 100; // milliseconds
        
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                if (dataSource == null || dataSource.isClosed()) {
                    initialize();
                }
                Connection conn = dataSource.getConnection();
                if (!conn.isValid(5)) {
                    conn.close();
                    throw new SQLException("Connection validation failed");
                }
                return conn;
            } catch (SQLException e) {
                if (isConnectionError(e) && attempt < maxRetries - 1) {
                    plugin.getLogger().warning("Database connection failed (attempt " + (attempt + 1) + "), retrying...");
                    initialize();
                    try {
                        Thread.sleep(retryDelay * (attempt + 1)); // Exponential backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new SQLException("Connection interrupted", ie);
                    }
                    continue;
                }
                throw e;
            }
        }
        throw new SQLException("Failed to establish database connection after " + maxRetries + " attempts");
    }

    private boolean isConnectionError(SQLException e) {
        String message = e.getMessage();
        return message != null && (
            message.contains("Communications link failure") ||
            message.contains("Connection refused") ||
            message.contains("No operations allowed after connection closed") ||
            message.contains("Connection is closed") ||
            message.contains("Connection has been closed") ||
            message.contains("Connection pool exhausted") ||
            message.contains("timeout") ||
            message.contains("busy")
        );
    }

    private void createTables() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            
            // Create duty_status table
            stmt.execute("CREATE TABLE IF NOT EXISTS " + tablePrefix + "duty_status (" +
                    "player_id VARCHAR(36) PRIMARY KEY, " +
                    "is_on_duty BOOLEAN NOT NULL DEFAULT FALSE, " +
                    "last_duty_change TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                    "total_duty_time BIGINT NOT NULL DEFAULT 0, " +
                    "last_duty_time BIGINT NOT NULL DEFAULT 0)");

            // Create activity_stats table
            stmt.execute("CREATE TABLE IF NOT EXISTS " + tablePrefix + "activity_stats (" +
                    "player_id VARCHAR(36) PRIMARY KEY, " +
                    "search_count INT NOT NULL DEFAULT 0, " +
                    "successful_search_count INT NOT NULL DEFAULT 0, " +
                    "kill_count INT NOT NULL DEFAULT 0, " +
                    "metal_detect_count INT NOT NULL DEFAULT 0, " +
                    "apprehension_count INT NOT NULL DEFAULT 0, " +
                    "FOREIGN KEY (player_id) REFERENCES " + tablePrefix + "duty_status(player_id) ON DELETE CASCADE)");

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create tables", e);
        }
    }

    @Override
    public void reload() {
        // Close existing connection
        shutdown();

        // Reinitialize
        initialize();
    }

    @Override
    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    @Override
    public void saveDutyStatus(UUID playerId, boolean isOnDuty) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO " + tablePrefix + "duty_status (player_id, is_on_duty) VALUES (?, ?) " +
                             "ON DUPLICATE KEY UPDATE is_on_duty = VALUES(is_on_duty)")) {

            stmt.setString(1, playerId.toString());
            stmt.setBoolean(2, isOnDuty);
            stmt.executeUpdate();

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save duty status", e);
        }
    }

    @Override
    public void saveDutyStatus(Map<UUID, Boolean> dutyStatus) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO " + tablePrefix + "duty_status (player_id, is_on_duty) VALUES (?, ?) " +
                            "ON DUPLICATE KEY UPDATE is_on_duty = VALUES(is_on_duty)")) {

                for (Map.Entry<UUID, Boolean> entry : dutyStatus.entrySet()) {
                    stmt.setString(1, entry.getKey().toString());
                    stmt.setBoolean(2, entry.getValue());
                    stmt.addBatch();
                }

                stmt.executeBatch();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save duty status batch", e);
        }
    }

    @Override
    public Map<UUID, Boolean> loadDutyStatus() {
        Map<UUID, Boolean> dutyStatus = new HashMap<>();

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT player_id, is_on_duty FROM " + tablePrefix + "duty_status")) {

            while (rs.next()) {
                try {
                    UUID playerId = UUID.fromString(rs.getString("player_id"));
                    boolean isOnDuty = rs.getBoolean("is_on_duty");
                    dutyStatus.put(playerId, isOnDuty);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid UUID in duty_status table: " + rs.getString("player_id"));
                }
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load duty status", e);
        }

        return dutyStatus;
    }

    @Override
    public void saveDutyStartTime(UUID playerId, long startTime) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO " + tablePrefix + "duty_start_times (player_id, start_time) VALUES (?, ?) " +
                             "ON DUPLICATE KEY UPDATE start_time = VALUES(start_time)")) {

            stmt.setString(1, playerId.toString());
            stmt.setLong(2, startTime);
            stmt.executeUpdate();

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save duty start time", e);
        }
    }

    @Override
    public void saveDutyStartTimes(Map<UUID, Long> dutyStartTimes) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO " + tablePrefix + "duty_start_times (player_id, start_time) VALUES (?, ?) " +
                            "ON DUPLICATE KEY UPDATE start_time = VALUES(start_time)")) {

                for (Map.Entry<UUID, Long> entry : dutyStartTimes.entrySet()) {
                    stmt.setString(1, entry.getKey().toString());
                    stmt.setLong(2, entry.getValue());
                    stmt.addBatch();
                }

                stmt.executeBatch();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save duty start times batch", e);
        }
    }

    @Override
    public Map<UUID, Long> loadDutyStartTimes() {
        Map<UUID, Long> dutyStartTimes = new HashMap<>();

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT player_id, start_time FROM " + tablePrefix + "duty_start_times")) {

            while (rs.next()) {
                try {
                    UUID playerId = UUID.fromString(rs.getString("player_id"));
                    long startTime = rs.getLong("start_time");
                    dutyStartTimes.put(playerId, startTime);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid UUID in duty_start_times table: " + rs.getString("player_id"));
                }
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load duty start times", e);
        }

        return dutyStartTimes;
    }

    @Override
    public void saveOffDutyMinutes(UUID playerId, int minutes) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO " + tablePrefix + "off_duty_minutes (player_id, minutes) VALUES (?, ?) " +
                             "ON DUPLICATE KEY UPDATE minutes = VALUES(minutes)")) {

            stmt.setString(1, playerId.toString());
            stmt.setInt(2, minutes);
            stmt.executeUpdate();

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save off duty minutes", e);
        }
    }

    @Override
    public void saveOffDutyMinutes(Map<UUID, Integer> offDutyMinutes) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO " + tablePrefix + "off_duty_minutes (player_id, minutes) VALUES (?, ?) " +
                            "ON DUPLICATE KEY UPDATE minutes = VALUES(minutes)")) {

                for (Map.Entry<UUID, Integer> entry : offDutyMinutes.entrySet()) {
                    stmt.setString(1, entry.getKey().toString());
                    stmt.setInt(2, entry.getValue());
                    stmt.addBatch();
                }

                stmt.executeBatch();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save off duty minutes batch", e);
        }
    }

    @Override
    public Map<UUID, Integer> loadOffDutyMinutes() {
        Map<UUID, Integer> offDutyMinutes = new HashMap<>();

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT player_id, minutes FROM " + tablePrefix + "off_duty_minutes")) {

            while (rs.next()) {
                try {
                    UUID playerId = UUID.fromString(rs.getString("player_id"));
                    int minutes = rs.getInt("minutes");
                    offDutyMinutes.put(playerId, minutes);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid UUID in off_duty_minutes table: " + rs.getString("player_id"));
                }
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load off duty minutes", e);
        }

        return offDutyMinutes;
    }

    @Override
    public int getSearchCount(UUID playerId) {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT search_count FROM " + tablePrefix + "activity_stats WHERE player_id = ?")) {
            
            stmt.setString(1, playerId.toString());
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                return rs.getInt("search_count");
            }
            
            // Create new record if none exists
            try (PreparedStatement insertStmt = conn.prepareStatement(
                    "INSERT INTO " + tablePrefix + "activity_stats (player_id, search_count) VALUES (?, 0)")) {
                insertStmt.setString(1, playerId.toString());
                insertStmt.executeUpdate();
            }
            
            return 0;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to get search count", e);
            return 0;
        }
    }

    @Override
    public void incrementSearchCount(UUID playerId) {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO " + tablePrefix + "activity_stats (player_id, search_count) VALUES (?, 1) " +
                     "ON DUPLICATE KEY UPDATE search_count = search_count + 1")) {
            
            stmt.setString(1, playerId.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to increment search count", e);
        }
    }

    @Override
    public int getSuccessfulSearchCount(UUID playerId) {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT successful_search_count FROM " + tablePrefix + "activity_stats WHERE player_id = ?")) {
            
            stmt.setString(1, playerId.toString());
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                return rs.getInt("successful_search_count");
            }
            
            // Create new record if none exists
            try (PreparedStatement insertStmt = conn.prepareStatement(
                    "INSERT INTO " + tablePrefix + "activity_stats (player_id, successful_search_count) VALUES (?, 0)")) {
                insertStmt.setString(1, playerId.toString());
                insertStmt.executeUpdate();
            }
            
            return 0;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to get successful search count", e);
            return 0;
        }
    }

    @Override
    public void incrementSuccessfulSearchCount(UUID playerId) {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO " + tablePrefix + "activity_stats (player_id, successful_search_count) VALUES (?, 1) " +
                     "ON DUPLICATE KEY UPDATE successful_search_count = successful_search_count + 1")) {
            
            stmt.setString(1, playerId.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to increment successful search count", e);
        }
    }

    @Override
    public int getKillCount(UUID playerId) {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT kill_count FROM " + tablePrefix + "activity_stats WHERE player_id = ?")) {
            
            stmt.setString(1, playerId.toString());
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                return rs.getInt("kill_count");
            }
            
            // Create new record if none exists
            try (PreparedStatement insertStmt = conn.prepareStatement(
                    "INSERT INTO " + tablePrefix + "activity_stats (player_id, kill_count) VALUES (?, 0)")) {
                insertStmt.setString(1, playerId.toString());
                insertStmt.executeUpdate();
            }
            
            return 0;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to get kill count", e);
            return 0;
        }
    }

    @Override
    public void incrementKillCount(UUID playerId) {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO " + tablePrefix + "activity_stats (player_id, kill_count) VALUES (?, 1) " +
                     "ON DUPLICATE KEY UPDATE kill_count = kill_count + 1")) {
            
            stmt.setString(1, playerId.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to increment kill count", e);
        }
    }

    @Override
    public int getMetalDetectCount(UUID playerId) {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT metal_detect_count FROM " + tablePrefix + "activity_stats WHERE player_id = ?")) {
            
            stmt.setString(1, playerId.toString());
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                return rs.getInt("metal_detect_count");
            }
            
            // Create new record if none exists
            try (PreparedStatement insertStmt = conn.prepareStatement(
                    "INSERT INTO " + tablePrefix + "activity_stats (player_id, metal_detect_count) VALUES (?, 0)")) {
                insertStmt.setString(1, playerId.toString());
                insertStmt.executeUpdate();
            }
            
            return 0;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to get metal detect count", e);
            return 0;
        }
    }

    @Override
    public void incrementMetalDetectCount(UUID playerId) {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO " + tablePrefix + "activity_stats (player_id, metal_detect_count) VALUES (?, 1) " +
                     "ON DUPLICATE KEY UPDATE metal_detect_count = metal_detect_count + 1")) {
            
            stmt.setString(1, playerId.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to increment metal detect count", e);
        }
    }

    @Override
    public int getApprehensionCount(UUID playerId) {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT apprehension_count FROM " + tablePrefix + "activity_stats WHERE player_id = ?")) {
            
            stmt.setString(1, playerId.toString());
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                return rs.getInt("apprehension_count");
            }
            
            // Create new record if none exists
            try (PreparedStatement insertStmt = conn.prepareStatement(
                    "INSERT INTO " + tablePrefix + "activity_stats (player_id, apprehension_count) VALUES (?, 0)")) {
                insertStmt.setString(1, playerId.toString());
                insertStmt.executeUpdate();
            }
            
            return 0;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to get apprehension count", e);
            return 0;
        }
    }

    @Override
    public void incrementApprehensionCount(UUID playerId) {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO " + tablePrefix + "activity_stats (player_id, apprehension_count) VALUES (?, 1) " +
                     "ON DUPLICATE KEY UPDATE apprehension_count = apprehension_count + 1")) {
            
            stmt.setString(1, playerId.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to increment apprehension count", e);
        }
    }

    @Override
    public void resetActivityCounts(UUID playerId) {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "UPDATE " + tablePrefix + "activity_stats SET " +
                     "search_count = 0, " +
                     "successful_search_count = 0, " +
                     "kill_count = 0, " +
                     "metal_detect_count = 0, " +
                     "apprehension_count = 0 " +
                     "WHERE player_id = ?")) {
            
            stmt.setString(1, playerId.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to reset activity counts", e);
        }
    }
}