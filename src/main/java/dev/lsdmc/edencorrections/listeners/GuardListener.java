package dev.lsdmc.edencorrections.listeners;

import dev.lsdmc.edencorrections.EdenCorrections;
import dev.lsdmc.edencorrections.utils.MessageUtils;
import dev.lsdmc.edencorrections.utils.RegionUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class GuardListener implements Listener {
    private final EdenCorrections plugin;
    private final Map<UUID, Long> lastMessageTime = new ConcurrentHashMap<>();
    private static final long MESSAGE_COOLDOWN = 1500; // 1.5 seconds

    public GuardListener(EdenCorrections plugin) {
        this.plugin = plugin;
    }

    // Death Events
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        UUID victimId = victim.getUniqueId();

        // Handle wanted level system (remove marks, etc.)
        plugin.getWantedLevelManager().handlePlayerDeath(victim);

        // Check if victim is a guard on duty
        if (!plugin.getDutyManager().isOnDuty(victimId)) {
            return;
        }

        // Process guard death
        plugin.getGuardLootManager().handleGuardDeath(victim, victim.getKiller());

        // Apply death penalties if enabled
        if (plugin.getConfig().getBoolean("guard-death-penalties.enabled", true)) {
            plugin.getGuardPenaltyManager().applyDeathPenalty(victim);
        }

        // Recalculate lone guard status
        plugin.getGuardBuffManager().recalculateLoneGuardStatus();
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Check if player has active death penalty
        if (plugin.getDutyManager().isOnDuty(playerId) &&
                plugin.getGuardPenaltyManager().hasActivePenalty(playerId)) {
            // Schedule reminder message
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;

                int remainingSeconds = plugin.getGuardPenaltyManager().getRemainingPenaltyTime(playerId);
                if (remainingSeconds <= 0) return;

                String message = plugin.getConfig().getString("guard-death-penalties.message",
                        "<dark_gray>[</dark_gray><dark_red><bold>𝕏</bold></dark_red><dark_gray>]</dark_gray> <gray>You cannot leave for <red>{time} seconds</red> for dying!</gray>");

                message = message.replace("{time}", String.valueOf(remainingSeconds));
                player.sendMessage(MessageUtils.parseMessage(message));
            }, 20L);
        }
    }

    // Movement Events
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        // Only check if block position changed
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
            event.getFrom().getBlockY() == event.getTo().getBlockY() &&
            event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Check if player is on duty
        if (!plugin.getDutyManager().isOnDuty(playerId)) {
            return;
        }

        // Check immobilization
        if (plugin.getDutyManager().isPlayerImmobilized(playerId)) {
            event.setCancelled(true);
            plugin.getDutyManager().sendImmobilizationReminderWithCooldown(player);
            return;
        }

        // Check chase restrictions
        if (plugin.getChaseManager().isBeingChased(player)) {
            for (String region : RegionUtils.getRegionsAtLocation(event.getTo())) {
                if (plugin.getChaseManager().isRegionRestricted(region)) {
                    event.setCancelled(true);
                    sendCooldownMessage(player, "§cYou cannot enter this area while being chased!");
                    return;
                }
            }
        }

        // Check penalty restrictions
        if (plugin.getGuardPenaltyManager().isPlayerLocked(playerId)) {
            boolean isLeavingRestricted = true;
            for (String region : plugin.getGuardPenaltyManager().getRestrictedRegions()) {
                if (RegionUtils.isLocationInRegion(event.getTo(), region)) {
                    isLeavingRestricted = false;
                    break;
                }
            }

            if (isLeavingRestricted) {
                event.setCancelled(true);
                plugin.getGuardPenaltyManager().handleRestrictedRegionExit(player);
                return;
            }
        }

        // Check general movement restrictions
        for (String region : RegionUtils.getRegionsAtLocation(event.getTo())) {
            if (plugin.getGuardRestrictionManager().isRegionRestricted(region)) {
                event.setCancelled(true);
                sendCooldownMessage(player, plugin.getGuardRestrictionManager().getMovementRestrictionMessage());
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (plugin.getDutyManager().isPlayerImmobilized(player.getUniqueId())) {
            event.setCancelled(true);
            plugin.getDutyManager().sendImmobilizationReminderWithCooldown(player);
        }
    }

    // Command Events
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();

        // Check if player is on duty
        if (!plugin.getDutyManager().isOnDuty(player.getUniqueId())) {
            return;
        }

        // Check if being chased
        if (plugin.getChaseManager().isBeingChased(player)) {
            String command = event.getMessage().substring(1).toLowerCase();
            if (plugin.getChaseManager().isCommandRestricted(command)) {
                event.setCancelled(true);
                sendCooldownMessage(player, "§cYou cannot use this command while being chased!");
                return;
            }
        }

        // Check general command restrictions
        String baseCommand = event.getMessage().substring(1).split(" ")[0].toLowerCase();
        if (plugin.getGuardRestrictionManager().isCommandRestricted(baseCommand)) {
            event.setCancelled(true);
            sendCooldownMessage(player, plugin.getGuardRestrictionManager().getCommandRestrictionMessage());
        }
    }

    // Block Events
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        // Check if player is a guard and the block is restricted
        if (plugin.getGuardRestrictionManager().isPlayerGuard(player) && 
            plugin.getGuardRestrictionManager().isBlockRestricted(event.getBlock(), player)) {
            event.setCancelled(true);
            plugin.getGuardRestrictionManager().handleRestrictedBlockBreak(player);
        }
    }

    // Join/Quit Events
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Check if player is a guard with a slight delay
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline() && plugin.getGuardBuffManager().isPlayerGuard(player)) {
                    // Only trigger join if they're on duty
                    if (plugin.getDutyManager().isOnDuty(player.getUniqueId())) {
                        plugin.getGuardBuffManager().onGuardJoin(player);
                    }
                }
            }
        }.runTaskLater(plugin, 20L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Handle guard buffs
        if (plugin.getGuardBuffManager().isPlayerGuard(player)) {
            plugin.getGuardBuffManager().onGuardQuit(player);
        }

        // Handle chase logout
        if (plugin.getChaseManager().isBeingChased(player)) {
            Player guard = plugin.getChaseManager().getChasingGuard(player);
            if (guard != null) {
                plugin.getJailManager().jailOfflinePlayer(
                    playerId,
                    plugin.getWantedLevelManager().getJailTime(
                        plugin.getWantedLevelManager().getWantedLevel(playerId)
                    ),
                    "Logged out during chase"
                );
                guard.sendMessage(MessageUtils.parseMessage("<green>" + player.getName() + " logged out during chase and will be jailed when they return.</green>"));
            }
            plugin.getChaseManager().endChase(player, true);
        }
    }

    // Combat Events
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        // Check immobilization
        if (plugin.getDutyManager().isPlayerImmobilized(attacker.getUniqueId())) {
            event.setCancelled(true);
            plugin.getDutyManager().sendImmobilizationReminderWithCooldown(attacker);
            return;
        }

        if (plugin.getDutyManager().isPlayerImmobilized(victim.getUniqueId())) {
            event.setCancelled(true);
            attacker.sendMessage("§c§lYou cannot attack a guard who is preparing for duty!");
        }
    }

    // Interaction Events
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        // Check if it's a guard item
        if (plugin.getGuardItemManager().isGuardItem(item)) {
            // Cancel the event immediately to prevent multiple triggers
            event.setCancelled(true);
            
            // Check if player is immobilized
            if (plugin.getDutyManager().isPlayerImmobilized(player.getUniqueId())) {
                plugin.getDutyManager().sendImmobilizationReminderWithCooldown(player);
                return;
            }
            
            // Add spam protection
            UUID playerId = player.getUniqueId();
            long now = System.currentTimeMillis();
            if (lastMessageTime.containsKey(playerId) && 
                now - lastMessageTime.get(playerId) < 500) { // 500ms cooldown between uses
                return; // Ignore rapid clicks
            }
            lastMessageTime.put(playerId, now);
            
            if (event.getRightClicked() instanceof Player) {
                Player target = (Player) event.getRightClicked();
                
                // Additional safety checks
                if (!target.isOnline() || target.equals(player)) {
                    return;
                }
                
                // Check if guard is on duty for guard items
                if (!plugin.getDutyManager().isOnDuty(player.getUniqueId())) {
                    sendCooldownMessage(player, "<red>You must be on duty to use guard items!</red>");
                    return;
                }
                
                boolean handled = plugin.getGuardItemManager().handleGuardItemUsage(player, target, item);
                if (!handled) {
                    sendCooldownMessage(player, "<red>This guard item cannot be used on players!</red>");
                }
            } else {
                // No target required for some guard items
                boolean handled = plugin.getGuardItemManager().handleGuardItemUsageNoTarget(player, item);
                if (!handled) {
                    sendCooldownMessage(player, "<red>This guard item requires a target! Right-click on a player.</red>");
                }
            }
        }
    }

    private void sendCooldownMessage(Player player, String message) {
        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long lastTime = lastMessageTime.get(playerId);

        if (lastTime == null || now - lastTime >= MESSAGE_COOLDOWN) {
            player.sendMessage(message);
            lastMessageTime.put(playerId, now);
        }
    }
} 