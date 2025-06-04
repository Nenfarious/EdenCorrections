package dev.lsdmc.edencorrections.managers;

import dev.lsdmc.edencorrections.EdenCorrections;
import dev.lsdmc.edencorrections.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.inventory.ItemStack;
import dev.lsdmc.edencorrections.storage.SQLiteStorage;
import dev.lsdmc.edencorrections.managers.StorageManager;
import net.kyori.adventure.text.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class JailManager {
    private final EdenCorrections plugin;
    private final Map<UUID, JailData> jailedPlayers = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> jailTasks = new HashMap<>();
    private final Set<UUID> offlineJailQueue = new HashSet<>();
    private final SQLiteStorage sqliteStorage;
    
    // Simple jail configuration
    private final String lowSecurityJail; // For 0-3 wanted stars
    private final String highSecurityJail; // For 4-5 wanted stars
    
    // CMI Integration tracking
    private boolean cmiAvailable = false;
    
    public JailManager(EdenCorrections plugin) {
        this.plugin = plugin;
        
        // Safety check for storage manager
        StorageManager storageManager = plugin.getStorageManager();
        if (storageManager instanceof SQLiteStorage) {
            this.sqliteStorage = (SQLiteStorage) storageManager;
            plugin.getLogger().info("JailManager using SQLiteStorage backend");
        } else {
            this.sqliteStorage = null;
            plugin.getLogger().warning("JailManager: Advanced jail features require SQLiteStorage and will be disabled.");
        }
        
        // Simple jail configuration
        this.lowSecurityJail = plugin.getConfig().getString("jail.low-security-jail", "jail");
        this.highSecurityJail = plugin.getConfig().getString("jail.high-security-jail", "jail2");
        
        initializeCMIIntegration();
        loadJailData();
    }
    
    /**
     * Initialize CMI integration and validate availability
     */
    private void initializeCMIIntegration() {
        try {
            if (Bukkit.getPluginManager().getPlugin("CMI") != null) {
                cmiAvailable = true;
                plugin.getLogger().info("CMI integration successful - jail system enabled");
            } else {
                cmiAvailable = false;
                plugin.getLogger().warning("CMI not detected - jail system will have limited functionality");
            }
        } catch (Exception e) {
            cmiAvailable = false;
            plugin.getLogger().severe("Failed to initialize CMI integration: " + e.getMessage());
        }
    }
    
    /**
     * Determine which jail to use based on wanted level
     */
    private String determineJail(int wantedLevel) {
        return wantedLevel > 3 ? highSecurityJail : lowSecurityJail;
    }
    
    /**
     * Jail a player with CMI integration
     */
    public void jailPlayer(Player player, double minutes, String reason) {
        jailPlayer(player, minutes, reason, null);
    }
    
    /**
     * Jail a player with full context
     */
    public void jailPlayer(Player player, double minutes, String reason, Player arrestingGuard) {
        // Emergency shutdown check
        if (EdenCorrections.isEmergencyShutdown()) {
            plugin.getLogger().warning("Jail operation blocked due to emergency shutdown");
            return;
        }

        UUID playerId = player.getUniqueId();
        
        // Remove from offline jail queue if present
        removeFromOfflineJailQueue(playerId);
        
        // Determine jail based on wanted level
        int wantedLevel = plugin.getWantedLevelManager().getWantedLevel(playerId);
        String selectedJail = determineJail(wantedLevel);
        
        // Calculate jail time in seconds
        int durationSeconds = (int) (minutes * 60);
        
        // Store jail data
        JailData jailData = new JailData(System.currentTimeMillis(), durationSeconds, reason, selectedJail, 
            arrestingGuard != null ? arrestingGuard.getUniqueId() : null);
        jailedPlayers.put(playerId, jailData);
        
        // Execute CMI jail command
        if (cmiAvailable) {
            executeCMIJailCommand(player, selectedJail, minutes, reason, arrestingGuard);
        } else {
            // Fallback jail method
            executeFallbackJail(player, minutes, reason);
        }

        // Remove contraband/drugs, preserve regular inventory
        removeContrabandAndDrugs(player);

        // Handle wanted level system (clear wanted level and marks)
        plugin.getWantedLevelManager().handlePlayerJail(player);

        // Simple broadcast
        broadcastJailEvent(player, selectedJail, minutes, reason, wantedLevel);

        // Start tracking timer
        startJailTrackingTimer(playerId, durationSeconds);
        
        saveJailData();

        // Award bonus to arresting guard
        if (arrestingGuard != null && plugin.getDutyManager().isOnDuty(arrestingGuard.getUniqueId())) {
            plugin.getGuardProgressionManager().recordArrest(arrestingGuard);
            
            // Simple bonus calculation
            int bonusMinutes = 1 + wantedLevel;
            plugin.getDutyManager().addOffDutyMinutes(arrestingGuard.getUniqueId(), bonusMinutes);
            
            // Notify guard
            arrestingGuard.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<green>Arrest bonus: " + bonusMinutes + " off-duty minutes!</green>")));
        }
        
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("Jailed " + player.getName() + " in " + selectedJail + 
                " for " + minutes + " minutes: " + reason);
        }
    }
    
    /**
     * Execute CMI jail command
     */
    private void executeCMIJailCommand(Player player, String jailName, double minutes, String reason, Player guard) {
        String jailCommand = String.format("cmi jail %s %s %.1fm %s", 
            player.getName(), jailName, minutes, reason);
        
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                boolean result = plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), jailCommand);
                if (result) {
                    plugin.getLogger().info("Successfully executed CMI jail command: " + jailCommand);
                    
                    // Send confirmation to guard
                    if (guard != null) {
                        guard.sendMessage(MessageUtils.getPrefix(plugin).append(
                            MessageUtils.parseMessage("<green>" + player.getName() + " has been jailed for " + 
                                minutes + " minutes!</green>")));
                    }
                } else {
                    plugin.getLogger().warning("CMI jail command failed: " + jailCommand);
                    // Fallback to basic jail
                    executeFallbackJail(player, minutes, reason);
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Error executing CMI jail command: " + e.getMessage());
                executeFallbackJail(player, minutes, reason);
            }
        });
    }
    
    /**
     * Fallback jail method when CMI is not available
     */
    private void executeFallbackJail(Player player, double minutes, String reason) {
        player.sendMessage(MessageUtils.getPrefix(plugin).append(
            MessageUtils.parseMessage("<red>You have been jailed for " + minutes + " minutes. Reason: " + reason + "</red>")));
    }
    
    /**
     * Broadcast jail event
     */
    private void broadcastJailEvent(Player player, String jailName, double minutes, String reason, int wantedLevel) {
        if (!plugin.getConfig().getBoolean("jail.broadcast-messages", true)) {
            return;
        }
        
        String stars = wantedLevel > 0 ? " [" + "★".repeat(wantedLevel) + "]" : "";
        
        Component message = MessageUtils.parseMessage(
            "<red>🚨 " + player.getName() + stars + " has been jailed for " + 
            String.format("%.1f", minutes) + " minutes</red>");
        
        // Broadcast to all players
        Bukkit.broadcast(message);
    }
    
    /**
     * Start jail tracking timer
     */
    private void startJailTrackingTimer(UUID playerId, int durationSeconds) {
        // Cancel existing task
        if (jailTasks.containsKey(playerId)) {
            jailTasks.get(playerId).cancel();
        }
        
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // Clean up our tracking when jail time expires
            jailTasks.remove(playerId);
            jailedPlayers.remove(playerId);
            saveJailData();
            
            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().info("Jail tracking timer expired for player " + playerId);
            }
        }, durationSeconds * 20L);
        
        jailTasks.put(playerId, task);
    }
    
    /**
     * Queue jail for offline player - simplified signature
     */
    public void jailOfflinePlayer(UUID playerId, double minutes, String reason) {
        jailOfflinePlayer(playerId, minutes, reason, null);
    }
    
    /**
     * Queue jail for offline player with arresting guard
     */
    public void jailOfflinePlayer(UUID playerId, double minutes, String reason, Player arrestingGuard) {
        // Emergency shutdown check
        if (EdenCorrections.isEmergencyShutdown()) {
            plugin.getLogger().warning("Offline jail operation blocked due to emergency shutdown");
            return;
        }

        // Store jail data for when they come online
        int durationSeconds = (int) (minutes * 60);
        
        // Determine jail based on wanted level
        int wantedLevel = plugin.getWantedLevelManager().getWantedLevel(playerId);
        String selectedJail = determineJail(wantedLevel);
        
        JailData jailData = new JailData(System.currentTimeMillis(), durationSeconds, reason, selectedJail,
            arrestingGuard != null ? arrestingGuard.getUniqueId() : null);
        jailedPlayers.put(playerId, jailData);
        
        // Add to offline jail queue
        addToOfflineJailQueue(playerId);
        
        // Notify arresting guard
        if (arrestingGuard != null) {
            arrestingGuard.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<yellow>Player queued for jail when they come online (" + 
                    minutes + " minutes)</yellow>")));
        }
        
        plugin.getLogger().info("Queued offline jail for player " + playerId + ": " + minutes + " minutes - " + reason);
    }
    
    /**
     * Call this on player join to handle offline jails
     */
    public void handlePlayerJoin(Player player) {
        // Emergency shutdown check
        if (EdenCorrections.isEmergencyShutdown()) {
            return;
        }

        UUID playerId = player.getUniqueId();
        
        // Check if player was in offline jail queue
        if (offlineJailQueue.contains(playerId)) {
            // Execute the pending jail
            JailData jailData = jailedPlayers.get(playerId);
            if (jailData != null) {
                double minutes = jailData.durationSeconds / 60.0;
                
                // Get arresting guard if still online
                Player arrestingGuard = jailData.arrestingGuard != null ? 
                    Bukkit.getPlayer(jailData.arrestingGuard) : null;
                
                jailPlayer(player, minutes, jailData.reason, arrestingGuard);
            }
        }
    }
    
    // Data management methods
    private void loadJailData() {
        if (sqliteStorage == null) {
            plugin.getLogger().info("JailManager: SQLiteStorage not available, skipping jail data loading");
            return;
        }
        
        try {
            Map<UUID, JailData> loaded = sqliteStorage.loadJailData();
            jailedPlayers.clear();
            jailedPlayers.putAll(loaded);
            
            // Restore tracking
            for (Map.Entry<UUID, JailData> entry : jailedPlayers.entrySet()) {
                UUID playerId = entry.getKey();
                JailData data = entry.getValue();
                
                long endTime = data.startTime + (data.durationSeconds * 1000L);
                if (System.currentTimeMillis() < endTime) {
                    long remainingTime = endTime - System.currentTimeMillis();
                    startJailTrackingTimer(playerId, (int) (remainingTime / 1000));
                }
            }
            
            // Load offline queue
            offlineJailQueue.clear();
            offlineJailQueue.addAll(sqliteStorage.loadOfflineJailQueue());
            
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load jail data: " + e.getMessage());
        }
    }

    private void saveJailData() {
        if (sqliteStorage == null) {
            return;
        }
        
        try {
            sqliteStorage.saveJailData(jailedPlayers);
            sqliteStorage.saveOfflineJailQueue(offlineJailQueue);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to save jail data: " + e.getMessage());
        }
    }

    public void addToOfflineJailQueue(UUID playerId) {
        offlineJailQueue.add(playerId);
        saveJailData();
    }

    public void removeFromOfflineJailQueue(UUID playerId) {
        offlineJailQueue.remove(playerId);
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
     * Check if CMI integration is available
     */
    public boolean isCMIAvailable() {
        return cmiAvailable;
    }

    /**
     * Simple jail data class
     */
    public static class JailData {
        public final long startTime;
        public final int durationSeconds;
        public final String reason;
        public final String jailLocation;
        public final UUID arrestingGuard;

        public JailData(long startTime, int durationSeconds, String reason) {
            this(startTime, durationSeconds, reason, null, null);
        }
        
        public JailData(long startTime, int durationSeconds, String reason, String jailLocation, UUID arrestingGuard) {
            this.startTime = startTime;
            this.durationSeconds = durationSeconds;
            this.reason = reason;
            this.jailLocation = jailLocation;
            this.arrestingGuard = arrestingGuard;
        }
    }

    // Call this method when the plugin is disabled
    public void shutdown() {
        saveJailData();
        
        // Cancel all jail tasks
        for (BukkitTask task : jailTasks.values()) {
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
        }
        jailTasks.clear();
    }

    /**
     * Reload jail configurations
     */
    public void reload() {
        // Cancel all active jail tasks
        for (BukkitTask task : jailTasks.values()) {
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
        }
        jailTasks.clear();

        // Save current data before reloading
        saveJailData();
        
        // Re-initialize CMI integration
        initializeCMIIntegration();

        plugin.getLogger().info("JailManager reloaded - cleared all active tasks");
    }
} 