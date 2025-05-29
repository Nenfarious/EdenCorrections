package dev.lsdmc.edencorrections.managers;

import dev.lsdmc.edencorrections.EdenCorrections;
import dev.lsdmc.edencorrections.utils.MessageUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages the guard loot system
 */
public class GuardLootManager {
    private final EdenCorrections plugin;
    private boolean lootEnabled;
    private int cooldownTime;
    private String cooldownMessage;

    // Token reward configuration
    private boolean tokenRewardEnabled;
    private int tokenRewardAmount;
    private String tokenRewardMessage;
    private String tokenRewardCommand;

    // Death cooldown tracking
    private final Map<UUID, Integer> deathCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> cooldownTasks = new ConcurrentHashMap<>();

    // Loot table configuration
    private final Map<String, RankLootTable> rankLootTables = new HashMap<>();

    // Random number generator
    private final Random random = new Random();

    public GuardLootManager(EdenCorrections plugin) {
        this.plugin = plugin;
        loadConfig();

        // Start cooldown tick task
        startCooldownTask();
    }

    private void loadConfig() {
        lootEnabled = plugin.getConfig().getBoolean("guard-loot.enabled", true);

        // Only process the rest if the feature is enabled
        if (lootEnabled) {
            cooldownTime = plugin.getConfig().getInt("guard-loot.cooldown", 600);
            cooldownMessage = plugin.getConfig().getString("guard-loot.cooldown-message",
                    "&8[&4&l𝕏&8] &b{victim} &7has their guard loot on cooldown! &b({time}s)");

            // Load token reward config
            tokenRewardEnabled = plugin.getConfig().getBoolean("guard-loot.token-reward.enabled", true);
            tokenRewardAmount = plugin.getConfig().getInt("guard-loot.token-reward.amount", 200);
            tokenRewardMessage = plugin.getConfig().getString("guard-loot.token-reward.message",
                    "&8[&4&l𝕏&8] &cYou fought bravely in combat and have received {tokens} tokens!");
            tokenRewardCommand = plugin.getConfig().getString("guard-loot.token-reward.command",
                    "tokenmanager give {player} {amount}");

            // Load loot tables
            loadLootTables();
        }
    }

    /**
     * Load loot tables from configuration
     */
    private void loadLootTables() {
        rankLootTables.clear();

        ConfigurationSection ranksSection = plugin.getConfig().getConfigurationSection("guard-loot.ranks");
        if (ranksSection == null) {
            plugin.getLogger().warning("No guard-loot.ranks section found in config, using defaults");
            createDefaultLootTables();
            return;
        }

        // Load each rank's loot table
        for (String rank : ranksSection.getKeys(false)) {
            ConfigurationSection rankSection = ranksSection.getConfigurationSection(rank);
            if (rankSection != null) {
                try {
                    RankLootTable lootTable = new RankLootTable(rank);

                    // Load armor section
                    loadItemsFromSection(rankSection, "armor", lootTable);

                    // Load weapons section
                    loadItemsFromSection(rankSection, "weapons", lootTable);

                    // Load resources section
                    loadItemsFromSection(rankSection, "resources", lootTable);

                    // Add to loot tables
                    rankLootTables.put(rank.toLowerCase(), lootTable);

                    plugin.getLogger().info("Loaded loot table for rank: " + rank +
                            " with " + lootTable.getTotalItemCount() + " items");
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Error loading loot table for rank " + rank, e);
                }
            }
        }

        // If no loot tables were loaded, create defaults
        if (rankLootTables.isEmpty()) {
            plugin.getLogger().warning("No loot tables loaded from config, creating defaults");
            createDefaultLootTables();
        } else {
            plugin.getLogger().info("Successfully loaded " + rankLootTables.size() + " loot tables");
        }
    }

