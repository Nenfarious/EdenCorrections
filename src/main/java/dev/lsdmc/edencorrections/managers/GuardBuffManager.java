package dev.lsdmc.edencorrections.managers;

import dev.lsdmc.edencorrections.EdenCorrections;
import dev.lsdmc.edencorrections.config.ConfigManager;
import dev.lsdmc.edencorrections.utils.MessageUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages guard buff system, particularly the lone guard buff feature
 * Updated to use centralized configuration management and only count on-duty guards
 */
public class GuardBuffManager {
    private final EdenCorrections plugin;
    private final ConfigManager configManager;
    private ConfigManager.GuardBuffConfig config;

    private int onlineGuardCount = 0;
    private final Map<String, String> effectsConfig = new HashMap<>();
    private final List<BuffedGuard> buffedGuards = new ArrayList<>();
    private final Map<UUID, BukkitTask> removalTasks = new ConcurrentHashMap<>();

    public GuardBuffManager(EdenCorrections plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        loadConfig();

        // Initialize guard count on startup
        recalculateOnlineGuards();
    }

    private void loadConfig() {
        this.config = configManager.getGuardBuffConfig();

        // Clear previous settings
        effectsConfig.clear();

        // Only process the rest if the feature is enabled
        if (config.enabled && config.loneGuardEnabled) {
            // Load effects
            for (String effectString : config.loneGuardEffects) {
                String[] parts = effectString.split(":");
                if (parts.length >= 2) {
                    String effectType = parts[0];
                    String effectDetails = effectString.substring(effectType.length() + 1);
                    effectsConfig.put(effectType, effectDetails);
                }
            }
        }
    }

