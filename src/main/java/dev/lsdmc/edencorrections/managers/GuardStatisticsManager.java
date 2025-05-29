package dev.lsdmc.edencorrections.managers;

import dev.lsdmc.edencorrections.EdenCorrections;
import dev.lsdmc.edencorrections.storage.SQLiteStorage;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GuardStatisticsManager {
    private final EdenCorrections plugin;
    private final File statsFile;
    private FileConfiguration statsConfig;
    private final SQLiteStorage sqliteStorage;

    // Cache for active statistics
    private final Map<UUID, GuardStats> activeStats = new ConcurrentHashMap<>();
    private final Map<UUID, GuardStats> lifetimeStats = new ConcurrentHashMap<>();

    public GuardStatisticsManager(EdenCorrections plugin) {
        this.plugin = plugin;
        this.statsFile = new File(plugin.getDataFolder(), "guard_statistics.yml");
        this.sqliteStorage = (SQLiteStorage) plugin.getStorageManager();
        loadAllStats();
    }

    private void loadAllStats() {
        // No need to pre-load all stats; load on demand from SQLite
        // Optionally, you could cache all stats here if needed
    }

    private GuardStats loadPlayerStats(UUID playerId) {
        Map<String, Object> data = sqliteStorage.loadLifetimeStats(playerId);
        GuardStats stats = new GuardStats();
        stats.totalDutyTime = (Long) data.getOrDefault("totalDutyTime", 0L);
        stats.totalSearches = (Integer) data.getOrDefault("totalSearches", 0);
        stats.successfulSearches = (Integer) data.getOrDefault("successfulSearches", 0);
        stats.metalDetections = (Integer) data.getOrDefault("metalDetections", 0);
        stats.apprehensions = (Integer) data.getOrDefault("apprehensions", 0);
        stats.deaths = (Integer) data.getOrDefault("deaths", 0);
        stats.tokensEarned = (Integer) data.getOrDefault("tokensEarned", 0);
        stats.lastDutyStart = (Long) data.getOrDefault("lastDutyStart", 0L);
        return stats;
    }

    public void saveAllStats() {
        // Save both active and lifetime stats
        for (Map.Entry<UUID, GuardStats> entry : lifetimeStats.entrySet()) {
            sqliteStorage.saveLifetimeStats(entry.getKey(), entry.getValue().toMap());
        }
        for (Map.Entry<UUID, GuardStats> entry : activeStats.entrySet()) {
            sqliteStorage.saveSessionStats(entry.getKey(), entry.getValue().toMap());
        }
    }

    private void savePlayerStats(UUID playerId, GuardStats stats, boolean lifetime) {
        if (lifetime) {
            sqliteStorage.saveLifetimeStats(playerId, stats.toMap());
        } else {
            sqliteStorage.saveSessionStats(playerId, stats.toMap());
        }
    }

    public void startDutySession(Player player) {
        UUID playerId = player.getUniqueId();
        GuardStats sessionStats = new GuardStats();
        sessionStats.lastDutyStart = Instant.now().getEpochSecond();
        activeStats.put(playerId, sessionStats);
        sqliteStorage.saveSessionStats(playerId, sessionStats.toMap());
    }

    public void endDutySession(Player player) {
        UUID playerId = player.getUniqueId();
        GuardStats sessionStats = activeStats.remove(playerId);
        if (sessionStats != null) {
            // Calculate final session duration
            long sessionEnd = Instant.now().getEpochSecond();
            long sessionDuration = sessionEnd - sessionStats.lastDutyStart;
            sessionStats.totalDutyTime += sessionDuration;
            // Update lifetime stats
            updateLifetimeStats(playerId, sessionStats);
            sqliteStorage.clearSessionStats(playerId);
        }
    }

    private void updateLifetimeStats(UUID playerId, GuardStats sessionStats) {
        GuardStats lifetime = loadPlayerStats(playerId);
        lifetime.totalDutyTime += sessionStats.totalDutyTime;
        lifetime.totalSearches += sessionStats.totalSearches;
        lifetime.successfulSearches += sessionStats.successfulSearches;
        lifetime.metalDetections += sessionStats.metalDetections;
        lifetime.apprehensions += sessionStats.apprehensions;
        lifetime.deaths += sessionStats.deaths;
        lifetime.tokensEarned += sessionStats.tokensEarned;
        sqliteStorage.saveLifetimeStats(playerId, lifetime.toMap());
    }

    public void recordSearch(Player player) {
        UUID playerId = player.getUniqueId();
        GuardStats stats = activeStats.computeIfAbsent(playerId, k -> new GuardStats());
        stats.totalSearches++;
        sqliteStorage.saveSessionStats(playerId, stats.toMap());
    }

    public void recordSuccessfulSearch(Player player) {
        UUID playerId = player.getUniqueId();
        GuardStats stats = activeStats.computeIfAbsent(playerId, k -> new GuardStats());
        stats.successfulSearches++;
        sqliteStorage.saveSessionStats(playerId, stats.toMap());
    }

    public void recordMetalDetection(Player player) {
        UUID playerId = player.getUniqueId();
        GuardStats stats = activeStats.computeIfAbsent(playerId, k -> new GuardStats());
        stats.metalDetections++;
        sqliteStorage.saveSessionStats(playerId, stats.toMap());
    }

    public void recordApprehension(Player player) {
        UUID playerId = player.getUniqueId();
        GuardStats stats = activeStats.computeIfAbsent(playerId, k -> new GuardStats());
        stats.apprehensions++;
        sqliteStorage.saveSessionStats(playerId, stats.toMap());
    }

    public void recordDeath(Player player) {
        UUID playerId = player.getUniqueId();
        GuardStats stats = activeStats.computeIfAbsent(playerId, k -> new GuardStats());
        stats.deaths++;
        sqliteStorage.saveSessionStats(playerId, stats.toMap());
    }

    public void recordTokensEarned(Player player, int amount) {
        UUID playerId = player.getUniqueId();
        GuardStats stats = activeStats.computeIfAbsent(playerId, k -> new GuardStats());
        stats.tokensEarned += amount;
        sqliteStorage.saveSessionStats(playerId, stats.toMap());
    }

    public Map<String, Object> getPlayerStats(Player player) {
        UUID playerId = player.getUniqueId();
        Map<String, Object> stats = new HashMap<>();
        // Get active session stats
        GuardStats active = activeStats.get(playerId);
        if (active != null) {
            stats.put("session", active.toMap());
        } else {
            // Try to load from DB if not in memory
            Map<String, Object> session = sqliteStorage.loadSessionStats(playerId);
            if (!session.isEmpty()) stats.put("session", session);
        }
        // Get lifetime stats
        Map<String, Object> lifetime = sqliteStorage.loadLifetimeStats(playerId);
        if (!lifetime.isEmpty()) stats.put("lifetime", lifetime);
        return stats;
    }

    public void shutdown() {
        saveAllStats();
        activeStats.clear();
        lifetimeStats.clear();
    }

    private static class GuardStats {
        long totalDutyTime = 0;
        int totalSearches = 0;
        int successfulSearches = 0;
        int metalDetections = 0;
        int apprehensions = 0;
        int deaths = 0;
        int tokensEarned = 0;
        long lastDutyStart = 0;
        Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("totalDutyTime", totalDutyTime);
            map.put("totalSearches", totalSearches);
            map.put("successfulSearches", successfulSearches);
            map.put("metalDetections", metalDetections);
            map.put("apprehensions", apprehensions);
            map.put("deaths", deaths);
            map.put("tokensEarned", tokensEarned);
            map.put("lastDutyStart", lastDutyStart);
            return map;
        }
    }
} 