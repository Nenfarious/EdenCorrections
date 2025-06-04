package dev.lsdmc.edencorrections.gui;

import dev.lsdmc.edencorrections.EdenCorrections;
import dev.lsdmc.edencorrections.config.ConfigManager;
import dev.lsdmc.edencorrections.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Redesigned GUI Manager for EdenCorrections
 */
public class GuiManager {
    private final EdenCorrections plugin;
    private final ConfigManager configManager;
    private ConfigManager.InterfaceConfig interfaceConfig;
    private ConfigManager.GuiConfig guiConfig;
    private ConfigManager.MessagesConfig messagesConfig;

    // GUI titles
    private static final String MAIN_MENU_TITLE = "§e§lCorrections Command Center";
    private static final String DUTY_MENU_TITLE = "§b§lDuty Management";
    private static final String STATS_MENU_TITLE = "§a§lGuard Statistics";
    private static final String ACTIONS_MENU_TITLE = "§c§lGuard Actions";
    private static final String SHOP_MENU_TITLE = "§6§lGuard Shop";

    // GUI sizes
    private static final int MAIN_MENU_SIZE = 36; // 4 rows
    private static final int SUB_MENU_SIZE = 36; // 4 rows for all sub-menus

    // Cooldown tracking
    private final Map<UUID, Long> actionCooldowns = new HashMap<>();

    public GuiManager(EdenCorrections plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.interfaceConfig = configManager.getInterfaceConfig();
        this.guiConfig = configManager.getGuiConfig();
        this.messagesConfig = configManager.getMessagesConfig();
    }

    /**
     * Reload configuration
     */
    public void reload() {
        this.interfaceConfig = configManager.getInterfaceConfig();
        this.guiConfig = configManager.getGuiConfig();
        this.messagesConfig = configManager.getMessagesConfig();
    }

