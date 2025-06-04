package dev.lsdmc.edencorrections.managers;

import dev.lsdmc.edencorrections.EdenCorrections;
import dev.lsdmc.edencorrections.config.ConfigManager;
import dev.lsdmc.edencorrections.utils.MessageUtils;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.NamespacedKey;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.Map;
import java.util.HashMap;

public class NPCManager implements Listener {
    private final EdenCorrections plugin;
    private final ConfigManager configManager;
    private ConfigManager.InterfaceConfig interfaceConfig;
    private final File npcFile;
    private FileConfiguration npcConfig;
    private final Map<String, NPC> dutyNPCs = new HashMap<>();
    private boolean npcEnabled;

    // List of Corrections NPC UUIDs (for both ArmorStand and Citizens NPCs)
    private final List<UUID> correctionsNpcs = new ArrayList<>();

    // Citizens integration flag
    private boolean citizensEnabled = false;

    // Add per-player cooldown map
    private final java.util.Map<java.util.UUID, Long> lastNpcInteraction = new java.util.HashMap<>();

    // Key for marking ArmorStand NPCs as EdenCorrections NPCs
    private final NamespacedKey correctionsNpcKey;

    private final int interactionRadius;
    private final boolean useCitizens;
    private final String npcSkin; // Default skin for Citizens NPCs

    public enum NPCType { DUTY, GUI }
    public enum GuiSection { MAIN, DUTY, STATS, ACTIONS, EQUIPMENT, SHOP, TOKENS }

