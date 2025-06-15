package dev.lsdmc.edencorrections.managers;

import dev.lsdmc.edencorrections.EdenCorrections;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class WantedManager {
    private final EdenCorrections plugin;
    private final Map<UUID, WantedData> wantedPlayers = new HashMap<>();
    private final Map<UUID, BukkitTask> wantedTasks = new HashMap<>();
    private final int maxWantedLevel;
    private final Map<Integer, Integer> wantedTimes;
    private final Map<Integer, Integer> wantedRewards;
    private final Map<String, JailConfig> jailConfigs;

    public WantedManager(EdenCorrections plugin) {
        this.plugin = plugin;
        
        // Load config values
        this.maxWantedLevel = plugin.getConfig().getInt("wanted.max_level", 5);
        
        // Load wanted times
        this.wantedTimes = new HashMap<>();
        ConfigurationSection timesSection = plugin.getConfig().getConfigurationSection("wanted.level_times");
        if (timesSection != null) {
            for (String key : timesSection.getKeys(false)) {
                wantedTimes.put(Integer.parseInt(key), timesSection.getInt(key));
            }
        }
        
        // Load wanted rewards
        this.wantedRewards = new HashMap<>();
        ConfigurationSection rewardsSection = plugin.getConfig().getConfigurationSection("wanted.rewards");
        if (rewardsSection != null) {
            for (String key : rewardsSection.getKeys(false)) {
                wantedRewards.put(Integer.parseInt(key), rewardsSection.getInt(key));
            }
        }

        // Load jail configurations
        this.jailConfigs = new HashMap<>();
        ConfigurationSection jailsSection = plugin.getConfig().getConfigurationSection("wanted.jails");
        if (jailsSection != null) {
            for (String jailKey : jailsSection.getKeys(false)) {
                ConfigurationSection jailSection = jailsSection.getConfigurationSection(jailKey);
                if (jailSection != null) {
                    JailConfig config = new JailConfig(
                        jailSection.getString("name"),
                        jailSection.getInt("min_level"),
                        jailSection.getInt("max_level"),
                        jailSection.getInt("time_per_level")
                    );
                    jailConfigs.put(jailKey, config);
                }
            }
        }
    }

    public void addWantedLevel(Player player) {
        UUID playerId = player.getUniqueId();
        WantedData data = wantedPlayers.getOrDefault(playerId, new WantedData(0, 0));
        
        if (data.level < maxWantedLevel) {
            data.level++;
            data.timeRemaining = wantedTimes.getOrDefault(data.level, 300);
            wantedPlayers.put(playerId, data);
            
            if (wantedTasks.containsKey(playerId)) {
                wantedTasks.get(playerId).cancel();
            }
            
            startWantedTimer(player);
            
            String message = plugin.getConfig().getString("messages.wanted_level_up", "&cYour wanted level is now %level%!")
                .replace("%level%", String.valueOf(data.level));
            player.sendMessage(Component.text(message, NamedTextColor.RED));
            
            Bukkit.broadcast(Component.text(player.getName() + " is now wanted level " + data.level, NamedTextColor.RED));
        }
    }

    public void removeWantedLevel(Player player) {
        UUID playerId = player.getUniqueId();
        if (wantedPlayers.containsKey(playerId)) {
            wantedPlayers.remove(playerId);
            if (wantedTasks.containsKey(playerId)) {
                wantedTasks.get(playerId).cancel();
                wantedTasks.remove(playerId);
            }
            String message = plugin.getConfig().getString("messages.wanted_level_clear", "&aYour wanted level has been cleared!");
            player.sendMessage(Component.text(message, NamedTextColor.GREEN));
        }
    }

    public int getWantedLevel(Player player) {
        return wantedPlayers.getOrDefault(player.getUniqueId(), new WantedData(0, 0)).level;
    }

    public int getWantedReward(Player player) {
        int level = getWantedLevel(player);
        return level > 0 ? wantedRewards.getOrDefault(level, 250) : 0;
    }

    public int getWantedTimeRemaining(Player player) {
        return wantedPlayers.getOrDefault(player.getUniqueId(), new WantedData(0, 0)).timeRemaining;
    }

    private void startWantedTimer(Player player) {
        UUID playerId = player.getUniqueId();
        WantedData data = wantedPlayers.get(playerId);
        
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (data.timeRemaining <= 0) {
                removeWantedLevel(player);
                return;
            }
            data.timeRemaining--;
        }, 20L, 20L);
        
        wantedTasks.put(playerId, task);
    }

    public void jailPlayer(Player guard, Player target) {
        int wantedLevel = getWantedLevel(target);
        if (wantedLevel > 0) {
            // Find appropriate jail for wanted level
            JailConfig jailConfig = null;
            for (JailConfig config : jailConfigs.values()) {
                if (wantedLevel >= config.minLevel && wantedLevel <= config.maxLevel) {
                    jailConfig = config;
                    break;
                }
            }
            
            if (jailConfig == null) {
                guard.sendMessage(Component.text("No appropriate jail found for wanted level " + wantedLevel, NamedTextColor.RED));
                return;
            }

            // Calculate jail time
            int jailTime = wantedLevel * jailConfig.timePerLevel;
            
            // Execute jail command
            plugin.getServer().dispatchCommand(guard, "cmi jail " + target.getName() + " " + jailConfig.name + " " + jailTime + "m");
            
            // Award tokens to guard
            int reward = getWantedReward(target);
            plugin.getGuardDutyManager().addTokens(guard, reward);
            
            // Clear wanted level
            removeWantedLevel(target);
            
            // Send messages
            String jailMessage = plugin.getConfig().getString("messages.wanted_jail", "&cYou have been jailed for %time% minutes!")
                .replace("%time%", String.valueOf(jailTime));
            target.sendMessage(Component.text(jailMessage, NamedTextColor.RED));
            
            String rewardMessage = plugin.getConfig().getString("messages.wanted_reward", "&aYou received %amount% tokens for catching a wanted player!")
                .replace("%amount%", String.valueOf(reward));
            guard.sendMessage(Component.text(rewardMessage, NamedTextColor.GREEN));
        }
    }

    private static class WantedData {
        int level;
        int timeRemaining;

        WantedData(int level, int timeRemaining) {
            this.level = level;
            this.timeRemaining = timeRemaining;
        }
    }

    private static class JailConfig {
        final String name;
        final int minLevel;
        final int maxLevel;
        final int timePerLevel;

        JailConfig(String name, int minLevel, int maxLevel, int timePerLevel) {
            this.name = name;
            this.minLevel = minLevel;
            this.maxLevel = maxLevel;
            this.timePerLevel = timePerLevel;
        }
    }
} 