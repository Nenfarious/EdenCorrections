package dev.lsdmc.edencorrections.managers;

import dev.lsdmc.edencorrections.EdenCorrections;
import dev.lsdmc.edencorrections.listeners.GuardRestrictionListener;
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
 */
public class GuardRestrictionManager {
    private final EdenCorrections plugin;
    private boolean restrictionsEnabled;
    private final Set<String> restrictedBlocks = new HashSet<>();
    private final Set<String> exceptedBlocks = new HashSet<>();
    private final Set<String> exemptRegions = new HashSet<>();
    private final Map<String, Set<Material>> tagCache = new HashMap<>();
    private String restrictionMessage;

    public GuardRestrictionManager(EdenCorrections plugin) {
        this.plugin = plugin;
        loadConfig();

        // Register this manager with the plugin
        plugin.getServer().getPluginManager().registerEvents(new GuardRestrictionListener(this, plugin), plugin);
    }

    private void loadConfig() {
        restrictionsEnabled = plugin.getConfig().getBoolean("guard-restrictions.block-breaking.enabled", true);

        // Clear previous settings
        restrictedBlocks.clear();
        exceptedBlocks.clear();
        exemptRegions.clear();
        tagCache.clear();

        // Only process the rest if the feature is enabled
        if (restrictionsEnabled) {
            // Load restricted blocks
            List<String> blocks = plugin.getConfig().getStringList("guard-restrictions.block-breaking.restricted-blocks");
            restrictedBlocks.addAll(blocks);

            // Load excepted blocks
            List<String> exceptions = plugin.getConfig().getStringList("guard-restrictions.block-breaking.exceptions");
            exceptedBlocks.addAll(exceptions);

            // Load exempt regions
            List<String> regions = plugin.getConfig().getStringList("guard-restrictions.block-breaking.exempt-regions");
            exemptRegions.addAll(regions);

            // Load message
            restrictionMessage = plugin.getConfig().getString("guard-restrictions.block-breaking.message",
                    "&8[&4&l𝕏&8] &cYou are not allowed to farm as a guard!");

            // Preload tag cache for performance
            preloadTagCache();
        }
    }

    /**
     * Preload material tags for faster lookup
     */
    private void preloadTagCache() {
        for (String entry : new ArrayList<>(restrictedBlocks)) {
            if (entry.startsWith("#")) {
                String tagName = entry.substring(1);
                Set<Material> materials = getTaggedMaterials(tagName);
                if (!materials.isEmpty()) {
                    tagCache.put(tagName, materials);
                } else {
                    plugin.getLogger().warning("Unknown block tag in restrictions: " + entry);
                    restrictedBlocks.remove(entry);
                }
            }
        }

        for (String entry : new ArrayList<>(exceptedBlocks)) {
            if (entry.startsWith("#")) {
                String tagName = entry.substring(1);
                Set<Material> materials = getTaggedMaterials(tagName);
                if (!materials.isEmpty()) {
                    tagCache.put(tagName, materials);
                } else {
                    plugin.getLogger().warning("Unknown block tag in exceptions: " + entry);
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
            Tag<Material> bukkitTag = Bukkit.getTag(Tag.REGISTRY_BLOCKS, new org.bukkit.NamespacedKey("minecraft", tagName.toLowerCase()), Material.class);
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
        if (!restrictionsEnabled) return false;

        // Check if player is a guard
        if (!isPlayerGuard(player)) return false;

        // Check exempt regions
        for (String region : exemptRegions) {
            if (RegionUtils.isPlayerInRegion(player, region)) {
                return false;
            }
        }

        Material material = block.getType();
        String materialKey = material.getKey().toString();

        // Check exceptions first
        for (String exception : exceptedBlocks) {
            if (exception.startsWith("#")) {
                // Tag exception
                String tagName = exception.substring(1);
                Set<Material> taggedMaterials = tagCache.getOrDefault(tagName, new HashSet<>());
                if (taggedMaterials.contains(material)) {
                    return false;
                }
            } else if (materialKey.equals(exception) || material.toString().equalsIgnoreCase(exception)) {
                return false;
            }
        }

        // Check restrictions
        for (String restriction : restrictedBlocks) {
            if (restriction.startsWith("#")) {
                // Tag restriction
                String tagName = restriction.substring(1);
                Set<Material> taggedMaterials = tagCache.getOrDefault(tagName, new HashSet<>());
                if (taggedMaterials.contains(material)) {
                    return true;
                }
            } else if (materialKey.equals(restriction) || material.toString().equalsIgnoreCase(restriction)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Handle a guard attempting to break a restricted block
     * @param player The player breaking the block
     */
    public void handleRestrictedBlockBreak(Player player) {
        Component message = MessageUtils.parseMessage(restrictionMessage);
        player.sendMessage(message);
    }

    /**
     * Check if a player is a guard
     * @param player The player to check
     * @return True if the player is a guard
     */
    public boolean isPlayerGuard(Player player) {
        return player.hasPermission("edenprison.guard") || player.hasPermission("edencorrections.duty");
    }

    /**
     * Reload the manager configuration
     */
    public void reload() {
        loadConfig();
    }
}