    public NPCManager(EdenCorrections plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.interfaceConfig = configManager.getInterfaceConfig();
        this.correctionsNpcKey = new NamespacedKey(plugin, "duty_npc");

        // Get config values
        FileConfiguration config = configManager.getConfig("config.yml");
        this.interactionRadius = config != null ? config.getInt("npc.interaction-radius", 3) : 3;
        this.useCitizens = config != null ? config.getBoolean("npc.use-citizens", false) : false;
        this.npcSkin = config != null ? config.getString("npc.skin", "PoliceOfficer") : "PoliceOfficer";

        // Check for Citizens
        if (useCitizens && Bukkit.getPluginManager().getPlugin("Citizens") != null) {
            plugin.getLogger().info("Citizens found, enabling NPC integration");
            citizensEnabled = true;
        } else if (useCitizens) {
            plugin.getLogger().warning("Citizens plugin not found but use-citizens is enabled. Falling back to ArmorStand NPCs.");
        }

        // Initialize NPC file
        npcFile = new File(plugin.getDataFolder(), "npcs.yml");
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

    /**
     * Load all NPCs from configuration
     */
    private void loadNpcs() {
        correctionsNpcs.clear();

        ConfigurationSection section = npcConfig.getConfigurationSection("npcs");
        if (section == null) return;

        // Load each NPC UUID
        for (String key : section.getKeys(false)) {
            try {
                UUID npcUuid = UUID.fromString(key);
                correctionsNpcs.add(npcUuid);

                // For ArmorStand NPCs, verify they still exist
                if (!citizensEnabled || !section.getBoolean(key + ".citizens")) {
                    String worldName = section.getString(key + ".world");
                    if (worldName == null) continue;

                    World world = Bukkit.getWorld(worldName);
                    if (world == null) continue;

                    // Try to find the entity in the world
                    boolean found = false;
                    for (Entity entity : world.getEntities()) {
                        if (entity.getUniqueId().equals(npcUuid) && entity instanceof ArmorStand) {
                            found = true;
                            break;
                        }
                    }

                    // If not found, remove from list
                    if (!found) {
                        plugin.getLogger().warning("NPC with UUID " + npcUuid + " not found in world " + worldName);
                        correctionsNpcs.remove(npcUuid);
                        section.set(key, null);
                    }
                }
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid UUID in npcs.yml: " + key);
            }
        }

        // Save any changes
        try {
            npcConfig.save(npcFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save npcs.yml", e);
        }
    }

    /**
     * Create a Corrections NPC at the specified location
     * @param location The location to create the NPC at
     * @param createdBy The player who created the NPC
     * @param name The name of the NPC
     * @param type The type of the NPC
     * @param guiSection The GUI section of the NPC
     * @return True if NPC was created successfully, false otherwise
     */
    public boolean createNpc(Location location, Player createdBy, String name, NPCType type, GuiSection guiSection) {
        if (citizensEnabled) {
            return createCitizensNpc(location, createdBy, name, type, guiSection);
        } else {
            return createArmorStandNpc(location, createdBy, name, type, guiSection);
        }
    }

    /**
     * Overload for backward compatibility
     * @param location The location to create the NPC at
     * @param createdBy The player who created the NPC
     * @param name The name of the NPC
     * @return True if NPC was created successfully, false otherwise
     */
    public boolean createNpc(Location location, Player createdBy, String name) {
        return createNpc(location, createdBy, name, NPCType.DUTY, null);
    }

    /**
     * Create a Citizens NPC at the specified location
     */
    private boolean createCitizensNpc(Location location, Player createdBy, String name, NPCType type, GuiSection guiSection) {
        try {
            CitizensAPI.getNPCRegistry();
            NPC npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, name);
            npc.data().setPersistent(NPC.Metadata.NAMEPLATE_VISIBLE, true);
            npc.data().setPersistent("player-skin-name", npcSkin);
            npc.spawn(location);
            UUID npcUuid = npc.getEntity().getUniqueId();
            correctionsNpcs.add(npcUuid);
            String base = "npcs." + npcUuid.toString();
            npcConfig.set(base + ".citizens", true);
            npcConfig.set(base + ".world", location.getWorld().getName());
            npcConfig.set(base + ".x", location.getX());
            npcConfig.set(base + ".y", location.getY());
            npcConfig.set(base + ".z", location.getZ());
            npcConfig.set(base + ".created-by", createdBy.getUniqueId().toString());
            npcConfig.set(base + ".name", name);
            npcConfig.set(base + ".type", type.name());
            if (type == NPCType.GUI && guiSection != null) {
                npcConfig.set(base + ".gui-section", guiSection.name());
            }
            try { npcConfig.save(npcFile); } catch (IOException e) { plugin.getLogger().log(Level.SEVERE, "Failed to save npcs.yml", e); }
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create Citizens NPC", e);
            return false;
        }
    }

    /**
     * Create an ArmorStand NPC at the specified location
     */
    private boolean createArmorStandNpc(Location location, Player createdBy, String name, NPCType type, GuiSection guiSection) {
        try {
            ArmorStand armorStand = (ArmorStand) location.getWorld().spawnEntity(location, EntityType.ARMOR_STAND);
            armorStand.setCustomName(name);
            armorStand.setCustomNameVisible(true);
            armorStand.setVisible(true);
            armorStand.setGravity(false);
            armorStand.setInvulnerable(true);
            armorStand.setCollidable(false);
            armorStand.setBasePlate(false);
            armorStand.setArms(true);
            PersistentDataContainer container = armorStand.getPersistentDataContainer();
            container.set(correctionsNpcKey, PersistentDataType.BYTE, (byte) 1);
            UUID npcUuid = armorStand.getUniqueId();
            correctionsNpcs.add(npcUuid);
            String base = "npcs." + npcUuid.toString();
            npcConfig.set(base + ".citizens", false);
            npcConfig.set(base + ".world", location.getWorld().getName());
            npcConfig.set(base + ".x", location.getX());
            npcConfig.set(base + ".y", location.getY());
            npcConfig.set(base + ".z", location.getZ());
            npcConfig.set(base + ".created-by", createdBy.getUniqueId().toString());
            npcConfig.set(base + ".name", name);
            npcConfig.set(base + ".type", type.name());
            if (type == NPCType.GUI && guiSection != null) {
                npcConfig.set(base + ".gui-section", guiSection.name());
            }
            try { npcConfig.save(npcFile); } catch (IOException e) { plugin.getLogger().log(Level.SEVERE, "Failed to save npcs.yml", e); }
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create ArmorStand NPC", e);
            return false;
        }
    }

    /**
     * Remove a Corrections NPC
     * @param npcUuid The UUID of the NPC to remove
     * @return True if NPC was removed successfully, false otherwise
     */
    public boolean removeNpc(UUID npcUuid) {
        if (!correctionsNpcs.contains(npcUuid)) {
            return false;
        }

        // Get NPC info
        boolean isCitizens = npcConfig.getBoolean("npcs." + npcUuid.toString() + ".citizens", false);
        String worldName = npcConfig.getString("npcs." + npcUuid.toString() + ".world");

        if (worldName == null) {
            // Just remove from list and config
            correctionsNpcs.remove(npcUuid);
            npcConfig.set("npcs." + npcUuid.toString(), null);
            try {
                npcConfig.save(npcFile);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save npcs.yml", e);
            }
            return true;
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            // World doesn't exist, just remove from list and config
            correctionsNpcs.remove(npcUuid);
            npcConfig.set("npcs." + npcUuid.toString(), null);
            try {
                npcConfig.save(npcFile);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save npcs.yml", e);
            }
            return true;
        }

        // Handle removal differently based on NPC type
        if (isCitizens) {
            try {
                // Remove Citizens NPC
                net.citizensnpcs.api.CitizensAPI.getNPCRegistry().getByUniqueId(npcUuid).destroy();
            } catch (Exception ignored) {
                // NPC might not exist anymore
            }
        } else {
            // Find and remove armor stand
            for (Entity entity : world.getEntities()) {
                if (entity.getUniqueId().equals(npcUuid)) {
                    entity.remove();
                    break;
                }
            }
        }

        // Remove from list and config
        correctionsNpcs.remove(npcUuid);
        npcConfig.set("npcs." + npcUuid.toString(), null);
        try {
            npcConfig.save(npcFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save npcs.yml", e);
        }

        return true;
    }

    /**
     * Handle NPC interaction event
     */
    @EventHandler
    public void onNpcInteract(PlayerInteractAtEntityEvent event) {
        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        if (lastNpcInteraction.containsKey(player.getUniqueId()) && now - lastNpcInteraction.get(player.getUniqueId()) < 2000) {
            event.setCancelled(true);
            return;
        }
        lastNpcInteraction.put(player.getUniqueId(), now);
        if (event.getHand() != EquipmentSlot.HAND) return;
        Entity entity = event.getRightClicked();
        UUID npcUuid = entity.getUniqueId();
        if (correctionsNpcs.contains(npcUuid) || (entity instanceof ArmorStand && entity.getPersistentDataContainer().has(correctionsNpcKey, PersistentDataType.BYTE))) {
            NPCType type = getNpcType(npcUuid);
            if (type == NPCType.DUTY) {
                plugin.getDutyManager().toggleDuty(player);
            } else if (type == NPCType.GUI) {
                GuiSection section = getGuiSection(npcUuid);
                if (section != null) {
                    openGuiSection(player, section);
                }
            }
            event.setCancelled(true);
        }
    }

    /**
     * Check if a player is near a Corrections NPC
     * @param player The player to check
     * @return True if player is near a Corrections NPC, false otherwise
     */
    public boolean isNearCorrectionsNpc(Player player) {
        // If NPC system is disabled, return false
        FileConfiguration config = configManager.getConfig("config.yml");
        if (config == null || !config.getBoolean("npc.enabled", false)) {
            return false;
        }

        Location playerLoc = player.getLocation();
        double radiusSquared = interactionRadius * interactionRadius;

        // Check each NPC
        for (UUID npcUuid : correctionsNpcs) {
            // Get NPC info
            String worldName = npcConfig.getString("npcs." + npcUuid.toString() + ".world");
            if (worldName == null) continue;

            // Skip if not in same world
            if (!player.getWorld().getName().equals(worldName)) continue;

            double x = npcConfig.getDouble("npcs." + npcUuid.toString() + ".x");
            double y = npcConfig.getDouble("npcs." + npcUuid.toString() + ".y");
            double z = npcConfig.getDouble("npcs." + npcUuid.toString() + ".z");

            // Calculate distance squared (more efficient than distance)
            double dx = playerLoc.getX() - x;
            double dy = playerLoc.getY() - y;
            double dz = playerLoc.getZ() - z;
            double distanceSquared = dx*dx + dy*dy + dz*dz;

            // Check if within radius
            if (distanceSquared <= radiusSquared) {
                return true;
            }
        }

        return false;
    }

    /**
     * Get the list of Corrections NPCs
     * @return The list of Corrections NPC UUIDs
     */
    public List<UUID> getCorrectionsNpcs() {
        return new ArrayList<>(correctionsNpcs);
    }

    /**
     * Shutdown the NPC manager
     */
    public void shutdown() {
        try {
            npcConfig.save(npcFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save npcs.yml", e);
        }
    }

    /**
     * Get the default skin for Citizens NPCs (from config)
     */
    public String getDefaultNpcSkin() {
        return npcSkin;
    }

    public NPCType getNpcType(UUID npcUuid) {
        String typeStr = npcConfig.getString("npcs." + npcUuid.toString() + ".type", "DUTY");
        try { return NPCType.valueOf(typeStr); } catch (Exception e) { return NPCType.DUTY; }
    }

    public GuiSection getGuiSection(UUID npcUuid) {
        String sectionStr = npcConfig.getString("npcs." + npcUuid.toString() + ".gui-section");
        if (sectionStr == null) return null;
        try { return GuiSection.valueOf(sectionStr); } catch (Exception e) { return null; }
    }

    private void openGuiSection(Player player, GuiSection section) {
        switch (section) {
            case MAIN -> plugin.getGuiManager().openMainMenu(player);
            case DUTY -> plugin.getGuiManager().openDutyMenu(player);
            case STATS -> plugin.getGuiManager().openStatsMenu(player);
            case ACTIONS -> plugin.getGuiManager().openActionsMenu(player);
            case EQUIPMENT -> plugin.getGuiManager().openEquipmentMenu(player);
            case SHOP -> plugin.getGuiManager().openShopMenu(player);
            case TOKENS -> plugin.getGuiManager().openStatsMenu(player); // fallback, or implement if needed
        }
    }

    private void loadConfiguration() {
        // Load NPC configuration from centralized config
        FileConfiguration config = configManager.getConfig("config.yml");
        this.npcEnabled = config != null ? config.getBoolean("npc.enabled", false) : false;
    }

    /**
     * Reload configuration
     */
    public void reload() {
        this.interfaceConfig = configManager.getInterfaceConfig();
        loadConfiguration();
        
        if (npcEnabled && isNPCPluginEnabled()) {
            loadNpcs();
        }
    }

    private boolean isNPCPluginEnabled() {
        // Implementation of isNPCPluginEnabled method
        return false; // Placeholder return, actual implementation needed
    }
}