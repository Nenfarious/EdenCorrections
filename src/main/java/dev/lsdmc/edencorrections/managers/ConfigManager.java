package dev.lsdmc.edencorrections.managers;

import dev.lsdmc.edencorrections.EdenCorrections;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

public class ConfigManager {
    private final EdenCorrections plugin;
    private File configFile;
    private FileConfiguration config;

    public ConfigManager(EdenCorrections plugin) {
        this.plugin = plugin;
        setupConfig();
    }

    private void setupConfig() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdir();
        }

        configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            saveDefaultConfig();
        }

        config = YamlConfiguration.loadConfiguration(configFile);
    }

    private void saveDefaultConfig() {
        config = new YamlConfiguration();

        // Guard Duty Settings
        config.set("guard_duty.break_time_ratio", 0.25);
        config.set("guard_duty.max_break_time", 3600);
        config.set("guard_duty.effect_interval", 300);
        config.set("guard_duty.guard_lounge_region", "guard_lounge");

        // Guard Ranks
        config.set("guard_ranks.trainee.permission", "edencorrections.rank.trainee");
        config.set("guard_ranks.private.permission", "edencorrections.rank.private");
        config.set("guard_ranks.officer.permission", "edencorrections.rank.officer");
        config.set("guard_ranks.sergeant.permission", "edencorrections.rank.sergeant");
        config.set("guard_ranks.warden.permission", "edencorrections.rank.warden");

        // Drug Items
        config.set("drugs.blaze.name", "Blaze");
        config.set("drugs.blaze.ei_id", "blaze");
        config.set("drugs.blaze.duration", 300);
        config.set("drugs.blaze.token_reward", 100);
        config.set("drugs.blaze2.name", "Blaze II");
        config.set("drugs.blaze2.ei_id", "blaze2");
        config.set("drugs.blaze2.duration", 300);
        config.set("drugs.blaze2.token_reward", 100);
        config.set("drugs.blaze3.name", "Blaze III");
        config.set("drugs.blaze3.ei_id", "blaze3");
        config.set("drugs.blaze3.duration", 300);
        config.set("drugs.blaze3.token_reward", 100);
        config.set("drugs.blaze4.name", "Blaze IV");
        config.set("drugs.blaze4.ei_id", "blaze4");
        config.set("drugs.blaze4.duration", 300);
        config.set("drugs.blaze4.token_reward", 100);
        config.set("drugs.invisibility.name", "Invisibility");
        config.set("drugs.invisibility.ei_id", "invisibility");
        config.set("drugs.invisibility.duration", 300);
        config.set("drugs.invisibility.token_reward", 100);
        config.set("drugs.invisibility2.name", "Invisibility II");
        config.set("drugs.invisibility2.ei_id", "invisibility2");
        config.set("drugs.invisibility2.duration", 300);
        config.set("drugs.invisibility2.token_reward", 100);
        config.set("drugs.invisibility3.name", "Invisibility III");
        config.set("drugs.invisibility3.ei_id", "invisibility3");
        config.set("drugs.invisibility3.duration", 300);
        config.set("drugs.invisibility3.token_reward", 100);
        config.set("drugs.invisibility4.name", "Invisibility IV");
        config.set("drugs.invisibility4.ei_id", "invisibility4");
        config.set("drugs.invisibility4.duration", 300);
        config.set("drugs.invisibility4.token_reward", 100);
        config.set("drugs.jump2.name", "Jump II");
        config.set("drugs.jump2.ei_id", "jump2");
        config.set("drugs.jump2.duration", 300);
        config.set("drugs.jump2.token_reward", 100);
        config.set("drugs.jump3.name", "Jump III");
        config.set("drugs.jump3.ei_id", "jump3");
        config.set("drugs.jump3.duration", 300);
        config.set("drugs.jump3.token_reward", 100);
        config.set("drugs.jump4.name", "Jump IV");
        config.set("drugs.jump4.ei_id", "jump4");
        config.set("drugs.jump4.duration", 300);
        config.set("drugs.jump4.token_reward", 100);
        config.set("drugs.magicmelon.name", "Magic Melon");
        config.set("drugs.magicmelon.ei_id", "magicmelon");
        config.set("drugs.magicmelon.duration", 300);
        config.set("drugs.magicmelon.token_reward", 100);
        config.set("drugs.melon2.name", "Melon II");
        config.set("drugs.melon2.ei_id", "melon2");
        config.set("drugs.melon2.duration", 300);
        config.set("drugs.melon2.token_reward", 100);
        config.set("drugs.melon3.name", "Melon III");
        config.set("drugs.melon3.ei_id", "melon3");
        config.set("drugs.melon3.duration", 300);
        config.set("drugs.melon3.token_reward", 100);
        config.set("drugs.melon4.name", "Melon IV");
        config.set("drugs.melon4.ei_id", "melon4");
        config.set("drugs.melon4.duration", 300);
        config.set("drugs.melon4.token_reward", 100);
        config.set("drugs.muscle2.name", "Muscle II");
        config.set("drugs.muscle2.ei_id", "muscle2");
        config.set("drugs.muscle2.duration", 300);
        config.set("drugs.muscle2.token_reward", 100);
        config.set("drugs.muscle3.name", "Muscle III");
        config.set("drugs.muscle3.ei_id", "muscle3");
        config.set("drugs.muscle3.duration", 300);
        config.set("drugs.muscle3.token_reward", 100);
        config.set("drugs.muscle4.name", "Muscle IV");
        config.set("drugs.muscle4.ei_id", "muscle4");
        config.set("drugs.muscle4.duration", 300);
        config.set("drugs.muscle4.token_reward", 100);
        config.set("drugs.musclemix.name", "Muscle Mix");
        config.set("drugs.musclemix.ei_id", "musclemix");
        config.set("drugs.musclemix.duration", 300);
        config.set("drugs.musclemix.token_reward", 100);
        config.set("drugs.Nightvision.name", "Night Vision");
        config.set("drugs.Nightvision.ei_id", "Nightvision");
        config.set("drugs.Nightvision.duration", 300);
        config.set("drugs.Nightvision.token_reward", 100);
        config.set("drugs.speedpowder.name", "Speed Powder");
        config.set("drugs.speedpowder.ei_id", "speedpowder");
        config.set("drugs.speedpowder.duration", 300);
        config.set("drugs.speedpowder.token_reward", 100);
        config.set("drugs.speedpowder2.name", "Speed Powder II");
        config.set("drugs.speedpowder2.ei_id", "speedpowder2");
        config.set("drugs.speedpowder2.duration", 300);
        config.set("drugs.speedpowder2.token_reward", 100);
        config.set("drugs.speedpowder3.name", "Speed Powder III");
        config.set("drugs.speedpowder3.ei_id", "speedpowder3");
        config.set("drugs.speedpowder3.duration", 300);
        config.set("drugs.speedpowder3.token_reward", 100);
        config.set("drugs.speedpowder4.name", "Speed Powder IV");
        config.set("drugs.speedpowder4.ei_id", "speedpowder4");
        config.set("drugs.speedpowder4.duration", 300);
        config.set("drugs.speedpowder4.token_reward", 100);

        // Messages
        config.set("messages.prefix", "&8[&cEdenCorrections&8] ");
        config.set("messages.no_permission", "&cYou don't have permission to use this command!");
        config.set("messages.player_not_found", "&cPlayer not found!");
        config.set("messages.guard_duty_start", "&aYou have started your guard duty shift!");
        config.set("messages.guard_duty_end", "&cYou have ended your guard duty shift!");
        config.set("messages.break_start", "&aYou are now on break!");
        config.set("messages.break_end", "&cYour break has ended!");
        config.set("messages.contraband_found", "&cContraband found: %item%");
        config.set("messages.drug_effect_start", "&aThe effects of %drug% have started!");
        config.set("messages.drug_effect_end", "&cThe effects of %drug% have worn off!");
        config.set("messages.token_reward", "&aYou received %amount% tokens for confiscating %item%!");

        saveConfig();
    }

    public void saveConfig() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save config.yml!");
            e.printStackTrace();
        }
    }

    public String getMessage(String path) {
        return config.getString("messages." + path, "Message not found: " + path);
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public void setGuardLoungeRegion(String regionName) {
        config.set("guard_duty.guard_lounge_region", regionName);
        saveConfig();
    }
} 