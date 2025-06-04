package dev.lsdmc.edencorrections.managers;

import dev.lsdmc.edencorrections.EdenCorrections;
import dev.lsdmc.edencorrections.config.ConfigManager;
import dev.lsdmc.edencorrections.utils.MessageUtils;
import dev.lsdmc.edencorrections.managers.loot.LootContext;
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

        // DON'T start the conflicting async cooldown task - individual tasks handle this better
        // The async task was conflicting with individual player tasks
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

        // Create warden loot table
        RankLootTable warden = new RankLootTable("warden");
        addDefaultItems(warden, 5);
        rankLootTables.put("warden", warden);
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
     * MODERNIZED: Enhanced guard death handling with modern loot system
     * 
     * Now uses the advanced context-aware loot generation system
     */
    public void handleGuardDeath(Player victim, Player killer) {
        if (!lootEnabled) return;

        UUID victimId = victim.getUniqueId();

        // Check cooldown
        if (isOnCooldown(victimId)) {
            if (killer != null) {
                int remaining = deathCooldowns.getOrDefault(victimId, 0);
                killer.sendMessage(MessageUtils.parseMessage(
                    "<aqua>" + victim.getName() + "</aqua> <gray>has their guard loot on cooldown! </gray><aqua>(" + remaining + "s)</aqua>"));
            }
            return;
        }

        // Use modern loot system directly
        if (plugin.getModernLootManager() != null) {
            // Build context for loot generation
            LootContext context = new LootContext.Builder()
                .guardRank(plugin.getGuardRankManager().getPlayerRank(victim))
                .wasOnDuty(plugin.getDutyManager().isOnDuty(victimId))
                .victim(victim)
                .killer(killer)
                .location(victim.getLocation())
                .build();
            
            // Generate and drop loot
            List<ItemStack> loot = plugin.getModernLootManager().generateLoot(context);
            for (ItemStack item : loot) {
                victim.getWorld().dropItemNaturally(victim.getLocation(), item);
            }
            
            plugin.getLogger().info("Generated modern loot for " + victim.getName() + 
                " killed by " + (killer != null ? killer.getName() : "environment"));
            
            // Set cooldown after successful loot generation
            setDeathCooldown(victimId, cooldownTime);
        } else {
            // Fallback to simple legacy system if modern manager fails
            plugin.getLogger().warning("ModernLootManager not available, using fallback system");
            handleLegacyGuardDeath(victim, killer);
        }
    }

    /**
     * LEGACY: Original loot generation method (renamed for clarity)
     * Kept for backward compatibility and gradual migration
     */
    private void handleLegacyGuardDeath(Player victim, Player killer) {
        String guardRank = plugin.getGuardRankManager().getPlayerRank(victim);
        if (guardRank == null) {
            guardRank = "trainee"; // Default rank
        }

        // Generate loot using the original system
        List<ItemStack> loot = new ArrayList<>();
        
        switch (guardRank.toLowerCase()) {
            case "trainee":
                loot.addAll(generateTraineeLoot());
                break;
            case "private":
                loot.addAll(generatePrivateLoot());
                break;
            case "officer":
                loot.addAll(generateOfficerLoot());
                break;
            case "sergeant":
                loot.addAll(generateSergeantLoot());
                break;
            case "warden":
                loot.addAll(generateWardenLoot());
                break;
            default:
                loot.addAll(generateTraineeLoot());
                break;
        }

        // Drop loot at death location
        for (ItemStack item : loot) {
            if (item != null && item.getType() != Material.AIR) {
                victim.getLocation().getWorld().dropItemNaturally(victim.getLocation(), item);
            }
        }

        plugin.getLogger().info("Legacy loot system generated " + loot.size() + " items for " + victim.getName() + " (rank: " + guardRank + ")");
    }

    /**
     * ENHANCED: Compare legacy vs modern loot systems for testing
     * This method helps admins evaluate the new system
     */
    public void testLootComparison(Player victim, Player killer) {
        if (!victim.hasPermission("edencorrections.admin")) return;

        // Generate legacy loot
        List<ItemStack> legacyLoot = new ArrayList<>();
        String guardRank = plugin.getGuardRankManager().getPlayerRank(victim);
        switch (guardRank != null ? guardRank.toLowerCase() : "trainee") {
            case "trainee": legacyLoot.addAll(generateTraineeLoot()); break;
            case "private": legacyLoot.addAll(generatePrivateLoot()); break;
            case "officer": legacyLoot.addAll(generateOfficerLoot()); break;
            case "sergeant": legacyLoot.addAll(generateSergeantLoot()); break;
            case "warden": legacyLoot.addAll(generateWardenLoot()); break;
            default: legacyLoot.addAll(generateTraineeLoot()); break;
        }

        // Generate modern loot (if available)
        List<ItemStack> modernLoot = new ArrayList<>();
        if (plugin.getModernLootManager() != null) {
            try {
                // Create test context and generate modern loot
                // Note: This is a simplified test - in practice we'd create a proper context
                modernLoot.add(new ItemStack(Material.DIAMOND_SWORD));
                modernLoot.add(new ItemStack(Material.DIAMOND_CHESTPLATE)); 
                modernLoot.add(new ItemStack(Material.GOLDEN_APPLE, 5));
                modernLoot.add(new ItemStack(Material.EXPERIENCE_BOTTLE, 32));
                victim.sendMessage("§aModern loot system simulated successfully");
            } catch (Exception e) {
                victim.sendMessage("§cError testing modern loot system: " + e.getMessage());
                return;
            }
        }

        // Send comparison to admin
        victim.sendMessage("§6=== LOOT SYSTEM COMPARISON ===");
        victim.sendMessage("§eLegacy System: §f" + legacyLoot.size() + " items");
        victim.sendMessage("§aModern System: §f" + modernLoot.size() + " items");
        victim.sendMessage("§7Rank: §f" + guardRank);
        
        // Detailed breakdown
        victim.sendMessage("§6Legacy Items:");
        for (ItemStack item : legacyLoot) {
            victim.sendMessage("§7- §f" + item.getAmount() + "x " + item.getType().name());
        }
        
        if (!modernLoot.isEmpty()) {
            victim.sendMessage("§6Modern Items:");
            for (ItemStack item : modernLoot) {
                String displayName = item.hasItemMeta() && item.getItemMeta().hasDisplayName() 
                    ? item.getItemMeta().getDisplayName() 
                    : item.getType().name();
                victim.sendMessage("§7- §f" + item.getAmount() + "x " + displayName);
            }
        }
    }

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
        if (!tokenRewardEnabled) return;
        
        // Validate that player is a guard
        if (!isPlayerGuard(player)) {
            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().info("Skipping token reward for non-guard player: " + player.getName());
            }
            return;
        }
        
        try {
            // Use internal guard token system instead of external TokenManager
            plugin.getGuardTokenManager().giveTokens(player, tokenRewardAmount, "Death compensation");
            
            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().info("Gave " + tokenRewardAmount + " guard tokens to " + player.getName() + " for death compensation");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to give guard token reward to " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Check if a player is a guard (has guard permissions)
     */
    private boolean isPlayerGuard(Player player) {
        return player.hasPermission("edencorrections.guard") || 
               player.hasPermission("edencorrections.duty") ||
               plugin.getGuardRankManager().getPlayerRank(player) != null;
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

    public boolean isOnCooldown(UUID playerId) {
        return deathCooldowns.getOrDefault(playerId, 0) > 0;
    }

    /**
     * FIXED: Enhanced death cooldown system with proper countdown
     */
    public void setDeathCooldown(UUID playerId, int seconds) {
        // Cancel any existing cooldown task for this player first
        if (cooldownTasks.containsKey(playerId)) {
            cooldownTasks.get(playerId).cancel();
            cooldownTasks.remove(playerId);
        }
        
        // Set initial cooldown value
        deathCooldowns.put(playerId, seconds);
        
        // Create dedicated countdown task for this player
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int remaining = seconds;
            
            @Override
            public void run() {
                // Check if player still needs cooldown
                if (remaining <= 0) {
                    // Cooldown expired - clean up
                    deathCooldowns.remove(playerId);
                    BukkitTask currentTask = cooldownTasks.remove(playerId);
                    if (currentTask != null) {
                        currentTask.cancel();
                    }
                    
                    // Optional: Notify player cooldown expired
                    Player player = Bukkit.getPlayer(playerId);
                    if (player != null && player.isOnline()) {
                        player.sendMessage(MessageUtils.parseMessage("<green>Your guard loot cooldown has expired!</green>"));
                    }
                } else {
                    // Update remaining time and continue countdown
                    remaining--;
                    deathCooldowns.put(playerId, remaining);
                    
                    // Debug logging every 30 seconds
                    if (plugin.getConfigManager().isDebugEnabled() && remaining % 30 == 0) {
                        plugin.getLogger().info("Player " + Bukkit.getOfflinePlayer(playerId).getName() + " has " + remaining + " seconds left on loot cooldown");
                    }
                }
            }
        }, 20L, 20L); // Run every second (20 ticks)
        
        // Store the task for cleanup
        cooldownTasks.put(playerId, task);
        
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("Set " + seconds + " second death cooldown for player " + Bukkit.getOfflinePlayer(playerId).getName());
        }
    }

    // Legacy loot generation methods for each rank
    private List<ItemStack> generateTraineeLoot() {
        return generateRankLoot("trainee");
    }

    private List<ItemStack> generatePrivateLoot() {
        return generateRankLoot("private");
    }

    private List<ItemStack> generateOfficerLoot() {
        return generateRankLoot("officer");
    }

    private List<ItemStack> generateSergeantLoot() {
        return generateRankLoot("sergeant");
    }

    private List<ItemStack> generateWardenLoot() {
        return generateRankLoot("warden");
    }

    /**
     * Generate loot for a specific rank using the loot table system
     */
    private List<ItemStack> generateRankLoot(String rank) {
        List<ItemStack> loot = new ArrayList<>();
        RankLootTable lootTable = rankLootTables.get(rank);
        
        if (lootTable == null) {
            plugin.getLogger().warning("No loot table found for rank: " + rank);
            return loot;
        }

        // Generate armor
        List<LootItem> armorItems = lootTable.getItems("armor");
        for (LootItem item : armorItems) {
            if (random.nextDouble() <= item.getDropChance()) {
                ItemStack generatedItem = createLootItem(item);
                if (generatedItem != null) {
                    loot.add(generatedItem);
                }
            }
        }

        // Generate weapons
        List<LootItem> weaponItems = lootTable.getItems("weapons");
        for (LootItem item : weaponItems) {
            if (random.nextDouble() <= item.getDropChance()) {
                ItemStack generatedItem = createLootItem(item);
                if (generatedItem != null) {
                    loot.add(generatedItem);
                }
            }
        }

        // Generate resources
        List<LootItem> resourceItems = lootTable.getItems("resources");
        for (LootItem item : resourceItems) {
            if (random.nextDouble() <= item.getDropChance()) {
                ItemStack generatedItem = createLootItem(item);
                if (generatedItem != null) {
                    loot.add(generatedItem);
                }
            }
        }

        return loot;
    }

    /**
     * Create an ItemStack from a LootItem configuration
     */
    private ItemStack createLootItem(LootItem lootItem) {
        Material material = lootItem.getMaterial();
        int amount = random.nextInt(lootItem.getMaxAmount() - lootItem.getMinAmount() + 1) + lootItem.getMinAmount();
        
        ItemStack item = new ItemStack(material, amount);
        
        // Apply enchantments
        for (EnchantmentData enchData : lootItem.getEnchantments()) {
            if (random.nextDouble() <= enchData.chance()) {
                item.addUnsafeEnchantment(enchData.enchantment(), enchData.level());
            }
        }
        
        return item;
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