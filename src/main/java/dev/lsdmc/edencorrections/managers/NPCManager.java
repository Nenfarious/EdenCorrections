package dev.lsdmc.edencorrections.managers;

import dev.lsdmc.edencorrections.EdenCorrections;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
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

public class NPCManager implements Listener {
    private final EdenCorrections plugin;
    private final File npcFile;
    private FileConfiguration npcConfig;
    private final int interactionRadius;
    private final boolean useCitizens;
    private final String npcName;
    private final NamespacedKey dutyNpcKey;

    // List of duty NPC UUIDs (for both ArmorStand and Citizens NPCs)
    private final List<UUID> dutyNpcs = new ArrayList<>();

    // Citizens integration flag
    private boolean citizensEnabled = false;

    public NPCManager(EdenCorrections plugin) {
        this.plugin = plugin;
        this.dutyNpcKey = new NamespacedKey(plugin, "duty_npc");

        // Get config values
        FileConfiguration config = plugin.getConfig();
        this.interactionRadius = config.getInt("duty.npc.interaction-radius", 3);
        this.useCitizens = config.getBoolean("duty.npc.use-citizens", false);
        this.npcName = ChatColor.translateAlternateColorCodes('&',
                config.getString("duty.npc.name", "&b&lDuty Officer"));

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
        dutyNpcs.clear();

        ConfigurationSection section = npcConfig.getConfigurationSection("npcs");
        if (section == null) return;

        // Load each NPC UUID
        for (String key : section.getKeys(false)) {
            try {
                UUID npcUuid = UUID.fromString(key);
                dutyNpcs.add(npcUuid);

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
                        dutyNpcs.remove(npcUuid);
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
     * Create a duty NPC at the specified location
     * @param location The location to create the NPC at
     * @param createdBy The player who created the NPC
     * @return True if NPC was created successfully, false otherwise
     */
    public boolean createNpc(Location location, Player createdBy) {
        if (citizensEnabled) {
            return createCitizensNpc(location, createdBy);
        } else {
            return createArmorStandNpc(location, createdBy);
        }
    }

    /**
     * Create a Citizens NPC at the specified location
     */
    private boolean createCitizensNpc(Location location, Player createdBy) {
        try {
            // Create Citizens NPC using their API
            CitizensAPI.getNPCRegistry();
            NPC npc = CitizensAPI.getNPCRegistry().createNPC(
                    EntityType.PLAYER, npcName);

            // Set NPC traits
            npc.data().setPersistent(NPC.Metadata.NAMEPLATE_VISIBLE, true);
            npc.data().setPersistent("player-skin-name", "Police");

            // Spawn the NPC
            npc.spawn(location);

            // Save NPC info
            UUID npcUuid = npc.getEntity().getUniqueId();
            dutyNpcs.add(npcUuid);

            npcConfig.set("npcs." + npcUuid.toString() + ".citizens", true);
            npcConfig.set("npcs." + npcUuid.toString() + ".world", location.getWorld().getName());
            npcConfig.set("npcs." + npcUuid.toString() + ".x", location.getX());
            npcConfig.set("npcs." + npcUuid.toString() + ".y", location.getY());
            npcConfig.set("npcs." + npcUuid.toString() + ".z", location.getZ());
            npcConfig.set("npcs." + npcUuid.toString() + ".created-by", createdBy.getUniqueId().toString());

            try {
                npcConfig.save(npcFile);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save npcs.yml", e);
            }

            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create Citizens NPC", e);
            return false;
        }
    }

    /**
     * Create an ArmorStand NPC at the specified location
     */
    private boolean createArmorStandNpc(Location location, Player createdBy) {
        try {
            // Spawn armor stand
            ArmorStand armorStand = (ArmorStand) location.getWorld().spawnEntity(location, EntityType.ARMOR_STAND);

            // Configure armor stand
            armorStand.setCustomName(npcName);
            armorStand.setCustomNameVisible(true);
            armorStand.setVisible(true);
            armorStand.setSmall(false);
            armorStand.setArms(true);
            armorStand.setBasePlate(true);
            armorStand.setGravity(false);
            armorStand.setInvulnerable(true);

            // Mark as duty NPC
            PersistentDataContainer container = armorStand.getPersistentDataContainer();
            container.set(dutyNpcKey, PersistentDataType.BYTE, (byte) 1);

            // Add to list
            UUID npcUuid = armorStand.getUniqueId();
            dutyNpcs.add(npcUuid);

            // Save to config
            npcConfig.set("npcs." + npcUuid.toString() + ".citizens", false);
            npcConfig.set("npcs." + npcUuid.toString() + ".world", location.getWorld().getName());
            npcConfig.set("npcs." + npcUuid.toString() + ".x", location.getX());
            npcConfig.set("npcs." + npcUuid.toString() + ".y", location.getY());
            npcConfig.set("npcs." + npcUuid.toString() + ".z", location.getZ());
            npcConfig.set("npcs." + npcUuid.toString() + ".created-by", createdBy.getUniqueId().toString());

            try {
                npcConfig.save(npcFile);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save npcs.yml", e);
            }

            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create armor stand NPC", e);
            return false;
        }
    }

    /**
     * Remove a duty NPC
     * @param npcUuid The UUID of the NPC to remove
     * @return True if NPC was removed successfully, false otherwise
     */
    public boolean removeNpc(UUID npcUuid) {
        if (!dutyNpcs.contains(npcUuid)) {
            return false;
        }

        // Get NPC info
        boolean isCitizens = npcConfig.getBoolean("npcs." + npcUuid.toString() + ".citizens", false);
        String worldName = npcConfig.getString("npcs." + npcUuid.toString() + ".world");

        if (worldName == null) {
            // Just remove from list and config
            dutyNpcs.remove(npcUuid);
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
            dutyNpcs.remove(npcUuid);
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
        dutyNpcs.remove(npcUuid);
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
        if (event.getHand() != EquipmentSlot.HAND) return;

        Entity entity = event.getRightClicked();
        Player player = event.getPlayer();

        // Check if entity is a duty NPC
        if (dutyNpcs.contains(entity.getUniqueId()) ||
                (entity instanceof ArmorStand && entity.getPersistentDataContainer().has(dutyNpcKey, PersistentDataType.BYTE))) {
            // Handle duty toggle
            plugin.getDutyManager().toggleDuty(player);
            event.setCancelled(true);
        }
    }

    /**
     * Check if a player is near a duty NPC
     * @param player The player to check
     * @return True if player is near a duty NPC, false otherwise
     */
    public boolean isNearDutyNpc(Player player) {
        // If NPC system is disabled, return false
        if (!plugin.getConfig().getBoolean("duty.npc.enabled", false)) {
            return false;
        }

        Location playerLoc = player.getLocation();
        double radiusSquared = interactionRadius * interactionRadius;

        // Check each NPC
        for (UUID npcUuid : dutyNpcs) {
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
     * Get the list of duty NPCs
     * @return The list of duty NPC UUIDs
     */
    public List<UUID> getDutyNpcs() {
        return new ArrayList<>(dutyNpcs);
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
}