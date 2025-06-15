package dev.lsdmc.edencorrections.services;

import dev.lsdmc.edencorrections.EdenCorrections;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GuardService {
    private final EdenCorrections plugin;
    private final Map<UUID, Boolean> dutyStatus = new ConcurrentHashMap<>();
    private final Map<UUID, Long> dutyStartTime = new ConcurrentHashMap<>();

    public GuardService(EdenCorrections plugin) {
        this.plugin = plugin;
    }

    public boolean isOnDuty(Player player) {
        return dutyStatus.getOrDefault(player.getUniqueId(), false);
    }

    public void toggleDuty(Player player) {
        UUID playerId = player.getUniqueId();
        if (isOnDuty(player)) {
            goOffDuty(player);
        } else {
            goOnDuty(player);
        }
    }

    private void goOnDuty(Player player) {
        UUID playerId = player.getUniqueId();
        dutyStatus.put(playerId, true);
        dutyStartTime.put(playerId, System.currentTimeMillis());
        player.sendMessage(Component.text("You are now on duty.", NamedTextColor.GREEN));
    }

    private void goOffDuty(Player player) {
        UUID playerId = player.getUniqueId();
        dutyStatus.remove(playerId);
        dutyStartTime.remove(playerId);
        player.sendMessage(Component.text("You are now off duty.", NamedTextColor.YELLOW));
    }

    public void confiscateItems(Player guard, String targetName) {
        if (!isOnDuty(guard)) {
            guard.sendMessage(Component.text("You must be on duty to confiscate items.", NamedTextColor.RED));
            return;
        }

        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            guard.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
            return;
        }

        // Check if target is in guard lounge
        if (!isInGuardLounge(target)) {
            guard.sendMessage(Component.text("Target must be in the guard lounge to confiscate items.", NamedTextColor.RED));
            return;
        }

        // Confiscate items
        ItemStack[] contents = target.getInventory().getContents();
        target.getInventory().clear();
        
        // TODO: Implement item storage/confiscation system
        guard.sendMessage(Component.text("Items confiscated from " + target.getName(), NamedTextColor.GREEN));
        target.sendMessage(Component.text("Your items have been confiscated by " + guard.getName(), NamedTextColor.YELLOW));
    }

    private boolean isInGuardLounge(Player player) {
        String loungeRegion = plugin.getConfigService().getGuardLoungeRegion();
        return plugin.getWorldGuardService().isInRegion(player, loungeRegion);
    }

    public void setDutyTime(Player player, int seconds) {
        UUID playerId = player.getUniqueId();
        dutyStartTime.put(playerId, System.currentTimeMillis());
    }
} 