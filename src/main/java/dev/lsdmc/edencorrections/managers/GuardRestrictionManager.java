package dev.lsdmc.edencorrections.managers;

import dev.lsdmc.edencorrections.EdenCorrections;
import dev.lsdmc.edencorrections.config.ConfigManager;
import dev.lsdmc.edencorrections.utils.MessageUtils;
import dev.lsdmc.edencorrections.utils.RegionUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Manages restrictions on guard activities
 * Updated to use centralized configuration management
 */
public class GuardRestrictionManager {
    private final EdenCorrections plugin;
    private final ConfigManager configManager;
    private ConfigManager.GuardRestrictionConfig config;

    private final Set<String> restrictedBlocks = new HashSet<>();
    private final Set<String> exceptedBlocks = new HashSet<>();
    private final Set<String> exemptRegions = new HashSet<>();
    private final Set<String> restrictedRegions = new HashSet<>();
    private final Set<String> restrictedCommands = new HashSet<>();
    private final Map<String, Set<Material>> tagCache = new HashMap<>();

    public GuardRestrictionManager(EdenCorrections plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        loadConfig();
    }

    private void loadConfig() {
        this.config = configManager.getGuardRestrictionConfig();

        // Clear previous settings
        restrictedBlocks.clear();
        exceptedBlocks.clear();
        exemptRegions.clear();
        restrictedRegions.clear();
        restrictedCommands.clear();
        tagCache.clear();

        // Only process if feature is enabled
        if (!config.enabled) {
            if (configManager.isDebugEnabled()) {
                plugin.getLogger().info("Guard restrictions disabled");
            }
            return;
        }

        // Load block breaking restrictions
        if (config.blockBreaking.enabled) {
            if (config.blockBreaking.restrictedBlocks != null) {
                restrictedBlocks.addAll(config.blockBreaking.restrictedBlocks);
            }
            if (config.blockBreaking.exceptions != null) {
                exceptedBlocks.addAll(config.blockBreaking.exceptions);
            }
            if (config.blockBreaking.exemptRegions != null) {
                exemptRegions.addAll(config.blockBreaking.exemptRegions);
            }
            preloadTagCache();
        }

        // Load movement restrictions
        if (config.movement.enabled && config.movement.restrictedRegions != null) {
            restrictedRegions.addAll(config.movement.restrictedRegions);
        }

        // Load command restrictions
        if (config.commands.enabled && config.commands.restrictedCommands != null) {
            restrictedCommands.addAll(config.commands.restrictedCommands);
        }

        if (configManager.isDebugEnabled()) {
            plugin.getLogger().info("Guard restrictions enabled");
            plugin.getLogger().info("Restricted blocks: " + restrictedBlocks.size());
            plugin.getLogger().info("Exception blocks: " + exceptedBlocks.size());
            plugin.getLogger().info("Exempt regions: " + exemptRegions.size());
            plugin.getLogger().info("Restricted regions: " + restrictedRegions.size());
            plugin.getLogger().info("Restricted commands: " + restrictedCommands.size());
            plugin.getLogger().info("Cached tags: " + tagCache.size());
        }
    }

    /**
     * Preload material tags for faster lookup
     */
    private void preloadTagCache() {
        // Process restricted blocks
        for (String entry : new ArrayList<>(restrictedBlocks)) {
            if (entry.startsWith("#")) {
                String tagName = entry.substring(1);
                Set<Material> materials = getTaggedMaterials(tagName);
                if (!materials.isEmpty()) {
                    tagCache.put("restricted:" + tagName, materials);
                    if (configManager.isDebugEnabled()) {
                        plugin.getLogger().info("Cached restricted tag '" + tagName + "' with " + materials.size() + " materials");
                    }
                } else {
                    plugin.getLogger().warning("Unknown block tag in restrictions: " + entry);
                    restrictedBlocks.remove(entry);
                }
            }
        }

        // Process excepted blocks
        for (String entry : new ArrayList<>(exceptedBlocks)) {
            if (entry.startsWith("#")) {
                String tagName = entry.substring(1);
                Set<Material> materials = getTaggedMaterials(tagName);
                if (!materials.isEmpty()) {
                    tagCache.put("excepted:" + tagName, materials);
                    if (configManager.isDebugEnabled()) {
                        plugin.getLogger().info("Cached exception tag '" + tagName + "' with " + materials.size() + " materials");
                    }
                } else {
                    plugin.getLogger().warning("Unknown block tag in exceptions: " + entry);
                    exceptedBlocks.remove(entry);
                }
            }
        }

        // Cache common material lookups
        for (String entry : restrictedBlocks) {
            if (!entry.startsWith("#")) {
                try {
                    Material material = Material.valueOf(entry.toUpperCase());
                    tagCache.put("material:" + entry.toLowerCase(), Set.of(material));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid material in restrictions: " + entry);
                    restrictedBlocks.remove(entry);
                }
            }
        }

        for (String entry : exceptedBlocks) {
            if (!entry.startsWith("#")) {
                try {
                    Material material = Material.valueOf(entry.toUpperCase());
                    tagCache.put("material:" + entry.toLowerCase(), Set.of(material));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid material in exceptions: " + entry);
                    exceptedBlocks.remove(entry);
                }
            }
        }
    }

