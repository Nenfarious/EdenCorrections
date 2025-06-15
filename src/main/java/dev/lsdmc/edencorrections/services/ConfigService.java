package dev.lsdmc.edencorrections.services;

import dev.lsdmc.edencorrections.EdenCorrections;
import org.bukkit.configuration.file.FileConfiguration;

public class ConfigService {
    private final EdenCorrections plugin;
    private String guardLoungeRegion;

    public ConfigService(EdenCorrections plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        plugin.saveDefaultConfig();
        FileConfiguration config = plugin.getConfig();
        guardLoungeRegion = config.getString("guard.lounge_region", "guard_lounge");
    }

    public void saveConfig() {
        FileConfiguration config = plugin.getConfig();
        config.set("guard.lounge_region", guardLoungeRegion);
        plugin.saveConfig();
    }

    public void setGuardLoungeRegion(String regionName) {
        this.guardLoungeRegion = regionName;
        saveConfig();
    }

    public String getGuardLoungeRegion() {
        return guardLoungeRegion;
    }
} 