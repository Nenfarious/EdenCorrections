package dev.lsdmc.edencorrections.managers;

import dev.lsdmc.edencorrections.EdenCorrections;
import dev.lsdmc.edencorrections.config.ConfigManager;
import dev.lsdmc.edencorrections.utils.MessageUtils;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

public class NPCManager implements Listener {
    private final EdenCorrections plugin;
    private final ConfigManager configManager;
    private final File dataDir;
    private final File npcFile;
    private FileConfiguration npcConfig;
    private final Map<String, NPC> dutyNPCs = new HashMap<>();
    private final Map<UUID, Long> lastNpcInteraction = new HashMap<>();
    private final int interactionRadius;
    private final String npcSkin;
    private boolean citizensEnabled = false;

    private static final String NPC_PLUGIN_IDENTIFIER = "edencorrections";
    private static final String NPC_DATA_KEY = "edencorrections-npc";

    public enum NPCType { DUTY, GUI }
    public enum GuiSection { MAIN, DUTY, STATS, ACTIONS, EQUIPMENT, SHOP, TOKENS }

    public NPCManager(EdenCorrections plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.dataDir = new File(plugin.getDataFolder(), "data");
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
        this.npcFile = new File(dataDir, "npcs.yml");

        // Get config values
        FileConfiguration config = configManager.getConfig("config.yml");
        this.interactionRadius = config != null ? config.getInt("npc.interaction-radius", 3) : 3;
        this.npcSkin = config != null ? config.getString("npc.skin", "PoliceOfficer") : "PoliceOfficer";

        // Check for Citizens
        if (Bukkit.getPluginManager().getPlugin("Citizens") != null) {
            plugin.getLogger().info("Citizens found, enabling NPC integration");
            citizensEnabled = true;
        } else {
            plugin.getLogger().severe("Citizens plugin not found! NPC system will be disabled.");
            return;
        }

        // Initialize NPC file
        if (!npcFile.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                npcFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to create npcs.yml", e);
            }
        }

        npcConfig = YamlConfiguration.loadConfiguration(npcFile);

        // Load NPCs
        loadNpcs();

        // Register events
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private void loadNpcs() {
        if (!citizensEnabled) return;

        // Track existing NPCs before destroying them
        Map<Location, NPC> existingNpcs = new HashMap<>();
        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            if (npc.data().has(NPC_DATA_KEY)) {
                existingNpcs.put(npc.getStoredLocation(), npc);
                npc.destroy();
            }
        }

