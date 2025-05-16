package dev.lsdmc.edencorrections.listeners;

import dev.lsdmc.edencorrections.EdenCorrections;
import dev.lsdmc.edencorrections.managers.DutyManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Listener that prevents player movement and actions during the duty immobilization period
 */
public class GuardImmobilizationListener implements Listener {
    private final EdenCorrections plugin;
    private final DutyManager dutyManager;

    public GuardImmobilizationListener(EdenCorrections plugin, DutyManager dutyManager) {
        this.plugin = plugin;
        this.dutyManager = dutyManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        // Only check if the player actually moved position (not just looked around)
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
                event.getFrom().getBlockY() == event.getTo().getBlockY() &&
                event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        // Check if player is immobilized
        if (dutyManager.isPlayerImmobilized(event.getPlayer().getUniqueId())) {
            // Cancel movement
            event.setCancelled(true);

            // Send a reminder message (with cooldown to avoid spam)
            dutyManager.sendImmobilizationReminderWithCooldown(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        // Prevent teleporting while immobilized
        if (dutyManager.isPlayerImmobilized(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            dutyManager.sendImmobilizationReminderWithCooldown(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Prevent interaction while immobilized
        if (dutyManager.isPlayerImmobilized(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            dutyManager.sendImmobilizationReminderWithCooldown(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        // Prevent damaging entities while immobilized
        if (event.getDamager() instanceof Player player &&
                dutyManager.isPlayerImmobilized(player.getUniqueId())) {
            event.setCancelled(true);
            dutyManager.sendImmobilizationReminderWithCooldown(player);
        }

        // Prevent damaging immobilized players
        if (event.getEntity() instanceof Player player &&
                dutyManager.isPlayerImmobilized(player.getUniqueId())) {
            event.setCancelled(true);

            // Optional: notify the attacker
            if (event.getDamager() instanceof Player attacker) {
                attacker.sendMessage("§c§lYou cannot attack a guard who is preparing for duty!");
            }
        }
    }
}