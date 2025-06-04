package dev.lsdmc.edencorrections.upgrades;

import dev.lsdmc.edencorrections.EdenCorrections;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class GuardUpgradeManager implements Listener {
    private final EdenCorrections plugin;
    private final File upgradesFile;
    private FileConfiguration upgradesConfig;

    // In-memory upgrade tracking
    private final Map<UUID, Set<String>> playerUpgrades = new HashMap<>();

    public GuardUpgradeManager(EdenCorrections plugin) {
        this.plugin = plugin;

        // Create upgrades data file
        upgradesFile = new File(plugin.getDataFolder(), "guard_upgrades.yml");
        if (!upgradesFile.exists()) {
            try {
                upgradesFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create guard_upgrades.yml file");
                e.printStackTrace();
            }
        }

        // Load configuration
        upgradesConfig = YamlConfiguration.loadConfiguration(upgradesFile);

        // Register events
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // Load all player upgrades
        loadAllPlayerUpgrades();
    }

    /**
     * Load all player upgrades from storage
     */
    private void loadAllPlayerUpgrades() {
        if (upgradesConfig.contains("players")) {
            for (String uuidString : upgradesConfig.getConfigurationSection("players").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidString);
                    Set<String> upgrades = new HashSet<>(upgradesConfig.getStringList("players." + uuidString + ".upgrades"));
                    playerUpgrades.put(uuid, upgrades);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid UUID in upgrades file: " + uuidString);
                }
            }
        }
    }

    /**
     * Save all player upgrades to storage
     */
    public void saveAllPlayerUpgrades() {
        for (Map.Entry<UUID, Set<String>> entry : playerUpgrades.entrySet()) {
            UUID uuid = entry.getKey();
            Set<String> upgrades = entry.getValue();

            upgradesConfig.set("players." + uuid.toString() + ".upgrades", new ArrayList<>(upgrades));
        }

        try {
            upgradesConfig.save(upgradesFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save guard_upgrades.yml file");
            e.printStackTrace();
        }
    }

    /**
     * Add an upgrade to a player
     */
    public void addUpgrade(UUID playerId, String upgradeType) {
        playerUpgrades.computeIfAbsent(playerId, k -> new HashSet<>());
        playerUpgrades.get(playerId).add(upgradeType);

        // Apply upgrade to player if online
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline() && plugin.getDutyManager().isOnDuty(playerId)) {
            applyUpgrade(player, upgradeType);
        }

        // Save to file
        upgradesConfig.set("players." + playerId.toString() + ".upgrades",
                new ArrayList<>(playerUpgrades.get(playerId)));
        try {
            upgradesConfig.save(upgradesFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save guard_upgrades.yml file");
            e.printStackTrace();
        }
    }

    /**
     * Check if a player has a specific upgrade
     */
    public boolean hasUpgrade(UUID playerId, String upgradeType) {
        Set<String> upgrades = playerUpgrades.get(playerId);
        return upgrades != null && upgrades.contains(upgradeType);
    }

    /**
     * Get all upgrades for a player
     */
    public Set<String> getPlayerUpgrades(UUID playerId) {
        Set<String> upgrades = playerUpgrades.get(playerId);
        return upgrades != null ? upgrades : new HashSet<>();
    }

    /**
     * Remove all upgrades from a player (on death)
     */
    public void removeAllUpgrades(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        Set<String> upgrades = playerUpgrades.get(playerId);
        if (player != null && player.isOnline() && upgrades != null) {
            for (String upgrade : upgrades) {
                removeUpgradeEffect(player, upgrade);
            }
        }

        // We don't remove from storage - just remove active effects
        // This is because upgrades persist through death, just the effects are removed
    }

    /**
     * Apply all upgrades to a player (when going on duty)
     */
    public void applyAllUpgrades(Player player) {
        UUID playerId = player.getUniqueId();
        Set<String> upgrades = playerUpgrades.get(playerId);
        if (upgrades != null) {
            for (String upgrade : upgrades) {
                applyUpgrade(player, upgrade);
            }
        }
    }

    /**
     * Apply a specific upgrade to a player
     */
    private void applyUpgrade(Player player, String upgradeType) {
        switch (upgradeType) {
            case "health":
                // Set max health to 12 hearts (24 health points)
                player.setMaxHealth(24.0);
                player.setHealth(Math.min(player.getHealth(), 24.0));
                player.sendMessage("§a§lHealth Upgrade Applied: §f+2 Hearts");
                break;

            case "strength":
                // Apply Strength I effect (indefinite duration)
                player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, Integer.MAX_VALUE, 0, false, true));
                player.sendMessage("§a§lStrength Upgrade Applied: §fStrength I Effect");
                break;

            case "speed":
                // Apply Speed I effect (indefinite duration)
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0, false, true));
                player.sendMessage("§a§lSpeed Upgrade Applied: §fSpeed I Effect");
                break;
        }
    }

    /**
     * Remove a specific upgrade effect from a player
     */
    private void removeUpgradeEffect(Player player, String upgradeType) {
        switch (upgradeType) {
            case "health":
                // Reset max health to 20 (10 hearts)
                player.setMaxHealth(20.0);
                player.setHealth(Math.min(player.getHealth(), 20.0));
                break;

            case "strength":
                // Remove Strength effect
                player.removePotionEffect(PotionEffectType.STRENGTH);
                break;

            case "speed":
                // Remove Speed effect
                player.removePotionEffect(PotionEffectType.SPEED);
                break;
        }
    }

    /**
     * Event handler for player death
     */
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID playerId = player.getUniqueId();

        // Check if player is on duty
        if (plugin.getDutyManager().isOnDuty(playerId)) {
            // Remove all upgrade effects
            removeAllUpgrades(playerId);

            // Notify player
            player.sendMessage("§c§lYour guard upgrades have been temporarily disabled due to death.");
        }
    }

    /**
     * Event handler for player join
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Check if player is on duty
        if (plugin.getDutyManager().isOnDuty(player.getUniqueId())) {
            // Apply all upgrades
            applyAllUpgrades(player);
        }
    }

    /**
     * Event handler for player quit
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Save player upgrades when they leave
        saveAllPlayerUpgrades();
    }
}