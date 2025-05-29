package dev.lsdmc.edencorrections.gui;

import dev.lsdmc.edencorrections.EdenCorrections;
import dev.lsdmc.edencorrections.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Redesigned GUI Manager for EdenCorrections
 */
public class GuiManager {
    private final EdenCorrections plugin;

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
    }

    /**
     * Opens the main menu for a player
     */
    public void openMainMenu(Player player) {
        // Check permissions
        if (!player.hasPermission("edencorrections.duty") && !hasAnyRankPermission(player)) {
            player.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("§cYou don't have permission to use the guard system.")));
            return;
        }

        // Create inventory
        GuiHolder holder = new GuiHolder(plugin, GuiHolder.GuiType.ENHANCED_MAIN);
        Inventory gui = Bukkit.createInventory(holder, MAIN_MENU_SIZE, MAIN_MENU_TITLE);

        // Fill with background
        fillBackground(gui, Material.GRAY_STAINED_GLASS_PANE);

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

        // 5. Shop at the bottom center (slot 22)
        List<String> shopLore = new ArrayList<>();
        shopLore.add("§7Purchase items with tokens");
        shopLore.add("§7Unlock special equipment");
        shopLore.add("");
        shopLore.add("§7Daily token bonus: §e" + getDailyTokenBonus(rank));
        shopLore.add("");
        shopLore.add("§7Click to open");
        gui.setItem(22, createItem(Material.GOLD_INGOT, "§e§lGuard Shop", shopLore.toArray(new String[0])));

        // 6. Close button (slot 31)
        gui.setItem(31, createItem(Material.BARRIER, "§c§lClose Menu",
                "§7Click to close this menu"));

        // Play sound and open
        playSound(player, Sound.BLOCK_CHEST_OPEN);
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
        // Create inventory
        GuiHolder holder = new GuiHolder(plugin, GuiHolder.GuiType.SHOP_VIEW);
        Inventory gui = Bukkit.createInventory(holder, SUB_MENU_SIZE, SHOP_MENU_TITLE);

        // Fill with background
        fillBackground(gui, Material.YELLOW_STAINED_GLASS_PANE);

        // Header
        gui.setItem(4, createItem(Material.GOLD_BLOCK, "§6§lGuard Shop",
                "§7Purchase items with tokens"));

        // Get player's available tokens
        int availableTokens = getPlayerTokens(player);

        // Token display
        gui.setItem(13, createItem(Material.SUNFLOWER, "§e§lAvailable Tokens",
                "§7You have §e" + availableTokens + " tokens",
                "",
                "§7Use these to purchase items below"));

        // Shop items from config
        addDynamicShopItems(gui, player, availableTokens);

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

    private void addDynamicShopItems(Inventory gui, Player player, int availableTokens) {
        org.bukkit.configuration.file.FileConfiguration config = plugin.getConfig();
        if (!config.contains("shop")) return;
        org.bukkit.configuration.ConfigurationSection shopSection = config.getConfigurationSection("shop");
        if (shopSection == null) return;

        int slot = 19; // Start slot for shop items
        for (String key : shopSection.getKeys(false)) {
            Object value = shopSection.get(key);
            if (value instanceof org.bukkit.configuration.ConfigurationSection) {
                org.bukkit.configuration.ConfigurationSection itemSection = shopSection.getConfigurationSection(key);
                // If this section has a cost, it's a direct item
                if (itemSection.contains("cost")) {
                    addShopItemFromSection(gui, slot++, key, itemSection, availableTokens);
                } else {
                    // Nested items (e.g., potions, upgrades)
                    for (String subKey : itemSection.getKeys(false)) {
                        org.bukkit.configuration.ConfigurationSection subSection = itemSection.getConfigurationSection(subKey);
                        if (subSection != null && subSection.contains("cost")) {
                            addShopItemFromSection(gui, slot++, key + "." + subKey, subSection, availableTokens);
                        }
                    }
                }
            }
        }
    }

    private void addShopItemFromSection(Inventory gui, int slot, String id, org.bukkit.configuration.ConfigurationSection section, int availableTokens) {
        int cost = section.getInt("cost", 0);
        String name = section.getString("name", null);
        if (name == null) {
            // Fallback: prettify id
            name = "§e§l" + id.replace("_", " ").replace(".", " ");
        }
        String[] lore = buildShopItemLore(section, cost, availableTokens);
        Material material = getShopMaterialForId(id);
        gui.setItem(slot, createItem(material, name, lore));
    }

    private String[] buildShopItemLore(org.bukkit.configuration.ConfigurationSection section, int cost, int availableTokens) {
        java.util.List<String> lore = new java.util.ArrayList<>();
        // Add description lines if present
        for (String key : section.getKeys(false)) {
            if (key.equals("cost")) continue;
            Object value = section.get(key);
            if (value instanceof String && !((String) value).isEmpty()) {
                lore.add("§7" + key.replace("-", " ") + ": §e" + value);
            }
        }
        lore.add("");
        lore.add("§7Cost: §e" + cost + " tokens");
        lore.add("");
        if (availableTokens >= cost) {
            lore.add("§aClick to purchase!");
        } else {
            lore.add("§cNot enough tokens!");
            lore.add("§cNeed §e" + (cost - availableTokens) + "§c more tokens");
        }
        return lore.toArray(new String[0]);
    }

    private Material getShopMaterialForId(String id) {
        // Map known ids to materials, fallback to GOLD_NUGGET
        String key = id.toLowerCase();
        if (key.contains("armor")) return Material.DIAMOND_CHESTPLATE;
        if (key.contains("weapon")) return Material.DIAMOND_SWORD;
        if (key.contains("rations")) return Material.GOLDEN_APPLE;
        if (key.contains("blessing")) return Material.TOTEM_OF_UNDYING;
        if (key.contains("enchant")) return Material.ENCHANTED_BOOK;
        if (key.contains("smoke")) return Material.FIRE_CHARGE;
        if (key.contains("taser")) return Material.TRIPWIRE_HOOK;
        if (key.contains("strength")) return Material.POTION;
        if (key.contains("swiftness")) return Material.POTION;
        if (key.contains("fire-resistance")) return Material.POTION;
        if (key.contains("health")) return Material.REDSTONE;
        if (key.contains("speed")) return Material.SUGAR;
        return Material.GOLD_NUGGET;
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
        int availableTokens = getPlayerTokens(player);

        switch (slot) {
            case 19: // Armor Upgrade
                handlePurchase(player, 2000, "armor_upgrade");
                break;
            case 20: // Weapon Upgrade
                handlePurchase(player, 1500, "weapon_upgrade");
                break;
            case 22: // Guard Rations
                handlePurchase(player, 500, "guard_rations");
                break;
            case 24: // Guard's Blessing
                handlePurchase(player, 5000, "guards_blessing");
                break;
            case 25: // Enchantment Package
                handlePurchase(player, 3000, "enchantment_package");
                break;
            case 27: // Back to main menu
                openMainMenu(player);
                break;
            case 35: // Close
                player.closeInventory();
                break;
        }
    }

    private void handlePurchase(Player player, int cost, String itemId) {
        if (!plugin.getGuardTokenManager().hasTokens(player.getUniqueId(), cost)) {
            player.sendMessage("§c§lNot enough tokens! §7You need §e" + (cost - getPlayerTokens(player)) + "§7 more tokens.");
            playSound(player, Sound.ENTITY_VILLAGER_NO);
            return;
        }
        boolean success = processPurchase(player, cost, itemId);
        if (success) {
            openShopMenu(player);
            playSound(player, Sound.ENTITY_PLAYER_LEVELUP);
            player.sendMessage("§a§lPurchase successful! §7Thank you for your purchase.");
        } else {
            player.sendMessage("§c§lError! §7Could not process your purchase. Please try again.");
            playSound(player, Sound.ENTITY_VILLAGER_NO);
        }
    }

    private boolean processPurchase(Player player, int cost, String itemId) {
        // Deduct tokens first
        if (!plugin.getGuardTokenManager().takeTokens(player, cost, "Shop purchase: " + itemId)) {
            return false;
        }
        // TODO: Grant the purchased item/upgrade here
        return true;
    }

    /**
     * Main handler for GUI clicks - determines which sub-handler to call
     */
    public void handleEnhancedGuiClick(Player player, int slot, Inventory inventory) {
        // Check the GUI type
        if (!(inventory.getHolder() instanceof GuiHolder)) {
            return;
        }

        GuiHolder holder = (GuiHolder) inventory.getHolder();
        GuiHolder.GuiType guiType = holder.getGuiType();

        // Route to appropriate handler
        switch (guiType) {
            case ENHANCED_MAIN:
                handleMainMenuClick(player, slot);
                break;
            case DUTY_SELECTION:
                handleDutyMenuClick(player, slot);
                break;
            case STATS_VIEW:
                handleStatsMenuClick(player, slot);
                break;
            case ACTIONS_VIEW:
                handleActionsMenuClick(player, slot);
                break;
            case EQUIPMENT_VIEW:
                handleEquipmentMenuClick(player, slot);
                break;
            case SHOP_VIEW:
                handleShopMenuClick(player, slot);
                break;
        }
    }

    /**
     * Legacy GUI handler - for backward compatibility
     */
    public void handleDutySelectionGuiClick(Player player, int slot) {
        // Use enhanced GUI if enabled
        if (plugin.getConfig().getBoolean("gui.use-enhanced-gui", true)) {
            openMainMenu(player);
            return;
        }

        // Otherwise, implement legacy behavior here
        // (omitted for brevity - would contain legacy implementation)
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
            case "captain": return Material.DIAMOND_CHESTPLATE;
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
}