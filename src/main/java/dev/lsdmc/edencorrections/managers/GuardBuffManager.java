package dev.lsdmc.edencorrections.managers;

import dev.lsdmc.edencorrections.EdenCorrections;
import dev.lsdmc.edencorrections.listeners.GuardBuffListener;
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
 */
public class GuardBuffManager {
    private final EdenCorrections plugin;
    private int onlineGuardCount = 0;
    private boolean buffEnabled;
    private final Map<String, String> effectsConfig = new HashMap<>();
    private final List<BuffedGuard> buffedGuards = new ArrayList<>();
    private final Map<UUID, BukkitTask> removalTasks = new ConcurrentHashMap<>();

    // Messages
    private String applyMessage;
    private String removeWarningMessage;
    private String removedMessage;
    private int removalDelay;

    public GuardBuffManager(EdenCorrections plugin) {
        this.plugin = plugin;
        loadConfig();

        // Initialize guard count on startup
        recalculateOnlineGuards();

        // Register this manager with the plugin
        plugin.getServer().getPluginManager().registerEvents(new GuardBuffListener(this, plugin), plugin);
    }

    private void loadConfig() {
        buffEnabled = plugin.getConfig().getBoolean("guard-buff.enabled", true);
        boolean loneGuardEnabled = plugin.getConfig().getBoolean("guard-buff.lone-guard.enabled", true);

        // Only process the rest if the feature is enabled
        if (buffEnabled && loneGuardEnabled) {
            // Load effects
            List<String> effects = plugin.getConfig().getStringList("guard-buff.lone-guard.effects");
            for (String effectString : effects) {
                String[] parts = effectString.split(":");
                if (parts.length >= 2) {
                    String effectType = parts[0];
                    String effectDetails = effectString.substring(effectType.length() + 1);
                    effectsConfig.put(effectType, effectDetails);
                }
            }

            // Load messages
            applyMessage = plugin.getConfig().getString("guard-buff.lone-guard.messages.apply",
                    "&8[&4&l𝕏&8] &cYou are the only guard online! You now have special protection!");

            removeWarningMessage = plugin.getConfig().getString("guard-buff.lone-guard.messages.remove-warning",
                    "&8[&4&l𝕏&8] &cAnother guard has logged in! Removing effects in {seconds} seconds!");

            removedMessage = plugin.getConfig().getString("guard-buff.lone-guard.messages.removed",
                    "&8[&4&l𝕏&8] &cYour special effects have been removed!");

            removalDelay = plugin.getConfig().getInt("guard-buff.lone-guard.removal-delay", 10);
        }
    }

    /**
     * Recalculate the number of online guards
     */
    public void recalculateOnlineGuards() {
        if (!buffEnabled) return;

        int count = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isPlayerGuard(player)) {
                count++;
            }
        }

        // Update count and handle buffs
        setOnlineGuardCount(count);
    }

    /**
     * Recalculate lone guard status
     * This is called when a guard dies to check if there's only one guard left
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
        }
    }

    /**
     * Apply buffs to the lone guard
     */
    private void applyLoneGuardBuff() {
        if (!buffEnabled) return;

        Player loneGuard = null;

        // Find the lone guard
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isPlayerGuard(player)) {
                loneGuard = player;
                break;
            }
        }

        if (loneGuard != null) {
            // Cancel any pending removal tasks
            UUID playerId = loneGuard.getUniqueId();
            if (removalTasks.containsKey(playerId)) {
                removalTasks.get(playerId).cancel();
                removalTasks.remove(playerId);
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
            Component message = MessageUtils.parseMessage(applyMessage);
            loneGuard.sendMessage(message);
        }
    }

    /**
     * Schedule the removal of buffs from the lone guard
     */
    private void scheduleLoneGuardBuffRemoval() {
        if (!buffEnabled || buffedGuards.isEmpty()) return;

        // Find all buffed guards and schedule removal
        for (BuffedGuard buffedGuard : new ArrayList<>(buffedGuards)) {
            Player player = Bukkit.getPlayer(buffedGuard.getPlayerId());
            if (player != null && player.isOnline()) {
                // Send warning message
                String warningMsg = removeWarningMessage.replace("{seconds}", String.valueOf(removalDelay));
                Component message = MessageUtils.parseMessage(warningMsg);
                player.sendMessage(message);

                // Schedule removal
                UUID playerId = player.getUniqueId();
                BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    removeBuffsFromPlayer(playerId);
                    removalTasks.remove(playerId);
                }, removalDelay * 20L);

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
                Component message = MessageUtils.parseMessage(removedMessage);
                player.sendMessage(message);
            }

            // Remove from list
            buffedGuards.remove(buffedGuard);
        }
    }

    /**
     * Handle a guard joining the server
     * @param player The player who joined
     */
    public void onGuardJoin(Player player) {
        if (!buffEnabled) return;

        setOnlineGuardCount(onlineGuardCount + 1);
    }

    /**
     * Handle a guard leaving the server
     * @param player The player who left
     */
    public void onGuardQuit(Player player) {
        if (!buffEnabled) return;

        setOnlineGuardCount(Math.max(0, onlineGuardCount - 1));
    }

    /**
     * Check if a player is a guard
     * @param player The player to check
     * @return True if the player is a guard
     */
    public boolean isPlayerGuard(Player player) {
        return player.hasPermission("edenprison.guard") || player.hasPermission("edencorrections.duty");
    }

    /**
     * Get the current number of online guards
     * @return Number of online guards
     */
    public int getOnlineGuardCount() {
        return onlineGuardCount;
    }

    /**
     * Reload the manager configuration
     */
    public void reload() {
        // Clear current state
        for (BuffedGuard buffedGuard : new ArrayList<>(buffedGuards)) {
            removeBuffsFromPlayer(buffedGuard.getPlayerId());
        }

        for (BukkitTask task : removalTasks.values()) {
            task.cancel();
        }
        removalTasks.clear();

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