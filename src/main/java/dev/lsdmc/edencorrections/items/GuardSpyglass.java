package dev.lsdmc.edencorrections.items;

import dev.lsdmc.edencorrections.EdenCorrections;
import dev.lsdmc.edencorrections.utils.MessageUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;

public class GuardSpyglass implements Listener {
    private final EdenCorrections plugin;
    private static final int MAX_RANGE = 50;
    private static final int COOLDOWN_TICKS = 30; // 1.5 seconds
    private final Map<UUID, Long> lastUseTime = new HashMap<>();

    public GuardSpyglass(EdenCorrections plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public static boolean isGuardSpyglass(ItemStack item) {
        return item != null && item.hasItemMeta() &&
               item.getType() == Material.SPYGLASS &&
               item.getItemMeta().hasDisplayName() &&
               item.getItemMeta().getDisplayName().equals("Guard Spyglass");
    }

    @EventHandler
    public void onSpyglassUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        
        if (!isGuardSpyglass(item)) return;
        
        // Check if player is on duty
        if (!plugin.getDutyManager().isOnDuty(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "You must be on duty to use the guard spyglass!");
            return;
        }
        
        // Check cooldown
        if (isOnCooldown(player)) {
            player.sendMessage(ChatColor.RED + "The spyglass is still cooling down!");
            return;
        }
        
        // Get target player
        Entity target = getTargetEntity(player);
        if (!(target instanceof Player)) {
            player.sendMessage(ChatColor.RED + "No valid target found!");
            return;
        }
        
        Player targetPlayer = (Player) target;
        
        // Check if target has minimum wanted level
        int wantedLevel = plugin.getWantedLevelManager().getWantedLevel(targetPlayer.getUniqueId());
        int minWantedLevel = plugin.getConfig().getInt("items.spyglass.min_wanted_level", 1);
        
        if (wantedLevel < minWantedLevel) {
            player.sendMessage(ChatColor.RED + "Target must have at least " + minWantedLevel + " wanted stars to mark! (Current: " + wantedLevel + ")");
            return;
        }
        
        // Mark the player
        boolean success = plugin.getWantedLevelManager().markPlayer(targetPlayer, player);
        if (success) {
            // Visual effects
            Location targetLoc = targetPlayer.getLocation().add(0, 1, 0);
            targetLoc.getWorld().spawnParticle(Particle.CLOUD, targetLoc, 20, 0.5, 0.5, 0.5, 0.1);
            targetLoc.getWorld().playSound(targetLoc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            
            // Set cooldown
            setCooldown(player);
        }
    }

    private boolean isOnCooldown(Player player) {
        long lastUse = lastUseTime.getOrDefault(player.getUniqueId(), 0L);
        return System.currentTimeMillis() - lastUse < COOLDOWN_TICKS * 50;
    }

    private void setCooldown(Player player) {
        lastUseTime.put(player.getUniqueId(), System.currentTimeMillis());
    }

    private Entity getTargetEntity(Player player) {
        List<Entity> nearbyEntities = player.getNearbyEntities(MAX_RANGE, MAX_RANGE, MAX_RANGE);
        Vector direction = player.getLocation().getDirection();
        Location eyeLocation = player.getEyeLocation();

        Entity closest = null;
        double closestDistance = MAX_RANGE;

        for (Entity entity : nearbyEntities) {
            if (!(entity instanceof Player)) continue;

            Vector toEntity = entity.getLocation().toVector().subtract(eyeLocation.toVector());
            double distance = toEntity.length();

            if (distance > MAX_RANGE) continue;

            // Check if entity is in the direction the player is looking
            double dot = direction.dot(toEntity.normalize());
            if (dot < 0.5) continue; // Must be within ~60 degrees of looking direction

            if (distance < closestDistance) {
                closest = entity;
                closestDistance = distance;
            }
        }

        return closest;
    }
} 