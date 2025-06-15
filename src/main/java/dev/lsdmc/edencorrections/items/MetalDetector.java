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

public class MetalDetector implements Listener {
    private final Map<String, Long> cooldowns = new HashMap<>(); // key: guardUUID:targetUUID
    private final Map<UUID, DetectingData> detecting = new HashMap<>();
    private static final int COUNTDOWN = 10;
    private static final double MAX_DISTANCE = 5.0;
    private static final long COOLDOWN_MILLIS = 30 * 60 * 1000L; // 30 minutes

    public static boolean isMetalDetector(ItemStack item) {
        return item != null && item.hasItemMeta() &&
               item.getType() == org.bukkit.Material.IRON_NUGGET &&
               item.getItemMeta().hasDisplayName() &&
               item.getItemMeta().getDisplayName().equals("Metal Detector");
    }

    @EventHandler
    public void onUse(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Player target)) return;
        Player guard = event.getPlayer();
        ItemStack item = guard.getInventory().getItemInMainHand();
        if (!isMetalDetector(item)) return;
        event.setCancelled(true);
        // Officer+ check
        if (!guard.hasPermission("edencorrections.rank.officer")) {
            guard.sendMessage(Component.text("Only Officers and above can use the Metal Detector!", NamedTextColor.RED));
            return;
        }
        String key = guard.getUniqueId() + ":" + target.getUniqueId();
        long now = System.currentTimeMillis();
        if (cooldowns.containsKey(key) && now - cooldowns.get(key) < COOLDOWN_MILLIS) {
            long mins = (COOLDOWN_MILLIS - (now - cooldowns.get(key))) / 60000;
            guard.sendMessage(Component.text("You must wait " + mins + " more minutes to search this player again.", NamedTextColor.RED));
            return;
        }
        if (detecting.containsKey(target.getUniqueId())) {
            guard.sendMessage(Component.text("This player is already being searched!", NamedTextColor.RED));
            return;
        }
        EdenCorrections plugin = (EdenCorrections) guard.getServer().getPluginManager().getPlugin("EdenCorrections");
        if (plugin == null) return;
        ContrabandManager contrabandManager = plugin.getContrabandManager();
        guard.sendMessage(Component.text("Starting metal contraband search on " + target.getName() + "...", NamedTextColor.YELLOW));
        target.sendMessage(Component.text(guard.getName() + " is searching you with a metal detector!", NamedTextColor.RED));
        DetectingData data = new DetectingData(guard, target, COUNTDOWN);
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!guard.isOnline() || !target.isOnline()) {
                cancelDetect(target);
                return;
            }
            if (guard.getLocation().distance(target.getLocation()) > MAX_DISTANCE) {
                guard.sendMessage(Component.text("Search cancelled - target moved too far away!", NamedTextColor.RED));
                target.sendMessage(Component.text("You escaped the metal detector search!", NamedTextColor.GREEN));
                // Initiate chase
                plugin.getChaseManager().startChase(guard, target);
                cancelDetect(target);
                return;
            }
            data.timeRemaining--;
            if (data.timeRemaining <= 0) {
                detecting.remove(target.getUniqueId());
                cooldowns.put(key, System.currentTimeMillis());
                // Find one random contraband (not showing enchantments)
                List<ItemStack> contraband = new ArrayList<>();
                for (ItemStack invItem : target.getInventory().getContents()) {
                    if (contrabandManager.isContraband(invItem)) {
                        contraband.add(invItem);
                    }
                }
                if (!contraband.isEmpty()) {
                    ItemStack found = contraband.get(new Random().nextInt(contraband.size()));
                    // Ask player to drop the item (no enchant info)
                    guard.sendMessage(Component.text("Contraband detected! Ask the player to drop the item:", NamedTextColor.GOLD));
                    guard.sendMessage(Component.text(found.getType().name(), NamedTextColor.YELLOW));
                    target.sendMessage(Component.text("A metal contraband was detected! Please drop the item: " + found.getType().name(), NamedTextColor.RED));
                } else {
                    plugin.getGuardDutyManager().addTokens(guard, 250);
                    guard.sendMessage(Component.text("No contraband found. Reward: 250 tokens.", NamedTextColor.GREEN));
                    target.sendMessage(Component.text("You had no contraband!", NamedTextColor.YELLOW));
                }
            } else {
                String msg = "Metal detector search in " + data.timeRemaining + "s...";
                guard.sendMessage(Component.text(msg, NamedTextColor.YELLOW));
                target.sendMessage(Component.text(msg, NamedTextColor.YELLOW));
            }
        }, 20L, 20L);
        data.task = task;
        detecting.put(target.getUniqueId(), data);
    }

    private void cancelDetect(Player target) {
        DetectingData data = detecting.remove(target.getUniqueId());
        if (data != null && data.task != null) {
            data.task.cancel();
        }
    }

    private static class DetectingData {
        final Player guard;
        final Player target;
        int timeRemaining;
        BukkitTask task;
        DetectingData(Player guard, Player target, int timeRemaining) {
            this.guard = guard;
            this.target = target;
            this.timeRemaining = timeRemaining;
        }
    }
} 