    /**
     * Opens the main menu for a player
     */
    public void openMainMenu(Player player) {
        GuiHolder holder = new GuiHolder(plugin, GuiHolder.GuiType.ENHANCED_MAIN);
        
        // Use configured title and size from interface config
        String title = "§6§lGuard Control Panel"; // Default fallback
        int size = 54; // Default fallback
        
        // Try to get configured values
        if (interfaceConfig != null) {
            // Note: Interface config structure needs to be implemented in ConfigManager
            title = "§6§lGuard Control Panel"; // For now, keep default
            size = 54; // For now, keep default
        }
        
        Inventory gui = Bukkit.createInventory(holder, size, title);
        
        // Play configured open sound
        if (guiConfig != null && guiConfig.openSound != null) {
            try {
                Sound sound = Sound.valueOf(guiConfig.openSound.toUpperCase().replace("MINECRAFT:", ""));
                player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
            } catch (IllegalArgumentException e) {
                // Fallback to default sound
                player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
            }
        } else {
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
        }

        // Check permissions
        if (!player.hasPermission("edencorrections.duty") && !hasAnyRankPermission(player)) {
            player.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("§cYou don't have permission to use the guard system.")));
            return;
        }

        // Get player data
        UUID uuid = player.getUniqueId();
        boolean isOnDuty = plugin.getDutyManager().isOnDuty(uuid);
        Map<String, Object> progressStats = plugin.getGuardProgressionManager().getProgressionStats(player);
        String rank = (String) progressStats.getOrDefault("current_rank", "None");
        String nextRank = (String) progressStats.getOrDefault("next_rank", null);
        int points = (int) progressStats.getOrDefault("points", 0);
        int pointsNeeded = (int) progressStats.getOrDefault("points_needed", 0);

        // Status display at the top center (slot 4)
        Material statusMaterial = isOnDuty ? Material.LIME_CONCRETE : Material.RED_CONCRETE;
        String statusName = isOnDuty ? "§a§lOn Duty" : "§c§lOff Duty";
        List<String> statusLore = new ArrayList<>();
        statusLore.add("§7Rank: §e" + rank);
        if (nextRank != null) {
            statusLore.add("§7Progress to §e" + nextRank + "§7:");
            statusLore.add("§7Points: §e" + points + "§7/§e" + (points + pointsNeeded));
        }

        if (isOnDuty) {
            long startTime = plugin.getDutyManager().getSessionStartTime(uuid);
            long duration = System.currentTimeMillis() - startTime;
            int minutes = (int) (duration / (1000 * 60));
            statusLore.add("§7Time on duty: §e" + formatTime(minutes * 60L));
        } else {
            int offDutyMinutes = plugin.getDutyManager().getRemainingOffDutyMinutes(uuid);
            statusLore.add("§7Off-duty time: §e" + formatTime(offDutyMinutes * 60L));
        }

        gui.setItem(4, createItem(statusMaterial, statusName, statusLore.toArray(new String[0])));

        // Main menu options (2x3 grid in the center)

        // 1. Duty Management (slot 10)
        Material dutyMaterial = isOnDuty ? Material.LIME_DYE : Material.RED_DYE;
        String dutyName = "§e§lDuty Management";
        List<String> dutyLore = new ArrayList<>();
        dutyLore.add("§7Toggle your on/off duty status");
        dutyLore.add("§7View your time balance");
        if (isOnDuty) {
            dutyLore.add("");
            dutyLore.add("§a✓ §7Currently on duty");
            dutyLore.add("§7Earning points and rewards");
        }
        dutyLore.add("");
        dutyLore.add("§7Click to open");
        gui.setItem(10, createItem(dutyMaterial, dutyName, dutyLore.toArray(new String[0])));

        // 2. Statistics (slot 12)
        List<String> statsLore = new ArrayList<>();
        statsLore.add("§7View your guard statistics");
        statsLore.add("§7Convert duty time to tokens");
        statsLore.add("");
        statsLore.add("§7Current points: §e" + points);
        if (nextRank != null) {
            statsLore.add("§7Next rank in: §e" + pointsNeeded + " points");
        }
        statsLore.add("");
        statsLore.add("§7Click to open");
        gui.setItem(12, createItem(Material.BOOK, "§e§lStatistics & Rewards", statsLore.toArray(new String[0])));

        // 3. Actions (slot 14)
        List<String> actionLore = new ArrayList<>();
        actionLore.add("§7Record prisoner searches");
        actionLore.add("§7Log contraband and metal detections");
        actionLore.add("");
        if (isOnDuty) {
            actionLore.add("§a✓ §7Actions available");
        } else {
            actionLore.add("§c✗ §7Must be on duty");
        }
        actionLore.add("");
        actionLore.add("§7Click to open");
        gui.setItem(14, createItem(Material.IRON_SWORD, "§e§lGuard Actions", actionLore.toArray(new String[0])));

        // 4. Equipment (slot 16)
        List<String> equipLore = new ArrayList<>();
        equipLore.add("§7View available guard equipment");
        equipLore.add("§7Based on your current rank:");
        equipLore.add("§e" + rank);
        equipLore.add("");
        List<String> tools = plugin.getConfig().getStringList("guard-progression.perks." + rank.toLowerCase() + ".can-use");
        if (!tools.isEmpty()) {
            equipLore.add("§7Available tools:");
            for (String tool : tools) {
                equipLore.add("§8- §e" + tool);
            }
        }
        equipLore.add("");
        equipLore.add("§7Click to open");
        gui.setItem(16, createItem(Material.IRON_CHESTPLATE, "§e§lEquipment", equipLore.toArray(new String[0])));

        // 5. Token Management (slot 20)
        int availableTokens = getPlayerTokens(player);
        List<String> tokenLore = new ArrayList<>();
        tokenLore.add("§7Manage your guard tokens");
        tokenLore.add("§7Convert off-duty time to tokens");
        tokenLore.add("");
        tokenLore.add("§7Current tokens: §e" + availableTokens);
        tokenLore.add("§7Off-duty time: §e" + formatTime(plugin.getDutyManager().getRemainingOffDutyMinutes(uuid) * 60));
        tokenLore.add("");
        if (player.hasPermission("edencorrections.converttime")) {
            tokenLore.add("§a✓ §7Token conversion available");
        } else {
            tokenLore.add("§c✗ §7No token conversion permission");
        }
        tokenLore.add("");
        tokenLore.add("§7Click to open");
        gui.setItem(20, createItem(Material.SUNFLOWER, "§e§lToken Management", tokenLore.toArray(new String[0])));

        // 6. Shop at the bottom center (slot 22)
        List<String> shopLore = new ArrayList<>();
        shopLore.add("§7Purchase items with tokens");
        shopLore.add("§7Unlock special equipment");
        shopLore.add("");
        shopLore.add("§7Daily token bonus: §e" + getDailyTokenBonus(rank));
        shopLore.add("");
        shopLore.add("§7Click to open");
        gui.setItem(22, createItem(Material.GOLD_INGOT, "§e§lGuard Shop", shopLore.toArray(new String[0])));

        // 7. Close button (slot 31)
        gui.setItem(31, createItem(Material.BARRIER, "§c§lClose Menu",
                "§7Click to close this menu"));

        // Play sound and open
        player.openInventory(gui);
    }

    /**
     * Opens the duty management menu
     */
    public void openDutyMenu(Player player) {
        // Create inventory
        GuiHolder holder = new GuiHolder(plugin, GuiHolder.GuiType.DUTY_SELECTION);
        Inventory gui = Bukkit.createInventory(holder, SUB_MENU_SIZE, DUTY_MENU_TITLE);

        // Fill with background
        fillBackground(gui, Material.LIGHT_BLUE_STAINED_GLASS_PANE);

        // Get player data
        UUID uuid = player.getUniqueId();
        boolean isOnDuty = plugin.getDutyManager().isOnDuty(uuid);
        int offDutyMinutes = plugin.getDutyManager().getRemainingOffDutyMinutes(uuid);

        // Header
        gui.setItem(4, createItem(Material.COMPASS, "§b§lDuty Management",
                "§7Manage your guard duty status"));

        // Current status in center top (slot 13)
        Material statusMaterial = isOnDuty ? Material.LIME_CONCRETE : Material.RED_CONCRETE;
        String statusName = isOnDuty ? "§a§lCurrently On Duty" : "§c§lCurrently Off Duty";
        List<String> statusLore = new ArrayList<>();

        if (isOnDuty) {
            long startTime = plugin.getDutyManager().getSessionStartTime(uuid);
            long duration = System.currentTimeMillis() - startTime;
            int minutesServed = (int) (duration / (1000 * 60));
            int threshold = plugin.getDutyManager().getThresholdMinutes();

            statusLore.add("§7Current session: §e" + formatTime(minutesServed * 60));

            if (minutesServed >= threshold) {
                statusLore.add("§a✓ §7Threshold reached");
                statusLore.add("§7Will earn §e" + plugin.getDutyManager().getRewardMinutes() + " minutes§7 off duty");
            } else {
                statusLore.add("§c✗ §7Threshold not reached");
                statusLore.add("§7Need §e" + (threshold - minutesServed) + " more minutes§7 to earn time");
            }
        } else {
            statusLore.add("§7Available off-duty time:");
            statusLore.add("§e" + formatTime(offDutyMinutes * 60));
            statusLore.add("");
            statusLore.add("§7Maximum time: §e" + formatTime(plugin.getDutyManager().getMaxOffDutyTime() * 60));
        }

        gui.setItem(13, createItem(statusMaterial, statusName, statusLore.toArray(new String[0])));

        // Duty toggle buttons (side by side)
        if (!isOnDuty) {
            // Go on duty button
            gui.setItem(11, createItem(Material.LIME_CONCRETE, "§a§lGo On Duty",
                    "§7Start your guard shift",
                    "§7Earn off-duty time by serving",
                    "",
                    "§8Note: You will be immobilized for 30s",
                    "§8when going on duty to prepare"));
        } else {
            // Go off duty button
            gui.setItem(11, createItem(Material.RED_CONCRETE, "§c§lGo Off Duty",
                    "§7End your guard shift",
                    offDutyMinutes > 0 ?
                            "§7You have §e" + formatTime(offDutyMinutes * 60) + "§7 saved time" :
                            "§c§lWARNING: §7You have no saved time"));
        }

        // Time info
        gui.setItem(15, createItem(Material.CLOCK, "§e§lOff-Duty Time",
                "§7Available time: §e" + formatTime(offDutyMinutes * 60),
                "§7Max time: §e" + formatTime(plugin.getDutyManager().getMaxOffDutyTime() * 60),
                "",
                "§7You earn time while on duty",
                "§7after reaching the threshold."));

        // Quick conversion option if applicable
        if (player.hasPermission("edencorrections.converttime") && offDutyMinutes >= 5) {
            gui.setItem(22, createItem(Material.GOLD_NUGGET, "§e§lQuick Convert",
                    "§7Convert 30 minutes to tokens",
                    "§7Ratio: §e" + plugin.getConfig().getInt("conversion.tokens.ratio", 100) + " tokens§7/minute",
                    "",
                    "§7You would receive: §e" + (30 * plugin.getConfig().getInt("conversion.tokens.ratio", 100)) + " tokens",
                    "",
                    offDutyMinutes < 30 ?
                            "§c§lWARNING: §7You only have §e" + offDutyMinutes + " minutes§7 available" :
                            "§7Click to convert §e30 minutes§7 to tokens"));
        }

        // Back button
        gui.setItem(27, createItem(Material.ARROW, "§e§lBack to Main Menu",
                "§7Return to the main menu"));

        // Close button
        gui.setItem(35, createItem(Material.BARRIER, "§c§lClose Menu",
                "§7Click to close this menu"));

        // Play sound and open
        playSound(player, Sound.UI_BUTTON_CLICK);
        player.openInventory(gui);
    }

    /**
     * Opens the stats and rewards menu
     */
    public void openStatsMenu(Player player) {
        // Create inventory
        GuiHolder holder = new GuiHolder(plugin, GuiHolder.GuiType.STATS_VIEW);
        Inventory gui = Bukkit.createInventory(holder, SUB_MENU_SIZE, STATS_MENU_TITLE);

        // Fill with background
        fillBackground(gui, Material.GREEN_STAINED_GLASS_PANE);

        // Header
        gui.setItem(4, createItem(Material.ENCHANTED_BOOK, "§a§lStatistics & Rewards",
                "§7View your guard performance",
                "§7Convert duty time to tokens"));

        // Get player data
        UUID uuid = player.getUniqueId();
        int offDutyMinutes = plugin.getDutyManager().getRemainingOffDutyMinutes(uuid);

        // Get progression stats
        Map<String, Object> progressStats = plugin.getGuardProgressionManager().getProgressionStats(player);
        int points = (int) progressStats.getOrDefault("points", 0);
        long totalTime = (long) progressStats.getOrDefault("total_time", 0L);
        int arrests = (int) progressStats.getOrDefault("arrests", 0);
        int contraband = (int) progressStats.getOrDefault("contraband", 0);
        String currentRank = (String) progressStats.getOrDefault("current_rank", "None");
        String nextRank = (String) progressStats.getOrDefault("next_rank", null);
        int pointsNeeded = (int) progressStats.getOrDefault("points_needed", 0);

        // Rank display (slot 10)
        List<String> rankLore = new ArrayList<>();
        rankLore.add("§7Current rank: §e" + currentRank);
        if (nextRank != null) {
            rankLore.add("§7Next rank: §e" + nextRank);
            rankLore.add("§7Points needed: §e" + pointsNeeded);
        } else {
            rankLore.add("§7Maximum rank achieved!");
        }
        rankLore.add("");
        rankLore.add("§7Total points: §e" + points);
        gui.setItem(10, createItem(getRankMaterial(currentRank), "§e§lRank Progress", rankLore.toArray(new String[0])));

        // Stats summary (slot 12)
        gui.setItem(12, createItem(Material.PAPER, "§e§lPerformance Stats",
                "§7Time served: §e" + formatTime(totalTime),
                "§7Arrests made: §e" + arrests,
                "§7Contraband found: §e" + contraband,
                "§7Off-duty time: §e" + formatTime(offDutyMinutes * 60)));

        // Rank perks (slot 14)
        List<String> perkLore = getRankPerks(currentRank);
        gui.setItem(14, createItem(Material.GOLDEN_APPLE, "§e§lRank Perks", perkLore.toArray(new String[0])));

        // Token conversion section - only if player has permission
        if (player.hasPermission("edencorrections.converttime")) {
            int ratio = plugin.getConfig().getInt("conversion.tokens.ratio", 100);
            int minimum = plugin.getConfig().getInt("conversion.tokens.minimum", 5);
            boolean canConvert = offDutyMinutes >= minimum;

            // Token info (slot 16)
            List<String> tokenLore = new ArrayList<>();
            tokenLore.add("§7Conversion ratio:");
            tokenLore.add("§e" + ratio + " tokens per minute");
            tokenLore.add("");
            tokenLore.add("§7Minimum conversion: §e" + minimum + " minutes");
            tokenLore.add("");
            tokenLore.add("§7Daily token bonus: §e" + getDailyTokenBonus(currentRank));

            if (canConvert) {
                tokenLore.add("");
                tokenLore.add("§a✓ §7You can convert your time to tokens");
                tokenLore.add("§7Maximum available: §e" + (offDutyMinutes * ratio) + " tokens");
            } else {
                tokenLore.add("");
                tokenLore.add("§c✗ §7You need at least §e" + minimum + " minutes§7 to convert");
            }

            gui.setItem(16, createItem(Material.GOLD_INGOT, "§e§lToken Conversion", tokenLore.toArray(new String[0])));

            // Conversion buttons - only if can convert
            if (canConvert) {
                // Small conversion (15 min)
                int smallAmount = Math.min(15, offDutyMinutes);
                gui.setItem(19, createItem(Material.GOLD_NUGGET, "§e§lConvert §f" + smallAmount + " §e§lMinutes",
                        "§7Convert §e" + smallAmount + " minutes §7to tokens",
                        "§7You will receive: §e" + (smallAmount * ratio) + " tokens",
                        "",
                        "§7Click to convert"));

                // Medium conversion (30 min)
                int mediumAmount = Math.min(30, offDutyMinutes);
                gui.setItem(22, createItem(Material.GOLD_INGOT, "§e§lConvert §f" + mediumAmount + " §e§lMinutes",
                        "§7Convert §e" + mediumAmount + " minutes §7to tokens",
                        "§7You will receive: §e" + (mediumAmount * ratio) + " tokens",
                        "",
                        "§7Click to convert"));

                // All conversion
                gui.setItem(25, createItem(Material.GOLD_BLOCK, "§e§lConvert §fAll §e§lMinutes",
                        "§7Convert §eall " + offDutyMinutes + " minutes §7to tokens",
                        "§7You will receive: §e" + (offDutyMinutes * ratio) + " tokens",
                        "",
                        "§c§lWARNING: §7This will convert all your time",
                        "§7Click to convert"));
            }
        }

        // Back button
        gui.setItem(27, createItem(Material.ARROW, "§e§lBack to Main Menu",
                "§7Return to the main menu"));

        // Close button
        gui.setItem(35, createItem(Material.BARRIER, "§c§lClose Menu",
                "§7Click to close this menu"));

        // Play sound and open
        playSound(player, Sound.UI_BUTTON_CLICK);
        player.openInventory(gui);
    }

    private List<String> getRankPerks(String rank) {
        List<String> perks = new ArrayList<>();
        if (rank == null) return perks;

        String path = "guard-progression.perks." + rank.toLowerCase();
        if (!plugin.getConfig().contains(path)) return perks;

        // Add description
        String description = plugin.getConfig().getString(path + ".description");
        if (description != null) {
            perks.add("§7" + description);
            perks.add("");
        }

        // Add daily tokens
        int dailyTokens = plugin.getConfig().getInt(path + ".daily-tokens", 0);
        perks.add("§7Daily tokens: §e" + dailyTokens);

        // Add available tools
        List<String> tools = plugin.getConfig().getStringList(path + ".can-use");
        if (!tools.isEmpty()) {
            perks.add("");
            perks.add("§7Available tools:");
            for (String tool : tools) {
                perks.add("§8- §e" + tool);
            }
        }

        // Add buffs
        List<String> buffs = plugin.getConfig().getStringList(path + ".buffs");
        if (!buffs.isEmpty()) {
            perks.add("");
            perks.add("§7Active buffs:");
            for (String buff : buffs) {
                String[] parts = buff.split(":");
                if (parts.length >= 2) {
                    perks.add("§8- §e" + parts[0].toLowerCase() + " " + parts[1]);
                }
            }
        }

        return perks;
    }

    private int getDailyTokenBonus(String rank) {
        if (rank == null) return 0;
        return plugin.getConfig().getInt("guard-progression.perks." + rank.toLowerCase() + ".daily-tokens", 0);
    }

    /**
     * Opens the guard actions menu
     */
    public void openActionsMenu(Player player) {
        // Create inventory
        GuiHolder holder = new GuiHolder(plugin, GuiHolder.GuiType.ACTIONS_VIEW);
        Inventory gui = Bukkit.createInventory(holder, SUB_MENU_SIZE, ACTIONS_MENU_TITLE);

        // Fill with background
        fillBackground(gui, Material.RED_STAINED_GLASS_PANE);

        // Header
        gui.setItem(4, createItem(Material.IRON_BARS, "§c§lGuard Actions",
                "§7Automated action tracking and session stats"));

        // Get player data
        UUID uuid = player.getUniqueId();
        boolean isOnDuty = plugin.getDutyManager().isOnDuty(uuid);

        // Current duty status (slot 13)
        Material statusMaterial = isOnDuty ? Material.LIME_CONCRETE : Material.RED_CONCRETE;
        String statusName = isOnDuty ? "§a§lOn Duty - Actions Tracked Automatically" : "§c§lOff Duty - No Action Tracking";
        String[] statusLore = isOnDuty
                ? new String[]{"§7All guard actions are recorded", "§7automatically when you use items"}
                : new String[]{"§cYou must be on duty for", "§cautomated action tracking"};

        gui.setItem(13, createItem(statusMaterial, statusName, statusLore));

        // Current session stats
        if (isOnDuty) {
            // Search stats (slot 11)
            gui.setItem(11, createItem(Material.IRON_BARS, "§e§lSearches This Session",
                    "§7Actions recorded automatically when using:",
                    "§8• Drug Sniffer",
                    "§8• Metal Detector", 
                    "",
                    "§7Current session: §e" + plugin.getDataManager().getSearchCount(uuid),
                    "",
                    "§7Use guard items to perform searches!"));

            // Contraband stats (slot 15)
            gui.setItem(15, createItem(Material.GOLD_NUGGET, "§e§lContraband Found",
                    "§7Recorded when contraband is detected:",
                    "§8• Drug items found via Drug Sniffer",
                    "§8• Illegal items via Metal Detector",
                    "",
                    "§7Current session: §e" + plugin.getDataManager().getSuccessfulSearchCount(uuid),
                    "",
                    "§7Detection happens automatically!"));

            // Apprehension stats (slot 22)
            gui.setItem(22, createItem(Material.TRIPWIRE_HOOK, "§e§lApprehensions",
                    "§7Recorded when successfully jailing players:",
                    "§8• Using Handcuffs on wanted players",
                    "§8• Completing cuffing countdown",
                    "",
                    "§7Current session: §e" + plugin.getDataManager().getApprehensionCount(uuid),
                    "",
                    "§7Use handcuffs on wanted criminals!"));
        } else {
            // Informational displays when off duty
            gui.setItem(11, createItem(Material.BARRIER, "§8§lAutomated Searches",
                    "§cGo on duty to start tracking",
                    "§7Searches are recorded automatically",
                    "§7when using guard items"));

            gui.setItem(15, createItem(Material.BARRIER, "§8§lAutomated Detection",
                    "§cGo on duty to start tracking",
                    "§7Contraband detection is automatic",
                    "§7when items are found"));

            gui.setItem(22, createItem(Material.BARRIER, "§8§lAutomated Apprehensions",
                    "§cGo on duty to start tracking",
                    "§7Apprehensions are recorded when",
                    "§7successfully jailing players"));
        }

        // Back button
        gui.setItem(27, createItem(Material.ARROW, "§e§lBack to Main Menu",
                "§7Return to the main menu"));

        // Close button
        gui.setItem(35, createItem(Material.BARRIER, "§c§lClose Menu",
                "§7Click to close this menu"));

        // Play sound and open
        playSound(player, Sound.UI_BUTTON_CLICK);
        player.openInventory(gui);
    }

    /**
     * Opens the equipment menu
     */
    public void openEquipmentMenu(Player player) {
        // Create inventory
        GuiHolder holder = new GuiHolder(plugin, GuiHolder.GuiType.EQUIPMENT_VIEW);
        Inventory gui = Bukkit.createInventory(holder, SUB_MENU_SIZE, "§9§lGuard Equipment");

        // Fill with background
        fillBackground(gui, Material.BLUE_STAINED_GLASS_PANE);

        // Header
        gui.setItem(4, createItem(Material.IRON_CHESTPLATE, "§9§lGuard Equipment",
                "§7View your available equipment"));

        // Get player's rank and available tools
        String rank = plugin.getGuardRankManager().getPlayerRank(player);
        List<String> availableTools = rank != null ?
                plugin.getConfig().getStringList("guard-progression.perks." + rank.toLowerCase() + ".can-use") :
                new ArrayList<>();

        // Equipment categories
        addEquipmentCategory(gui, 10, Material.IRON_SWORD, "§7§lBasic Equipment",
                availableTools, "baton", "handcuffs");

        addEquipmentCategory(gui, 12, Material.TRIPWIRE_HOOK, "§7§lDetection Tools",
                availableTools, "metal-detector");

        addEquipmentCategory(gui, 14, Material.SHIELD, "§7§lDefensive Equipment",
                availableTools, "riot-shield");

        addEquipmentCategory(gui, 16, Material.SPLASH_POTION, "§7§lSpecial Equipment",
                availableTools, "taser", "tear-gas");

        // Active penalties display if applicable
        UUID playerId = player.getUniqueId();
        int cooldown = plugin.getGuardLootManager().getPlayerCooldown(playerId);
        int penalty = plugin.getGuardPenaltyManager().getPlayerLockTime(playerId);

        if (cooldown > 0 || penalty > 0) {
            List<String> penaltyLore = new ArrayList<>();
            penaltyLore.add("§7Your current status:");

            if (cooldown > 0) {
                penaltyLore.add("§7Death Cooldown: §e" + MessageUtils.formatTime(cooldown));
                penaltyLore.add("§7(No loot will drop if you die)");
            }

            if (penalty > 0) {
                if (cooldown > 0) penaltyLore.add("");
                penaltyLore.add("§7Movement Penalty: §e" + MessageUtils.formatTime(penalty));
                penaltyLore.add("§7(You cannot leave the guard area)");
            }

            gui.setItem(22, createItem(Material.REDSTONE_BLOCK, "§c§lActive Penalties",
                    penaltyLore.toArray(new String[0])));
        } else {
            // Instructions
            gui.setItem(22, createItem(Material.PAPER, "§e§lEquipment Instructions",
                    "§71. Go on duty to receive kit",
                    "§72. Use items during your shift",
                    "§73. Items are lost when going off duty",
                    "§74. Higher ranks = better equipment"));
        }

        // Back button
        gui.setItem(27, createItem(Material.ARROW, "§e§lBack to Main Menu",
                "§7Return to the main menu"));

        // Close button
        gui.setItem(35, createItem(Material.BARRIER, "§c§lClose Menu",
                "§7Click to close this menu"));

        // Play sound and open
        playSound(player, Sound.UI_BUTTON_CLICK);
        player.openInventory(gui);
    }

    private void addEquipmentCategory(Inventory gui, int slot, Material icon, String name,
                                    List<String> availableTools, String... tools) {
        List<String> lore = new ArrayList<>();
        lore.add("§7Available items:");
        
        for (String tool : tools) {
            boolean hasAccess = availableTools.contains(tool);
            lore.add((hasAccess ? "§a✓ " : "§c✗ ") + "§7" + tool);
        }
        
        gui.setItem(slot, createItem(icon, name, lore.toArray(new String[0])));
    }

    /**
     * Opens the shop menu
     */
    public void openShopMenu(Player player) {
        // Create inventory using shop config
        ConfigManager.ShopConfig shopConfig = plugin.getConfigManager().getShopConfig();
        GuiHolder holder = new GuiHolder(plugin, GuiHolder.GuiType.SHOP_VIEW);
        Inventory gui = Bukkit.createInventory(holder, shopConfig.gui.size, shopConfig.gui.title);

        // Fill with background
        fillBackground(gui, Material.YELLOW_STAINED_GLASS_PANE);

        // Token display
        int availableTokens = getPlayerTokens(player);
        gui.setItem(shopConfig.navigation.tokenDisplay.slot, createItem(
            shopConfig.navigation.tokenDisplay.material, 
            shopConfig.navigation.tokenDisplay.name,
            "§7You have §e" + availableTokens + " tokens",
            "",
            "§7Use these to purchase items below"
        ));

        // Add category buttons
        gui.setItem(shopConfig.categories.equipment.slot, createItem(
            shopConfig.categories.equipment.material,
            shopConfig.categories.equipment.name,
            "§7Browse equipment items",
            "§7• Weapons and armor",
            "§7• Combat gear",
            "",
            "§eClick to browse equipment!"
        ));

        gui.setItem(shopConfig.categories.consumables.slot, createItem(
            shopConfig.categories.consumables.material,
            shopConfig.categories.consumables.name,
            "§7Browse consumable items",
            "§7• Potions and food",
            "§7• Temporary effects",
            "",
            "§eClick to browse consumables!"
        ));

        gui.setItem(shopConfig.categories.upgrades.slot, createItem(
            shopConfig.categories.upgrades.material,
            shopConfig.categories.upgrades.name,
            "§7Browse upgrade items",
            "§7• Permanent improvements",
            "§7• Special abilities",
            "",
            "§eClick to browse upgrades!"
        ));

        // Add actual shop items
        addShopItemsToGui(gui, shopConfig, availableTokens);

        // Navigation buttons
        gui.setItem(shopConfig.navigation.backToMenu.slot, createItem(
            shopConfig.navigation.backToMenu.material,
            shopConfig.navigation.backToMenu.name,
            "§7Return to the main menu"
        ));

        // Play sound and open
        playSound(player, Sound.UI_BUTTON_CLICK);
        player.openInventory(gui);
    }

    private void addShopItemsToGui(Inventory gui, ConfigManager.ShopConfig shopConfig, int availableTokens) {
        int slot = 28; // Start slot for actual shop items
        
        // Add equipment items
        for (Map.Entry<String, ConfigManager.ShopItem> entry : shopConfig.equipmentItems.entrySet()) {
            if (slot >= gui.getSize() - 9) break; // Leave space for navigation
            addShopItemToGui(gui, slot++, entry.getKey(), entry.getValue(), availableTokens);
        }
        
        // Add consumable items
        for (Map.Entry<String, ConfigManager.ShopItem> entry : shopConfig.consumableItems.entrySet()) {
            if (slot >= gui.getSize() - 9) break; // Leave space for navigation
            addShopItemToGui(gui, slot++, entry.getKey(), entry.getValue(), availableTokens);
        }
        
        // Add upgrade items
        for (Map.Entry<String, ConfigManager.ShopItem> entry : shopConfig.upgradeItems.entrySet()) {
            if (slot >= gui.getSize() - 9) break; // Leave space for navigation
            addShopItemToGui(gui, slot++, entry.getKey(), entry.getValue(), availableTokens);
        }
    }

    private void addShopItemToGui(Inventory gui, int slot, String itemKey, ConfigManager.ShopItem shopItem, int availableTokens) {
        List<String> lore = new ArrayList<>();
        
        // Add description
        if (!shopItem.description.isEmpty()) {
            lore.add("§7" + shopItem.description);
            lore.add("");
        }
        
        // Add effects if present
        if (!shopItem.effects.isEmpty()) {
            lore.add("§6Effects:");
            for (String effect : shopItem.effects) {
                lore.add("§8  • §7" + effect);
            }
            lore.add("");
        }
        
        // Add enchantments if present
        if (!shopItem.enchantments.isEmpty()) {
            lore.add("§5Enchantments:");
            for (String enchant : shopItem.enchantments) {
                lore.add("§8  • §7" + enchant);
            }
            lore.add("");
        }
        
        // Add special properties
        if (shopItem.stunDuration > 0) {
            lore.add("§7Stun Duration: §e" + shopItem.stunDuration + "s");
        }
        if (shopItem.range > 0) {
            lore.add("§7Range: §e" + shopItem.range + " blocks");
        }
        if (shopItem.duration > 0) {
            lore.add("§7Duration: §e" + shopItem.duration + "s");
        }
        if (shopItem.radius > 0) {
            lore.add("§7Radius: §e" + shopItem.radius + " blocks");
        }
        if (shopItem.protectionDuration > 0) {
            lore.add("§7Protection Duration: §e" + shopItem.protectionDuration + "s");
        }
        if (shopItem.droppedCharges > 0) {
            lore.add("§7Dropped Charges: §e" + shopItem.droppedCharges);
        }
        
        // Add cost and purchase info
        lore.add("");
        lore.add("§7Cost: §e" + shopItem.cost + " tokens");
        lore.add("§7Amount: §e" + shopItem.amount);
        lore.add("");
        
        if (availableTokens >= shopItem.cost) {
            lore.add("§aClick to purchase!");
        } else {
            lore.add("§cNot enough tokens!");
            lore.add("§cNeed §e" + (shopItem.cost - availableTokens) + "§c more tokens");
        }

        // Create and set item
        ItemStack displayItem = createItem(shopItem.material, "§e§l" + formatItemName(itemKey), lore.toArray(new String[0]));
        gui.setItem(slot, displayItem);
    }

    private String formatItemName(String itemKey) {
        // Convert item key to display name (e.g., "smoke_bomb" -> "Smoke Bomb")
        return Arrays.stream(itemKey.split("_"))
                .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase())
                .collect(Collectors.joining(" "));
    }

    /**
     * Handles clicks in the main menu
     */
    public void handleMainMenuClick(Player player, int slot) {
        switch (slot) {
            case 10:  // Duty Management
                openDutyMenu(player);
                break;
            case 12:  // Statistics & Rewards
                openStatsMenu(player);
                break;
            case 14:  // Guard Actions
                openActionsMenu(player);
                break;
            case 16:  // Equipment
                openEquipmentMenu(player);
                break;
            case 20:  // Token Management
                openTokensView(player);
                break;
            case 22:  // Shop
                openShopMenu(player);
                break;
            case 31:  // Close
                player.closeInventory();
                break;
        }
    }

    /**
     * Handles clicks in the duty menu
     */
    public void handleDutyMenuClick(Player player, int slot) {
        UUID playerId = player.getUniqueId();
        boolean isOnDuty = plugin.getDutyManager().isOnDuty(playerId);

        switch (slot) {
            case 11:  // Toggle duty
                player.closeInventory();
                plugin.getDutyManager().toggleDuty(player);

                // Re-open GUI after a delay
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) {
                        openDutyMenu(player);
                    }
                }, 5L);
                break;
            case 22:  // Quick convert (if shown)
                if (player.hasPermission("edencorrections.converttime")) {
                    int offDutyMinutes = plugin.getDutyManager().getRemainingOffDutyMinutes(playerId);
                    if (offDutyMinutes >= 5) {
                        // Convert up to 30 minutes
                        int minutesToConvert = Math.min(30, offDutyMinutes);
                        plugin.getDutyManager().convertOffDutyMinutes(player, minutesToConvert);

                        // Play sound
                        playSound(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP);

                        // Refresh GUI
                        openDutyMenu(player);
                    }
                }
                break;
            case 27:  // Back to main menu
                openMainMenu(player);
                break;
            case 35:  // Close
                player.closeInventory();
                break;
        }
    }

    /**
     * Handles clicks in the stats menu
     */
    public void handleStatsMenuClick(Player player, int slot) {
        UUID playerId = player.getUniqueId();

        switch (slot) {
            case 19:  // Small conversion
                if (player.hasPermission("edencorrections.converttime")) {
                    int offDutyMinutes = plugin.getDutyManager().getRemainingOffDutyMinutes(playerId);
                    int smallAmount = Math.min(15, offDutyMinutes);
                    if (smallAmount >= 5) {
                        plugin.getDutyManager().convertOffDutyMinutes(player, smallAmount);
                        playSound(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP);
                        openStatsMenu(player);
                    }
                }
                break;
            case 22:  // Medium conversion
                if (player.hasPermission("edencorrections.converttime")) {
                    int offDutyMinutes = plugin.getDutyManager().getRemainingOffDutyMinutes(playerId);
                    int mediumAmount = Math.min(30, offDutyMinutes);
                    if (mediumAmount >= 5) {
                        plugin.getDutyManager().convertOffDutyMinutes(player, mediumAmount);
                        playSound(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP);
                        openStatsMenu(player);
                    }
                }
                break;
            case 25:  // All conversion
                if (player.hasPermission("edencorrections.converttime")) {
                    int offDutyMinutes = plugin.getDutyManager().getRemainingOffDutyMinutes(playerId);
                    if (offDutyMinutes >= 5) {
                        plugin.getDutyManager().convertOffDutyMinutes(player, offDutyMinutes);
                        playSound(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP);
                        openStatsMenu(player);
                    }
                }
                break;
            case 27:  // Back to main menu
                openMainMenu(player);
                break;
            case 35:  // Close
                player.closeInventory();
                break;
        }
    }

    /**
     * Handles clicks in the actions menu
     */
    public void handleActionsMenuClick(Player player, int slot) {
        // Actions menu now only shows stats and information, no clickable actions
        switch (slot) {
            case 27:  // Back to main menu
                openMainMenu(player);
                break;
            case 35:  // Close
                player.closeInventory();
                break;
            default:
                // All other slots are informational only - refresh the menu
                openActionsMenu(player);
                break;
        }
    }

    /**
     * Handles clicks in the equipment menu
     */
    public void handleEquipmentMenuClick(Player player, int slot) {
        // For now, just navigation
        if (slot == 27) {  // Back to main menu
            openMainMenu(player);
        } else if (slot == 35) {  // Close
            player.closeInventory();
        }
    }

    /**
     * Handles clicks in the shop menu
     */
    public void handleShopMenuClick(Player player, int slot) {
        ConfigManager.ShopConfig shopConfig = plugin.getConfigManager().getShopConfig();
        int availableTokens = getPlayerTokens(player);

        // Check navigation buttons first
        if (slot == shopConfig.navigation.backToMenu.slot) {
            openMainMenu(player);
            return;
        }

        // Check if it's a shop item slot
        if (slot >= 28 && slot < shopConfig.gui.size - 9) {
            // Find which shop item was clicked
            ConfigManager.ShopItem clickedItem = findShopItemBySlot(slot, shopConfig);
            String itemKey = findShopItemKeyBySlot(slot, shopConfig);
            
            if (clickedItem != null && itemKey != null) {
                handleDynamicShopPurchase(player, itemKey, clickedItem);
                return;
            }
        }

        // Check category buttons (for future expansion)
        if (slot == shopConfig.categories.equipment.slot ||
            slot == shopConfig.categories.consumables.slot ||
            slot == shopConfig.categories.upgrades.slot) {
            // For now, just refresh the shop (categories are displayed inline)
            openShopMenu(player);
            return;
        }

        // Fallback - close inventory for unhandled clicks
        player.closeInventory();
    }

    private ConfigManager.ShopItem findShopItemBySlot(int targetSlot, ConfigManager.ShopConfig shopConfig) {
        int currentSlot = 28;
        
        // Check equipment items
        for (ConfigManager.ShopItem item : shopConfig.equipmentItems.values()) {
            if (currentSlot == targetSlot) return item;
            currentSlot++;
            if (currentSlot >= shopConfig.gui.size - 9) break;
        }
        
        // Check consumable items
        for (ConfigManager.ShopItem item : shopConfig.consumableItems.values()) {
            if (currentSlot == targetSlot) return item;
            currentSlot++;
            if (currentSlot >= shopConfig.gui.size - 9) break;
        }
        
        // Check upgrade items
        for (ConfigManager.ShopItem item : shopConfig.upgradeItems.values()) {
            if (currentSlot == targetSlot) return item;
            currentSlot++;
            if (currentSlot >= shopConfig.gui.size - 9) break;
        }
        
        return null;
    }

    private String findShopItemKeyBySlot(int targetSlot, ConfigManager.ShopConfig shopConfig) {
        int currentSlot = 28;
        
        // Check equipment items
        for (String key : shopConfig.equipmentItems.keySet()) {
            if (currentSlot == targetSlot) return key;
            currentSlot++;
            if (currentSlot >= shopConfig.gui.size - 9) break;
        }
        
        // Check consumable items
        for (String key : shopConfig.consumableItems.keySet()) {
            if (currentSlot == targetSlot) return key;
            currentSlot++;
            if (currentSlot >= shopConfig.gui.size - 9) break;
        }
        
        // Check upgrade items
        for (String key : shopConfig.upgradeItems.keySet()) {
            if (currentSlot == targetSlot) return key;
            currentSlot++;
            if (currentSlot >= shopConfig.gui.size - 9) break;
        }
        
        return null;
    }

    private void handleDynamicShopPurchase(Player player, String itemKey, ConfigManager.ShopItem shopItem) {
        // Check if player has enough tokens
        if (!plugin.getGuardTokenManager().hasTokens(player.getUniqueId(), shopItem.cost)) {
            int needed = shopItem.cost - getPlayerTokens(player);
            player.sendMessage("§c§lNot enough tokens! §7You need §e" + needed + "§7 more tokens.");
            playSound(player, Sound.ENTITY_VILLAGER_NO);
            return;
        }

        // Deduct tokens
        if (!plugin.getGuardTokenManager().takeTokens(player, shopItem.cost, "Shop purchase: " + itemKey)) {
            player.sendMessage("§c§lError! §7Could not process your purchase. Please try again.");
            playSound(player, Sound.ENTITY_VILLAGER_NO);
            return;
        }

        // Create and give the item
        boolean success = createAndGiveShopItem(player, itemKey, shopItem);
        
        if (success) {
            player.sendMessage("§a§lPurchase successful! §7You received: §e" + formatItemName(itemKey));
            playSound(player, Sound.ENTITY_PLAYER_LEVELUP);
            
            // Refresh the shop menu
            openShopMenu(player);
        } else {
            // Refund the tokens
            plugin.getGuardTokenManager().giveTokens(player, shopItem.cost, "Shop refund: " + itemKey);
            player.sendMessage("§c§lError! §7Could not create item. Tokens refunded.");
            playSound(player, Sound.ENTITY_VILLAGER_NO);
        }
    }

    private boolean createAndGiveShopItem(Player player, String itemKey, ConfigManager.ShopItem shopItem) {
        try {
            ItemStack item = new ItemStack(shopItem.material, shopItem.amount);
            
            // Apply custom display name if needed
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§e§l" + formatItemName(itemKey));
                
                // Add description as lore if present
                if (!shopItem.description.isEmpty()) {
                    List<String> lore = new ArrayList<>();
                    lore.add("§7" + shopItem.description);
                    if (!shopItem.effects.isEmpty()) {
                        lore.add("");
                        lore.add("§6Effects:");
                        for (String effect : shopItem.effects) {
                            lore.add("§8  • §7" + effect);
                        }
                    }
                    meta.setLore(lore);
                }
                
                item.setItemMeta(meta);
            }
            
            // Apply enchantments if specified
            if (!shopItem.enchantments.isEmpty()) {
                for (String enchantmentStr : shopItem.enchantments) {
                    String[] parts = enchantmentStr.split(":");
                    if (parts.length >= 2) {
                        try {
                            Enchantment enchantment = Enchantment.getByName(parts[0].toUpperCase());
                            int level = Integer.parseInt(parts[1]);
                            if (enchantment != null) {
                                item.addUnsafeEnchantment(enchantment, level);
                            }
                        } catch (Exception e) {
                            plugin.getLogger().warning("Invalid enchantment in shop item " + itemKey + ": " + enchantmentStr);
                        }
                    }
                }
            }
            
            // Give the item to the player
            player.getInventory().addItem(item);
            
            // Apply effects if this is a consumable with effects
            if (!shopItem.effects.isEmpty()) {
                applyShopItemEffects(player, shopItem);
            }
            
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to create shop item " + itemKey + " for " + player.getName() + ": " + e.getMessage());
            return false;
        }
    }

    private void applyShopItemEffects(Player player, ConfigManager.ShopItem shopItem) {
        for (String effectStr : shopItem.effects) {
            String[] parts = effectStr.split(":");
            if (parts.length >= 3) {
                try {
                    PotionEffectType effectType = PotionEffectType.getByName(parts[0].toUpperCase());
                    int amplifier = Integer.parseInt(parts[1]);
                    int duration = Integer.parseInt(parts[2]);
                    
                    if (effectType != null) {
                        player.addPotionEffect(new PotionEffect(effectType, duration * 20, amplifier));
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Invalid effect format: " + effectStr);
                }
            }
        }
    }

    private void addDynamicShopItems(Inventory gui, Player player, int availableTokens) {
        // This method is no longer used - replaced with addShopItemsToGui
        // Keeping for backward compatibility but functionality moved to proper shop loading
    }

    /**
     * Fills the GUI background with a material
     */
    private void fillBackground(Inventory gui, Material material) {
        ItemStack filler = createItem(material, " ", "");
        for (int i = 0; i < gui.getSize(); i++) {
            gui.setItem(i, filler);
        }
    }

    /**
     * Helper method to create inventory items
     */
    private ItemStack createItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore.length > 0) {
                meta.setLore(Arrays.asList(lore));
            }
            // Hide enchants, attributes, etc.
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS,
                    ItemFlag.HIDE_DYE);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Play a sound for a player
     */
    private void playSound(Player player, Sound sound) {
        player.playSound(player.getLocation(), sound, 0.7f, 1.0f);
    }

    /**
     * Format time in seconds to a readable string
     */
    private String formatTime(long seconds) {
        if (seconds < 60) {
            return seconds + "s";
        }

        long minutes = seconds / 60;
        seconds = seconds % 60;

        if (minutes < 60) {
            return minutes + "m" + (seconds > 0 ? " " + seconds + "s" : "");
        }

        long hours = minutes / 60;
        minutes = minutes % 60;

        if (hours < 24) {
            return hours + "h" + (minutes > 0 ? " " + minutes + "m" : "");
        }

        long days = hours / 24;
        hours = hours % 24;

        return days + "d" + (hours > 0 ? " " + hours + "h" : "");
    }

    /**
     * Gets the material to represent a guard rank
     */
    private Material getRankMaterial(String rank) {
        if (rank == null) return Material.BARRIER;

        switch (rank.toLowerCase()) {
            case "trainee": return Material.LEATHER_CHESTPLATE;
            case "private": return Material.CHAINMAIL_CHESTPLATE;
            case "officer": return Material.IRON_CHESTPLATE;
            case "sergeant": return Material.GOLDEN_CHESTPLATE;
            case "warden": return Material.DIAMOND_CHESTPLATE;
            default: return Material.LEATHER_CHESTPLATE;
        }
    }

    /**
     * Set a cooldown for an action
     */
    private void setCooldown(UUID playerId, String action, int seconds) {
        String key = playerId.toString() + ":" + action;
        actionCooldowns.put(UUID.fromString(key), System.currentTimeMillis() + (seconds * 1000L));
    }

    /**
     * Check if a player has a cooldown for an action
     */
    private boolean hasCooldown(UUID playerId, String action) {
        String key = playerId.toString() + ":" + action;
        try {
            UUID cooldownKey = UUID.fromString(key);
            if (actionCooldowns.containsKey(cooldownKey)) {
                long endTime = actionCooldowns.get(cooldownKey);
                if (System.currentTimeMillis() < endTime) {
                    return true;
                } else {
                    actionCooldowns.remove(cooldownKey);
                    return false;
                }
            }
        } catch (Exception e) {
            // Invalid key format, no cooldown
        }
        return false;
    }

    /**
     * Check if a player has any guard rank permission
     */
    private boolean hasAnyRankPermission(Player player) {
        if (plugin.getConfig().contains("duty.rank-kits")) {
            for (String rank : plugin.getConfig().getConfigurationSection("duty.rank-kits").getKeys(false)) {
                if (player.hasPermission("edencorrections.rank." + rank)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Check if a player should see the GUI on join
     */
    public boolean shouldShowOnJoin(Player player) {
        // First check if feature is enabled
        if (!plugin.getConfig().getBoolean("gui.show-on-join", false)) {
            return false;
        }

        // Check if player has permission
        return player.hasPermission("edencorrections.duty") || hasAnyRankPermission(player);
    }

    private int getPlayerTokens(Player player) {
        return plugin.getGuardTokenManager().getTokens(player.getUniqueId());
    }

    /**
     * Opens the token management view for a player
     */
    public void openTokensView(Player player) {
        GuiHolder holder = new GuiHolder(plugin, GuiHolder.GuiType.TOKENS_VIEW);
        Inventory gui = Bukkit.createInventory(holder, 27, "§6§lToken Management");

        // Fill with background
        fillBackground(gui, Material.YELLOW_STAINED_GLASS_PANE);

        // Current tokens display
        int currentTokens = getPlayerTokens(player);
        gui.setItem(4, createItem(Material.SUNFLOWER, "§6§lYour Tokens", 
            "§7Current Balance: §e" + currentTokens + " tokens",
            "",
            "§aClick to refresh"));

        // Off-duty minutes display
        int offDutyMinutes = plugin.getDutyManager().getRemainingOffDutyMinutes(player.getUniqueId());
        gui.setItem(10, createItem(Material.CLOCK, "§b§lOff-Duty Time", 
            "§7Available: §b" + offDutyMinutes + " minutes",
            "",
            "§7Convert to tokens for rewards"));

        // Convert minutes to tokens
        if (offDutyMinutes >= 5) {
            gui.setItem(12, createItem(Material.GOLD_NUGGET, "§a§lConvert Time → Tokens", 
                "§7Convert off-duty minutes to tokens",
                "§7Rate: 1 minute = 100 tokens",
                "",
                "§aClick to convert 5 minutes"));
        } else {
            gui.setItem(12, createItem(Material.BARRIER, "§c§lConvert Time → Tokens", 
                "§7Convert off-duty minutes to tokens",
                "§7Rate: 1 minute = 100 tokens",
                "",
                "§cNeed at least 5 minutes"));
        }

        // Daily reward
        boolean canReceiveDaily = plugin.getGuardTokenManager().canReceiveDailyReward(player);
        if (canReceiveDaily) {
            gui.setItem(14, createItem(Material.EMERALD, "§a§lDaily Reward", 
                "§7Claim your daily token bonus",
                "§7Amount: §e50 tokens",
                "",
                "§aClick to claim!"));
        } else {
            long timeUntil = plugin.getGuardTokenManager().getTimeUntilNextReward(player);
            String timeStr = formatTime(timeUntil / 1000);
            gui.setItem(14, createItem(Material.COAL, "§c§lDaily Reward", 
                "§7Claim your daily token bonus",
                "§7Next reward in: §c" + timeStr,
                "",
                "§cCome back later"));
        }

        // Shop link
        gui.setItem(16, createItem(Material.CHEST, "§6§lGuard Shop", 
            "§7Spend your tokens on equipment",
            "§7and upgrades",
            "",
            "§eClick to open shop"));

        // Back button
        gui.setItem(22, createItem(Material.ARROW, "§7« Back to Main Menu"));

        player.openInventory(gui);
        playSound(player, Sound.UI_BUTTON_CLICK);
    }

    /**
     * Handle clicks in the token management view
     */
    public void handleTokensViewClick(Player player, int slot) {
        switch (slot) {
            case 4:  // Refresh tokens
                openTokensView(player);
                break;
            case 12:  // Convert minutes to tokens
                int offDutyMinutes = plugin.getDutyManager().getRemainingOffDutyMinutes(player.getUniqueId());
                if (offDutyMinutes >= 5) {
                    boolean success = plugin.getDutyManager().convertOffDutyMinutes(player, 5);
                    if (success) {
                        player.sendMessage(MessageUtils.getPrefix(plugin).append(
                            MessageUtils.parseMessage("§aConverted 5 minutes to 500 tokens!")));
                        openTokensView(player); // Refresh view
                    }
                } else {
                    player.sendMessage(MessageUtils.getPrefix(plugin).append(
                        MessageUtils.parseMessage("§cYou need at least 5 off-duty minutes to convert!")));
                }
                break;
            case 14:  // Daily reward
                plugin.getGuardTokenManager().checkAndGiveDailyReward(player);
                openTokensView(player); // Refresh view
                break;
            case 16:  // Shop
                openShopMenu(player);
                break;
            case 22:  // Back to main menu
                openMainMenu(player);
                break;
        }
    }
}