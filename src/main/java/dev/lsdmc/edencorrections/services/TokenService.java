package dev.lsdmc.edencorrections.services;

import dev.lsdmc.edencorrections.EdenCorrections;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TokenService {
    private final EdenCorrections plugin;
    private final Map<UUID, Integer> tokens = new ConcurrentHashMap<>();

    public TokenService(EdenCorrections plugin) {
        this.plugin = plugin;
    }

    public int getTokens(Player player) {
        return tokens.getOrDefault(player.getUniqueId(), 0);
    }

    public void addTokens(Player player, int amount) {
        UUID playerId = player.getUniqueId();
        tokens.put(playerId, getTokens(player) + amount);
        player.sendMessage(Component.text("You received " + amount + " guard tokens!", NamedTextColor.GREEN));
    }

    public void removeTokens(Player player, int amount) {
        UUID playerId = player.getUniqueId();
        int currentTokens = getTokens(player);
        if (currentTokens >= amount) {
            tokens.put(playerId, currentTokens - amount);
            player.sendMessage(Component.text("You spent " + amount + " guard tokens.", NamedTextColor.YELLOW));
        } else {
            player.sendMessage(Component.text("You don't have enough tokens!", NamedTextColor.RED));
        }
    }

    public void setTokens(Player player, int amount) {
        tokens.put(player.getUniqueId(), amount);
        player.sendMessage(Component.text("Your tokens have been set to " + amount, NamedTextColor.GREEN));
    }
} 