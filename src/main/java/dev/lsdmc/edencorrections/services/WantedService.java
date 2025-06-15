package dev.lsdmc.edencorrections.services;

import dev.lsdmc.edencorrections.EdenCorrections;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class WantedService {
    private final EdenCorrections plugin;
    private final Set<UUID> wantedPlayers = new HashSet<>();

    public WantedService(EdenCorrections plugin) {
        this.plugin = plugin;
    }

    public boolean isWanted(Player player) {
        return wantedPlayers.contains(player.getUniqueId());
    }

    public void setWanted(Player player, boolean wanted) {
        if (wanted) {
            wantedPlayers.add(player.getUniqueId());
            Bukkit.broadcast(Component.text(player.getName() + " is now wanted!", NamedTextColor.RED));
        } else {
            wantedPlayers.remove(player.getUniqueId());
            Bukkit.broadcast(Component.text(player.getName() + " is no longer wanted.", NamedTextColor.GREEN));
        }
    }

    public void jailPlayer(Player guard, Player target) {
        if (!plugin.getGuardService().isOnDuty(guard)) {
            guard.sendMessage(Component.text("You must be on duty to jail players.", NamedTextColor.RED));
            return;
        }

        if (!plugin.getWorldGuardService().isInRegion(target, plugin.getConfigService().getGuardLoungeRegion())) {
            guard.sendMessage(Component.text("Target must be in the guard lounge to be jailed.", NamedTextColor.RED));
            return;
        }

        // TODO: Implement jail system
        setWanted(target, false);
        guard.sendMessage(Component.text("Successfully jailed " + target.getName(), NamedTextColor.GREEN));
        target.sendMessage(Component.text("You have been jailed by " + guard.getName(), NamedTextColor.RED));
    }
} 