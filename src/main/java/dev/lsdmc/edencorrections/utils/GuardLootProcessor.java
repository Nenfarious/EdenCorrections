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

        // Get the player's rank
        String rank = rankManager.getPlayerRank(player);
        if (rank == null) return loot;

        // Log the rank detection for debugging
        plugin.getLogger().info("Generating loot for " + player.getName() + " with rank: " + rank);

        // Get the loot table for the rank
        String configPath = "guard-loot.ranks." + rank;
        if (!plugin.getConfig().contains(configPath)) {
            plugin.getLogger().warning("No loot table found for rank: " + rank);
            return loot;
        }

        // Process armor items
        plugin.getLogger().info("Processing armor items for " + player.getName());
        loot.addAll(processLootCategory(player, configPath + ".armor"));

        // Process weapon items
        plugin.getLogger().info("Processing weapon items for " + player.getName());
        loot.addAll(processLootCategory(player, configPath + ".weapons"));

        // Process resource items
        plugin.getLogger().info("Processing resource items for " + player.getName());
        loot.addAll(processLootCategory(player, configPath + ".resources"));

        // Always add a shield for all ranks if not configured
        boolean hasShield = loot.stream().anyMatch(item -> item.getType() == Material.SHIELD);
        if (!hasShield) {
            plugin.getLogger().info("Adding default shield for " + player.getName() + " as none was found in config");
            loot.add(new ItemStack(Material.SHIELD));
        }

        // Log all generated loot items
        plugin.getLogger().info("Generated " + loot.size() + " loot items for " + player.getName());
        for (ItemStack item : loot) {
            plugin.getLogger().info("Loot item: " + item.getType() + " x" + item.getAmount() +
                    (item.getEnchantments().size() > 0 ? " with " + item.getEnchantments().size() + " enchantments" : ""));
        }

        return loot;
    }

    private List<ItemStack> processLootCategory(Player player, String configPath) {
        List<ItemStack> items = new ArrayList<>();

        if (!plugin.getConfig().contains(configPath)) {
            plugin.getLogger().warning("Config path not found: " + configPath);
            return items;
        }

        try {
            // Get items from config
            List<Map<?, ?>> itemsData = null;
            Object configSection = plugin.getConfig().get(configPath);

            if (configSection instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<?, ?>> typedList = (List<Map<?, ?>>) configSection;
                itemsData = typedList;
            }

            if (itemsData == null || itemsData.isEmpty()) {
                plugin.getLogger().warning("No items found at config path: " + configPath);
                return items;
            }

            for (Map<?, ?> itemData : itemsData) {
                try {
                    // Get item details
                    String itemName = itemData.get("item").toString();
                    String dropChanceStr = itemData.containsKey("drop-chance") ?
                            itemData.get("drop-chance").toString() : "100%";

                    // Log item processing
                    plugin.getLogger().info("Processing item " + itemName + " with drop chance " + dropChanceStr);

                    // Parse drop chance
                    double dropChance = parseDropChance(dropChanceStr);

                    // Roll for drop chance
                    double roll = random.nextDouble() * 100;
                    plugin.getLogger().info("Drop chance roll: " + roll + " vs " + dropChance);

                    if (roll > dropChance) {
                        plugin.getLogger().info("Item " + itemName + " skipped due to drop chance roll");
                        continue; // Skip this item based on drop chance
                    }

                    // Create the item
                    Material material = Material.matchMaterial(itemName);
                    if (material == null) {
                        plugin.getLogger().warning("Invalid material: " + itemName);
                        continue;
                    }

                    ItemStack item = new ItemStack(material);

                    // Handle item amount (if specified)
                    if (itemData.containsKey("amount")) {
                        String amountStr = itemData.get("amount").toString();
                        int amount = parseAmount(amountStr);
                        item.setAmount(amount);
                        plugin.getLogger().info("Set amount to " + amount + " for item " + itemName);
                    }

                    // Handle enchantments
                    if (itemData.containsKey("enchantments")) {
                        @SuppressWarnings("unchecked")
                        List<String> enchantmentsList = (List<String>) itemData.get("enchantments");
                        applyEnchantments(item, enchantmentsList);
                    }

                    items.add(item);
                    plugin.getLogger().info("Added item " + item.getType() + " to loot table");
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Error processing item in loot table", e);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error processing loot category: " + configPath, e);
        }

        return items;
    }

    private double parseDropChance(String dropChanceStr) {
        try {
            if (dropChanceStr.endsWith("%")) {
                return Double.parseDouble(dropChanceStr.substring(0, dropChanceStr.length() - 1));
            }
            return Double.parseDouble(dropChanceStr);
        } catch (NumberFormatException e) {
            plugin.getLogger().warning("Invalid drop chance format: " + dropChanceStr + ". Using 100%");
            return 100.0;
        }
    }

    private int parseAmount(String amountStr) {
        try {
            if (amountStr.contains("-")) {
                String[] parts = amountStr.split("-");
                int min = Integer.parseInt(parts[0]);
                int max = Integer.parseInt(parts[1]);
                return min + random.nextInt(max - min + 1);
            }
            return Integer.parseInt(amountStr);
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            plugin.getLogger().warning("Invalid amount format: " + amountStr + ". Using 1");
            return 1;
        }
    }

    private void applyEnchantments(ItemStack item, List<String> enchantmentsList) {
        for (String enchantmentEntry : enchantmentsList) {
            try {
                // Parse enchantment string
                String[] parts = enchantmentEntry.split(":");
                if (parts.length < 2) {
                    plugin.getLogger().warning("Invalid enchantment format: " + enchantmentEntry);
                    continue;
                }

                // Multiple enchantments can be specified with comma
                String[] enchantmentNames = parts[0].split(",");
                int level = Integer.parseInt(parts[1]);

                // Calculate chance to apply this enchantment
                double chance = 100.0;
                if (parts.length >= 3 && parts[2].endsWith("%")) {
                    chance = Double.parseDouble(parts[2].substring(0, parts[2].length() - 1));
                }

                // Roll for enchantment chance
                double roll = random.nextDouble() * 100;
                if (roll > chance) {
                    plugin.getLogger().info("Enchantment skipped due to chance roll: " + enchantmentEntry);
                    continue;
                }

                // Apply each enchantment
                for (String enchName : enchantmentNames) {
                    Enchantment enchantment = getEnchantment(enchName);
                    if (enchantment == null) {
                        plugin.getLogger().warning("Invalid enchantment: " + enchName);
                        continue;
                    }

                    ItemMeta meta = item.getItemMeta();
                    if (meta != null) {
                        meta.addEnchant(enchantment, level, true);
                        item.setItemMeta(meta);
                        plugin.getLogger().info("Added enchantment " + enchName + " level " + level + " to item " + item.getType());
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Error applying enchantment: " + enchantmentEntry, e);
            }
        }
    }

    /**
     * Get an enchantment by name using Paper/Spigot naming conventions
     * @param name Enchantment name
     * @return Enchantment object or null
     */
    private Enchantment getEnchantment(String name) {
        // Try by direct name first (Paper uses logical names directly)
        try {
            Enchantment enchantment = Enchantment.getByName(name.toUpperCase());
            if (enchantment != null) {
                return enchantment;
            }
        } catch (Exception ignored) {
            // Continue to next method
        }

        // Try by namespaced key (modern method)
        try {
            NamespacedKey key = NamespacedKey.minecraft(name.toLowerCase());
            return Enchantment.getByKey(key);
        } catch (Exception ignored) {
            // Invalid key format, continue to fallback methods
        }

        // Fallback for common naming variations
        switch (name.toUpperCase()) {
            case "PROT": return Enchantment.PROTECTION;
            case "FPROT": return Enchantment.FIRE_PROTECTION;
            case "FFALL": return Enchantment.FEATHER_FALLING;
            case "BPROT": return Enchantment.BLAST_PROTECTION;
            case "PROJPROT": return Enchantment.PROJECTILE_PROTECTION;
            case "RESP": return Enchantment.RESPIRATION;
            case "AQUAAFF": return Enchantment.AQUA_AFFINITY;
            case "THORNS": return Enchantment.THORNS;
            case "STRIDER": return Enchantment.DEPTH_STRIDER;
            case "FWALKER": return Enchantment.FROST_WALKER;
            case "BCURSE": return Enchantment.BINDING_CURSE;
            case "SHARP": return Enchantment.SHARPNESS;
            case "SMITE": return Enchantment.SMITE;
            case "BANE": return Enchantment.BANE_OF_ARTHROPODS;
            case "KB": return Enchantment.KNOCKBACK;
            case "FIRE": return Enchantment.FIRE_ASPECT;
            case "LOOTING": return Enchantment.LOOTING;
            case "SWEEPING": return Enchantment.SWEEPING_EDGE;
            case "EFFICIENCY": return Enchantment.EFFICIENCY;
            case "SILK": return Enchantment.SILK_TOUCH;
            case "UNBREAKING": return Enchantment.UNBREAKING;
            case "FORTUNE": return Enchantment.FORTUNE;
            case "POWER": return Enchantment.POWER;
            case "PUNCH": return Enchantment.PUNCH;
            case "FLAME": return Enchantment.FLAME;
            case "INFINITY": return Enchantment.INFINITY;
            case "LUCK": return Enchantment.LUCK_OF_THE_SEA;
            case "LURE": return Enchantment.LURE;
            case "MENDING": return Enchantment.MENDING;
            case "VANISHING": return Enchantment.VANISHING_CURSE;
            case "CHANNELING": return Enchantment.CHANNELING;
            case "IMPALING": return Enchantment.IMPALING;
            case "LOYALTY": return Enchantment.LOYALTY;
            case "RIPTIDE": return Enchantment.RIPTIDE;
            default: return null;
        }
    }
}