    /**
     * Load items from a configuration section into a loot table
     * FIXED: Now properly handles list format from config
     * @param rankSection The rank's configuration section
     * @param sectionName The name of the section (armor, weapons, resources)
     * @param lootTable The loot table to add to
     */
    private void loadItemsFromSection(ConfigurationSection rankSection, String sectionName, RankLootTable lootTable) {
        // Get the section (e.g., "armor", "weapons", "resources")
        Object sectionData = rankSection.get(sectionName);

        if (sectionData == null) {
            plugin.getLogger().info("No " + sectionName + " section found for rank " + lootTable.getRank());
            return;
        }

        // Handle list format from config
        if (sectionData instanceof List) {
            @SuppressWarnings("unchecked")
            List<Map<?, ?>> itemsList = (List<Map<?, ?>>) sectionData;

            plugin.getLogger().info("Loading " + itemsList.size() + " " + sectionName + " items for rank " + lootTable.getRank());

            for (Map<?, ?> itemData : itemsList) {
                try {
                    String itemName = itemData.get("item").toString();

                    Material material = Material.matchMaterial(itemName);
                    if (material == null) {
                        plugin.getLogger().warning("Invalid material in loot table: " + itemName);
                        continue;
                    }

                    LootItem lootItem = new LootItem(material);

                    // Load drop chance
                    if (itemData.containsKey("drop-chance")) {
                        String dropChanceStr = itemData.get("drop-chance").toString();
                        double dropChance = parsePercentage(dropChanceStr);
                        lootItem.setDropChance(dropChance);
                    }

                    // Load amount
                    if (itemData.containsKey("amount")) {
                        String amountStr = itemData.get("amount").toString();
                        if (amountStr.contains("-")) {
                            String[] parts = amountStr.split("-");
                            int min = Integer.parseInt(parts[0]);
                            int max = Integer.parseInt(parts[1]);
                            lootItem.setMinAmount(min);
                            lootItem.setMaxAmount(max);
                        } else {
                            int amount = Integer.parseInt(amountStr);
                            lootItem.setMinAmount(amount);
                            lootItem.setMaxAmount(amount);
                        }
                    }

                    // Load enchantments
                    if (itemData.containsKey("enchantments")) {
                        @SuppressWarnings("unchecked")
                        List<String> enchantmentStrs = (List<String>) itemData.get("enchantments");
                        for (String enchStr : enchantmentStrs) {
                            String[] parts = enchStr.split(":");
                            if (parts.length >= 2) {
                                String enchName = parts[0];
                                int level = Integer.parseInt(parts[1]);
                                double chance = 100.0;
                                if (parts.length >= 3) {
                                    chance = parsePercentage(parts[2]);
                                }

                                Enchantment enchantment = getEnchantmentByName(enchName);
                                if (enchantment != null) {
                                    lootItem.addEnchantment(enchantment, level, chance);
                                } else {
                                    plugin.getLogger().warning("Invalid enchantment: " + enchName);
                                }
                            }
                        }
                    }

                    // Add to loot table
                    lootTable.addItem(sectionName, lootItem);
                    plugin.getLogger().info("Added " + sectionName + " item: " + itemName +
                            " (drop chance: " + (lootItem.getDropChance() * 100) + "%)");

                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Error loading loot item in " + sectionName, e);
                }
            }
        } else {
            plugin.getLogger().warning("Expected list format for " + sectionName + " in rank " + lootTable.getRank() +
                    ", but got: " + sectionData.getClass().getSimpleName());
        }
    }

    /**
     * Parse a percentage string (e.g. "50%") to a double (0.5)
     * @param str Percentage string
     * @return Double value
     */
    private double parsePercentage(String str) {
        str = str.trim();
        if (str.endsWith("%")) {
            str = str.substring(0, str.length() - 1);
            return Double.parseDouble(str) / 100.0;
        }
        return Double.parseDouble(str);
    }

