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
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.Location;

import java.util.List;

public class ChaseListener implements Listener {
    private final EdenCorrections plugin;

    public ChaseListener(EdenCorrections plugin) {
        this.plugin = plugin;
    }

    /**
     * Handle command restrictions for chased and wanted players
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String command = event.getMessage().toLowerCase();

        // Check if player is being chased
        if (plugin.getChaseManager().isBeingChased(player)) {
            if (plugin.getChaseManager().isCommandRestricted(command) || 
                plugin.getChaseManager().isTeleportCommand(command)) {
                
                event.setCancelled(true);
                String message = plugin.getChaseManager().getRestrictionMessage(player, "command");
                player.sendMessage(MessageUtils.parseMessage(message));
                return;
            }
        }
        
        // Check if player has wanted level restrictions
        if (plugin.getWantedLevelManager().isCommandRestricted(player, command)) {
            event.setCancelled(true);
            String message = plugin.getWantedLevelManager().getRestrictionMessage(player, "command");
            player.sendMessage(MessageUtils.parseMessage(message));
        }
    }

    /**
     * Handle teleportation restrictions for chased and wanted players
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        
        // Block ALL teleports for chased players except jail-related ones
        if (plugin.getChaseManager().isBeingChased(player)) {
            // Only allow teleports that are specifically for jail purposes
            // We'll check if this teleport is happening as part of jailing
            boolean isJailTeleport = event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN && 
                                   (event.getTo().getWorld().getName().contains("jail") || 
                                    isJailRelatedTeleport(event.getTo()));
            
            if (!isJailTeleport) {
                event.setCancelled(true);
                String message = plugin.getChaseManager().getRestrictionMessage(player, "teleport");
                player.sendMessage(MessageUtils.parseMessage(message));
                return;
            }
        }
        
        // Handle wanted level restrictions for non-chased players
        if (plugin.getWantedLevelManager().shouldRestrict(player) && 
            !plugin.getChaseManager().isBeingChased(player)) {
            
            // Check if destination is restricted
            Location to = event.getTo();
            if (to != null && shouldRestrictTeleport(player, to)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /**
     * Check if a teleport destination is jail-related
     */
    private boolean isJailRelatedTeleport(Location destination) {
        if (destination == null) return false;
        
        // Check if teleporting to a known jail location
        String lowercaseWorld = destination.getWorld().getName().toLowerCase();
        if (lowercaseWorld.contains("jail") || lowercaseWorld.contains("prison")) {
            return true;
        }
        
        // Check WorldGuard regions for jail areas
        List<String> regions = RegionUtils.getRegionsAtLocation(destination);
        for (String region : regions) {
            String lowercaseRegion = region.toLowerCase();
            if (lowercaseRegion.contains("jail") || lowercaseRegion.contains("prison")) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * Handle ender chest access restrictions
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        
        // Block ender chest access for restricted players
        if (event.getInventory().getType() == InventoryType.ENDER_CHEST) {
            if (plugin.getWantedLevelManager().shouldRestrict(player)) {
                event.setCancelled(true);
                
                // Cancel the sound by scheduling a task to stop it
                Bukkit.getScheduler().runTask(plugin, () -> {
                    // Stop the ender chest opening sound
                    player.stopSound(org.bukkit.Sound.BLOCK_ENDER_CHEST_OPEN);
                });
                
                String message = plugin.getChaseManager().isBeingChased(player) ?
                    plugin.getChaseManager().getRestrictionMessage(player, "enderchest") :
                    plugin.getWantedLevelManager().getRestrictionMessage(player, "enderchest");
                    
                player.sendMessage(MessageUtils.parseMessage(message));
            }
        }
    }

    /**
     * Handle region movement restrictions for chased and wanted players
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlock().equals(event.getTo().getBlock())) return;
        
        Player player = event.getPlayer();
        handleRegionMovement(player, event);
    }

    /**
     * Handle chased/wanted player movement between regions
     */
    private void handleRegionMovement(Player player, PlayerMoveEvent event) {
        // Check if player should be restricted
        if (!plugin.getWantedLevelManager().shouldRestrict(player)) return;
        
        // Get regions at destination
        List<String> regions = RegionUtils.getRegionsAtLocation(event.getTo());
        
        for (String region : regions) {
            // Check chase manager restrictions
            if (!plugin.getChaseManager().canAccessRegion(player, region)) {
                event.setCancelled(true);
                teleportToSafeLocation(player, event.getFrom());
                
                String restrictionType = determineRestrictionType(region);
                String message = plugin.getChaseManager().isBeingChased(player) ?
                    plugin.getChaseManager().getRestrictionMessage(player, restrictionType) :
                    plugin.getWantedLevelManager().getRestrictionMessage(player, restrictionType);
                    
                player.sendMessage(MessageUtils.parseMessage(message));
                return;
            }
            
            // Check wanted level restrictions  
            if (!plugin.getWantedLevelManager().canAccessRegion(player, region)) {
                event.setCancelled(true);
                teleportToSafeLocation(player, event.getFrom());
                
                String restrictionType = determineRestrictionType(region);
                String message = plugin.getWantedLevelManager().getRestrictionMessage(player, restrictionType);
                player.sendMessage(MessageUtils.parseMessage(message));
                return;
            }
        }
    }

    /**
     * Check if teleport destination should be restricted
     */
    private boolean shouldRestrictTeleport(Player player, Location destination) {
        if (!plugin.getWantedLevelManager().shouldRestrict(player)) return false;
        
        List<String> regions = RegionUtils.getRegionsAtLocation(destination);
        
        for (String region : regions) {
            if (!plugin.getChaseManager().canAccessRegion(player, region) ||
                !plugin.getWantedLevelManager().canAccessRegion(player, region)) {
                
                String restrictionType = determineRestrictionType(region);
                String message = plugin.getChaseManager().isBeingChased(player) ?
                    plugin.getChaseManager().getRestrictionMessage(player, restrictionType) :
                    plugin.getWantedLevelManager().getRestrictionMessage(player, restrictionType);
                    
                player.sendMessage(MessageUtils.parseMessage(message));
                return true;
            }
        }
        
        return false;
    }

    /**
     * Determine the type of restriction based on region name
     */
    private String determineRestrictionType(String region) {
        if (plugin.getChaseManager().isMineRegion(region)) return "mine";
        if (plugin.getChaseManager().isCellRegion(region)) return "cell";
        return "region";
    }

    /**
     * Teleport player to a safe location (prevents getting stuck)
     */
    private void teleportToSafeLocation(Player player, Location safeLocation) {
        // Ensure the safe location is actually safe
        if (safeLocation != null && safeLocation.getWorld() != null) {
            // Make sure the safe location is a block they can stand on
            Location safe = safeLocation.clone();
            safe.setY(Math.max(safe.getY(), safe.getWorld().getHighestBlockYAt(safe) + 1));
            
            player.teleport(safe);
        }
    }

    /**
     * Handle chase end when player quits
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        
        // End any active chase when a player quits
        if (plugin.getChaseManager().isBeingChased(player)) {
            plugin.getChaseManager().endChase(player);
        }
    }
} 