    /**
     * Get materials for a tag
     * @param tagName Name of the tag
     * @return Set of materials for the tag
     */
    private Set<Material> getTaggedMaterials(String tagName) {
        Set<Material> materials = new HashSet<>();

        // Try standard Minecraft tags
        Tag<Material> tag = null;
        try {
            // Using reflection to get the tag field
            java.lang.reflect.Field field = Tag.class.getDeclaredField(tagName.toUpperCase());
            field.setAccessible(true);
            tag = (Tag<Material>) field.get(null);
        } catch (Exception ignored) {
            // Tag might not exist as a field, continue with other methods
        }

        if (tag != null) {
            return tag.getValues();
        }

        // Check if it's a Bukkit Tag
        try {
            Tag<Material> bukkitTag = Bukkit.getTag(Tag.REGISTRY_BLOCKS,
                    new org.bukkit.NamespacedKey("minecraft", tagName.toLowerCase()), Material.class);
            if (bukkitTag != null) {
                return bukkitTag.getValues();
            }
        } catch (Exception ignored) {
            // Tag might not exist in Bukkit registry
        }

        return materials;
    }

    /**
     * Check if a block is restricted for guards
     * @param block The block to check
     * @param player The player breaking the block
     * @return True if the block is restricted
     */
    public boolean isBlockRestricted(Block block, Player player) {
        if (!config.enabled || !config.blockBreaking.enabled) return false;

        // Check if player is a guard on duty
        if (!plugin.getDutyManager().isOnDuty(player.getUniqueId())) return false;

        // Check exempt regions first (most likely to allow action)
        for (String region : exemptRegions) {
            if (RegionUtils.isPlayerInRegion(player, region)) {
                if (configManager.isDebugEnabled()) {
                    plugin.getLogger().info("Player " + player.getName() + " is in exempt region " + region);
                }
                return false;
            }
        }

        Material material = block.getType();

        // Check exceptions first (more likely to allow action)
        // Check cached material exceptions
        String materialKey = "material:" + material.name().toLowerCase();
        if (tagCache.containsKey(materialKey)) {
            return false;
        }

        // Check cached tag exceptions
        for (Map.Entry<String, Set<Material>> entry : tagCache.entrySet()) {
            if (entry.getKey().startsWith("excepted:") && entry.getValue().contains(material)) {
                if (configManager.isDebugEnabled()) {
                    plugin.getLogger().info("Block " + material + " is in exception tag cache");
                }
                return false;
            }
        }

        // Check restrictions
        // Check cached material restrictions
        if (tagCache.containsKey(materialKey)) {
            if (configManager.isDebugEnabled()) {
                plugin.getLogger().info("Block " + material + " is in restricted materials cache");
            }
            return true;
        }

        // Check cached tag restrictions
        for (Map.Entry<String, Set<Material>> entry : tagCache.entrySet()) {
            if (entry.getKey().startsWith("restricted:") && entry.getValue().contains(material)) {
                if (configManager.isDebugEnabled()) {
                    plugin.getLogger().info("Block " + material + " is in restricted tag cache");
                }
                return true;
            }
        }

        return false;
    }

