package dev.lsdmc.edencorrections.managers;

import dev.lsdmc.edencorrections.EdenCorrections;
import dev.lsdmc.edencorrections.config.ConfigManager;
import dev.lsdmc.edencorrections.utils.MessageUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages guard death penalties
 * Updated to use centralized configuration management
 */
public class GuardPenaltyManager {
    private final EdenCorrections plugin;
    private final ConfigManager configManager;
    private ConfigManager.GuardPenaltyConfig config;

    // Track players with active penalties
    private final Map<UUID, Integer> lockedPlayers = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> penaltyTasks = new ConcurrentHashMap<>();

    public GuardPenaltyManager(EdenCorrections plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        loadConfig();

        // Start penalty tick task
        startPenaltyTask();
    }

    private void loadConfig() {
        this.config = configManager.getGuardPenaltyConfig();

        if (configManager.isDebugEnabled()) {
            plugin.getLogger().info("Guard death penalties " + (config.enabled ? "enabled" : "disabled"));
            if (config.enabled) {
                plugin.getLogger().info("Lock time: " + config.lockTime + " seconds");
                plugin.getLogger().info("Restricted regions: " + (config.restrictedRegions != null ? config.restrictedRegions.size() : 0));
            }
        }
    }

    /**
     * Start the task that decrements penalty times
     */
    private void startPenaltyTask() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            // Decrement all penalties by 1
            for (UUID playerId : new ArrayList<>(lockedPlayers.keySet())) {
                int current = lockedPlayers.get(playerId);
                if (current <= 1) {
                    lockedPlayers.remove(playerId);

                    // Notify player if online
                    Player player = Bukkit.getPlayer(playerId);
                    if (player != null && player.isOnline()) {
                        Component message = MessageUtils.parseMessage("<green>You can now leave the guard area!</green>");
                        player.sendMessage(message);

                        if (configManager.isDebugEnabled()) {
                            plugin.getLogger().info("Death penalty expired for " + player.getName());
                        }
                    }
                } else {
                    lockedPlayers.put(playerId, current - 1);
                }
            }
        }, 20L, 20L); // Run every second
    }

    /**
     * Handle a guard death
     * @param player The guard who died
     */
    public void handleGuardDeath(Player player) {
        if (!config.enabled) return;

        // Apply penalty
        UUID playerId = player.getUniqueId();
        setPlayerLockTime(playerId, config.lockTime);

        // Notify player
        String message = config.message.replace("{time}", String.valueOf(config.lockTime));
        Component component = MessageUtils.parseMessage(message);
        player.sendMessage(component);

        if (configManager.isDebugEnabled()) {
            plugin.getLogger().info("Applied death penalty to " + player.getName() + " for " + config.lockTime + " seconds");
        }
    }

    /**
     * Apply a death penalty to a player
     * @param player The player to apply the penalty to
     */
    public void applyDeathPenalty(Player player) {
        if (!config.enabled) return;

        UUID playerId = player.getUniqueId();
        setPlayerLockTime(playerId, config.lockTime);

        // Notify player
        String message = config.message.replace("{time}", String.valueOf(config.lockTime));
        Component component = MessageUtils.parseMessage(message);
        player.sendMessage(component);

        if (configManager.isDebugEnabled()) {
            plugin.getLogger().info("Applied death penalty to " + player.getName() + " for " + config.lockTime + " seconds");
        }
    }

    /**
     * Check if a player is locked (has an active penalty)
     * @param playerId The player's UUID
     * @return True if the player is locked
     */
    public boolean isPlayerLocked(UUID playerId) {
        return lockedPlayers.containsKey(playerId) && lockedPlayers.get(playerId) > 0;
    }

    /**
     * Check if a player has an active penalty
     * @param playerId The player's UUID
     * @return True if the player has an active penalty
     */
    public boolean hasActivePenalty(UUID playerId) {
        return isPlayerLocked(playerId);
    }

    /**
     * Set a player's lock time
     * @param playerId The player's UUID
     * @param seconds Lock time in seconds
     */
    public void setPlayerLockTime(UUID playerId, int seconds) {
        lockedPlayers.put(playerId, seconds);
    }

    /**
     * Get a player's remaining lock time
     * @param playerId The player's UUID
     * @return Remaining lock time in seconds, 0 if not locked
     */
    public int getPlayerLockTime(UUID playerId) {
        return lockedPlayers.getOrDefault(playerId, 0);
    }

    /**
     * Get the remaining penalty time for a player
     * @param playerId The player's UUID
     * @return The remaining penalty time in seconds
     */
    public int getRemainingPenaltyTime(UUID playerId) {
        return getPlayerLockTime(playerId);
    }

    /**
     * Clear a player's lock time
     * @param playerId The player's UUID
     */
    public void clearPlayerLockTime(UUID playerId) {
        boolean hadPenalty = lockedPlayers.remove(playerId) != null;

        if (hadPenalty && configManager.isDebugEnabled()) {
            Player player = Bukkit.getPlayer(playerId);
            String playerName = player != null ? player.getName() : playerId.toString();
            plugin.getLogger().info("Cleared death penalty for " + playerName);
        }
    }

    /**
     * Check if a region is restricted for locked players
     * @param regionName The region name
     * @return True if the region is restricted
     */
    public boolean isRestrictedRegion(String regionName) {
        if (config.restrictedRegions == null) return false;
        return config.restrictedRegions.contains(regionName);
    }

    /**
     * Get the set of restricted regions
     * @return Set of restricted regions
     */
    public Set<String> getRestrictedRegions() {
        if (config.restrictedRegions == null) return new HashSet<>();
        return new HashSet<>(config.restrictedRegions);
    }

    /**
     * Handle a player trying to exit a restricted region while locked
     * @param player The player
     */
    public void handleRestrictedRegionExit(Player player) {
        int timeLeft = getPlayerLockTime(player.getUniqueId());
        String message = config.message.replace("{time}", String.valueOf(timeLeft));

        Component component = MessageUtils.parseMessage(message);
        player.sendMessage(component);

        if (configManager.isDebugEnabled()) {
            plugin.getLogger().info("Blocked " + player.getName() + " from leaving restricted region (penalty: " + timeLeft + "s)");
        }
    }

    /**
     * Get the configured lock time
     * @return Lock time in seconds
     */
    public int getConfiguredLockTime() {
        return config.lockTime;
    }

    /**
     * Get all players with active penalties
     * @return Map of player UUIDs to remaining penalty time
     */
    public Map<UUID, Integer> getActivePenalties() {
        return new ConcurrentHashMap<>(lockedPlayers);
    }

    /**
     * Check how many players currently have active penalties
     * @return Number of players with active penalties
     */
    public int getActivePenaltyCount() {
        return lockedPlayers.size();
    }

    /**
     * Reload the manager configuration
     */
    public void reload() {
        loadConfig();
        plugin.getLogger().info("GuardPenaltyManager reloaded successfully");
    }

    /**
     * Get whether penalties are enabled
     * @return True if enabled
     */
    public boolean arePenaltiesEnabled() {
        return config.enabled;
    }

    /**
     * Shutdown the manager and clear all tasks
     */
    public void shutdown() {
        // Cancel all penalty tasks
        for (BukkitTask task : penaltyTasks.values()) {
            task.cancel();
        }
        penaltyTasks.clear();

        if (configManager.isDebugEnabled()) {
            plugin.getLogger().info("GuardPenaltyManager shutdown");
        }
    }
}