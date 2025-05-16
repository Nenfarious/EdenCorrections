package dev.lsdmc.edencorrections.managers;

import dev.lsdmc.edencorrections.EdenCorrections;
import dev.lsdmc.edencorrections.listeners.GuardPenaltyListener;
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
 */
public class GuardPenaltyManager {
    private final EdenCorrections plugin;
    private boolean penaltiesEnabled;
    private int lockTime;
    private final Set<String> restrictedRegions = new HashSet<>();
    private String restrictionMessage;

    // Track players with active penalties
    private final Map<UUID, Integer> lockedPlayers = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> penaltyTasks = new ConcurrentHashMap<>();

    public GuardPenaltyManager(EdenCorrections plugin) {
        this.plugin = plugin;
        loadConfig();

        // Register this manager with the plugin
        plugin.getServer().getPluginManager().registerEvents(new GuardPenaltyListener(this, plugin), plugin);

        // Start penalty tick task
        startPenaltyTask();
    }

    private void loadConfig() {
        penaltiesEnabled = plugin.getConfig().getBoolean("guard-death-penalties.enabled", true);

        // Only process the rest if the feature is enabled
        if (penaltiesEnabled) {
            lockTime = plugin.getConfig().getInt("guard-death-penalties.lock-time", 60);

            // Load restricted regions
            restrictedRegions.clear();
            List<String> regions = plugin.getConfig().getStringList("guard-death-penalties.restricted-regions");
            restrictedRegions.addAll(regions);

            // Load message
            restrictionMessage = plugin.getConfig().getString("guard-death-penalties.message",
                    "&8[&4&l𝕏&8] &7You cannot leave for &c{time} seconds &7for dying!");

            // If no regions are defined, add a default
            if (restrictedRegions.isEmpty()) {
                restrictedRegions.add("guardeath");
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
                        Component message = MessageUtils.parseMessage("&8[&4&l𝕏&8] &aYou can now leave the guard area!");
                        player.sendMessage(message);
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
        if (!penaltiesEnabled) return;

        // Apply penalty
        UUID playerId = player.getUniqueId();
        setPlayerLockTime(playerId, lockTime);

        // Notify player
        Component message = MessageUtils.parseMessage(
                "&8[&4&l𝕏&8] &7You have been locked in the guard area for &c" + lockTime + " seconds &7due to dying!");
        player.sendMessage(message);
    }

    /**
     * Apply a death penalty to a player
     * @param player The player to apply the penalty to
     */
    public void applyDeathPenalty(Player player) {
        if (!penaltiesEnabled) return;

        UUID playerId = player.getUniqueId();
        setPlayerLockTime(playerId, lockTime);

        // Notify player
        String message = restrictionMessage.replace("{time}", String.valueOf(lockTime));
        player.sendMessage(MessageUtils.parseMessage(message));
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
        lockedPlayers.remove(playerId);
    }

    /**
     * Check if a region is restricted for locked players
     * @param regionName The region name
     * @return True if the region is restricted
     */
    public boolean isRestrictedRegion(String regionName) {
        return restrictedRegions.contains(regionName);
    }

    /**
     * Get the set of restricted regions
     * @return Set of restricted regions
     */
    public Set<String> getRestrictedRegions() {
        return new HashSet<>(restrictedRegions);
    }

    /**
     * Handle a player trying to exit a restricted region while locked
     * @param player The player
     */
    public void handleRestrictedRegionExit(Player player) {
        int timeLeft = getPlayerLockTime(player.getUniqueId());
        String message = restrictionMessage.replace("{time}", String.valueOf(timeLeft));

        Component component = MessageUtils.parseMessage(message);
        player.sendMessage(component);
    }

    /**
     * Reload the manager configuration
     */
    public void reload() {
        loadConfig();
    }

    /**
     * Get whether penalties are enabled
     * @return True if enabled
     */
    public boolean arePenaltiesEnabled() {
        return penaltiesEnabled;
    }
}