    /**
     * Handle a guard attempting to break a restricted block
     */
    public void handleRestrictedBlockBreak(Player player) {
        if (config.blockBreaking.message != null && !config.blockBreaking.message.isEmpty()) {
            Component message = MessageUtils.parseMessage(config.blockBreaking.message);
            player.sendMessage(message);
        }

        if (configManager.isDebugEnabled()) {
            plugin.getLogger().info("Blocked " + player.getName() + " from breaking restricted block");
        }
    }

    /**
     * Check if a player is a guard
     * @param player The player to check
     * @return True if the player is a guard
     */
    public boolean isPlayerGuard(Player player) {
        return plugin.getDutyManager().isOnDuty(player.getUniqueId());
    }

    /**
     * Get all restricted blocks (for debugging/admin purposes)
     * @return Set of restricted block strings
     */
    public Set<String> getRestrictedBlocks() {
        return new HashSet<>(restrictedBlocks);
    }

    /**
     * Get all exception blocks (for debugging/admin purposes)
     * @return Set of exception block strings
     */
    public Set<String> getExceptionBlocks() {
        return new HashSet<>(exceptedBlocks);
    }

    /**
     * Get all exempt regions (for debugging/admin purposes)
     * @return Set of exempt region names
     */
    public Set<String> getExemptRegions() {
        return new HashSet<>(exemptRegions);
    }

    /**
     * Get the restriction message
     * @return The configured restriction message
     */
    public String getRestrictionMessage() {
        return config.blockBreaking.message;
    }

    /**
     * Check if restrictions are enabled
     * @return True if enabled
     */
    public boolean areRestrictionsEnabled() {
        return config.enabled;
    }

    /**
     * Get statistics about the restriction system
     * @return Map with various statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("enabled", config.enabled);
        stats.put("restrictedBlocks", restrictedBlocks.size());
        stats.put("exceptionBlocks", exceptedBlocks.size());
        stats.put("exemptRegions", exemptRegions.size());
        stats.put("restrictedRegions", restrictedRegions.size());
        stats.put("restrictedCommands", restrictedCommands.size());
        stats.put("cachedTags", tagCache.size());

        // Count materials in cached tags
        int totalCachedMaterials = 0;
        for (Set<Material> materials : tagCache.values()) {
            totalCachedMaterials += materials.size();
        }
        stats.put("totalCachedMaterials", totalCachedMaterials);

        return stats;
    }

    /**
     * Reload the manager configuration
     */
    public void reload() {
        loadConfig();
        plugin.getLogger().info("GuardRestrictionManager reloaded successfully");
    }

    /**
     * Check if a command is restricted for guards
     */
    public boolean isCommandRestricted(String command) {
        if (!config.enabled || !config.commands.enabled) return false;
        
        String cmdLower = command.toLowerCase();
        return restrictedCommands.stream()
                .anyMatch(restricted -> cmdLower.startsWith(restricted.toLowerCase()));
    }

    /**
     * Check if a region is restricted for guards
     */
    public boolean isRegionRestricted(String region) {
        if (!config.enabled || !config.movement.enabled) return false;
        return restrictedRegions.contains(region.toLowerCase());
    }

    /**
     * Handle a guard attempting to use a restricted command
     */
    public void handleRestrictedCommand(Player player) {
        if (config.commands.message != null && !config.commands.message.isEmpty()) {
            Component message = MessageUtils.parseMessage(config.commands.message);
            player.sendMessage(message);
        }
    }

    /**
     * Handle a guard attempting to enter a restricted region
     */
    public void handleRestrictedRegion(Player player) {
        if (config.movement.message != null && !config.movement.message.isEmpty()) {
            Component message = MessageUtils.parseMessage(config.movement.message);
            player.sendMessage(message);
        }
    }

    /**
     * Get the movement restriction message
     * @return The configured movement restriction message
     */
    public String getMovementRestrictionMessage() {
        return config.movement.message;
    }

    /**
     * Get the command restriction message
     * @return The configured command restriction message
     */
    public String getCommandRestrictionMessage() {
        return config.commands.message;
    }

    /**
     * Get the block breaking restriction message
     * @return The configured block breaking restriction message
     */
    public String getBlockBreakingRestrictionMessage() {
        return config.blockBreaking.message;
    }
}