    /**
     * Get an enchantment by name (supports multiple naming conventions)
     * @param name Enchantment name
     * @return Enchantment object or null
     */
    private Enchantment getEnchantmentByName(String name) {
        // Try exact match first
        Enchantment enchantment = Enchantment.getByName(name.toUpperCase());
        if (enchantment != null) return enchantment;

        // Try matching by namespaced key
        try {
            NamespacedKey key = NamespacedKey.minecraft(name.toLowerCase());
            enchantment = Enchantment.getByKey(key);
            if (enchantment != null) return enchantment;
        } catch (Exception ignored) {
            // Continue to fallback methods
        }

        // Fallback for common naming variations
        return switch (name.toUpperCase()) {
            case "PROTECTION", "PROT" -> Enchantment.PROTECTION;
            case "FIRE_PROTECTION", "FPROT" -> Enchantment.FIRE_PROTECTION;
            case "FEATHER_FALLING", "FFALL" -> Enchantment.FEATHER_FALLING;
            case "BLAST_PROTECTION", "BPROT" -> Enchantment.BLAST_PROTECTION;
            case "PROJECTILE_PROTECTION", "PROJPROT" -> Enchantment.PROJECTILE_PROTECTION;
            case "RESPIRATION", "RESP" -> Enchantment.RESPIRATION;
            case "AQUA_AFFINITY", "AQUAAFF" -> Enchantment.AQUA_AFFINITY;
            case "THORNS" -> Enchantment.THORNS;
            case "DEPTH_STRIDER", "STRIDER" -> Enchantment.DEPTH_STRIDER;
            case "FROST_WALKER", "FWALKER" -> Enchantment.FROST_WALKER;
            case "SHARPNESS", "SHARP" -> Enchantment.SHARPNESS;
            case "SMITE" -> Enchantment.SMITE;
            case "BANE_OF_ARTHROPODS", "BANE" -> Enchantment.BANE_OF_ARTHROPODS;
            case "KNOCKBACK", "KB" -> Enchantment.KNOCKBACK;
            case "FIRE_ASPECT", "FIRE" -> Enchantment.FIRE_ASPECT;
            case "LOOTING" -> Enchantment.LOOTING;
            case "SWEEPING_EDGE", "SWEEPING" -> Enchantment.SWEEPING_EDGE;
            case "EFFICIENCY" -> Enchantment.EFFICIENCY;
            case "SILK_TOUCH", "SILK" -> Enchantment.SILK_TOUCH;
            case "UNBREAKING" -> Enchantment.UNBREAKING;
            case "FORTUNE" -> Enchantment.FORTUNE;
            case "POWER" -> Enchantment.POWER;
            case "PUNCH" -> Enchantment.PUNCH;
            case "FLAME" -> Enchantment.FLAME;
            case "INFINITY" -> Enchantment.INFINITY;
            case "LUCK_OF_THE_SEA", "LUCK" -> Enchantment.LUCK_OF_THE_SEA;
            case "LURE" -> Enchantment.LURE;
            case "MENDING" -> Enchantment.MENDING;
            case "VANISHING_CURSE", "VANISHING" -> Enchantment.VANISHING_CURSE;
            case "CHANNELING" -> Enchantment.CHANNELING;
            case "IMPALING" -> Enchantment.IMPALING;
            case "LOYALTY" -> Enchantment.LOYALTY;
            case "RIPTIDE" -> Enchantment.RIPTIDE;
            default -> null;
        };
    }

    /**
     * Create default loot tables if none are defined in the config
     */
    private void createDefaultLootTables() {
        plugin.getLogger().info("Creating default guard loot tables");

        // Create trainee loot table
        RankLootTable trainee = new RankLootTable("trainee");
        addDefaultItems(trainee, 1);
        rankLootTables.put("trainee", trainee);

        // Create private loot table
        RankLootTable private_ = new RankLootTable("private");
        addDefaultItems(private_, 2);
        rankLootTables.put("private", private_);

        // Create officer loot table
        RankLootTable officer = new RankLootTable("officer");
        addDefaultItems(officer, 3);
        rankLootTables.put("officer", officer);

        // Create sergeant loot table
        RankLootTable sergeant = new RankLootTable("sergeant");
        addDefaultItems(sergeant, 4);
        rankLootTables.put("sergeant", sergeant);

        // Create captain loot table
        RankLootTable captain = new RankLootTable("captain");
        addDefaultItems(captain, 5);
        rankLootTables.put("captain", captain);
    }

