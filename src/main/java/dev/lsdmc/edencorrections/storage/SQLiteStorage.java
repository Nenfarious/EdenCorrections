package dev.lsdmc.edencorrections.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.lsdmc.edencorrections.EdenCorrections;
import dev.lsdmc.edencorrections.managers.StorageManager;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class SQLiteStorage implements StorageManager {
    private final EdenCorrections plugin;
    private HikariDataSource dataSource;
    private final String dbFile;

    public SQLiteStorage(EdenCorrections plugin) {
        this.plugin = plugin;
        this.dbFile = plugin.getConfig().getString("storage.sqlite.file", "database.db");
    }

    @Override
    public void initialize() {
        // Create data folder if it doesn't exist
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        // Initialize connection pool
        try {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:sqlite:" + new File(plugin.getDataFolder(), dbFile).getAbsolutePath());
            config.setConnectionTestQuery("SELECT 1");
            config.setPoolName("EdenCorrections-SQLite");
            config.setMaximumPoolSize(10);

            // Set driver
            config.setDriverClassName("org.sqlite.JDBC");

            // Create data source
            dataSource = new HikariDataSource(config);

            // Create tables
            createTables();

            plugin.getLogger().info("SQLite connection established successfully");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize SQLite connection", e);
        }
    }

    private void createTables() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            // Create duty status table
            stmt.execute("CREATE TABLE IF NOT EXISTS duty_status (" +
                    "player_id VARCHAR(36) PRIMARY KEY, " +
                    "is_on_duty BOOLEAN NOT NULL" +
                    ")");

            // Create duty start times table
            stmt.execute("CREATE TABLE IF NOT EXISTS duty_start_times (" +
                    "player_id VARCHAR(36) PRIMARY KEY, " +
                    "start_time BIGINT NOT NULL" +
                    ")");

            // Create off-duty minutes table
            stmt.execute("CREATE TABLE IF NOT EXISTS off_duty_minutes (" +
                    "player_id VARCHAR(36) PRIMARY KEY, " +
                    "minutes INT NOT NULL" +
                    ")");

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
                     "INSERT OR REPLACE INTO duty_status (player_id, is_on_duty) VALUES (?, ?)")) {

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
                    "INSERT OR REPLACE INTO duty_status (player_id, is_on_duty) VALUES (?, ?)")) {

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
             ResultSet rs = stmt.executeQuery("SELECT player_id, is_on_duty FROM duty_status")) {

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
                     "INSERT OR REPLACE INTO duty_start_times (player_id, start_time) VALUES (?, ?)")) {

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
                    "INSERT OR REPLACE INTO duty_start_times (player_id, start_time) VALUES (?, ?)")) {

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
             ResultSet rs = stmt.executeQuery("SELECT player_id, start_time FROM duty_start_times")) {

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
                     "INSERT OR REPLACE INTO off_duty_minutes (player_id, minutes) VALUES (?, ?)")) {

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
                    "INSERT OR REPLACE INTO off_duty_minutes (player_id, minutes) VALUES (?, ?)")) {

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
             ResultSet rs = stmt.executeQuery("SELECT player_id, minutes FROM off_duty_minutes")) {

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
}