    /**
     * Recalculate the number of online guards
     */
    public void recalculateOnlineGuards() {
        if (!config.enabled) return;

        int count = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isPlayerOnDutyGuard(player)) {
                count++;
            }
        }

        // Update count and handle buffs
        setOnlineGuardCount(count);
    }

    /**
     * Recalculate lone guard status
     * This is called when a guard dies or goes off duty to check if there's only one guard left
     */
    public void recalculateLoneGuardStatus() {
        recalculateOnlineGuards();
    }

    /**
     * Set the number of online guards and handle buffs accordingly
     * @param count New count of online guards
     */
    public void setOnlineGuardCount(int count) {
        int oldCount = onlineGuardCount;
        onlineGuardCount = count;

        // Handle buff logic
        if (oldCount > 1 && count == 1) {
            // We went from multiple guards to one guard - apply buff
            applyLoneGuardBuff();
        } else if (oldCount == 1 && count > 1) {
            // We went from one guard to multiple guards - schedule buff removal
            scheduleLoneGuardBuffRemoval();
        } else if (count == 0) {
            // No guards on duty - remove all buffs immediately
            removeAllBuffs();
        }
    }

    /**
     * Apply buffs to the lone guard
     */
    private void applyLoneGuardBuff() {
        if (!config.enabled) return;

        Player loneGuard = null;

        // Find the lone guard who is on duty
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isPlayerOnDutyGuard(player)) {
                loneGuard = player;
                break;
            }
        }

        if (loneGuard != null) {
            // Cancel any pending removal tasks
            UUID playerId = loneGuard.getUniqueId();
            if (removalTasks.containsKey(playerId) && removalTasks.get(playerId) != null) {
                removalTasks.get(playerId).cancel();
            }

            // Apply effects
            BuffedGuard buffedGuard = new BuffedGuard(loneGuard.getUniqueId());

            for (Map.Entry<String, String> entry : effectsConfig.entrySet()) {
                try {
                    PotionEffectType effectType = PotionEffectType.getByName(entry.getKey());
                    if (effectType != null) {
                        String[] details = entry.getValue().split(":");

                        int amplifier = 0;
                        if (details.length >= 1) {
                            amplifier = Integer.parseInt(details[0]);
                        }

                        int duration = 999999; // Default to a very long time
                        if (details.length >= 2 && !details[1].equalsIgnoreCase("infinite")) {
                            duration = Integer.parseInt(details[1]) * 20; // Convert to ticks
                        }

                        PotionEffect effect = new PotionEffect(effectType, duration, amplifier, false, true, true);
                        loneGuard.addPotionEffect(effect);
                        buffedGuard.addEffect(effect);
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Invalid effect configuration: " + entry.getKey() + ":" + entry.getValue());
                }
            }

            // Add to buffed guards list
            buffedGuards.add(buffedGuard);

            // Send message
            Component message = MessageUtils.parseMessage(config.applyMessage);
            loneGuard.sendMessage(message);
        }
    }

    /**
     * Remove all active buffs immediately
     */
    private void removeAllBuffs() {
        for (BuffedGuard buffedGuard : new ArrayList<>(buffedGuards)) {
            removeBuffsFromPlayer(buffedGuard.getPlayerId());
        }
        buffedGuards.clear();
        removalTasks.values().forEach(BukkitTask::cancel);
        removalTasks.clear();
    }

    /**
     * Schedule the removal of buffs from the lone guard
     */
    private void scheduleLoneGuardBuffRemoval() {
        if (!config.enabled || buffedGuards.isEmpty()) return;

        // Find all buffed guards and schedule removal
        for (BuffedGuard buffedGuard : new ArrayList<>(buffedGuards)) {
            Player player = Bukkit.getPlayer(buffedGuard.getPlayerId());
            if (player != null && player.isOnline()) {
                // Send warning message
                String warningMsg = config.removeWarningMessage.replace("{seconds}", String.valueOf(config.loneGuardRemovalDelay));
                Component message = MessageUtils.parseMessage(warningMsg);
                player.sendMessage(message);

                // Schedule removal
                UUID playerId = player.getUniqueId();
                BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    removeBuffsFromPlayer(playerId);
                    removalTasks.remove(playerId);
                }, config.loneGuardRemovalDelay * 20L);

                // Store task for potential cancellation
                removalTasks.put(playerId, task);
            } else {
                // Player offline, remove from list
                buffedGuards.remove(buffedGuard);
            }
        }
    }

    /**
     * Remove buffs from a player
     * @param playerId UUID of the player
     */
    public void removeBuffsFromPlayer(UUID playerId) {
        // Find the buffed guard
        BuffedGuard buffedGuard = null;
        for (BuffedGuard bg : buffedGuards) {
            if (bg.getPlayerId().equals(playerId)) {
                buffedGuard = bg;
                break;
            }
        }

        if (buffedGuard != null) {
            // Remove effects
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                for (PotionEffect effect : buffedGuard.getEffects()) {
                    player.removePotionEffect(effect.getType());
                }

                // Send message
                Component message = MessageUtils.parseMessage(config.removedMessage);
                player.sendMessage(message);
            }

            // Remove from list
            buffedGuards.remove(buffedGuard);
        }
    }

    /**
     * Handle a guard joining the server or going on duty
     * @param player The player who joined or went on duty
     */
    public void onGuardJoin(Player player) {
        if (!config.enabled) return;

        if (isPlayerOnDutyGuard(player)) {
            setOnlineGuardCount(onlineGuardCount + 1);
        }
    }

    /**
     * Handle a guard leaving the server or going off duty
     * @param player The player who left or went off duty
     */
    public void onGuardQuit(Player player) {
        if (!config.enabled) return;

        if (isPlayerOnDutyGuard(player)) {
            setOnlineGuardCount(Math.max(0, onlineGuardCount - 1));
        }
    }

    /**
     * Check if a player is a guard and on duty
     * @param player The player to check
     * @return True if the player is a guard and on duty
     */
    public boolean isPlayerOnDutyGuard(Player player) {
        return isPlayerGuard(player) && plugin.getDutyManager().isOnDuty(player.getUniqueId());
    }

    /**
     * Check if a player has guard permissions
     * @param player The player to check
     * @return True if the player has guard permissions
     */
    public boolean isPlayerGuard(Player player) {
        return player.hasPermission("edencorrections.guard") || player.hasPermission("edencorrections.duty");
    }

    /**
     * Get the current number of online guards who are on duty
     * @return Number of online guards on duty
     */
    public int getOnlineGuardCount() {
        return onlineGuardCount;
    }

    /**
     * Reload the manager configuration
     */
    public void reload() {
        // Clear current state
        removeAllBuffs();

        // Reload config
        loadConfig();

        // Recalculate guard count
        recalculateOnlineGuards();
    }

    /**
     * Class to track buffed guards and their effects
     */
    private static class BuffedGuard {
        private final UUID playerId;
        private final List<PotionEffect> effects = new ArrayList<>();

        public BuffedGuard(UUID playerId) {
            this.playerId = playerId;
        }

        public UUID getPlayerId() {
            return playerId;
        }

        public void addEffect(PotionEffect effect) {
            effects.add(effect);
        }

        public List<PotionEffect> getEffects() {
            return effects;
        }
    }
}