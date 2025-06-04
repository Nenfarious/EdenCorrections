package dev.lsdmc.edencorrections.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.lsdmc.edencorrections.EdenCorrections;
import dev.lsdmc.edencorrections.managers.StorageManager;
import dev.lsdmc.edencorrections.managers.JailManager;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public class SQLiteStorage implements StorageManager {
    private final EdenCorrections plugin;
    private volatile HikariDataSource dataSource;
    private final String dbFile;
    private final Object connectionLock = new Object();
    private volatile boolean isInitializing = false;

    public SQLiteStorage(EdenCorrections plugin) {
        this.plugin = plugin;
        this.dbFile = plugin.getConfig().getString("storage.sqlite.file", "database.db");
    }

    @Override
    public void initialize() {
        synchronized (connectionLock) {
            if (isInitializing) {
                return; // Prevent multiple simultaneous initializations
            }
            isInitializing = true;
            
            try {
                // Create data folder if it doesn't exist
                if (!plugin.getDataFolder().exists()) {
                    plugin.getDataFolder().mkdirs();
                }

                // Close existing connection if any
                if (dataSource != null && !dataSource.isClosed()) {
                    dataSource.close();
                }

                // Initialize connection pool
                HikariConfig config = new HikariConfig();
                config.setJdbcUrl("jdbc:sqlite:" + new File(plugin.getDataFolder(), dbFile).getAbsolutePath());
                config.setConnectionTestQuery("SELECT 1");
                config.setPoolName("EdenCorrections-SQLite");
                config.setMaximumPoolSize(10);
                config.setMaxLifetime(600000); // 10 minutes
                config.setIdleTimeout(300000); // 5 minutes
                config.setConnectionTimeout(10000); // 10 seconds

                // Set driver
                config.setDriverClassName("org.sqlite.JDBC");

                // Additional SQLite-specific settings
                config.addDataSourceProperty("journal_mode", "WAL");
                config.addDataSourceProperty("synchronous", "NORMAL");
                config.addDataSourceProperty("cache_size", "10000");
                config.addDataSourceProperty("foreign_keys", "true");

                // Create data source
                dataSource = new HikariDataSource(config);

                // Create tables
                createTables();

                plugin.getLogger().info("SQLite connection established successfully");
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to initialize SQLite connection", e);
            } finally {
                isInitializing = false;
            }
        }
    }

    /**
     * Get a database connection with automatic recovery for moved database errors
     */
    private Connection getConnection() throws SQLException {
        try {
            if (dataSource == null || dataSource.isClosed()) {
                reinitializeDatabase();
            }
            return dataSource.getConnection();
        } catch (SQLException e) {
            // Check if this is the specific "database moved" error
            if (isDatabaseMovedError(e)) {
                plugin.getLogger().warning("Database file has been moved, reinitializing connection...");
                reinitializeDatabase();
                return dataSource.getConnection(); // Try again with new connection
            }
            throw e; // Re-throw other SQL exceptions
        }
    }

    /**
     * Check if the SQLException indicates the database file was moved
     */
    private boolean isDatabaseMovedError(SQLException e) {
        return e.getMessage() != null && 
               (e.getMessage().contains("SQLITE_READONLY_DBMOVED") ||
                e.getMessage().contains("database file has been moved") ||
                e.getMessage().contains("attempt to write a readonly database"));
    }

    /**
     * Reinitialize the database connection pool
     */
    private void reinitializeDatabase() {
        synchronized (connectionLock) {
            if (isInitializing) {
                return; // Another thread is already reinitializing
            }
            
            plugin.getLogger().info("Reinitializing database connection due to connection error...");
            initialize(); // This will close old connections and create new ones
        }
    }

    /**
     * Execute a database operation with automatic retry on connection errors
     */
    private <T> T executeWithRetry(DatabaseOperation<T> operation) {
        int maxRetries = 2;
        SQLException lastException = null;
        
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                return operation.execute();
            } catch (SQLException e) {
                lastException = e;
                
                if (isDatabaseMovedError(e) && attempt < maxRetries - 1) {
                    plugin.getLogger().warning("Database operation failed (attempt " + (attempt + 1) + "), retrying with fresh connection...");
                    reinitializeDatabase();
                    try {
                        Thread.sleep(100); // Brief pause before retry
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    break; // Don't retry for other types of errors or if we've exhausted retries
                }
            }
        }
        
        // If we get here, all retries failed
        plugin.getLogger().log(Level.SEVERE, "Database operation failed after " + maxRetries + " attempts", lastException);
        return null; // Return null for failed operations (methods should handle this)
    }

    /**
     * Functional interface for database operations
     */
    @FunctionalInterface
    private interface DatabaseOperation<T> {
        T execute() throws SQLException;
    }

    private void createTables() {
        try (Connection conn = getConnection();
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

            // Create activity stats table
            stmt.execute("CREATE TABLE IF NOT EXISTS activity_stats (" +
                    "player_id VARCHAR(36) PRIMARY KEY, " +
                    "search_count INT NOT NULL DEFAULT 0, " +
                    "successful_search_count INT NOT NULL DEFAULT 0, " +
                    "kill_count INT NOT NULL DEFAULT 0, " +
                    "metal_detect_count INT NOT NULL DEFAULT 0, " +
                    "apprehension_count INT NOT NULL DEFAULT 0" +
                    ")");

            // Create guard statistics table
            stmt.execute("CREATE TABLE IF NOT EXISTS guard_statistics (" +
                    "player_id VARCHAR(36) PRIMARY KEY, " +
                    "total_duty_time BIGINT NOT NULL DEFAULT 0, " +
                    "total_searches INT NOT NULL DEFAULT 0, " +
                    "successful_searches INT NOT NULL DEFAULT 0, " +
                    "metal_detections INT NOT NULL DEFAULT 0, " +
                    "apprehensions INT NOT NULL DEFAULT 0, " +
                    "deaths INT NOT NULL DEFAULT 0, " +
                    "tokens_earned INT NOT NULL DEFAULT 0, " +
                    "last_duty_start BIGINT NOT NULL DEFAULT 0" +
                    ")");

            // Create guard session stats table
            stmt.execute("CREATE TABLE IF NOT EXISTS guard_session_stats (" +
                    "player_id VARCHAR(36) PRIMARY KEY, " +
                    "total_duty_time BIGINT NOT NULL DEFAULT 0, " +
                    "total_searches INT NOT NULL DEFAULT 0, " +
                    "successful_searches INT NOT NULL DEFAULT 0, " +
                    "metal_detections INT NOT NULL DEFAULT 0, " +
                    "apprehensions INT NOT NULL DEFAULT 0, " +
                    "deaths INT NOT NULL DEFAULT 0, " +
                    "tokens_earned INT NOT NULL DEFAULT 0, " +
                    "last_duty_start BIGINT NOT NULL DEFAULT 0" +
                    ")");

            // Create guard progression table
            stmt.execute("CREATE TABLE IF NOT EXISTS guard_progression (" +
                    "player_id VARCHAR(36) PRIMARY KEY, " +
                    "points INT NOT NULL DEFAULT 0, " +
                    "total_time_served BIGINT NOT NULL DEFAULT 0, " +
                    "successful_arrests INT NOT NULL DEFAULT 0, " +
                    "contraband INT NOT NULL DEFAULT 0" +
                    ")");

            // Create guard tokens table
            stmt.execute("CREATE TABLE IF NOT EXISTS guard_tokens (" +
                    "player_id VARCHAR(36) PRIMARY KEY, " +
                    "tokens INT NOT NULL DEFAULT 0, " +
                    "last_reward_time BIGINT NOT NULL DEFAULT 0" +
                    ")");

            // Create jail data table
            stmt.execute("CREATE TABLE IF NOT EXISTS jail_data (" +
                    "player_id VARCHAR(36) PRIMARY KEY, " +
                    "start_time BIGINT NOT NULL, " +
                    "duration_seconds INT NOT NULL, " +
                    "reason TEXT NOT NULL, " +
                    "jail_location TEXT, " +
                    "arresting_guard VARCHAR(36)" +
                    ")");

            // Create offline jail queue table
            stmt.execute("CREATE TABLE IF NOT EXISTS offline_jail_queue (" +
                    "player_id VARCHAR(36) PRIMARY KEY" +
                    ")");

            // Create contraband registry table
            stmt.execute("CREATE TABLE IF NOT EXISTS contraband_registry (" +
                    "type VARCHAR(32) NOT NULL, " +
                    "material VARCHAR(64) NOT NULL, " +
                    "display_name TEXT, " +
                    "lore TEXT, " +
                    "added_by VARCHAR(64), " +
                    "added_time BIGINT, " +
                    "lore_hash INT, " +
                    "PRIMARY KEY (type, material, display_name, lore_hash)" +
                    ")");

            // Create wanted levels table
            stmt.execute("CREATE TABLE IF NOT EXISTS wanted_levels (" +
                    "player_id VARCHAR(36) PRIMARY KEY, " +
                    "level INT NOT NULL, " +
                    "expiry BIGINT NOT NULL, " +
                    "marked BOOLEAN NOT NULL DEFAULT 0" +
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
        synchronized (connectionLock) {
            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.close();
            }
        }
    }

    @Override
    public void saveDutyStatus(UUID playerId, boolean isOnDuty) {
        executeWithRetry(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "INSERT OR REPLACE INTO duty_status (player_id, is_on_duty) VALUES (?, ?)")) {

                stmt.setString(1, playerId.toString());
                stmt.setBoolean(2, isOnDuty);
                stmt.executeUpdate();
                return null;
            }
        });
    }

    @Override
    public void saveDutyStatus(Map<UUID, Boolean> dutyStatus) {
        executeWithRetry(() -> {
            try (Connection conn = getConnection()) {
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
                    return null;
                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(true);
                }
            }
        });
    }

    @Override
    public Map<UUID, Boolean> loadDutyStatus() {
        return executeWithRetry(() -> {
            Map<UUID, Boolean> dutyStatus = new HashMap<>();

            try (Connection conn = getConnection();
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
            }

            return dutyStatus;
        });
    }

    @Override
    public void saveDutyStartTime(UUID playerId, long startTime) {
        executeWithRetry(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "INSERT OR REPLACE INTO duty_start_times (player_id, start_time) VALUES (?, ?)")) {

                stmt.setString(1, playerId.toString());
                stmt.setLong(2, startTime);
                stmt.executeUpdate();
                return null;
            }
        });
    }

    @Override
    public void saveDutyStartTimes(Map<UUID, Long> dutyStartTimes) {
        executeWithRetry(() -> {
            try (Connection conn = getConnection()) {
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
                    return null;
                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(true);
                }
            }
        });
    }

    @Override
    public Map<UUID, Long> loadDutyStartTimes() {
        return executeWithRetry(() -> {
            Map<UUID, Long> dutyStartTimes = new HashMap<>();

            try (Connection conn = getConnection();
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
            }

            return dutyStartTimes;
        });
    }

    @Override
    public void saveOffDutyMinutes(UUID playerId, int minutes) {
        executeWithRetry(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "INSERT OR REPLACE INTO off_duty_minutes (player_id, minutes) VALUES (?, ?)")) {

                stmt.setString(1, playerId.toString());
                stmt.setInt(2, minutes);
                stmt.executeUpdate();
                return null;
            }
        });
    }

    @Override
    public void saveOffDutyMinutes(Map<UUID, Integer> offDutyMinutes) {
        executeWithRetry(() -> {
            try (Connection conn = getConnection()) {
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
                    return null;
                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(true);
                }
            }
        });
    }

    @Override
    public Map<UUID, Integer> loadOffDutyMinutes() {
        return executeWithRetry(() -> {
            Map<UUID, Integer> offDutyMinutes = new HashMap<>();

            try (Connection conn = getConnection();
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
            }

            return offDutyMinutes;
        });
    }

    // Activity tracking methods for SQLite
    @Override
    public int getSearchCount(UUID playerId) {
        return getActivityStat(playerId, "search_count");
    }
    @Override
    public void incrementSearchCount(UUID playerId) {
        incrementActivityStat(playerId, "search_count");
    }
    @Override
    public int getSuccessfulSearchCount(UUID playerId) {
        return getActivityStat(playerId, "successful_search_count");
    }
    @Override
    public void incrementSuccessfulSearchCount(UUID playerId) {
        incrementActivityStat(playerId, "successful_search_count");
    }
    @Override
    public int getKillCount(UUID playerId) {
        return getActivityStat(playerId, "kill_count");
    }
    @Override
    public void incrementKillCount(UUID playerId) {
        incrementActivityStat(playerId, "kill_count");
    }
    @Override
    public int getMetalDetectCount(UUID playerId) {
        return getActivityStat(playerId, "metal_detect_count");
    }
    @Override
    public void incrementMetalDetectCount(UUID playerId) {
        incrementActivityStat(playerId, "metal_detect_count");
    }
    @Override
    public int getApprehensionCount(UUID playerId) {
        return getActivityStat(playerId, "apprehension_count");
    }
    @Override
    public void incrementApprehensionCount(UUID playerId) {
        incrementActivityStat(playerId, "apprehension_count");
    }
    @Override
    public void resetActivityCounts(UUID playerId) {
        executeWithRetry(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "INSERT INTO activity_stats (player_id, search_count, successful_search_count, kill_count, metal_detect_count, apprehension_count) " +
                                 "VALUES (?, 0, 0, 0, 0, 0) " +
                                 "ON CONFLICT(player_id) DO UPDATE SET " +
                                 "search_count=0, successful_search_count=0, kill_count=0, metal_detect_count=0, apprehension_count=0")) {
                stmt.setString(1, playerId.toString());
                stmt.executeUpdate();
                return null;
            }
        });
    }
    private int getActivityStat(UUID playerId, String column) {
        Integer result = executeWithRetry(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "SELECT " + column + " FROM activity_stats WHERE player_id = ?")) {
                stmt.setString(1, playerId.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            }
            return 0;
        });
        return result != null ? result : 0;
    }
    private void incrementActivityStat(UUID playerId, String column) {
        executeWithRetry(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "INSERT INTO activity_stats (player_id, " + column + ") VALUES (?, 1) " +
                                 "ON CONFLICT(player_id) DO UPDATE SET " + column + " = " + column + " + 1")) {
                stmt.setString(1, playerId.toString());
                stmt.executeUpdate();
                return null;
            }
        });
    }

    // Guard statistics methods
    public Map<String, Object> loadLifetimeStats(UUID playerId) {
        return executeWithRetry(() -> {
            Map<String, Object> stats = new HashMap<>();
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "SELECT * FROM guard_statistics WHERE player_id = ?")) {
                stmt.setString(1, playerId.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        stats.put("totalDutyTime", rs.getLong("total_duty_time"));
                        stats.put("totalSearches", rs.getInt("total_searches"));
                        stats.put("successfulSearches", rs.getInt("successful_searches"));
                        stats.put("metalDetections", rs.getInt("metal_detections"));
                        stats.put("apprehensions", rs.getInt("apprehensions"));
                        stats.put("deaths", rs.getInt("deaths"));
                        stats.put("tokensEarned", rs.getInt("tokens_earned"));
                        stats.put("lastDutyStart", rs.getLong("last_duty_start"));
                    }
                }
            }
            return stats;
        });
    }

    public void saveLifetimeStats(UUID playerId, Map<String, Object> stats) {
        executeWithRetry(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "INSERT INTO guard_statistics (player_id, total_duty_time, total_searches, successful_searches, metal_detections, apprehensions, deaths, tokens_earned, last_duty_start) " +
                                 "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                                 "ON CONFLICT(player_id) DO UPDATE SET " +
                                 "total_duty_time = excluded.total_duty_time, " +
                                 "total_searches = excluded.total_searches, " +
                                 "successful_searches = excluded.successful_searches, " +
                                 "metal_detections = excluded.metal_detections, " +
                                 "apprehensions = excluded.apprehensions, " +
                                 "deaths = excluded.deaths, " +
                                 "tokens_earned = excluded.tokens_earned, " +
                                 "last_duty_start = excluded.last_duty_start")) {
                stmt.setString(1, playerId.toString());
                stmt.setLong(2, (Long) stats.getOrDefault("totalDutyTime", 0L));
                stmt.setInt(3, (Integer) stats.getOrDefault("totalSearches", 0));
                stmt.setInt(4, (Integer) stats.getOrDefault("successfulSearches", 0));
                stmt.setInt(5, (Integer) stats.getOrDefault("metalDetections", 0));
                stmt.setInt(6, (Integer) stats.getOrDefault("apprehensions", 0));
                stmt.setInt(7, (Integer) stats.getOrDefault("deaths", 0));
                stmt.setInt(8, (Integer) stats.getOrDefault("tokensEarned", 0));
                stmt.setLong(9, (Long) stats.getOrDefault("lastDutyStart", 0L));
                stmt.executeUpdate();
                return null;
            }
        });
    }

    public Map<String, Object> loadSessionStats(UUID playerId) {
        Map<String, Object> stats = new HashMap<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT * FROM guard_session_stats WHERE player_id = ?")) {
            stmt.setString(1, playerId.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    stats.put("totalDutyTime", rs.getLong("total_duty_time"));
                    stats.put("totalSearches", rs.getInt("total_searches"));
                    stats.put("successfulSearches", rs.getInt("successful_searches"));
                    stats.put("metalDetections", rs.getInt("metal_detections"));
                    stats.put("apprehensions", rs.getInt("apprehensions"));
                    stats.put("deaths", rs.getInt("deaths"));
                    stats.put("tokensEarned", rs.getInt("tokens_earned"));
                    stats.put("lastDutyStart", rs.getLong("last_duty_start"));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load guard session stats", e);
        }
        return stats;
    }

    public void saveSessionStats(UUID playerId, Map<String, Object> stats) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO guard_session_stats (player_id, total_duty_time, total_searches, successful_searches, metal_detections, apprehensions, deaths, tokens_earned, last_duty_start) " +
                             "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                             "ON CONFLICT(player_id) DO UPDATE SET " +
                             "total_duty_time = excluded.total_duty_time, " +
                             "total_searches = excluded.total_searches, " +
                             "successful_searches = excluded.successful_searches, " +
                             "metal_detections = excluded.metal_detections, " +
                             "apprehensions = excluded.apprehensions, " +
                             "deaths = excluded.deaths, " +
                             "tokens_earned = excluded.tokens_earned, " +
                             "last_duty_start = excluded.last_duty_start")) {
            stmt.setString(1, playerId.toString());
            stmt.setLong(2, (Long) stats.getOrDefault("totalDutyTime", 0L));
            stmt.setInt(3, (Integer) stats.getOrDefault("totalSearches", 0));
            stmt.setInt(4, (Integer) stats.getOrDefault("successfulSearches", 0));
            stmt.setInt(5, (Integer) stats.getOrDefault("metalDetections", 0));
            stmt.setInt(6, (Integer) stats.getOrDefault("apprehensions", 0));
            stmt.setInt(7, (Integer) stats.getOrDefault("deaths", 0));
            stmt.setInt(8, (Integer) stats.getOrDefault("tokensEarned", 0));
            stmt.setLong(9, (Long) stats.getOrDefault("lastDutyStart", 0L));
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save guard session stats", e);
        }
    }

    public void clearSessionStats(UUID playerId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "DELETE FROM guard_session_stats WHERE player_id = ?")) {
            stmt.setString(1, playerId.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to clear guard session stats", e);
        }
    }

    // Guard progression methods
    public Map<String, Object> loadProgression(UUID playerId) {
        return executeWithRetry(() -> {
            Map<String, Object> data = new HashMap<>();
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "SELECT * FROM guard_progression WHERE player_id = ?")) {
                stmt.setString(1, playerId.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        data.put("points", rs.getInt("points"));
                        data.put("totalTimeServed", rs.getLong("total_time_served"));
                        data.put("successfulArrests", rs.getInt("successful_arrests"));
                        data.put("contraband", rs.getInt("contraband"));
                    }
                }
            }
            return data;
        });
    }

    public void saveProgression(UUID playerId, Map<String, Object> data) {
        executeWithRetry(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "INSERT INTO guard_progression (player_id, points, total_time_served, successful_arrests, contraband) " +
                                 "VALUES (?, ?, ?, ?, ?) " +
                                 "ON CONFLICT(player_id) DO UPDATE SET " +
                                 "points = excluded.points, " +
                                 "total_time_served = excluded.total_time_served, " +
                                 "successful_arrests = excluded.successful_arrests, " +
                                 "contraband = excluded.contraband")) {
                stmt.setString(1, playerId.toString());
                stmt.setInt(2, (Integer) data.getOrDefault("points", 0));
                stmt.setLong(3, (Long) data.getOrDefault("totalTimeServed", 0L));
                stmt.setInt(4, (Integer) data.getOrDefault("successfulArrests", 0));
                stmt.setInt(5, (Integer) data.getOrDefault("contraband", 0));
                stmt.executeUpdate();
                return null;
            }
        });
    }

    // Guard token methods
    public int getTokens(UUID playerId) {
        Integer result = executeWithRetry(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "SELECT tokens FROM guard_tokens WHERE player_id = ?")) {
                stmt.setString(1, playerId.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("tokens");
                    }
                }
            }
            return 0;
        });
        return result != null ? result : 0;
    }
    
    public void setTokens(UUID playerId, int tokens) {
        executeWithRetry(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "INSERT INTO guard_tokens (player_id, tokens, last_reward_time) VALUES (?, ?, ?) " +
                                 "ON CONFLICT(player_id) DO UPDATE SET tokens = excluded.tokens")) {
                stmt.setString(1, playerId.toString());
                stmt.setInt(2, tokens);
                stmt.setLong(3, getLastRewardTime(playerId));
                stmt.executeUpdate();
                return null;
            }
        });
    }
    
    public void addTokens(UUID playerId, int amount) {
        setTokens(playerId, getTokens(playerId) + amount);
    }
    
    public boolean removeTokens(UUID playerId, int amount) {
        int current = getTokens(playerId);
        if (current < amount) return false;
        setTokens(playerId, current - amount);
        return true;
    }
    
    public long getLastRewardTime(UUID playerId) {
        Long result = executeWithRetry(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "SELECT last_reward_time FROM guard_tokens WHERE player_id = ?")) {
                stmt.setString(1, playerId.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getLong("last_reward_time");
                    }
                }
            }
            return 0L;
        });
        return result != null ? result : 0L;
    }
    
    public void setLastRewardTime(UUID playerId, long time) {
        executeWithRetry(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "INSERT INTO guard_tokens (player_id, tokens, last_reward_time) VALUES (?, ?, ?) " +
                                 "ON CONFLICT(player_id) DO UPDATE SET last_reward_time = excluded.last_reward_time")) {
                stmt.setString(1, playerId.toString());
                stmt.setInt(2, getTokens(playerId));
                stmt.setLong(3, time);
                stmt.executeUpdate();
                return null;
            }
        });
    }

    // Jail data methods
    public Map<UUID, JailManager.JailData> loadJailData() {
        Map<UUID, JailManager.JailData> jailData = new HashMap<>();
        String selectQuery = "SELECT player_id, start_time, duration_seconds, reason, jail_location, arresting_guard FROM jail_data";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(selectQuery);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                UUID playerId = UUID.fromString(rs.getString("player_id"));
                long startTime = rs.getLong("start_time");
                int durationSeconds = rs.getInt("duration_seconds");
                String reason = rs.getString("reason");
                String jailLocation = rs.getString("jail_location");
                String arrestingGuardStr = rs.getString("arresting_guard");
                UUID arrestingGuard = arrestingGuardStr != null ? UUID.fromString(arrestingGuardStr) : null;
                JailManager.JailData data = new JailManager.JailData(startTime, durationSeconds, reason, jailLocation, arrestingGuard);
                jailData.put(playerId, data);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load jail data: " + e.getMessage());
        }
        return jailData;
    }

    public void saveJailData(Map<UUID, JailManager.JailData> jailData) {
        String insertQuery = "INSERT OR REPLACE INTO jail_data (player_id, start_time, duration_seconds, reason, jail_location, arresting_guard) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(insertQuery)) {
            for (Map.Entry<UUID, JailManager.JailData> entry : jailData.entrySet()) {
                UUID playerId = entry.getKey();
                JailManager.JailData data = entry.getValue();
                stmt.setString(1, playerId.toString());
                stmt.setLong(2, data.startTime);
                stmt.setInt(3, data.durationSeconds);
                stmt.setString(4, data.reason);
                stmt.setString(5, data.jailLocation);
                stmt.setString(6, data.arrestingGuard != null ? data.arrestingGuard.toString() : null);
                stmt.addBatch();
            }
            stmt.executeBatch();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to save jail data: " + e.getMessage());
        }
    }

    public void saveOfflineJailQueue(Set<UUID> queue) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (Statement clearStmt = conn.createStatement()) {
                clearStmt.execute("DELETE FROM offline_jail_queue");
            }
            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO offline_jail_queue (player_id) VALUES (?)")) {
                for (UUID id : queue) {
                    stmt.setString(1, id.toString());
                    stmt.addBatch();
                }
                stmt.executeBatch();
            }
            conn.commit();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save offline jail queue", e);
        }
    }

    public Set<UUID> loadOfflineJailQueue() {
        Set<UUID> set = new HashSet<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT player_id FROM offline_jail_queue")) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    set.add(UUID.fromString(rs.getString("player_id")));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load offline jail queue", e);
        }
        return set;
    }

    // Contraband registry methods
    public Map<String, Set<dev.lsdmc.edencorrections.managers.ContrabandManager.ContrabandItem>> loadContrabandRegistry() {
        Map<String, Set<dev.lsdmc.edencorrections.managers.ContrabandManager.ContrabandItem>> map = new HashMap<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT * FROM contraband_registry")) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String type = rs.getString("type");
                    String material = rs.getString("material");
                    String displayName = rs.getString("display_name");
                    String loreStr = rs.getString("lore");
                    String addedBy = rs.getString("added_by");
                    long addedTime = rs.getLong("added_time");
                    int loreHash = rs.getInt("lore_hash");
                    java.util.List<String> lore = new java.util.ArrayList<>();
                    if (loreStr != null && !loreStr.isEmpty()) {
                        lore = java.util.Arrays.asList(loreStr.split("\n", -1));
                    }
                    dev.lsdmc.edencorrections.managers.ContrabandManager.ContrabandItem item =
                        new dev.lsdmc.edencorrections.managers.ContrabandManager.ContrabandItem(
                            org.bukkit.Material.valueOf(material), displayName, lore, addedBy, addedTime);
                    java.lang.reflect.Field field = item.getClass().getDeclaredField("loreHash");
                    field.setAccessible(true);
                    field.setInt(item, loreHash);
                    map.computeIfAbsent(type, k -> new java.util.HashSet<>()).add(item);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load contraband registry", e);
        }
        return map;
    }
    public void saveContrabandRegistry(Map<String, Set<dev.lsdmc.edencorrections.managers.ContrabandManager.ContrabandItem>> registry) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (Statement clearStmt = conn.createStatement()) {
                clearStmt.execute("DELETE FROM contraband_registry");
            }
            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO contraband_registry (type, material, display_name, lore, added_by, added_time, lore_hash) VALUES (?, ?, ?, ?, ?, ?, ?)") ) {
                for (Map.Entry<String, Set<dev.lsdmc.edencorrections.managers.ContrabandManager.ContrabandItem>> entry : registry.entrySet()) {
                    String type = entry.getKey();
                    for (dev.lsdmc.edencorrections.managers.ContrabandManager.ContrabandItem item : entry.getValue()) {
                        stmt.setString(1, type);
                        stmt.setString(2, item.material.name());
                        stmt.setString(3, item.displayName);
                        stmt.setString(4, item.lore != null ? String.join("\n", item.lore) : null);
                        stmt.setString(5, item.addedBy);
                        stmt.setLong(6, item.addedTime);
                        java.lang.reflect.Field field = item.getClass().getDeclaredField("loreHash");
                        field.setAccessible(true);
                        stmt.setInt(7, field.getInt(item));
                        stmt.addBatch();
                    }
                }
                stmt.executeBatch();
            }
            conn.commit();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save contraband registry", e);
        }
    }

    // Wanted level methods
    public Map<UUID, Integer> loadWantedLevels(Map<UUID, Long> wantedTimers, Set<UUID> markedPlayers) {
        Map<UUID, Integer> map = new HashMap<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT * FROM wanted_levels")) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    UUID playerId = UUID.fromString(rs.getString("player_id"));
                    int level = rs.getInt("level");
                    long expiry = rs.getLong("expiry");
                    boolean marked = rs.getBoolean("marked");
                    map.put(playerId, level);
                    wantedTimers.put(playerId, expiry);
                    if (marked) markedPlayers.add(playerId);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load wanted levels", e);
        }
        return map;
    }
    public void saveWantedLevels(Map<UUID, Integer> wantedLevels, Map<UUID, Long> wantedTimers, Set<UUID> markedPlayers) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (Statement clearStmt = conn.createStatement()) {
                clearStmt.execute("DELETE FROM wanted_levels");
            }
            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO wanted_levels (player_id, level, expiry, marked) VALUES (?, ?, ?, ?)") ) {
                for (Map.Entry<UUID, Integer> entry : wantedLevels.entrySet()) {
                    UUID playerId = entry.getKey();
                    stmt.setString(1, playerId.toString());
                    stmt.setInt(2, entry.getValue());
                    stmt.setLong(3, wantedTimers.getOrDefault(playerId, 0L));
                    stmt.setBoolean(4, markedPlayers.contains(playerId));
                    stmt.addBatch();
                }
                stmt.executeBatch();
            }
            conn.commit();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save wanted levels", e);
        }
    }
}