    /**
     * Add default items to a loot table based on a tier level
     * @param lootTable The loot table to add to
     * @param tier The tier level (1-5)
     */
    private void addDefaultItems(RankLootTable lootTable, int tier) {
        // Add basic armor pieces with increased enchantment chance based on tier
        double baseProtectionChance = 0.4 + (tier * 0.1); // 50% to 90%

        // Chestplate
        LootItem chestplate = new LootItem(Material.IRON_CHESTPLATE);
        chestplate.setDropChance(0.5);
        chestplate.addEnchantment(Enchantment.PROTECTION, 2, baseProtectionChance);
        chestplate.addEnchantment(Enchantment.PROTECTION, 3, baseProtectionChance * 0.5);
        chestplate.addEnchantment(Enchantment.PROTECTION, 4, baseProtectionChance * 0.2);
        lootTable.addItem("armor", chestplate);

        // Leggings
        LootItem leggings = new LootItem(Material.IRON_LEGGINGS);
        leggings.setDropChance(0.5);
        leggings.addEnchantment(Enchantment.PROTECTION, 2, baseProtectionChance);
        leggings.addEnchantment(Enchantment.PROTECTION, 3, baseProtectionChance * 0.5);
        leggings.addEnchantment(Enchantment.PROTECTION, 4, baseProtectionChance * 0.2);
        lootTable.addItem("armor", leggings);

        // Add weapons and resources...
        LootItem sword = new LootItem(Material.IRON_SWORD);
        sword.setDropChance(1.0);
        sword.addEnchantment(Enchantment.SHARPNESS, 2, 0.6);
        lootTable.addItem("weapons", sword);

        LootItem arrows = new LootItem(Material.ARROW);
        arrows.setMinAmount(16 + (tier * 8));
        arrows.setMaxAmount(32 + (tier * 8));
        arrows.setDropChance(1.0);
        lootTable.addItem("resources", arrows);
    }

