package dev.lsdmc.edencorrections.managers;

import dev.lsdmc.edencorrections.EdenCorrections;
import dev.lsdmc.edencorrections.storage.SQLiteStorage;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GuardProgressionManager {
    private final EdenCorrections plugin;
    private final SQLiteStorage sqliteStorage;
    private final GuardRankManager rankManager;
    
    // Cache for progression data
    private final Map<UUID, ProgressionData> progressionCache = new ConcurrentHashMap<>();
    
    // Rank thresholds (points needed for each rank)
    private final Map<String, Integer> rankThresholds = new HashMap<>();
    
    public GuardProgressionManager(EdenCorrections plugin) {
        this.plugin = plugin;
        this.sqliteStorage = (SQLiteStorage) plugin.getStorageManager();
        this.rankManager = plugin.getGuardRankManager();
        loadRankThresholds();
    }
    
    private void loadRankThresholds() {
        rankThresholds.clear();
        
        // Load from ranks.yml instead of main config.yml
        FileConfiguration ranksConfig = plugin.getConfigManager().getRanksConfig();
        if (ranksConfig == null) {
            plugin.getLogger().warning("ranks.yml not found, using default progression thresholds");
            setDefaultThresholds();
            return;
        }

        // Load progression thresholds from correct path: progression.thresholds
        if (ranksConfig.contains("progression.thresholds")) {
            for (String rank : ranksConfig.getConfigurationSection("progression.thresholds").getKeys(false)) {
                rankThresholds.put(rank.toLowerCase(), ranksConfig.getInt("progression.thresholds." + rank));
            }
        } else {
            plugin.getLogger().warning("No progression.thresholds found in ranks.yml, using defaults");
            setDefaultThresholds();
        }

        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("Loaded progression thresholds: " + rankThresholds);
        }
    }
    
    private void setDefaultThresholds() {
        rankThresholds.put("trainee", 0);
        rankThresholds.put("private", 1000);
        rankThresholds.put("officer", 3000);
        rankThresholds.put("sergeant", 6000);
        rankThresholds.put("warden", 10000);
    }
    
    public void saveProgression() {
        for (Map.Entry<UUID, ProgressionData> entry : progressionCache.entrySet()) {
            sqliteStorage.saveProgression(entry.getKey(), entry.getValue().toMap());
        }
    }
    
    private ProgressionData loadPlayerProgression(UUID playerId) {
        Map<String, Object> data = sqliteStorage.loadProgression(playerId);
        ProgressionData pd = new ProgressionData();
        pd.points = (Integer) data.getOrDefault("points", 0);
        pd.totalTimeServed = (Long) data.getOrDefault("totalTimeServed", 0L);
        pd.successfulArrests = (Integer) data.getOrDefault("successfulArrests", 0);
        pd.contraband = (Integer) data.getOrDefault("contraband", 0);
        return pd;
    }
    
    public void addPoints(Player player, int points, String reason) {
        UUID playerId = player.getUniqueId();
        ProgressionData data = progressionCache.computeIfAbsent(playerId, k -> loadPlayerProgression(playerId));
        data.points += points;
        sqliteStorage.saveProgression(playerId, data.toMap());
        
        // Check for rank up
        String currentRank = rankManager.getPlayerRank(player);
        String nextRank = getNextRank(currentRank);
        
        if (nextRank != null && data.points >= rankThresholds.get(nextRank)) {
            // Player has reached the next rank
            promotePlayer(player, nextRank);
        }
        
        // Notify player
        player.sendMessage("§a+" + points + " guard points: " + reason);
        player.sendMessage("§7Total points: §e" + data.points);
    }
    
    public void recordArrest(Player player) {
        UUID playerId = player.getUniqueId();
        ProgressionData data = progressionCache.computeIfAbsent(playerId, k -> loadPlayerProgression(playerId));
        data.successfulArrests++;
        sqliteStorage.saveProgression(playerId, data.toMap());
        addPoints(player, 50, "Successful arrest");
    }
    
    public void recordContraband(Player player) {
        UUID playerId = player.getUniqueId();
        ProgressionData data = progressionCache.computeIfAbsent(playerId, k -> loadPlayerProgression(playerId));
        data.contraband++;
        sqliteStorage.saveProgression(playerId, data.toMap());
        addPoints(player, 25, "Contraband found");
    }
    
    public void updateTimeServed(Player player, long additionalSeconds) {
        UUID playerId = player.getUniqueId();
        ProgressionData data = progressionCache.computeIfAbsent(playerId, k -> loadPlayerProgression(playerId));
        data.totalTimeServed += additionalSeconds;
        sqliteStorage.saveProgression(playerId, data.toMap());
        
        // Award points every hour served
        if (data.totalTimeServed >= 3600 && data.totalTimeServed % 3600 == 0) {
            addPoints(player, 100, "Hour of duty completed");
        }
    }
    
    private String getNextRank(String currentRank) {
        if (currentRank == null) return rankManager.getRankList().get(0);
        
        List<String> ranks = rankManager.getRankList();
        int currentIndex = ranks.indexOf(currentRank.toLowerCase());
        
        if (currentIndex == -1 || currentIndex >= ranks.size() - 1) {
            return null;
        }
        
        return ranks.get(currentIndex + 1);
    }
    
    private void promotePlayer(Player player, String newRank) {
        if (rankManager.assignRank(player, newRank)) {
            // Broadcast promotion
            String message = "§6§lCONGRATULATIONS! §e" + player.getName() + " §7has been promoted to §e" + newRank + "§7!";
            plugin.getServer().broadcastMessage(message);
            // Play sound for player
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        } else {
            plugin.getLogger().warning("Failed to promote player " + player.getName() + " to rank " + newRank);
        }
    }
    
    public Map<String, Object> getProgressionStats(Player player) {
        UUID playerId = player.getUniqueId();
        ProgressionData data = progressionCache.computeIfAbsent(playerId, k -> loadPlayerProgression(playerId));
        Map<String, Object> stats = new HashMap<>();
        stats.put("points", data.points);
        stats.put("total_time", data.totalTimeServed);
        stats.put("arrests", data.successfulArrests);
        stats.put("contraband", data.contraband);
        
        String currentRank = rankManager.getPlayerRank(player);
        stats.put("current_rank", currentRank);
        
        String nextRank = getNextRank(currentRank);
        if (nextRank != null) {
            int pointsNeeded = rankThresholds.get(nextRank) - data.points;
            stats.put("next_rank", nextRank);
            stats.put("points_needed", Math.max(0, pointsNeeded));
        }
        
        return stats;
    }
    
    private static class ProgressionData {
        int points = 0;
        long totalTimeServed = 0;
        int successfulArrests = 0;
        int contraband = 0;
        
        Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("points", points);
            map.put("totalTimeServed", totalTimeServed);
            map.put("successfulArrests", successfulArrests);
            map.put("contraband", contraband);
            return map;
        }
    }
    
    public void shutdown() {
        saveProgression();
    }

    /**
     * Reload progression configurations
     */
    public void reload() {
        // Save current progression data
        saveProgression();
        
        // Clear cache to force reload from storage
        progressionCache.clear();
        
        // Reload rank thresholds from config
        loadRankThresholds();
        
        plugin.getLogger().info("GuardProgressionManager reloaded - cleared cache and reloaded rank thresholds");
    }

    /**
     * Get progression reward amount for a specific activity
     */
    public int getRewardAmount(String activity) {
        FileConfiguration ranksConfig = plugin.getConfigManager().getRanksConfig();
        if (ranksConfig != null) {
            return ranksConfig.getInt("progression.rewards." + activity, getDefaultReward(activity));
        }
        return getDefaultReward(activity);
    }

    private int getDefaultReward(String activity) {
        return switch (activity) {
            case "search" -> 10;
            case "successful-search" -> 25;
            case "metal-detect" -> 15;
            case "apprehension" -> 50;
            case "chase-complete" -> 50;
            case "contraband-found" -> 30;
            case "drug-detection" -> 20;
            case "wanted-level-increase" -> 15;
            default -> 5;
        };
    }
} 