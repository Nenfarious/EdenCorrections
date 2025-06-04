package dev.lsdmc.edencorrections.managers;

import dev.lsdmc.edencorrections.EdenCorrections;
import dev.lsdmc.edencorrections.managers.StorageManager;
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
    private final boolean sqliteAvailable;

    // Cache for active statistics
    private final Map<UUID, GuardStats> activeStats = new ConcurrentHashMap<>();
    private final Map<UUID, GuardStats> lifetimeStats = new ConcurrentHashMap<>();

    public GuardStatisticsManager(EdenCorrections plugin) {
        this.plugin = plugin;
        this.statsFile = new File(plugin.getDataFolder(), "guard_statistics.yml");
        
        // Safety check for storage manager
        StorageManager storageManager = plugin.getStorageManager();
        if (storageManager instanceof SQLiteStorage) {
            this.sqliteStorage = (SQLiteStorage) storageManager;
            this.sqliteAvailable = true;
            plugin.getLogger().info("GuardStatisticsManager using SQLiteStorage backend");
        } else {
            this.sqliteStorage = null;
            this.sqliteAvailable = false;
            if (storageManager == null) {
                plugin.getLogger().warning("GuardStatisticsManager: StorageManager is null! Statistics will be disabled.");
            } else {
                plugin.getLogger().warning("GuardStatisticsManager: StorageManager is " + storageManager.getClass().getSimpleName() + 
                    ", not SQLiteStorage. Guard statistics require SQLiteStorage and will be disabled.");
            }
            
            // Initialize file-based fallback
            initFileConfig();
        }
        
        loadAllStats();
    }
    
    private void initFileConfig() {
        if (!statsFile.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                statsFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to create guard_statistics.yml: " + e.getMessage());
            }
        }
        statsConfig = YamlConfiguration.loadConfiguration(statsFile);
    }

    private void loadAllStats() {
        if (!sqliteAvailable) {
            // Load from file if SQLite is not available
            loadFromFile();
            return;
        }
        // No need to pre-load all stats; load on demand from SQLite
        // Optionally, you could cache all stats here if needed
    }
    
    private void loadFromFile() {
        if (statsConfig == null) return;
        
        // Load stats from file (simplified implementation)
        // This would be a basic fallback when SQLite is not available
        plugin.getLogger().info("Loading guard statistics from file (fallback mode)");
    }
    
    private void saveToFile() {
        if (statsConfig == null) return;
        
        try {
            // Save stats to file (simplified implementation)
            for (Map.Entry<UUID, GuardStats> entry : activeStats.entrySet()) {
                String path = "active." + entry.getKey().toString();
                Map<String, Object> data = entry.getValue().toMap();
                for (Map.Entry<String, Object> statEntry : data.entrySet()) {
                    statsConfig.set(path + "." + statEntry.getKey(), statEntry.getValue());
                }
            }
            statsConfig.save(statsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save guard statistics to file: " + e.getMessage());
        }
    }

    private GuardStats loadPlayerStats(UUID playerId) {
        if (!sqliteAvailable) {
            return new GuardStats(); // Return empty stats if SQLite not available
        }
        
        try {
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
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load player stats for " + playerId + ": " + e.getMessage());
            return new GuardStats();
        }
    }

    public void saveAllStats() {
        if (!sqliteAvailable || sqliteStorage == null) {
            saveToFile();
            return;
        }
        
        try {
            // Save both active and lifetime stats
            for (Map.Entry<UUID, GuardStats> entry : lifetimeStats.entrySet()) {
                sqliteStorage.saveLifetimeStats(entry.getKey(), entry.getValue().toMap());
            }
            for (Map.Entry<UUID, GuardStats> entry : activeStats.entrySet()) {
                sqliteStorage.saveSessionStats(entry.getKey(), entry.getValue().toMap());
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to save statistics to SQLite: " + e.getMessage());
            // Fallback to file
            saveToFile();
        }
    }

    private void savePlayerStats(UUID playerId, GuardStats stats, boolean lifetime) {
        if (!sqliteAvailable) {
            return; // Skip saving if SQLite not available
        }
        
        try {
            if (lifetime) {
                sqliteStorage.saveLifetimeStats(playerId, stats.toMap());
            } else {
                sqliteStorage.saveSessionStats(playerId, stats.toMap());
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to save player stats for " + playerId + ": " + e.getMessage());
        }
    }

    public void startDutySession(Player player) {
        UUID playerId = player.getUniqueId();
        GuardStats sessionStats = new GuardStats();
        sessionStats.lastDutyStart = Instant.now().getEpochSecond();
        activeStats.put(playerId, sessionStats);
        
        if (sqliteAvailable && sqliteStorage != null) {
            try {
                sqliteStorage.saveSessionStats(playerId, sessionStats.toMap());
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to save session start for " + player.getName() + ": " + e.getMessage());
            }
        }
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
            
            if (sqliteAvailable && sqliteStorage != null) {
                try {
                    sqliteStorage.clearSessionStats(playerId);
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to clear session stats for " + player.getName() + ": " + e.getMessage());
                }
            }
        }
    }

    private void updateLifetimeStats(UUID playerId, GuardStats sessionStats) {
        if (!sqliteAvailable || sqliteStorage == null) {
            return; // Skip if SQLite not available
        }
        
        try {
            GuardStats lifetime = loadPlayerStats(playerId);
            lifetime.totalDutyTime += sessionStats.totalDutyTime;
            lifetime.totalSearches += sessionStats.totalSearches;
            lifetime.successfulSearches += sessionStats.successfulSearches;
            lifetime.metalDetections += sessionStats.metalDetections;
            lifetime.apprehensions += sessionStats.apprehensions;
            lifetime.deaths += sessionStats.deaths;
            lifetime.tokensEarned += sessionStats.tokensEarned;
            sqliteStorage.saveLifetimeStats(playerId, lifetime.toMap());
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to update lifetime stats for " + playerId + ": " + e.getMessage());
        }
    }

    public void recordSearch(Player player) {
        UUID playerId = player.getUniqueId();
        GuardStats stats = activeStats.computeIfAbsent(playerId, k -> new GuardStats());
        stats.totalSearches++;
        
        if (sqliteAvailable && sqliteStorage != null) {
            try {
                sqliteStorage.saveSessionStats(playerId, stats.toMap());
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to record search for " + player.getName() + ": " + e.getMessage());
            }
        }
    }

    public void recordSuccessfulSearch(Player player) {
        UUID playerId = player.getUniqueId();
        GuardStats stats = activeStats.computeIfAbsent(playerId, k -> new GuardStats());
        stats.successfulSearches++;
        
        if (sqliteAvailable && sqliteStorage != null) {
            try {
                sqliteStorage.saveSessionStats(playerId, stats.toMap());
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to record successful search for " + player.getName() + ": " + e.getMessage());
            }
        }
    }

    public void recordMetalDetection(Player player) {
        UUID playerId = player.getUniqueId();
        GuardStats stats = activeStats.computeIfAbsent(playerId, k -> new GuardStats());
        stats.metalDetections++;
        
        if (sqliteAvailable && sqliteStorage != null) {
            try {
                sqliteStorage.saveSessionStats(playerId, stats.toMap());
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to record metal detection for " + player.getName() + ": " + e.getMessage());
            }
        }
    }

    public void recordApprehension(Player player) {
        UUID playerId = player.getUniqueId();
        GuardStats stats = activeStats.computeIfAbsent(playerId, k -> new GuardStats());
        stats.apprehensions++;
        
        if (sqliteAvailable && sqliteStorage != null) {
            try {
                sqliteStorage.saveSessionStats(playerId, stats.toMap());
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to record apprehension for " + player.getName() + ": " + e.getMessage());
            }
        }
    }

    public void recordDeath(Player player) {
        UUID playerId = player.getUniqueId();
        GuardStats stats = activeStats.computeIfAbsent(playerId, k -> new GuardStats());
        stats.deaths++;
        
        if (sqliteAvailable && sqliteStorage != null) {
            try {
                sqliteStorage.saveSessionStats(playerId, stats.toMap());
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to record death for " + player.getName() + ": " + e.getMessage());
            }
        }
    }

    public void recordTokensEarned(Player player, int amount) {
        UUID playerId = player.getUniqueId();
        GuardStats stats = activeStats.computeIfAbsent(playerId, k -> new GuardStats());
        stats.tokensEarned += amount;
        
        if (sqliteAvailable && sqliteStorage != null) {
            try {
                sqliteStorage.saveSessionStats(playerId, stats.toMap());
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to record tokens earned for " + player.getName() + ": " + e.getMessage());
            }
        }
    }

    public Map<String, Object> getPlayerStats(Player player) {
        UUID playerId = player.getUniqueId();
        Map<String, Object> stats = new HashMap<>();
        
        if (!sqliteAvailable || sqliteStorage == null) {
            // Return basic stats from memory if SQLite not available
            GuardStats active = activeStats.get(playerId);
            if (active != null) {
                stats.put("session", active.toMap());
            }
            return stats;
        }
        
        try {
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
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to get player stats for " + player.getName() + ": " + e.getMessage());
        }
        
        return stats;
    }
    
    public boolean isStatisticsEnabled() {
        return sqliteAvailable;
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