    /**
     * Start the task that decrements cooldowns
     */
    private void startCooldownTask() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            // Decrement all cooldowns by 1
            for (UUID playerId : new ArrayList<>(deathCooldowns.keySet())) {
                int current = deathCooldowns.get(playerId);
                if (current <= 1) {
                    deathCooldowns.remove(playerId);
                } else {
                    deathCooldowns.put(playerId, current - 1);
                }
            }
        }, 20L, 20L); // Run every second
    }

    /**
     * Handle a guard death event - FIXED to properly use loot tables
     */
    public void handleGuardDeath(Player victim, Player killer) {
        if (!lootEnabled) {
            plugin.getLogger().info("Guard loot system is disabled");
            return;
        }

        plugin.getLogger().info("Processing guard death for " + victim.getName());

        // Check if player is on cooldown
        if (isPlayerOnCooldown(victim.getUniqueId())) {
            plugin.getLogger().info(victim.getName() + " is on loot cooldown");
            notifyCooldown(victim, killer);
            return;
        }

        // Get guard's rank
        String rank = plugin.getGuardRankManager().getPlayerRank(victim);
        if (rank == null) {
            rank = "trainee";
            plugin.getLogger().info("No rank found for " + victim.getName() + ", using trainee");
        } else {
            plugin.getLogger().info("Guard " + victim.getName() + " has rank: " + rank);
        }

        // IMPORTANT: Clear inventory FIRST to prevent default death drops
        Location dropLocation = victim.getLocation();
        victim.getInventory().clear();

        plugin.getLogger().info("Cleared inventory for " + victim.getName());

        // Get loot table for rank
        RankLootTable lootTable = rankLootTables.get(rank.toLowerCase());
        if (lootTable == null) {
            plugin.getLogger().warning("No loot table found for rank: " + rank + ", trying trainee");
            lootTable = rankLootTables.get("trainee");
        }

        if (lootTable != null) {
            plugin.getLogger().info("Using loot table for rank: " + lootTable.getRank());

            // Generate and drop loot from each category
            int armorDropped = dropItemsFromCategory(lootTable, "armor", dropLocation);
            int weaponsDropped = dropItemsFromCategory(lootTable, "weapons", dropLocation);
            int resourcesDropped = dropItemsFromCategory(lootTable, "resources", dropLocation);

            plugin.getLogger().info("Dropped loot for " + victim.getName() + ": " +
                    armorDropped + " armor, " + weaponsDropped + " weapons, " + resourcesDropped + " resources");
        } else {
            plugin.getLogger().severe("No loot table available! Check configuration.");
        }

        // Set cooldown
        setPlayerCooldown(victim.getUniqueId(), cooldownTime);
        plugin.getLogger().info("Set loot cooldown for " + victim.getName() + ": " + cooldownTime + " seconds");

        // Schedule token reward
        if (tokenRewardEnabled) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> giveTokenReward(victim), 3 * 20L);
        }
    }

    /**
     * Drop items from a category in a loot table
     * @param lootTable The loot table
     * @param category The category
     * @param location The location to drop items
     * @return The number of items dropped
     */
    private int dropItemsFromCategory(RankLootTable lootTable, String category, Location location) {
        List<LootItem> items = lootTable.getItems(category);
        int dropped = 0;

        plugin.getLogger().info("Processing " + items.size() + " " + category + " items");

        for (LootItem item : items) {
            // Check drop chance
            double roll = random.nextDouble();
            double dropChance = item.getDropChance();

            plugin.getLogger().info("Item " + item.getMaterial() + " - Roll: " + roll + ", Chance: " + dropChance);

            if (roll > dropChance) {
                plugin.getLogger().info("Item " + item.getMaterial() + " not dropped due to chance");
                continue;
            }

            // Create item
            Material material = item.getMaterial();
            int amount = random.nextInt(item.getMaxAmount() - item.getMinAmount() + 1) + item.getMinAmount();

            ItemStack itemStack = new ItemStack(material, amount);
            ItemMeta meta = itemStack.getItemMeta();

            // Apply enchantments
            if (meta != null) {
                boolean enchantmentApplied = false;
                for (EnchantmentData enchData : item.getEnchantments()) {
                    // Check enchantment chance
                    if (random.nextDouble() <= enchData.chance()) {
                        meta.addEnchant(enchData.enchantment(), enchData.level(), true);
                        enchantmentApplied = true;
                        plugin.getLogger().info("Applied enchantment " + enchData.enchantment().getKey() +
                                " level " + enchData.level() + " to " + material);
                        break; // Only apply one level of each enchantment
                    }
                }
                itemStack.setItemMeta(meta);

                if (!enchantmentApplied && !item.getEnchantments().isEmpty()) {
                    plugin.getLogger().info("No enchantments applied to " + material + " due to chance");
                }
            }

            // Drop item
            location.getWorld().dropItemNaturally(location, itemStack);
            dropped++;

            plugin.getLogger().info("Dropped " + amount + "x " + material);
        }

        return dropped;
    }

    // Rest of the existing methods remain the same...

    public boolean isPlayerOnCooldown(UUID playerId) {
        return deathCooldowns.containsKey(playerId) && deathCooldowns.get(playerId) > 0;
    }

    public boolean isOnLootCooldown(UUID playerId) {
        return isPlayerOnCooldown(playerId);
    }

    public void setPlayerCooldown(UUID playerId, int seconds) {
        deathCooldowns.put(playerId, seconds);
    }

    public void startLootCooldown(UUID playerId) {
        setPlayerCooldown(playerId, cooldownTime);
    }

    public int getPlayerCooldown(UUID playerId) {
        return deathCooldowns.getOrDefault(playerId, 0);
    }

    public int getRemainingCooldown(UUID playerId) {
        return getPlayerCooldown(playerId);
    }

    public void clearPlayerCooldown(UUID playerId) {
        deathCooldowns.remove(playerId);
    }

    private void notifyCooldown(Player victim, Player attacker) {
        if (attacker == null) return;

        int timeLeft = getPlayerCooldown(victim.getUniqueId());
        String message = cooldownMessage
                .replace("{victim}", victim.getName())
                .replace("{time}", String.valueOf(timeLeft));

        Component component = MessageUtils.parseMessage(message);
        attacker.sendMessage(component);
    }

    private void giveTokenReward(Player player) {
        if (!player.isOnline()) return;
        // Send message
        String message = tokenRewardMessage.replace("{tokens}", String.valueOf(tokenRewardAmount));
        Component component = MessageUtils.parseMessage(message);
        player.sendMessage(component);
        // Give tokens directly
        plugin.getGuardTokenManager().giveTokens(player, tokenRewardAmount, "Guard death reward");
    }

    public void reload() {
        loadConfig();
    }

    public boolean isLootEnabled() {
        return lootEnabled;
    }

    /**
     * Check if an item is from a guard kit - IMPROVED
     */
    private boolean isKitItem(ItemStack item, String rank) {
        if (item == null || !item.hasItemMeta()) return false;

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();

        // Check for kit item tag
        NamespacedKey kitKey = new NamespacedKey(plugin, "guard_kit_item");
        if (container.has(kitKey, PersistentDataType.STRING)) {
            String kitRank = container.get(kitKey, PersistentDataType.STRING);
            return kitRank != null && kitRank.equalsIgnoreCase(rank);
        }

        return false;
    }

    // Inner classes for configuration sections

    public static class RankLootTable {
        private final String rank;
        private final Map<String, List<LootItem>> items = new HashMap<>();

        public RankLootTable(String rank) {
            this.rank = rank;
            // Initialize categories
            items.put("armor", new ArrayList<>());
            items.put("weapons", new ArrayList<>());
            items.put("resources", new ArrayList<>());
        }

        public String getRank() {
            return rank;
        }

        public void addItem(String category, LootItem item) {
            if (!items.containsKey(category)) {
                items.put(category, new ArrayList<>());
            }
            items.get(category).add(item);
        }

        public List<LootItem> getItems(String category) {
            return items.getOrDefault(category, new ArrayList<>());
        }

        public int getTotalItemCount() {
            return items.values().stream().mapToInt(List::size).sum();
        }
    }

    public static class LootItem {
        private final Material material;
        private double dropChance = 1.0;
        private int minAmount = 1;
        private int maxAmount = 1;
        private final List<EnchantmentData> enchantments = new ArrayList<>();

        public LootItem(Material material) {
            this.material = material;
        }

        public Material getMaterial() {
            return material;
        }

        public double getDropChance() {
            return dropChance;
        }

        public void setDropChance(double dropChance) {
            this.dropChance = dropChance;
        }

        public int getMinAmount() {
            return minAmount;
        }

        public void setMinAmount(int minAmount) {
            this.minAmount = minAmount;
        }

        public int getMaxAmount() {
            return maxAmount;
        }

        public void setMaxAmount(int maxAmount) {
            this.maxAmount = maxAmount;
        }

        public void addEnchantment(Enchantment enchantment, int level, double chance) {
            enchantments.add(new EnchantmentData(enchantment, level, chance));
        }

        public List<EnchantmentData> getEnchantments() {
            return enchantments;
        }
    }

    public record EnchantmentData(Enchantment enchantment, int level, double chance) {
    }
}