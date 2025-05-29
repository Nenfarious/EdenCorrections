package dev.lsdmc.edencorrections.managers;

import dev.lsdmc.edencorrections.EdenCorrections;
import dev.lsdmc.edencorrections.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import dev.lsdmc.edencorrections.storage.SQLiteStorage;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.io.File;

public class JailManager {
    private final EdenCorrections plugin;
    private final Map<UUID, JailData> jailedPlayers = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> jailTasks = new HashMap<>();
    private final Set<UUID> offlineJailQueue = new HashSet<>();
    private final File offlineJailFile;
    private final File jailDataFile;
    private final org.bukkit.configuration.file.YamlConfiguration offlineJailConfig;
    private final org.bukkit.configuration.file.YamlConfiguration jailDataConfig;
    private final SQLiteStorage sqliteStorage;

    public JailManager(EdenCorrections plugin) {
        this.plugin = plugin;
        this.sqliteStorage = (SQLiteStorage) plugin.getStorageManager();
        
        // Initialize offline jail queue file
        this.offlineJailFile = new File(plugin.getDataFolder(), "offline_jail_queue.yml");
        if (!offlineJailFile.exists()) {
            try { offlineJailFile.createNewFile(); } catch (Exception e) {
                plugin.getLogger().severe("Failed to create offline_jail_queue.yml: " + e.getMessage());
            }
        }
        this.offlineJailConfig = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(offlineJailFile);
        
        // Initialize jail data file
        this.jailDataFile = new File(plugin.getDataFolder(), "jail_data.yml");
        if (!jailDataFile.exists()) {
            try { jailDataFile.createNewFile(); } catch (Exception e) {
                plugin.getLogger().severe("Failed to create jail_data.yml: " + e.getMessage());
            }
        }
        this.jailDataConfig = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(jailDataFile);

        loadOfflineJailQueue();
        loadJailData();
    }