        ConfigurationSection section = npcConfig.getConfigurationSection("npcs");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            try {
                String worldName = section.getString(key + ".world");
                if (worldName == null) continue;

                World world = Bukkit.getWorld(worldName);
                if (world == null) continue;

                double x = section.getDouble(key + ".x");
                double y = section.getDouble(key + ".y");
                double z = section.getDouble(key + ".z");
                String name = section.getString(key + ".name", "Corrections Officer");
                String typeStr = section.getString(key + ".type", "DUTY");
                String sectionStr = section.getString(key + ".gui-section");

                Location location = new Location(world, x, y, z);
                NPCType type = NPCType.valueOf(typeStr);
                GuiSection guiSection = sectionStr != null ? GuiSection.valueOf(sectionStr) : null;

                // Check if we already had an NPC at this location before destroying
                boolean hadNpc = false;
                for (Location existingLoc : existingNpcs.keySet()) {
                    if (existingLoc.getWorld().equals(location.getWorld()) &&
                        Math.abs(existingLoc.getX() - location.getX()) < 0.1 &&
                        Math.abs(existingLoc.getY() - location.getY()) < 0.1 &&
                        Math.abs(existingLoc.getZ() - location.getZ()) < 0.1) {
                        hadNpc = true;
                        break;
                    }
                }

                // Only create if we didn't have an NPC here before
                if (!hadNpc) {
                    createCitizensNpc(location, null, name, type, guiSection);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load NPC: " + key);
            }
        }
    }

    private boolean isPluginNpcAtLocation(Location location) {
        if (!citizensEnabled) return false;
        
        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            if (npc.data().has(NPC_DATA_KEY)) {
                Location npcLoc = npc.getStoredLocation();
                if (npcLoc.getWorld().equals(location.getWorld()) &&
                    Math.abs(npcLoc.getX() - location.getX()) < 0.1 &&
                    Math.abs(npcLoc.getY() - location.getY()) < 0.1 &&
                    Math.abs(npcLoc.getZ() - location.getZ()) < 0.1) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean createNpc(Location location, Player createdBy, String name, NPCType type, GuiSection guiSection) {
        if (!citizensEnabled) {
            if (createdBy != null) {
                createdBy.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>NPC system is disabled! Citizens plugin not found.</red>")));
            }
            return false;
        }

        if (location == null || location.getWorld() == null) {
            if (createdBy != null) {
                createdBy.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>Invalid location for NPC creation!</red>")));
            }
            return false;
        }

        if (name == null || name.trim().isEmpty()) {
            if (createdBy != null) {
                createdBy.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>NPC name cannot be empty!</red>")));
            }
            return false;
        }

        if (type == NPCType.GUI && guiSection == null) {
            if (createdBy != null) {
                createdBy.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>GUI NPCs require a section to be specified!</red>")));
            }
            return false;
        }

        return createCitizensNpc(location, createdBy, name, type, guiSection);
    }

    private boolean createCitizensNpc(Location location, Player createdBy, String name, NPCType type, GuiSection guiSection) {
        try {
            // Check if our plugin's NPC already exists at this location
            if (isPluginNpcAtLocation(location)) {
                if (createdBy != null) {
                    createdBy.sendMessage(MessageUtils.getPrefix(plugin).append(
                        MessageUtils.parseMessage("<red>An NPC already exists at this location!</red>")));
                }
                return false;
            }

            NPCRegistry registry = CitizensAPI.getNPCRegistry();
            if (registry == null) {
                if (createdBy != null) {
                    createdBy.sendMessage(MessageUtils.getPrefix(plugin).append(
                        MessageUtils.parseMessage("<red>Failed to access Citizens NPC registry!</red>")));
                }
                return false;
            }

            NPC npc = registry.createNPC(EntityType.PLAYER, name);
            if (npc == null) {
                if (createdBy != null) {
                    createdBy.sendMessage(MessageUtils.getPrefix(plugin).append(
                        MessageUtils.parseMessage("<red>Failed to create NPC!</red>")));
                }
                return false;
            }
            
            // Configure NPC with our plugin identifier
            npc.data().setPersistent(NPC.Metadata.NAMEPLATE_VISIBLE, true);
            npc.data().setPersistent("player-skin-name", npcSkin);
            npc.data().setPersistent(NPC_DATA_KEY, true); // Mark as our plugin's NPC
            npc.data().setPersistent("npc-type", type.name());
            if (type == NPCType.GUI && guiSection != null) {
                npc.data().setPersistent("gui-section", guiSection.name());
            }
            
            // Spawn NPC
            if (!npc.spawn(location)) {
                if (createdBy != null) {
                    createdBy.sendMessage(MessageUtils.getPrefix(plugin).append(
                        MessageUtils.parseMessage("<red>Failed to spawn NPC at location!</red>")));
                }
                npc.destroy();
                return false;
            }

            // Save to config
            String base = "npcs." + npc.getUniqueId().toString();
            npcConfig.set(base + ".world", location.getWorld().getName());
            npcConfig.set(base + ".x", location.getX());
            npcConfig.set(base + ".y", location.getY());
            npcConfig.set(base + ".z", location.getZ());
            npcConfig.set(base + ".created-by", createdBy != null ? createdBy.getUniqueId().toString() : "system");
            npcConfig.set(base + ".name", name);
            npcConfig.set(base + ".type", type.name());
            if (type == NPCType.GUI && guiSection != null) {
                npcConfig.set(base + ".gui-section", guiSection.name());
            }
            
            try {
                npcConfig.save(npcFile);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save npcs.yml", e);
                if (createdBy != null) {
                    createdBy.sendMessage(MessageUtils.getPrefix(plugin).append(
                        MessageUtils.parseMessage("<yellow>NPC created but failed to save configuration!</yellow>")));
                }
            }

            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create Citizens NPC", e);
            if (createdBy != null) {
                createdBy.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("<red>An error occurred while creating the NPC!</red>")));
            }
            return false;
        }
    }

    public boolean removeNpc(UUID npcUuid) {
        if (!citizensEnabled) return false;

        try {
            NPC npc = CitizensAPI.getNPCRegistry().getByUniqueId(npcUuid);
            if (npc != null) {
                npc.destroy();
            }
            
            // Remove from config
            npcConfig.set("npcs." + npcUuid.toString(), null);
            try {
                npcConfig.save(npcFile);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save npcs.yml", e);
            }
            
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to remove NPC", e);
            return false;
        }
    }

    @EventHandler
    public void onNpcInteract(PlayerInteractAtEntityEvent event) {
        if (!citizensEnabled) return;
        
        Player player = event.getPlayer();
        if (event.getHand() != EquipmentSlot.HAND) return;

        // Cooldown check
        long now = System.currentTimeMillis();
        if (lastNpcInteraction.containsKey(player.getUniqueId()) && 
            now - lastNpcInteraction.get(player.getUniqueId()) < 2000) {
            event.setCancelled(true);
            return;
        }
        lastNpcInteraction.put(player.getUniqueId(), now);

        // Check if clicked entity is a Citizens NPC
        NPC npc = CitizensAPI.getNPCRegistry().getNPC(event.getRightClicked());
        if (npc == null || !npc.data().has(NPC_DATA_KEY)) return; // Only handle our plugin's NPCs

        // Get NPC type and handle interaction
        String typeStr = npc.data().get("npc-type");
        if (typeStr == null) {
            plugin.getLogger().warning("NPC " + npc.getUniqueId() + " has no type set!");
            return;
        }

        try {
            NPCType type = NPCType.valueOf(typeStr);
            if (type == NPCType.DUTY) {
                // Check if player has permission
                if (!player.hasPermission("edencorrections.duty")) {
                    player.sendMessage(MessageUtils.getPrefix(plugin).append(
                        MessageUtils.parseMessage("<red>You don't have permission to use duty NPCs!</red>")));
                    event.setCancelled(true);
                    return;
                }
                plugin.getDutyManager().toggleDuty(player);
            } else if (type == NPCType.GUI) {
                String sectionStr = npc.data().get("gui-section");
                if (sectionStr == null) {
                    plugin.getLogger().warning("GUI NPC " + npc.getUniqueId() + " has no section set!");
                    return;
                }
                try {
                    GuiSection section = GuiSection.valueOf(sectionStr);
                    // Check if player has permission for the specific GUI section
                    if (!hasGuiSectionPermission(player, section)) {
                        player.sendMessage(MessageUtils.getPrefix(plugin).append(
                            MessageUtils.parseMessage("<red>You don't have permission to use this NPC!</red>")));
                        event.setCancelled(true);
                        return;
                    }
                    openGuiSection(player, section);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid GUI section for NPC " + npc.getUniqueId() + ": " + sectionStr);
                }
            }
            event.setCancelled(true);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid NPC type: " + typeStr);
        }
    }

    private boolean hasGuiSectionPermission(Player player, GuiSection section) {
        return switch (section) {
            case MAIN -> player.hasPermission("edencorrections.gui.main");
            case DUTY -> player.hasPermission("edencorrections.gui.duty");
            case STATS -> player.hasPermission("edencorrections.gui.stats");
            case ACTIONS -> player.hasPermission("edencorrections.gui.actions");
            case EQUIPMENT -> player.hasPermission("edencorrections.gui.equipment");
            case SHOP -> player.hasPermission("edencorrections.gui.shop");
            case TOKENS -> player.hasPermission("edencorrections.gui.tokens");
        };
    }

    private void openGuiSection(Player player, GuiSection section) {
        try {
            switch (section) {
                case MAIN -> plugin.getGuiManager().openMainMenu(player);
                case DUTY -> plugin.getGuiManager().openDutyMenu(player);
                case STATS -> plugin.getGuiManager().openStatsMenu(player);
                case ACTIONS -> plugin.getGuiManager().openActionsMenu(player);
                case EQUIPMENT -> plugin.getGuiManager().openEquipmentMenu(player);
                case SHOP -> plugin.getGuiManager().openShopMenu(player);
                case TOKENS -> plugin.getGuiManager().openTokensView(player);
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to open GUI section " + section + " for player " + player.getName());
            player.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>Failed to open menu! Please try again later.</red>")));
        }
    }

    public List<UUID> getCorrectionsNpcs() {
        if (!citizensEnabled) return new ArrayList<>();
        
        List<UUID> npcs = new ArrayList<>();
        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            if (npc.data().has(NPC_DATA_KEY)) {
                npcs.add(npc.getUniqueId());
            }
        }
        return npcs;
    }

    public NPCType getNpcType(UUID npcUuid) {
        if (!citizensEnabled) return NPCType.DUTY;
        
        NPC npc = CitizensAPI.getNPCRegistry().getByUniqueId(npcUuid);
        if (npc == null) return NPCType.DUTY;
        
        String typeStr = npc.data().get("npc-type");
        try {
            return NPCType.valueOf(typeStr);
        } catch (Exception e) {
            return NPCType.DUTY;
        }
    }

    public GuiSection getGuiSection(UUID npcUuid) {
        if (!citizensEnabled) return null;
        
        NPC npc = CitizensAPI.getNPCRegistry().getByUniqueId(npcUuid);
        if (npc == null) return null;
        
        String sectionStr = npc.data().get("gui-section");
        try {
            return GuiSection.valueOf(sectionStr);
        } catch (Exception e) {
            return null;
        }
    }

    public void shutdown() {
        if (!citizensEnabled) return;
        
        try {
            npcConfig.save(npcFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save npcs.yml", e);
        }
    }

    public String getDefaultNpcSkin() {
        return npcSkin;
    }

    public boolean isNearCorrectionsNpc(Player player) {
        if (!citizensEnabled) return false;
        
        Location playerLoc = player.getLocation();
        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            if (npc.data().has(NPC_DATA_KEY) && 
                npc.getStoredLocation().getWorld().equals(playerLoc.getWorld()) &&
                npc.getStoredLocation().distance(playerLoc) <= interactionRadius) {
                return true;
            }
        }
        return false;
    }
}