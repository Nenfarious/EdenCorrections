package dev.lsdmc.edencorrections.items;

import dev.lsdmc.edencorrections.EdenCorrections;
import dev.lsdmc.edencorrections.managers.ContrabandManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class DrugSniffer implements Listener {
    private final Map<UUID, SniffingData> sniffing = new HashMap<>();
    private static final int COUNTDOWN = 5;
    private static final double MAX_DISTANCE = 5.0;

    public static boolean isDrugSniffer(ItemStack item) {
        return item != null && item.hasItemMeta() &&
               item.getType() == org.bukkit.Material.BONE &&
               item.getItemMeta().hasDisplayName() &&
               item.getItemMeta().getDisplayName().equals("Drug Sniffer");
    }

    @EventHandler
    public void onUse(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Player target)) return;
        Player guard = event.getPlayer();
        ItemStack item = guard.getInventory().getItemInMainHand();
        if (!isDrugSniffer(item)) return;
        event.setCancelled(true);
        if (sniffing.containsKey(target.getUniqueId())) {
            guard.sendMessage(Component.text("This player is already being searched!", NamedTextColor.RED));
            return;
        }
        EdenCorrections plugin = (EdenCorrections) guard.getServer().getPluginManager().getPlugin("EdenCorrections");
        if (plugin == null) return;
        ContrabandManager contrabandManager = plugin.getContrabandManager();
        guard.sendMessage(Component.text("Starting drug search on " + target.getName() + "...", NamedTextColor.YELLOW));
        target.sendMessage(Component.text(guard.getName() + " is searching you for drugs!", NamedTextColor.RED));
        SniffingData data = new SniffingData(guard, target, COUNTDOWN);
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!guard.isOnline() || !target.isOnline()) {
                cancelSniff(target);
                return;
            }
            if (guard.getLocation().distance(target.getLocation()) > MAX_DISTANCE) {
                guard.sendMessage(Component.text("Search cancelled - target moved too far away!", NamedTextColor.RED));
                target.sendMessage(Component.text("You escaped the drug search!", NamedTextColor.GREEN));
                cancelSniff(target);
                return;
            }
            data.timeRemaining--;
            if (data.timeRemaining <= 0) {
                sniffing.remove(target.getUniqueId());
                // Remove all drugs and reward
                int drugsFound = 0;
                List<ItemStack> toRemove = new ArrayList<>();
                for (ItemStack invItem : target.getInventory().getContents()) {
                    if (contrabandManager.isDrug(invItem)) {
                        drugsFound++;
                        toRemove.add(invItem);
                    }
                }
                for (ItemStack drug : toRemove) {
                    target.getInventory().removeItem(drug);
                }
                if (drugsFound > 0) {
                    int reward = drugsFound * 50;
                    plugin.getGuardDutyManager().addTokens(guard, reward);
                    guard.sendMessage(Component.text("Found and removed " + drugsFound + " drugs. Reward: " + reward + " tokens.", NamedTextColor.GREEN));
                    target.sendMessage(Component.text("All your drugs have been confiscated!", NamedTextColor.RED));
                } else {
                    plugin.getGuardDutyManager().addTokens(guard, 250);
                    guard.sendMessage(Component.text("No drugs found. Reward: 250 tokens.", NamedTextColor.GREEN));
                    target.sendMessage(Component.text("You had no drugs!", NamedTextColor.YELLOW));
                }
            } else {
                String msg = "Drug search in " + data.timeRemaining + "s...";
                guard.sendMessage(Component.text(msg, NamedTextColor.YELLOW));
                target.sendMessage(Component.text(msg, NamedTextColor.YELLOW));
            }
        }, 20L, 20L);
        data.task = task;
        sniffing.put(target.getUniqueId(), data);
    }

    private void cancelSniff(Player target) {
        SniffingData data = sniffing.remove(target.getUniqueId());
        if (data != null && data.task != null) {
            data.task.cancel();
        }
    }

    private static class SniffingData {
        final Player guard;
        final Player target;
        int timeRemaining;
        BukkitTask task;
        SniffingData(Player guard, Player target, int timeRemaining) {
            this.guard = guard;
            this.target = target;
            this.timeRemaining = timeRemaining;
        }
    }
} 