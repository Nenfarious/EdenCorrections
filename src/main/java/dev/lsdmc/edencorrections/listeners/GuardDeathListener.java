package dev.lsdmc.edencorrections.listeners;

import dev.lsdmc.edencorrections.EdenCorrections;
import dev.lsdmc.edencorrections.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Listener class that handles guard death-related events.
 * This includes processing loot drops, applying death penalties,
 * and handling cooldowns when guards die.
 */
public class GuardDeathListener implements Listener {
    private final EdenCorrections plugin;

    public GuardDeathListener(EdenCorrections plugin) {
        this.plugin = plugin;
    }

    /**
     * Handle the death of a player, including guard death processing.
     * This method manages loot drops and applies death penalties for guards.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        UUID victimId = victim.getUniqueId();

        // Check if the victim is a guard on duty
        if (!plugin.getDutyManager().isOnDuty(victimId)) {
            return;
        }

        try {
            // Get guard rank using the centralized rank manager
            String rank = plugin.getGuardRankManager().getPlayerRank(victim);
            if (rank == null) {
                plugin.getLogger().warning("Player " + victim.getName() + " has no guard rank but is on duty");
                return;
            }

            // Log for debugging
            plugin.getLogger().info("Guard death event: " + victim.getName() + " with rank " + rank);

            // Process guard death
            processGuardDeath(event, victim);

            // Apply death penalties if enabled
            if (plugin.getConfig().getBoolean("guard-death-penalties.enabled", true)) {
                plugin.getGuardPenaltyManager().applyDeathPenalty(victim);
            }

            // Recalculate lone guard status (in case this was the last guard)
            plugin.getGuardBuffManager().recalculateLoneGuardStatus();

            // Log this guard death for debugging purposes
            plugin.getLogger().info("Guard " + victim.getName() + " died and was processed by the guard death system");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error processing guard death for " + victim.getName(), e);
        }
    }

    /**
     * Process a guard's death, handling loot drops and token rewards.
     */
    private void processGuardDeath(PlayerDeathEvent event, Player victim) {
        // Check if guard loot system is enabled
        if (!plugin.getConfig().getBoolean("guard-loot.enabled", true)) {
            plugin.getLogger().info("Guard loot system is disabled, skipping loot generation for " + victim.getName());
            return;
        }

        // Check if the victim is on cooldown
        if (plugin.getGuardLootManager().isOnLootCooldown(victim.getUniqueId())) {
            // Get the killer if there is one
            Player killer = victim.getKiller();
            if (killer != null) {
                int cooldownSeconds = plugin.getGuardLootManager().getRemainingCooldown(victim.getUniqueId());
                String cooldownMessage = plugin.getConfig().getString("guard-loot.cooldown-message",
                        "<dark_gray>[</dark_gray><dark_red><bold>𝕏</bold></dark_red><dark_gray>]</dark_gray> <aqua>{victim}</aqua> <gray>has their guard loot on cooldown! </gray><aqua>({time}s)</aqua>");

                cooldownMessage = cooldownMessage
                        .replace("{victim}", victim.getName())
                        .replace("{time}", String.valueOf(cooldownSeconds));

                killer.sendMessage(MessageUtils.parseMessage(cooldownMessage));
                plugin.getLogger().info(victim.getName() + " is on loot cooldown (" + cooldownSeconds + "s), no loot generated");
            }
            return;
        }

        // Generate loot using our new loot processor
        List<ItemStack> lootItems = plugin.getGuardLootProcessor().generateLootForPlayer(victim);

        // Clear existing drops and add our generated loot
        if (!lootItems.isEmpty()) {
            event.getDrops().clear();
            event.getDrops().addAll(lootItems);
            plugin.getLogger().info("Added " + lootItems.size() + " loot items for " + victim.getName());
        } else {
            plugin.getLogger().warning("No loot generated for " + victim.getName());
        }

        // Start the loot cooldown for this guard
        plugin.getGuardLootManager().startLootCooldown(victim.getUniqueId());
        plugin.getLogger().info("Started loot cooldown for " + victim.getName());

        // Handle token rewards for the guard who died
        if (plugin.getConfig().getBoolean("guard-loot.token-reward.enabled", true)) {
            int tokenAmount = plugin.getConfig().getInt("guard-loot.token-reward.amount", 200);
            String rewardMessage = plugin.getConfig().getString("guard-loot.token-reward.message",
                    "<dark_gray>[</dark_gray><dark_red><bold>𝕏</bold></dark_red><dark_gray>]</dark_gray> <red>You fought bravely in combat and have received {tokens} tokens!</red>");

            rewardMessage = rewardMessage.replace("{tokens}", String.valueOf(tokenAmount));
            victim.sendMessage(MessageUtils.parseMessage(rewardMessage));

            // Execute the token reward command
            String command = plugin.getConfig().getString("guard-loot.token-reward.command", "tokenmanager give {player} {amount}");
            command = command
                    .replace("{player}", victim.getName())
                    .replace("{amount}", String.valueOf(tokenAmount));

            plugin.getLogger().info("Executing token reward command: " + command);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        }
    }

    /**
     * Handle player respawn event for guards with death penalties.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Check if the player is a guard on duty with an active death penalty
        if (plugin.getDutyManager().isOnDuty(playerId) &&
                plugin.getGuardPenaltyManager().hasActivePenalty(playerId)) {

            // Schedule a task to remind the player about their death penalty
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;

                int remainingSeconds = plugin.getGuardPenaltyManager().getRemainingPenaltyTime(playerId);
                if (remainingSeconds <= 0) return;

                String message = plugin.getConfig().getString("guard-death-penalties.message",
                        "<dark_gray>[</dark_gray><dark_red><bold>𝕏</bold></dark_red><dark_gray>]</dark_gray> <gray>You cannot leave for <red>{time} seconds</red> for dying!</gray>");

                message = message.replace("{time}", String.valueOf(remainingSeconds));
                player.sendMessage(MessageUtils.parseMessage(message));

                plugin.getLogger().info("Reminded " + player.getName() + " about death penalty: " + remainingSeconds + "s remaining");
            }, 20L); // 1 second delay after respawn
        }
    }
}