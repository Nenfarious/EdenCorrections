package dev.lsdmc.edencorrections.utils;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import dev.lsdmc.edencorrections.EdenCorrections;
import dev.lsdmc.edencorrections.managers.GuardRankManager;

import java.util.*;
import java.util.logging.Level;

public class GuardLootProcessor {
    private final EdenCorrections plugin;
    private final GuardRankManager rankManager;
    private final Random random = new Random();

    public GuardLootProcessor(EdenCorrections plugin, GuardRankManager rankManager) {
        this.plugin = plugin;
        this.rankManager = rankManager;
    }

    /**
     * Generates loot items for a player based on their rank
     * @param player The player to generate loot for
     * @return A list of items to drop
     */
    public List<ItemStack> generateLootForPlayer(Player player) {
        List<ItemStack> loot = new ArrayList<>();
        String rank = null;

        try {
            // Get the player's rank with validation
            rank = rankManager.getPlayerRank(player);
            if (rank == null || rank.trim().isEmpty()) {
                plugin.getLogger().warning("No rank found for player " + player.getName() + ", using default loot table");
                rank = "trainee";
            }

            // Log the rank detection for debugging
            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().info("Generating loot for " + player.getName() + " with rank: " + rank);
            }

            // Get the loot table for the rank with validation
            String configPath = "guard-loot.ranks." + rank;
            if (!plugin.getConfig().contains(configPath)) {
                plugin.getLogger().warning("No loot table found for rank: " + rank + ", falling back to trainee");
                configPath = "guard-loot.ranks.trainee";
                if (!plugin.getConfig().contains(configPath)) {
                    plugin.getLogger().severe("No fallback loot table found! Check configuration.");
                    return ensureBasicEquipment(new ArrayList<>());
                }
            }

            // Process each category with proper error handling
            loot.addAll(processLootCategorySafely(player, configPath + ".armor", "armor"));
            loot.addAll(processLootCategorySafely(player, configPath + ".weapons", "weapons"));
            loot.addAll(processLootCategorySafely(player, configPath + ".resources", "resources"));

            // Always ensure basic equipment
            ensureBasicEquipment(loot);

            // Log all generated loot items
            if (plugin.getConfigManager().isDebugEnabled()) {
                logGeneratedLoot(player, loot);
            }

        } catch (Exception e) {
            plugin.getLogger().severe("Critical error generating loot for " + player.getName() + ": " + e.getMessage());
            e.printStackTrace();
            // Ensure player gets at least basic equipment even if there's an error
            return ensureBasicEquipment(loot);
        }