    private void loadJailData() {
        // Load from SQLite
        Map<UUID, JailData> loaded = sqliteStorage.loadJailData();
        jailedPlayers.clear();
        jailedPlayers.putAll(loaded);
        // Schedule release tasks for loaded jail data
        for (Map.Entry<UUID, JailData> entry : jailedPlayers.entrySet()) {
            UUID playerId = entry.getKey();
            JailData data = entry.getValue();
            long endTime = data.startTime + (data.durationSeconds * 1000L);
            if (System.currentTimeMillis() < endTime) {
                long remainingTime = endTime - System.currentTimeMillis();
                BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    Player player = Bukkit.getPlayer(playerId);
                    if (player != null && player.isOnline()) {
                        releasePlayer(player);
                    }
                }, remainingTime / 50);
                jailTasks.put(playerId, task);
            }
        }
    }

    private void saveJailData() {
        sqliteStorage.saveJailData(jailedPlayers);
    }

    private void saveOfflineJailQueue() {
        sqliteStorage.saveOfflineJailQueue(offlineJailQueue);
    }

    private void loadOfflineJailQueue() {
        offlineJailQueue.clear();
        offlineJailQueue.addAll(sqliteStorage.loadOfflineJailQueue());
    }

    public void addToOfflineJailQueue(UUID playerId) {
        offlineJailQueue.add(playerId);
        saveOfflineJailQueue();
    }

    public void removeFromOfflineJailQueue(UUID playerId) {
        offlineJailQueue.remove(playerId);
        saveOfflineJailQueue();
    }

    /**
     * Jail a player for a specified duration (in minutes)
     */
    public void jailPlayer(Player player, double minutes, String reason) {
        UUID playerId = player.getUniqueId();
        int seconds = (int) (minutes * 60);

        // Get wanted level before clearing it (for reward calculation)
        int wantedLevel = plugin.getWantedLevelManager().getWantedLevel(playerId);

        // Only remove contraband/drugs, preserve regular inventory
        removeContrabandAndDrugs(player);

        // CMI handles jail teleportation, so we don't need to teleport manually

        // Handle wanted level system (clear wanted level and marks)
        plugin.getWantedLevelManager().handlePlayerJail(player);

        // Broadcast event
        String msg = "<red>" + player.getName() + " has been jailed for " + minutes + " minutes. Reason: " + reason + "</red>";
        Bukkit.broadcast(MessageUtils.parseMessage(msg));

        // Start jail timer
        if (jailTasks.containsKey(playerId)) {
            jailTasks.get(playerId).cancel();
        }
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> releasePlayer(player), seconds * 20L);
        jailTasks.put(playerId, task);
        jailedPlayers.put(playerId, new JailData(System.currentTimeMillis(), seconds, reason));
        saveJailData();

        // Award points to the arresting guard if available
        Player arrestingGuard = getArrestingGuard(player);
        if (arrestingGuard != null && plugin.getDutyManager().isOnDuty(arrestingGuard.getUniqueId())) {
            plugin.getGuardProgressionManager().recordArrest(arrestingGuard);
            
            // Award off-duty time based on wanted level (calculated before clearing)
            plugin.getDutyManager().addOffDutyMinutes(arrestingGuard.getUniqueId(), 1 + wantedLevel);
        }
    }

    /**
     * Get the guard who arrested a player (if any)
     */
    private Player getArrestingGuard(Player prisoner) {
        // Check if prisoner was being chased
        if (plugin.getChaseManager().isBeingChased(prisoner)) {
            return plugin.getChaseManager().getChasingGuard(prisoner);
        }
        
        // Check nearby players for guards
        for (Player nearby : prisoner.getLocation().getNearbyPlayers(5)) {
            if (plugin.getDutyManager().isOnDuty(nearby.getUniqueId())) {
                return nearby;
            }
        }
        
        return null;
    }

    /**
     * Queue jail for offline player
     */
    public void jailOfflinePlayer(UUID playerId, double minutes, String reason) {
        addToOfflineJailQueue(playerId);
        // Store jail info in persistent storage if needed
        // For now, just keep in memory
    }

    /**
     * Call this on player join
     */
    public void handlePlayerJoin(Player player) {
        UUID playerId = player.getUniqueId();
        if (offlineJailQueue.contains(playerId)) {
            // Jail for default time (e.g., 5 minutes)
            jailPlayer(player, 5.0, "Offline jail");
            offlineJailQueue.remove(playerId);
        }
    }

    /**
     * Release a player from jail
     */
    public void releasePlayer(Player player) {
        UUID playerId = player.getUniqueId();
        if (jailTasks.containsKey(playerId)) {
            jailTasks.get(playerId).cancel();
            jailTasks.remove(playerId);
        }
        jailedPlayers.remove(playerId);
        player.sendMessage(MessageUtils.parseMessage("<green>You have been released from jail!</green>"));
        saveJailData();
    }

    /**
     * Remove contraband and drugs from a player's inventory
     */
    public void removeContrabandAndDrugs(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        int removedCount = 0;
        
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null) continue;

            // Use comprehensive contraband/drug detection
            if (plugin.getExternalPluginIntegration().isContrabandComprehensive(item) ||
                plugin.getExternalPluginIntegration().isDrugComprehensive(item)) {
                
                if (plugin.getConfigManager().isDebugEnabled()) {
                    plugin.getLogger().info("Removing contraband/drug item from " + player.getName());
                }
                contents[i] = null;
                removedCount++;
            }
        }
        
        player.getInventory().setContents(contents);
        
        if (removedCount > 0) {
            player.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<yellow>Removed " + removedCount + " contraband/drug item(s) from your inventory.</yellow>")));
            
            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().info("Removed " + removedCount + " contraband/drug items from " + player.getName());
            }
        }
    }

    /**
     * Check if a player is currently jailed
     */
    public boolean isJailed(UUID playerId) {
        return jailedPlayers.containsKey(playerId);
    }

    /**
     * Data class for jail info
     */
    public static class JailData {
        public final long startTime;
        public final int durationSeconds;
        public final String reason;

        public JailData(long startTime, int durationSeconds, String reason) {
            this.startTime = startTime;
            this.durationSeconds = durationSeconds;
            this.reason = reason;
        }
    }

    // Call this method when the plugin is disabled
    public void shutdown() {
        saveJailData();
        saveOfflineJailQueue();
    }
} 