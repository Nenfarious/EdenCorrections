package dev.lsdmc.edencorrections.listeners;

import dev.lsdmc.edencorrections.EdenCorrections;
import dev.lsdmc.edencorrections.managers.GuardPenaltyManager;
import dev.lsdmc.edencorrections.utils.RegionUtils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Listener for handling guard penalty-related events
 */
public class GuardPenaltyListener implements Listener {
    private final GuardPenaltyManager penaltyManager;
    private final EdenCorrections plugin;

    public GuardPenaltyListener(GuardPenaltyManager penaltyManager, EdenCorrections plugin) {
        this.penaltyManager = penaltyManager;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        // Skip if penalties are disabled
        if (!penaltyManager.arePenaltiesEnabled()) return;

        Player victim = event.getEntity();

        // Check if victim is a guard
        if (isPlayerGuard(victim)) {
            penaltyManager.handleGuardDeath(victim);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        // Skip if penalties are disabled
        if (!penaltyManager.arePenaltiesEnabled()) return;

        // Only process if the block position changes (more efficient)
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
                event.getFrom().getBlockY() == event.getTo().getBlockY() &&
                event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();

        // Check if player is a locked guard
        if (isPlayerGuard(player) && penaltyManager.isPlayerLocked(player.getUniqueId())) {
            // Check if player was in a restricted region
            boolean wasInRestricted = false;
            for (String region : penaltyManager.getRestrictedRegions()) {
                if (RegionUtils.isPlayerInRegion(player, region)) {
                    wasInRestricted = true;
                    break;
                }
            }

            // If player was in a restricted region, check if they're trying to leave
            if (wasInRestricted) {
                boolean isEnteringUnrestricted = true;
                for (String region : penaltyManager.getRestrictedRegions()) {
                    if (RegionUtils.isLocationInRegion(event.getTo(), region)) {
                        isEnteringUnrestricted = false;
                        break;
                    }
                }

                // If trying to enter an unrestricted area, cancel and notify
                if (isEnteringUnrestricted) {
                    event.setCancelled(true);
                    penaltyManager.handleRestrictedRegionExit(player);
                }
            }
        }
    }

    /**
     * Check if a player is a guard
     * @param player The player to check
     * @return True if the player is a guard
     */
    private boolean isPlayerGuard(Player player) {
        return player.hasPermission("edenprison.guard") || player.hasPermission("edencorrections.duty");
    }
}