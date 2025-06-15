package dev.lsdmc.edencorrections.services;

import dev.lsdmc.edencorrections.EdenCorrections;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class HandcuffService implements Listener {
    private final EdenCorrections plugin;
    private final Map<UUID, CuffingData> cuffingPlayers = new HashMap<>();
    private final int countdownTime;

    public HandcuffService(EdenCorrections plugin) {
        this.plugin = plugin;
        this.countdownTime = plugin.getConfig().getInt("wanted.cuffing.countdown", 5);
    }

    @EventHandler
    public void onHandcuffsUse(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Player)) return;
        
        Player guard = event.getPlayer();
        Player target = (Player) event.getRightClicked();
        
        if (!plugin.getHandcuffs().isHandcuffs(guard.getInventory().getItemInMainHand())) return;
        
        event.setCancelled(true);
        
        if (!plugin.getGuardService().isOnDuty(guard)) {
            guard.sendMessage(Component.text("You must be on duty to use handcuffs!", NamedTextColor.RED));
            return;
        }
        
        if (cuffingPlayers.containsKey(target.getUniqueId())) {
            guard.sendMessage(Component.text("This player is already being cuffed!", NamedTextColor.RED));
            return;
        }

        if (!plugin.getWorldGuardService().isInRegion(target, plugin.getConfigService().getGuardLoungeRegion())) {
            guard.sendMessage(Component.text("You can only cuff players in the guard lounge!", NamedTextColor.RED));
            return;
        }

        if (guard.getLocation().distance(target.getLocation()) > 3) {
            guard.sendMessage(Component.text("You are too far away to cuff this player!", NamedTextColor.RED));
            return;
        }

        startCuffing(guard, target);
    }

    private void startCuffing(Player guard, Player target) {
        CuffingData data = new CuffingData(guard, countdownTime);
        cuffingPlayers.put(target.getUniqueId(), data);
        
        guard.sendMessage(Component.text("Starting to cuff " + target.getName() + "...", NamedTextColor.YELLOW));
        target.sendMessage(Component.text(guard.getName() + " is attempting to cuff you!", NamedTextColor.RED));
        
        data.task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!guard.isOnline() || !target.isOnline()) {
                cancelCuffing(target);
                return;
            }
            
            if (!plugin.getWorldGuardService().isInRegion(target, plugin.getConfigService().getGuardLoungeRegion())) {
                guard.sendMessage(Component.text("Cuffing cancelled - target left guard lounge!", NamedTextColor.RED));
                target.sendMessage(Component.text("Cuffing cancelled - you left the guard lounge!", NamedTextColor.GREEN));
                cancelCuffing(target);
                return;
            }
            
            if (guard.getLocation().distance(target.getLocation()) > 3) {
                guard.sendMessage(Component.text("Cuffing cancelled - you moved too far away!", NamedTextColor.RED));
                target.sendMessage(Component.text("Cuffing cancelled - guard moved too far away!", NamedTextColor.GREEN));
                cancelCuffing(target);
                return;
            }
            
            data.timeRemaining--;
            
            if (data.timeRemaining <= 0) {
                cuffingPlayers.remove(target.getUniqueId());
                plugin.getHandcuffs().applyHandcuffs(guard, target);
            } else {
                guard.sendMessage(Component.text("Cuffing in " + data.timeRemaining + " seconds...", NamedTextColor.YELLOW));
                target.sendMessage(Component.text("Cuffing in " + data.timeRemaining + " seconds...", NamedTextColor.YELLOW));
            }
        }, 20L, 20L);
    }

    private void cancelCuffing(Player target) {
        CuffingData data = cuffingPlayers.remove(target.getUniqueId());
        if (data != null && data.task != null) {
            data.task.cancel();
        }
    }

    private static class CuffingData {
        final Player guard;
        int timeRemaining;
        BukkitTask task;

        CuffingData(Player guard, int timeRemaining) {
            this.guard = guard;
            this.timeRemaining = timeRemaining;
        }
    }
} 