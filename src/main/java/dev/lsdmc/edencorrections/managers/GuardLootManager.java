package dev.lsdmc.edencorrections.managers;

import dev.lsdmc.edencorrections.EdenCorrections;
import dev.lsdmc.edencorrections.listeners.GuardLootListener;
import dev.lsdmc.edencorrections.utils.LuckPermsUtil;
import dev.lsdmc.edencorrections.utils.MessageUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
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

        // Register this manager with the plugin
        plugin.getServer().getPluginManager().registerEvents(new GuardLootListener(this, plugin), plugin);

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
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Error loading loot table for rank " + rank, e);
                }
            }
        }

        // If no loot tables were loaded, create defaults
        if (rankLootTables.isEmpty()) {
            createDefaultLootTables();
        }
    }

    /**
     * Load items from a configuration section into a loot table
     * @param rankSection The rank's configuration section
     * @param sectionName The name of the section (armor, weapons, resources)
     * @param lootTable The loot table to add to
     */
    private void loadItemsFromSection(ConfigurationSection rankSection, String sectionName, RankLootTable lootTable) {
        ConfigurationSection section = rankSection.getConfigurationSection(sectionName);
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            ConfigurationSection itemSection = section.getConfigurationSection(key);
            if (itemSection != null) {
                try {
                    String itemName = itemSection.getString("item");
                    if (itemName == null) continue;

                    Material material = Material.matchMaterial(itemName);
                    if (material == null) {
                        plugin.getLogger().warning("Invalid material in loot table: " + itemName);
                        continue;
                    }

                    LootItem lootItem = new LootItem(material);

                    // Load drop chance
                    String dropChanceStr = itemSection.getString("drop-chance", "100%");
                    double dropChance = parsePercentage(dropChanceStr);
                    lootItem.setDropChance(dropChance);

                    // Load amount
                    String amountStr = itemSection.getString("amount", "1");
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

                    // Load enchantments
                    List<String> enchantmentStrs = itemSection.getStringList("enchantments");
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

                    // Add to loot table
                    lootTable.addItem(sectionName, lootItem);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Error loading loot item", e);
                }
            }
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

        // Try matching by common name
        String nameUpper = name.toUpperCase();
        for (Enchantment ench : Enchantment.values()) {
            if (ench.getKey().getKey().equalsIgnoreCase(name)) {
                return ench;
            }

            // Check common aliases
            switch (nameUpper) {
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
                case "MENDING":
                    return Enchantment.MENDING;
            }
        }

        return null;
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

        // Boots
        LootItem boots = new LootItem(Material.IRON_BOOTS);
        boots.setDropChance(0.5);
        boots.addEnchantment(Enchantment.PROTECTION, 2, baseProtectionChance);
        boots.addEnchantment(Enchantment.PROTECTION, 3, baseProtectionChance * 0.5);
        boots.addEnchantment(Enchantment.PROTECTION, 4, baseProtectionChance * 0.2);
        lootTable.addItem("armor", boots);

        // Helmet
        LootItem helmet = new LootItem(Material.IRON_HELMET);
        helmet.setDropChance(0.5);
        helmet.addEnchantment(Enchantment.PROTECTION, 2, baseProtectionChance);
        helmet.addEnchantment(Enchantment.PROTECTION, 3, baseProtectionChance * 0.5);
        helmet.addEnchantment(Enchantment.PROTECTION, 4, baseProtectionChance * 0.2);
        lootTable.addItem("armor", helmet);

        // Add weapons
        double baseWeaponChance = 0.5 + (tier * 0.1); // 60% to 100%

        // Sword
        LootItem sword = new LootItem(Material.IRON_SWORD);
        sword.setDropChance(1.0);
        sword.addEnchantment(Enchantment.SHARPNESS, 2, baseWeaponChance);
        sword.addEnchantment(Enchantment.SHARPNESS, 3, baseWeaponChance * 0.5);
        sword.addEnchantment(Enchantment.SHARPNESS, 4, baseWeaponChance * 0.2);
        lootTable.addItem("weapons", sword);

        // Bow
        LootItem bow = new LootItem(Material.BOW);
        bow.setDropChance(1.0);
        bow.addEnchantment(Enchantment.POWER, 2, baseWeaponChance);
        bow.addEnchantment(Enchantment.POWER, 3, baseWeaponChance * 0.5);
        bow.addEnchantment(Enchantment.POWER, 4, baseWeaponChance * 0.2);
        lootTable.addItem("weapons", bow);

        // Add resources
        LootItem arrows = new LootItem(Material.ARROW);
        arrows.setMinAmount(16 + (tier * 8));
        arrows.setMaxAmount(32 + (tier * 8));
        arrows.setDropChance(1.0);
        lootTable.addItem("resources", arrows);

        LootItem food = new LootItem(Material.COOKED_BEEF);
        food.setMinAmount(4);
        food.setMaxAmount(16);
        food.setDropChance(1.0);
        lootTable.addItem("resources", food);

        LootItem ironIngots = new LootItem(Material.IRON_INGOT);
        ironIngots.setMinAmount(10 + (tier * 5));
        ironIngots.setMaxAmount(30 + (tier * 10));
        ironIngots.setDropChance(1.0);
        lootTable.addItem("resources", ironIngots);

        LootItem xpBottles = new LootItem(Material.EXPERIENCE_BOTTLE);
        xpBottles.setMinAmount(8 + (tier * 4));
        xpBottles.setMaxAmount(16 + (tier * 8));
        xpBottles.setDropChance(1.0);
        lootTable.addItem("resources", xpBottles);

        // Add gold and diamonds for higher tiers
        if (tier >= 4) {
            LootItem goldIngots = new LootItem(Material.GOLD_INGOT);
            goldIngots.setMinAmount(5 + ((tier - 4) * 5));
            goldIngots.setMaxAmount(15 + ((tier - 4) * 10));
            goldIngots.setDropChance(1.0);
            lootTable.addItem("resources", goldIngots);

            LootItem diamonds = new LootItem(Material.DIAMOND);
            diamonds.setMinAmount(1 + ((tier - 4) * 1));
            diamonds.setMaxAmount(3 + ((tier - 4) * 2));
            diamonds.setDropChance(0.5);
            lootTable.addItem("resources", diamonds);
        }

        // Add diamond gear for highest tier
        if (tier >= 5) {
            // Diamond armor with low chance
            LootItem diamondChest = new LootItem(Material.DIAMOND_CHESTPLATE);
            diamondChest.setDropChance(0.2);
            diamondChest.addEnchantment(Enchantment.PROTECTION, 1, 0.6);
            diamondChest.addEnchantment(Enchantment.PROTECTION, 2, 0.3);
            diamondChest.addEnchantment(Enchantment.PROTECTION, 3, 0.1);
            lootTable.addItem("armor", diamondChest);

            // Diamond sword with low chance
            LootItem diamondSword = new LootItem(Material.DIAMOND_SWORD);
            diamondSword.setDropChance(0.2);
            diamondSword.addEnchantment(Enchantment.SHARPNESS, 1, 0.6);
            diamondSword.addEnchantment(Enchantment.SHARPNESS, 2, 0.3);
            diamondSword.addEnchantment(Enchantment.SHARPNESS, 3, 0.1);
            lootTable.addItem("weapons", diamondSword);
        }
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
     * Handle a guard death event
     * @param victim The guard who died
     * @param killer The player who killed the guard
     */
    public void handleGuardDeath(Player victim, Player killer) {
        if (!lootEnabled) return;

        // Check if player is on cooldown
        if (isPlayerOnCooldown(victim.getUniqueId())) {
            notifyCooldown(victim, killer);
            return;
        }

        // Get guard's rank
        String rank = LuckPermsUtil.getGuardRank(victim);
        if (rank == null) {
            // Default to trainee if no rank found
            rank = "trainee";
        }

        // Get loot table for rank
        RankLootTable lootTable = rankLootTables.get(rank.toLowerCase());
        if (lootTable == null) {
            // Fall back to trainee if rank not found
            lootTable = rankLootTables.get("trainee");
            if (lootTable == null) {
                plugin.getLogger().warning("No loot table found for rank " + rank + " and no fallback available");
                return;
            }
        }

        // Drop loot
        Location dropLocation = victim.getLocation();
        dropItemsFromCategory(lootTable, "armor", dropLocation);
        dropItemsFromCategory(lootTable, "weapons", dropLocation);
        dropItemsFromCategory(lootTable, "resources", dropLocation);

        // Set cooldown
        setPlayerCooldown(victim.getUniqueId(), cooldownTime);

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
     */
    private void dropItemsFromCategory(RankLootTable lootTable, String category, Location location) {
        List<LootItem> items = lootTable.getItems(category);
        for (LootItem item : items) {
            // Check drop chance
            if (random.nextDouble() > item.getDropChance()) {
                continue;
            }

            // Create item
            Material material = item.getMaterial();
            int amount = random.nextInt(item.getMaxAmount() - item.getMinAmount() + 1) + item.getMinAmount();

            ItemStack itemStack = new ItemStack(material, amount);
            ItemMeta meta = itemStack.getItemMeta();

            // Apply enchantments
            if (meta != null) {
                for (EnchantmentData enchData : item.getEnchantments()) {
                    // Check enchantment chance
                    if (random.nextDouble() <= enchData.getChance()) {
                        meta.addEnchant(enchData.getEnchantment(), enchData.getLevel(), true);
                        break; // Only apply one level of each enchantment
                    }
                }
                itemStack.setItemMeta(meta);
            }

            // Drop item
            location.getWorld().dropItemNaturally(location, itemStack);
        }
    }

    /**
     * Generate loot for a specific rank
     * @param rank The rank to generate loot for
     * @return A list of item stacks
     */
    public List<ItemStack> generateLoot(String rank) {
        List<ItemStack> lootItems = new ArrayList<>();

        // Get loot table for rank
        RankLootTable lootTable = rankLootTables.get(rank.toLowerCase());
        if (lootTable == null) {
            // Fall back to trainee if rank not found
            lootTable = rankLootTables.get("trainee");
            if (lootTable == null) {
                return lootItems; // Empty list if no loot table found
            }
        }

        // Generate items from each category
        generateItemsFromCategory(lootTable, "armor", lootItems);
        generateItemsFromCategory(lootTable, "weapons", lootItems);
        generateItemsFromCategory(lootTable, "resources", lootItems);

        return lootItems;
    }

    /**
     * Generate items from a category in a loot table
     * @param lootTable The loot table
     * @param category The category
     * @param lootItems The list to add generated items to
     */
    private void generateItemsFromCategory(RankLootTable lootTable, String category, List<ItemStack> lootItems) {
        List<LootItem> items = lootTable.getItems(category);
        for (LootItem item : items) {
            // Check drop chance
            if (random.nextDouble() > item.getDropChance()) {
                continue;
            }

            // Create item
            Material material = item.getMaterial();
            int amount = random.nextInt(item.getMaxAmount() - item.getMinAmount() + 1) + item.getMinAmount();

            ItemStack itemStack = new ItemStack(material, amount);
            ItemMeta meta = itemStack.getItemMeta();

            // Apply enchantments
            if (meta != null) {
                for (EnchantmentData enchData : item.getEnchantments()) {
                    // Check enchantment chance
                    if (random.nextDouble() <= enchData.getChance()) {
                        meta.addEnchant(enchData.getEnchantment(), enchData.getLevel(), true);
                        break; // Only apply one level of each enchantment
                    }
                }
                itemStack.setItemMeta(meta);
            }

            // Add item to loot
            lootItems.add(itemStack);
        }
    }

    /**
     * Check if a player is on cooldown
     * @param playerId The player's UUID
     * @return True if the player is on cooldown
     */
    public boolean isPlayerOnCooldown(UUID playerId) {
        return deathCooldowns.containsKey(playerId) && deathCooldowns.get(playerId) > 0;
    }

    /**
     * Check if a player is on loot cooldown
     * @param playerId The player's UUID
     * @return True if the player is on cooldown
     */
    public boolean isOnLootCooldown(UUID playerId) {
        return isPlayerOnCooldown(playerId);
    }

    /**
     * Set a player's cooldown
     * @param playerId The player's UUID
     * @param seconds Cooldown time in seconds
     */
    public void setPlayerCooldown(UUID playerId, int seconds) {
        deathCooldowns.put(playerId, seconds);
    }

    /**
     * Start the loot cooldown for a player
     * @param playerId The player's UUID
     */
    public void startLootCooldown(UUID playerId) {
        setPlayerCooldown(playerId, cooldownTime);
    }

    /**
     * Get a player's remaining cooldown time
     * @param playerId The player's UUID
     * @return Remaining cooldown time in seconds, 0 if not on cooldown
     */
    public int getPlayerCooldown(UUID playerId) {
        return deathCooldowns.getOrDefault(playerId, 0);
    }

    /**
     * Get the remaining cooldown time for a player
     * @param playerId The player's UUID
     * @return The remaining cooldown time in seconds
     */
    public int getRemainingCooldown(UUID playerId) {
        return getPlayerCooldown(playerId);
    }

    /**
     * Clear a player's cooldown
     * @param playerId The player's UUID
     */
    public void clearPlayerCooldown(UUID playerId) {
        deathCooldowns.remove(playerId);
    }

    /**
     * Notify about cooldown to attacker
     * @param victim The guard
     * @param attacker The attacker
     */
    private void notifyCooldown(Player victim, Player attacker) {
        int timeLeft = getPlayerCooldown(victim.getUniqueId());
        String message = cooldownMessage
                .replace("{victim}", victim.getName())
                .replace("{time}", String.valueOf(timeLeft));

        Component component = MessageUtils.parseMessage(message);
        attacker.sendMessage(component);
    }

    /**
     * Give token reward to a guard who died
     * @param player The guard
     */
    private void giveTokenReward(Player player) {
        if (!player.isOnline()) return;

        // Send message
        String message = tokenRewardMessage.replace("{tokens}", String.valueOf(tokenRewardAmount));
        Component component = MessageUtils.parseMessage(message);
        player.sendMessage(component);

        // Execute command
        String command = tokenRewardCommand
                .replace("{player}", player.getName())
                .replace("{amount}", String.valueOf(tokenRewardAmount));

        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
    }

    /**
     * Reload the manager configuration
     */
    public void reload() {
        loadConfig();
    }

    /**
     * Get whether the loot system is enabled
     * @return True if enabled
     */
    public boolean isLootEnabled() {
        return lootEnabled;
    }

    /**
     * Class to hold loot table data for a guard rank
     */
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
    }

    /**
     * Class to hold loot item data
     */
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

    /**
     * Class to hold enchantment data
     */
    public static class EnchantmentData {
        private final Enchantment enchantment;
        private final int level;
        private final double chance;

        public EnchantmentData(Enchantment enchantment, int level, double chance) {
            this.enchantment = enchantment;
            this.level = level;
            this.chance = chance;
        }

        public Enchantment getEnchantment() {
            return enchantment;
        }

        public int getLevel() {
            return level;
        }

        public double getChance() {
            return chance;
        }
    }
}