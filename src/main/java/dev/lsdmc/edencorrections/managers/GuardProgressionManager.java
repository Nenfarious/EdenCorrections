package dev.lsdmc.edencorrections.managers;

import dev.lsdmc.edencorrections.EdenCorrections;
import dev.lsdmc.edencorrections.storage.SQLiteStorage;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GuardProgressionManager {
    private final EdenCorrections plugin;
    private final SQLiteStorage sqliteStorage;
    
    // Cache for progression data
    private final Map<UUID, ProgressionData> progressionCache = new ConcurrentHashMap<>();
    
    // Rank thresholds (points needed for each rank)
    private final Map<String, Integer> rankThresholds = new HashMap<>();
    
    public GuardProgressionManager(EdenCorrections plugin) {
        this.plugin = plugin;
        this.sqliteStorage = (SQLiteStorage) plugin.getStorageManager();
        loadRankThresholds();
    }
    
    private void loadRankThresholds() {
        // Default thresholds if not configured
        rankThresholds.put("trainee", 0);
        rankThresholds.put("private", 1000);
        rankThresholds.put("officer", 3000);
        rankThresholds.put("sergeant", 6000);
        rankThresholds.put("warden", 10000);
        
        // Load from config if available
        if (plugin.getConfig().contains("guard-progression.thresholds")) {
            for (String rank : plugin.getConfig().getConfigurationSection("guard-progression.thresholds").getKeys(false)) {
                rankThresholds.put(rank.toLowerCase(), plugin.getConfig().getInt("guard-progression.thresholds." + rank));
            }
        }
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
        String currentRank = plugin.getGuardRankManager().getPlayerRank(player);
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
        if (currentRank == null) return "trainee";
        
        switch (currentRank.toLowerCase()) {
            case "trainee": return "private";
            case "private": return "officer";
            case "officer": return "sergeant";
            case "sergeant": return "warden";
            default: return null;
        }
    }
    
    private void promotePlayer(Player player, String newRank) {
        // Move player to the correct LuckPerms group for the new rank
        if (plugin.getConfig().contains("guard-rank-groups." + newRank)) {
            String group = plugin.getConfig().getString("guard-rank-groups." + newRank);
            try {
                var luckPerms = net.luckperms.api.LuckPermsProvider.get();
                var user = luckPerms.getUserManager().getUser(player.getUniqueId());
                if (user != null) {
                    // Remove from all other guard rank groups
                    for (String rank : plugin.getConfig().getConfigurationSection("guard-rank-groups").getKeys(false)) {
                        String otherGroup = plugin.getConfig().getString("guard-rank-groups." + rank);
                        if (!otherGroup.equalsIgnoreCase(group)) {
                            user.data().remove(net.luckperms.api.node.types.InheritanceNode.builder(otherGroup).build());
                        }
                    }
                    // Add to new group
                    user.data().add(net.luckperms.api.node.types.InheritanceNode.builder(group).build());
                    luckPerms.getUserManager().saveUser(user);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to update LuckPerms group for promotion: " + e.getMessage());
            }
        } else {
            // Fallback: use command
            String command = "lp user " + player.getName() + " parent add " + newRank;
            plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), command);
        }
        // Broadcast promotion
        String message = "§6§lCONGRATULATIONS! §e" + player.getName() + " §7has been promoted to §e" + newRank + "§7!";
        plugin.getServer().broadcastMessage(message);
        // Play sound for player
        player.playSound(player.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
    }
    
    public Map<String, Object> getProgressionStats(Player player) {
        UUID playerId = player.getUniqueId();
        ProgressionData data = progressionCache.computeIfAbsent(playerId, k -> loadPlayerProgression(playerId));
        Map<String, Object> stats = new HashMap<>();
        stats.put("points", data.points);
        stats.put("total_time", data.totalTimeServed);
        stats.put("arrests", data.successfulArrests);
        stats.put("contraband", data.contraband);
        
        String currentRank = plugin.getGuardRankManager().getPlayerRank(player);
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
} 