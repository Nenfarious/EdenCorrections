package dev.lsdmc.edencorrections.listeners;

import dev.lsdmc.edencorrections.EdenCorrections;
import dev.lsdmc.edencorrections.utils.MessageUtils;
import dev.lsdmc.edencorrections.utils.RegionUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public class ChaseListener implements Listener {
    private final EdenCorrections plugin;

    public ChaseListener(EdenCorrections plugin) {
        this.plugin = plugin;
    }

    /**
     * Handle command restrictions during chase
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        
        // Only check if player is being chased
        if (!plugin.getChaseManager().isBeingChased(player)) {
            return;
        }

        String command = event.getMessage().toLowerCase();
        
        // Remove leading slash and get base command
        String baseCommand = command.substring(1).split(" ")[0];
        
        // Check if command is restricted
        if (plugin.getChaseManager().isCommandRestricted(baseCommand)) {
            event.setCancelled(true);
            player.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>⚠️ You cannot use this command while being chased!</red>")));
        }
    }

    /**
     * Handle player movement during chase
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        
        // Check if this player is involved in a chase
        if (plugin.getChaseManager().isBeingChased(player)) {
            handleChasedPlayerMovement(player, event);
        } else if (plugin.getChaseManager().isGuardChasing(player)) {
            handleChasingGuardMovement(player, event);
        }
    }

    private void handleChasedPlayerMovement(Player player, PlayerMoveEvent event) {
        // Check if player entered a restricted region
        if (event.getTo() != null) {
            String region = RegionUtils.getHighestPriorityRegion(event.getTo());
            if (region != null && plugin.getChaseManager().isRegionRestricted(region)) {
                // Cancel movement
                event.setCancelled(true);
                player.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>⚠️ You cannot enter this area while being chased!</red>")));
                return;
            }
        }
    }

    private void handleChasingGuardMovement(Player guard, PlayerMoveEvent event) {
        // Check if guard is getting too far from target
        Player target = plugin.getChaseManager().getChaseTarget(guard);
        if (target != null && target.isOnline()) {
            double distance = guard.getLocation().distance(target.getLocation());
            double maxDistance = plugin.getConfig().getDouble("chase.max-distance", 100.0);
            
            if (distance > maxDistance) {
                // Warn the guard
                guard.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<yellow>⚠️ You are getting far from your chase target! (" + Math.round(distance) + " blocks away)</yellow>")));
            }
        }
    }

    /**
     * Handle teleportation during chase
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        
        // Prevent teleportation if being chased (except for admin teleports)
        if (plugin.getChaseManager().isBeingChased(player)) {
            // Allow certain teleport causes (like respawn, plugin teleports)
            PlayerTeleportEvent.TeleportCause cause = event.getCause();
            if (cause == PlayerTeleportEvent.TeleportCause.COMMAND ||
                cause == PlayerTeleportEvent.TeleportCause.PLUGIN ||
                cause == PlayerTeleportEvent.TeleportCause.UNKNOWN) {
                
                // Check if destination is in a restricted region
                if (event.getTo() != null) {
                    String region = RegionUtils.getHighestPriorityRegion(event.getTo());
                    if (region != null && plugin.getChaseManager().isRegionRestricted(region)) {
                        event.setCancelled(true);
                        player.sendMessage(MessageUtils.getPrefix(plugin).append(
                            MessageUtils.parseMessage("<red>⚠️ You cannot teleport to this area while being chased!</red>")));
                        return;
                    }
                }
            }
        }
    }

    /**
     * Handle player quit during chase
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        
        // End chase if player leaves
        if (plugin.getChaseManager().isBeingChased(player)) {
            plugin.getChaseManager().endChase(player, true);
            
            // Notify the chasing guard
            Player guard = plugin.getChaseManager().getChasingGuard(player);
            if (guard != null && guard.isOnline()) {
                guard.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<yellow>Chase target " + player.getName() + " has left the server.</yellow>")));
            }
        } else if (plugin.getChaseManager().isGuardChasing(player)) {
            Player target = plugin.getChaseManager().getChaseTarget(player);
            plugin.getChaseManager().endChase(target, true);
            
            // Notify the target
            if (target != null && target.isOnline()) {
                target.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<green>The guard chasing you has left the server. Chase ended.</green>")));
            }
        }
    }
} 