        return loot;
    }

    private List<ItemStack> processLootCategorySafely(Player player, String configPath, String category) {
        try {
            plugin.getLogger().info("Processing " + category + " items for " + player.getName());
            return processLootCategory(player, configPath);
        } catch (Exception e) {
            plugin.getLogger().warning("Error processing " + category + " items for " + player.getName() + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }

    private void logGeneratedLoot(Player player, List<ItemStack> loot) {
        plugin.getLogger().info("Generated " + loot.size() + " loot items for " + player.getName());
        for (ItemStack item : loot) {
            if (item != null) {
                plugin.getLogger().info("Loot item: " + item.getType() + " x" + item.getAmount() +
                        (item.getEnchantments().size() > 0 ? " with " + item.getEnchantments().size() + " enchantments" : ""));
            }
        }
    }

    private List<ItemStack> processLootCategory(Player player, String configPath) {
        if (!plugin.getConfig().contains(configPath)) {
            plugin.getLogger().warning("Config path not found: " + configPath);
            return new ArrayList<>();
        }

        List<Map<?, ?>> itemMaps = plugin.getConfig().getMapList(configPath);
        if (itemMaps.isEmpty()) {
            plugin.getLogger().warning("No items found at config path: " + configPath);
            return new ArrayList<>();
        }

        List<ItemStack> items = new ArrayList<>();
        for (Map<?, ?> itemMap : itemMaps) {
            if (!(itemMap instanceof Map)) {
                plugin.getLogger().warning("Invalid item map format at " + configPath);
                continue;
            }

            try {
                ItemStack item = processItemMap(itemMap);
                if (item != null) {
                    items.add(item);
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Error processing item in loot table", e);
            }
        }

        return items;
    }

    private ItemStack processItemMap(Map<?, ?> itemMap) {
        try {
            String itemName = String.valueOf(itemMap.get("item"));
            if (itemName == null || itemName.trim().isEmpty()) {
                return null;
            }

            Material material;
            try {
                material = Material.valueOf(itemName.toUpperCase());
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid material: " + itemName);
                return null;
            }

            ItemStack item = new ItemStack(material);
            
            // Process amount with validation
            Object amountObj = itemMap.get("amount");
            if (amountObj != null) {
                int amount = parseAmount(String.valueOf(amountObj));
                if (amount > 0) {
                    item.setAmount(amount);
                }
            }

            // Process enchantments with validation
            Object enchantsObj = itemMap.get("enchantments");
            if (enchantsObj instanceof List) {
                processEnchantments(item, (List<?>) enchantsObj);
            }

            return item;
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error processing item map", e);
            return null;
        }
    }

    private int parseAmount(String amountStr) {
        try {
            if (amountStr.contains("-")) {
                String[] parts = amountStr.split("-");
                if (parts.length == 2) {
                    int min = Integer.parseInt(parts[0].trim());
                    int max = Integer.parseInt(parts[1].trim());
                    return min + (int) (Math.random() * (max - min + 1));
                }
            }
            return Integer.parseInt(amountStr.trim());
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            plugin.getLogger().warning("Invalid amount format: " + amountStr + ". Using 1");
            return 1;
        }
    }

    private void processEnchantments(ItemStack item, List<?> enchantments) {
        for (Object enchantmentEntry : enchantments) {
            if (!(enchantmentEntry instanceof String)) {
                continue;
            }

            String[] parts = ((String) enchantmentEntry).split(":");
            if (parts.length != 2) {
                plugin.getLogger().warning("Invalid enchantment format: " + enchantmentEntry);
                continue;
            }

            try {
                String enchName = parts[0].trim().toUpperCase();
                int level = Integer.parseInt(parts[1].trim());

                Enchantment enchantment = getEnchantment(enchName);
                if (enchantment == null) {
                    plugin.getLogger().warning("Invalid enchantment: " + enchName);
                    continue;
                }

                if (level > 0 && level <= enchantment.getMaxLevel()) {
                    item.addEnchantment(enchantment, level);
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Error applying enchantment: " + enchantmentEntry, e);
            }
        }
    }

    private List<ItemStack> ensureBasicEquipment(List<ItemStack> loot) {
        // Check for and add basic equipment if missing
        boolean hasChestplate = false;
        boolean hasSword = false;
        boolean hasShield = false;

        for (ItemStack item : loot) {
            if (item == null) continue;
            Material type = item.getType();
            if (type == Material.IRON_CHESTPLATE || type == Material.DIAMOND_CHESTPLATE) hasChestplate = true;
            if (type == Material.IRON_SWORD || type == Material.DIAMOND_SWORD) hasSword = true;
            if (type == Material.SHIELD) hasShield = true;
        }

        // Add missing basic equipment
        if (!hasChestplate) loot.add(new ItemStack(Material.IRON_CHESTPLATE));
        if (!hasSword) loot.add(new ItemStack(Material.IRON_SWORD));
        if (!hasShield) loot.add(new ItemStack(Material.SHIELD));
        
        return loot;
    }

    /**
     * Get an enchantment by name using Paper/Spigot naming conventions
     * @param name Enchantment name
     * @return Enchantment object or null
     */
    private Enchantment getEnchantment(String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }

        // Try by direct name first (Paper uses logical names directly)
        try {
            Enchantment enchantment = Enchantment.getByName(name.toUpperCase());
            if (enchantment != null) {
                return enchantment;
            }

            // Try legacy names
            switch (name.toUpperCase()) {
                case "PROTECTION":
                    return Enchantment.PROTECTION;
                case "FIRE_PROTECTION":
                    return Enchantment.FIRE_PROTECTION;
                case "FEATHER_FALLING":
                    return Enchantment.FEATHER_FALLING;
                case "BLAST_PROTECTION":
                    return Enchantment.BLAST_PROTECTION;
                case "PROJECTILE_PROTECTION":
                    return Enchantment.PROJECTILE_PROTECTION;
                case "RESPIRATION":
                    return Enchantment.RESPIRATION;
                case "AQUA_AFFINITY":
                    return Enchantment.AQUA_AFFINITY;
                case "THORNS":
                    return Enchantment.THORNS;
                case "DEPTH_STRIDER":
                    return Enchantment.DEPTH_STRIDER;
                case "FROST_WALKER":
                    return Enchantment.FROST_WALKER;
                case "BINDING_CURSE":
                    return Enchantment.BINDING_CURSE;
                case "SHARPNESS":
                    return Enchantment.SHARPNESS;
                case "SMITE":
                    return Enchantment.SMITE;
                case "BANE_OF_ARTHROPODS":
                    return Enchantment.BANE_OF_ARTHROPODS;
                case "KNOCKBACK":
                    return Enchantment.KNOCKBACK;
                case "FIRE_ASPECT":
                    return Enchantment.FIRE_ASPECT;
                case "LOOTING":
                    return Enchantment.LOOTING;
                case "SWEEPING":
                    return Enchantment.SWEEPING_EDGE;
                case "EFFICIENCY":
                    return Enchantment.EFFICIENCY;
                case "SILK_TOUCH":
                    return Enchantment.SILK_TOUCH;
                case "UNBREAKING":
                    return Enchantment.UNBREAKING;
                case "FORTUNE":
                    return Enchantment.FORTUNE;
                case "POWER":
                    return Enchantment.POWER;
                case "PUNCH":
                    return Enchantment.PUNCH;
                case "FLAME":
                    return Enchantment.FLAME;
                case "INFINITY":
                    return Enchantment.INFINITY;
                case "LUCK_OF_THE_SEA":
                    return Enchantment.LUCK_OF_THE_SEA;
                case "LURE":
                    return Enchantment.LURE;
                case "LOYALTY":
                    return Enchantment.LOYALTY;
                case "IMPALING":
                    return Enchantment.IMPALING;
                case "RIPTIDE":
                    return Enchantment.RIPTIDE;
                case "CHANNELING":
                    return Enchantment.CHANNELING;
                case "MULTISHOT":
                    return Enchantment.MULTISHOT;
                case "QUICK_CHARGE":
                    return Enchantment.QUICK_CHARGE;
                case "PIERCING":
                    return Enchantment.PIERCING;
                case "MENDING":
                    return Enchantment.MENDING;
                case "VANISHING_CURSE":
                    return Enchantment.VANISHING_CURSE;
                case "SOUL_SPEED":
                    return Enchantment.SOUL_SPEED;
                case "SWIFT_SNEAK":
                    return Enchantment.SWIFT_SNEAK;
                default:
                    return null;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error getting enchantment: " + name);
            return null;
        }
    }
}