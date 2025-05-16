package dev.lsdmc.edencorrections.gui;

import dev.lsdmc.edencorrections.EdenCorrections;
import dev.lsdmc.edencorrections.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class GuiManager {
    private final EdenCorrections plugin;

    // Constants for legacy GUI elements
    private static final String DUTY_GUI_TITLE = "§b§lGuard Duty Selection";
    private static final int DUTY_GUI_SIZE = 27; // 3 rows of 9 slots

    // Slot positions for legacy GUI
    private static final int INFO_SLOT = 4;
    private static final int ON_DUTY_SLOT = 11;
    private static final int OFF_DUTY_SLOT = 15;
    private static final int CLOSE_SLOT = 22;

    // Constants for enhanced GUI
    private static final String MAIN_GUI_TITLE = "§b§lCorrections Command Center";
    private static final int MAIN_GUI_SIZE = 54; // 6 rows of 9 slots

    // GUI navigation constants
    private static final int DUTY_SECTION = 0;
    private static final int STATS_SECTION = 1;
    private static final int EQUIPMENT_SECTION = 2;
    private static final int ACTIONS_SECTION = 3;
    private static final int TOKENS_SECTION = 4;
    private static final int SHOP_SECTION = 5;
    private static final String SHOP_TITLE = "§b§lGuard Equipment Shop";

    // Cooldown tracking for certain actions
    private final Map<UUID, Long> actionCooldowns = new HashMap<>();

    public GuiManager(EdenCorrections plugin) {
        this.plugin = plugin;
    }

    /**
     * Opens the duty selection GUI for a player
     * This is the legacy GUI, kept for backward compatibility
     */
    public void openDutySelectionGui(Player player) {
        // Check if player is eligible for guard duty (has permission)
        if (!player.hasPermission("edencorrections.duty") && !hasAnyRankPermission(player)) {
            return;
        }

        // Check if we should use the enhanced GUI instead
        if (plugin.getConfig().getBoolean("gui.use-enhanced-gui", true)) {
            openEnhancedCorrectionsGui(player);
            return;
        }

        // Create inventory with custom holder
        GuiHolder holder = new GuiHolder(plugin, GuiHolder.GuiType.DUTY_SELECTION);
        Inventory gui = Bukkit.createInventory(holder, DUTY_GUI_SIZE, DUTY_GUI_TITLE);

        // Get player's off-duty time
        int offDutyMinutes = plugin.getDutyManager().getRemainingOffDutyMinutes(player.getUniqueId());
        boolean hasOffDutyTime = offDutyMinutes > 0;
        boolean isOnDuty = plugin.getDutyManager().isOnDuty(player.getUniqueId());

        // Fill with glass panes for decoration
        ItemStack glassFiller = createItem(Material.LIGHT_BLUE_STAINED_GLASS_PANE, " ", "");
        for (int i = 0; i < DUTY_GUI_SIZE; i++) {
            gui.setItem(i, glassFiller);
        }

        // Info item
        Material infoMaterial = Material.BOOK;
        String infoName = "§e§lGuard Duty Information";
        List<String> infoLore = new ArrayList<>();

        if (hasOffDutyTime) {
            infoLore.add("§7You have §a" + formatTime(offDutyMinutes * 60) + " §7of saved off-duty time");
            infoLore.add("");
            infoLore.add("§7Choose an option below to continue");
        } else {
            infoLore.add("§7You have §cno saved off-duty time");
            infoLore.add("§7Going on duty will help you earn time");
            infoLore.add("");
            infoLore.add("§7Choose an option below to continue");
        }

        // Add explanation text to info lore
        infoLore.add("");

        // Get the duty time explanation from config if available
        String dutyExplanation = plugin.getConfig().getString("duty.explanation",
                "§7Guards earn time while on duty.\n§7This time can be used later while off duty.");

        // Add explanation text line by line
        for (String line : dutyExplanation.split("\n")) {
            infoLore.add(line);
        }

        // Additional explanation from duty manager if available
        if (plugin.getDutyManager().getTimeTerminologyExplanation() != null) {
            String[] explanationText = plugin.getDutyManager().getTimeTerminologyExplanation().split("\n");
            for (String line : explanationText) {
                infoLore.add(line);
            }
        }

        gui.setItem(INFO_SLOT, createItem(infoMaterial, infoName, infoLore.toArray(new String[0])));

        // On-duty button
        Material onDutyMaterial = isOnDuty ? Material.LIME_DYE : Material.IRON_SWORD;
        String onDutyName = isOnDuty ? "§a§lCurrently On Duty" : "§a§lGo On Duty";
        String[] onDutyLore = isOnDuty
                ? new String[]{"§7You are already on duty", "§7Click to remain on duty"}
                : new String[]{"§7Start your guard shift", "§7Earn off-duty time by serving"};
        gui.setItem(ON_DUTY_SLOT, createItem(onDutyMaterial, onDutyName, onDutyLore));

        // Off-duty button
        Material offDutyMaterial = !isOnDuty ? Material.RED_DYE : Material.CLOCK;
        String offDutyName = !isOnDuty ? "§c§lCurrently Off Duty" : "§c§lGo Off Duty";
        String[] offDutyLore;

        if (!isOnDuty) {
            offDutyLore = new String[]{"§7You are already off duty", "§7Click to remain off duty"};
        } else if (hasOffDutyTime) {
            offDutyLore = new String[]{"§7End your guard shift", "§7Use your saved time: §a" + formatTime(offDutyMinutes * 60)};
        } else {
            offDutyLore = new String[]{"§7End your guard shift", "§c§lWARNING: §7You have no saved time"};
        }

        gui.setItem(OFF_DUTY_SLOT, createItem(offDutyMaterial, offDutyName, offDutyLore));

        // Close button
        gui.setItem(CLOSE_SLOT, createItem(Material.BARRIER, "§c§lClose Menu", "§7Click to close this menu"));

        // Open the GUI
        playOpenSound(player);
        player.openInventory(gui);
    }

    /**
     * Opens the main enhanced GUI for a player
     */
    public void openEnhancedCorrectionsGui(Player player) {
        // Check if player is eligible for guard duty (has permission)
        if (!player.hasPermission("edencorrections.duty") && !hasAnyRankPermission(player)) {
            return;
        }

        // Create inventory with custom holder
        GuiHolder holder = new GuiHolder(plugin, GuiHolder.GuiType.ENHANCED_MAIN);
        Inventory gui = Bukkit.createInventory(holder, MAIN_GUI_SIZE, MAIN_GUI_TITLE);

        // Create a more elegant background pattern
        fillBackgroundPattern(gui);

        // Add navigation buttons at the top
        addNavigationButtons(gui, player, DUTY_SECTION);

        // Load the main duty section by default
        loadDutySectionContent(gui, player);

        // Play sound if enabled
        playOpenSound(player);

        // Open the GUI
        player.openInventory(gui);
    }

    /**
     * Creates a more elegant background pattern
     */
    private void fillBackgroundPattern(Inventory gui) {
        // Border glass (dark blue)
        ItemStack borderGlass = createItem(Material.BLUE_STAINED_GLASS_PANE, " ", "");
        ItemStack centerGlass = createItem(Material.LIGHT_BLUE_STAINED_GLASS_PANE, " ", "");
        ItemStack cornerGlass = createItem(Material.CYAN_STAINED_GLASS_PANE, " ", "");

        // Fill all slots with the center glass first
        for (int i = 0; i < gui.getSize(); i++) {
            gui.setItem(i, centerGlass);
        }

        // Top and bottom rows
        for (int i = 0; i < 9; i++) {
            gui.setItem(i, borderGlass); // Top row
            gui.setItem(45 + i, borderGlass); // Bottom row
        }

        // Side columns
        for (int i = 1; i < 5; i++) {
            gui.setItem(i * 9, borderGlass); // Left column
            gui.setItem(i * 9 + 8, borderGlass); // Right column
        }

        // Corner pieces for style
        gui.setItem(0, cornerGlass); // Top left
        gui.setItem(8, cornerGlass); // Top right
        gui.setItem(45, cornerGlass); // Bottom left
        gui.setItem(53, cornerGlass); // Bottom right

        // Clear the content area
        clearContentArea(gui);
    }

    /**
     * Adds navigation buttons to the top of the GUI
     */
    private void addNavigationButtons(Inventory gui, Player player, int activeSection) {
        // Duty status button (always slot 1)
        boolean isOnDuty = plugin.getDutyManager().isOnDuty(player.getUniqueId());
        ItemStack dutyButton = createDutyStatusButton(player, isOnDuty, activeSection == DUTY_SECTION);
        gui.setItem(1, dutyButton);

        // Statistics button (slot 2)
        ItemStack statsButton = createNavigationButton(
                Material.BOOK,
                "§e§lStatistics & Info",
                "§7View your duty statistics and info",
                activeSection == STATS_SECTION);
        gui.setItem(2, statsButton);

        // Equipment button (slot 3)
        ItemStack equipButton = createNavigationButton(
                Material.IRON_CHESTPLATE,
                "§e§lEquipment & Gear",
                "§7Manage your guard equipment",
                activeSection == EQUIPMENT_SECTION);
        gui.setItem(3, equipButton);

        // Actions button (slot 4)
        ItemStack actionsButton = createNavigationButton(
                Material.IRON_SWORD,
                "§e§lGuard Actions",
                "§7Record prisoner searches and actions",
                activeSection == ACTIONS_SECTION);
        gui.setItem(4, actionsButton);

        // Tokens button (slot 5)
        ItemStack tokensButton = createNavigationButton(
                Material.GOLD_INGOT,
                "§e§lToken Conversion",
                "§7Convert duty time to tokens",
                activeSection == TOKENS_SECTION);
        gui.setItem(5, tokensButton);

        // NEW: Shop button (slot 6)
        ItemStack shopButton = createNavigationButton(
                Material.EMERALD,
                "§e§lGuard Shop",
                "§7Purchase special guard equipment",
                activeSection == SHOP_SECTION);
        gui.setItem(6, shopButton);

        // Close button (slot 8)
        gui.setItem(8, createItem(Material.BARRIER, "§c§lClose Menu", "§7Click to close this menu"));
    }

    /**
     * Creates a navigation button for the GUI
     */
    private ItemStack createNavigationButton(Material material, String name, String description, boolean isActive) {
        ItemStack button = new ItemStack(material);
        ItemMeta meta = button.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(name);

            List<String> lore = new ArrayList<>();
            lore.add(description);
            lore.add("");

            if (isActive) {
                lore.add("§a§lCURRENTLY VIEWING");
                // Add glow effect to active tab
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            } else {
                lore.add("§7Click to view");
            }

            meta.setLore(lore);
            button.setItemMeta(meta);
        }

        return button;
    }

    /**
     * Creates the duty status button
     */
    private ItemStack createDutyStatusButton(Player player, boolean isOnDuty, boolean isActive) {
        Material material = isOnDuty ? Material.LIME_DYE : Material.RED_DYE;
        String name = isOnDuty ? "§a§lCurrently On Duty" : "§c§lCurrently Off Duty";

        ItemStack button = new ItemStack(material);
        ItemMeta meta = button.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(name);

            List<String> lore = new ArrayList<>();
            if (isOnDuty) {
                long startTime = plugin.getDutyManager().getSessionStartTime(player.getUniqueId());
                long duration = System.currentTimeMillis() - startTime;
                int minutes = (int) (duration / (1000 * 60));

                lore.add("§7Time on duty: §e" + formatTime(minutes * 60));
                lore.add("§7Searches: §e" + plugin.getDataManager().getSearchCount(player.getUniqueId()));
                lore.add("§7Successful searches: §e" + plugin.getDataManager().getSuccessfulSearchCount(player.getUniqueId()));
            } else {
                int offDutyMinutes = plugin.getDutyManager().getRemainingOffDutyMinutes(player.getUniqueId());
                lore.add("§7Off-duty time: §e" + formatTime(offDutyMinutes * 60));
            }

            lore.add("");
            lore.add("§7Click to §eview duty management");

            if (isActive) {
                lore.add("");
                lore.add("§a§lCURRENTLY VIEWING");
                // Add glow effect
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }

            meta.setLore(lore);
            button.setItemMeta(meta);
        }

        return button;
    }

    /**
     * Loads the duty section content
     */
    private void loadDutySectionContent(Inventory gui, Player player) {
        // Clear the content area
        clearContentArea(gui);

        // Get player data
        UUID uuid = player.getUniqueId();
        boolean isOnDuty = plugin.getDutyManager().isOnDuty(uuid);
        int offDutyMinutes = plugin.getDutyManager().getRemainingOffDutyMinutes(uuid);
        String rank = plugin.getGuardRankManager().getPlayerRank(player);

        // Section header with decorative elements
        gui.setItem(10, createItem(Material.SHIELD, "§6§l≡ Duty Management ≡",
                "§7Manage your guard duty status and time"));

        // Rank display with improved visuals
        String rankDisplay = rank != null ?
                rank.substring(0, 1).toUpperCase() + rank.substring(1) : "None";

        Material rankMaterial = getRankMaterial(rank);
        ItemStack rankItem = createItem(
                rankMaterial,
                "§e§lRank: §f" + rankDisplay,
                "§7Your current guard rank",
                "§7Determines equipment and privileges");

        // Add glow effect to rank item
        ItemMeta rankMeta = rankItem.getItemMeta();
        if (rankMeta != null) {
            rankMeta.addEnchant(Enchantment.UNBREAKING, 1, true);
            rankMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            rankItem.setItemMeta(rankMeta);
        }

        gui.setItem(12, rankItem);

        // Time display with improved visuals
        Material timeMaterial = offDutyMinutes > 0 ? Material.CLOCK : Material.BARRIER;
        List<String> timeLore = new ArrayList<>();
        timeLore.add("§7Off-duty time balance:");
        timeLore.add("§e" + formatTime(offDutyMinutes * 60));
        timeLore.add("");
        timeLore.add("§7Maximum time limit:");
        timeLore.add("§e" + formatTime(plugin.getDutyManager().getMaxOffDutyTime() * 60));
        if (isOnDuty) {
            long startTime = plugin.getDutyManager().getSessionStartTime(uuid);
            long duration = System.currentTimeMillis() - startTime;
            int minutesServed = (int) (duration / (1000 * 60));
            int threshold = plugin.getDutyManager().getThresholdMinutes();

            timeLore.add("");
            timeLore.add("§7Current session duration:");
            timeLore.add("§e" + formatTime(minutesServed * 60));

            if (minutesServed >= threshold) {
                timeLore.add("");
                timeLore.add("§a✓ §7Threshold reached");
                timeLore.add("§7Will earn §e" + plugin.getDutyManager().getRewardMinutes() + " minutes§7 off duty");
            } else {
                timeLore.add("");
                timeLore.add("§c✗ §7Threshold not reached");
                timeLore.add("§7Need §e" + (threshold - minutesServed) + " more minutes§7 to earn time");
            }
        }
        gui.setItem(14, createItem(timeMaterial, "§e§lOff-Duty Time Balance", timeLore.toArray(new String[0])));

        // Duty toggle button with enhanced design
        if (!isOnDuty) {
            ItemStack onDutyButton = createItem(Material.LIME_CONCRETE, "§a§l⚔ Go On Duty ⚔",
                    "§7Start your guard shift",
                    "§7Earn off-duty time by serving",
                    "",
                    "§8Note: You will be immobilized for 30s",
                    "§8when going on duty to allow inmates",
                    "§8to prepare for guard patrol");

            // Add glow effect
            ItemMeta buttonMeta = onDutyButton.getItemMeta();
            if (buttonMeta != null) {
                buttonMeta.addEnchant(Enchantment.UNBREAKING, 1, true);
                buttonMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                onDutyButton.setItemMeta(buttonMeta);
            }

            gui.setItem(22, onDutyButton);
        } else {
            ItemStack offDutyButton = createItem(Material.RED_CONCRETE, "§c§l⚔ Go Off Duty ⚔",
                    "§7End your guard shift",
                    offDutyMinutes > 0 ?
                            "§7You have §e" + formatTime(offDutyMinutes * 60) + "§7 saved time" :
                            "§c§lWARNING: §7You have no saved time");

            // Add glow effect
            ItemMeta buttonMeta = offDutyButton.getItemMeta();
            if (buttonMeta != null) {
                buttonMeta.addEnchant(Enchantment.UNBREAKING, 1, true);
                buttonMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                offDutyButton.setItemMeta(buttonMeta);
            }

            gui.setItem(22, offDutyButton);
        }

        // Add time conversion shortcut
        if (player.hasPermission("edencorrections.converttime") && offDutyMinutes >= 5) {
            gui.setItem(30, createItem(Material.GOLD_INGOT, "§e§l⚡ Quick Convert ⚡",
                    "§7Convert 30 minutes to tokens",
                    "§7Current ratio: §e" + plugin.getConfig().getInt("conversion.tokens.ratio", 100) + " tokens§7 per minute",
                    "",
                    "§7You would receive: §e" + (30 * plugin.getConfig().getInt("conversion.tokens.ratio", 100)) + " tokens",
                    "",
                    offDutyMinutes < 30 ?
                            "§c§lWARNING: §7You only have §e" + offDutyMinutes + " minutes§7 available" :
                            "§7Click to convert §e30 minutes§7 to tokens"));
        }

        // Add duty explanation with scrolls
        gui.setItem(32, createItem(Material.BOOK, "§e§l📜 Duty System Info 📜",
                plugin.getDutyManager().getTimeTerminologyExplanation().split("\n")));

        // Add shop shortcut if on duty
        if (isOnDuty && player.hasPermission("edencorrections.shop")) {
            ItemStack shopItem = createItem(Material.EMERALD, "§a§l💰 Guard Shop 💰",
                    "§7Access the guard equipment shop",
                    "§7Purchase special items and upgrades",
                    "",
                    "§7Click to browse available items");
            gui.setItem(40, shopItem);
        }

        // Add duty action shortcuts if on duty (with improved icons)
        if (isOnDuty && player.hasPermission("edencorrections.duty.actions")) {
            // Create a visually pleasing row of action buttons
            gui.setItem(24, createItem(Material.IRON_BARS, "§e§l🔍 Record Search",
                    "§7Record a prisoner search",
                    "",
                    "§7Total searches this session: §e" + plugin.getDataManager().getSearchCount(uuid)));

            gui.setItem(25, createItem(Material.GOLD_NUGGET, "§e§l💰 Record Contraband",
                    "§7Record finding contraband",
                    "",
                    "§7Total contraband found: §e" + plugin.getDataManager().getSuccessfulSearchCount(uuid)));

            gui.setItem(26, createItem(Material.TRIPWIRE_HOOK, "§e§l🔔 Record Detection",
                    "§7Record a successful metal detection",
                    "",
                    "§7Total detections: §e" + plugin.getDataManager().getMetalDetectCount(uuid)));
        }
    }

    /**
     * Loads the stats section content
     */
    private void loadStatsSectionContent(Inventory gui, Player player) {
        // Clear the content area
        clearContentArea(gui);

        UUID uuid = player.getUniqueId();
        boolean isOnDuty = plugin.getDutyManager().isOnDuty(uuid);

        // Section title
        gui.setItem(10, createItem(Material.BOOK, "§6§lGuard Statistics",
                "§7View your guard duty statistics"));

        // Current status
        Material statusMaterial = isOnDuty ? Material.LIME_DYE : Material.RED_DYE;
        String statusName = isOnDuty ? "§a§lCurrently On Duty" : "§c§lCurrently Off Duty";
        List<String> statusLore = new ArrayList<>();

        if (isOnDuty) {
            long startTime = plugin.getDutyManager().getSessionStartTime(uuid);
            long duration = System.currentTimeMillis() - startTime;
            int minutesServed = (int) (duration / (1000 * 60));

            statusLore.add("§7Current session duration:");
            statusLore.add("§e" + formatTime(minutesServed * 60));
            statusLore.add("");
            statusLore.add("§7Session started at:");
            statusLore.add("§e" + new java.text.SimpleDateFormat("MM/dd/yyyy HH:mm:ss").format(new java.util.Date(startTime)));
        } else {
            statusLore.add("§7You are currently off duty");
            statusLore.add("");
            statusLore.add("§7Use the Duty Management tab");
            statusLore.add("§7to go on duty");
        }

        gui.setItem(12, createItem(statusMaterial, statusName, statusLore.toArray(new String[0])));

        // Off-duty time balance
        int offDutyMinutes = plugin.getDutyManager().getRemainingOffDutyMinutes(uuid);
        List<String> timeLore = new ArrayList<>();
        timeLore.add("§7Available off-duty time:");
        timeLore.add("§e" + formatTime(offDutyMinutes * 60));
        timeLore.add("");
        timeLore.add("§7Maximum accumulation:");
        timeLore.add("§e" + formatTime(plugin.getDutyManager().getMaxOffDutyTime() * 60));

        gui.setItem(14, createItem(Material.CLOCK, "§e§lTime Balance", timeLore.toArray(new String[0])));

        // Search statistics
        int searches = plugin.getDataManager().getSearchCount(uuid);
        int successfulSearches = plugin.getDataManager().getSuccessfulSearchCount(uuid);
        int metalDetections = plugin.getDataManager().getMetalDetectCount(uuid);

        double successRate = searches > 0 ? (double) successfulSearches / searches * 100.0 : 0.0;

        List<String> searchLore = new ArrayList<>();
        searchLore.add("§7Total searches: §e" + searches);
        searchLore.add("§7Successful searches: §e" + successfulSearches);
        searchLore.add("§7Success rate: §e" + String.format("%.1f%%", successRate));
        searchLore.add("§7Metal detections: §e" + metalDetections);

        gui.setItem(20, createItem(Material.IRON_BARS, "§e§lSearch Statistics", searchLore.toArray(new String[0])));

        // System information
        List<String> systemLore = new ArrayList<>();
        systemLore.add("§7Current threshold: §e" + plugin.getDutyManager().getThresholdMinutes() + " minutes");
        systemLore.add("§7Reward time: §e" + plugin.getDutyManager().getRewardMinutes() + " minutes");
        systemLore.add("");
        systemLore.add("§7Token conversion ratio:");
        systemLore.add("§e" + plugin.getConfig().getInt("conversion.tokens.ratio", 100) + " tokens per minute");
        systemLore.add("");
        systemLore.add("§7Minimum conversion: §e" + plugin.getConfig().getInt("conversion.tokens.minimum", 5) + " minutes");

        gui.setItem(24, createItem(Material.ENCHANTED_BOOK, "§e§lSystem Information", systemLore.toArray(new String[0])));

        // Current rank information
        String rank = plugin.getGuardRankManager().getPlayerRank(player);
        String rankDisplay = rank != null ? rank.substring(0, 1).toUpperCase() + rank.substring(1) : "None";

        List<String> rankLore = new ArrayList<>();
        rankLore.add("§7Your current guard rank: §e" + rankDisplay);
        rankLore.add("");
        rankLore.add("§7Kit: §e" + plugin.getGuardRankManager().getKitNameForPlayer(player));

        gui.setItem(30, createItem(getRankMaterial(rank), "§e§lRank Information", rankLore.toArray(new String[0])));

        // Online guard count
        int guardCount = plugin.getGuardBuffManager().getOnlineGuardCount();
        List<String> guardCountLore = new ArrayList<>();
        guardCountLore.add("§7Current guards on duty: §e" + guardCount);

        if (guardCount == 1 && isOnDuty) {
            guardCountLore.add("");
            guardCountLore.add("§a§lYou are the only guard online!");
            guardCountLore.add("§7You receive special protection");
        }

        gui.setItem(32, createItem(Material.SHIELD, "§e§lGuard Status", guardCountLore.toArray(new String[0])));
    }

    /**
     * Loads the equipment section content
     */
    private void loadEquipmentSectionContent(Inventory gui, Player player) {
        // Clear the content area
        clearContentArea(gui);

        // Section title
        gui.setItem(10, createItem(Material.IRON_CHESTPLATE, "§6§lGuard Equipment",
                "§7View and manage your guard equipment"));

        // Get player's rank
        String rank = plugin.getGuardRankManager().getPlayerRank(player);
        String rankDisplay = rank != null ? rank.substring(0, 1).toUpperCase() + rank.substring(1) : "None";

        // Rank information
        gui.setItem(12, createItem(
                getRankMaterial(rank),
                "§e§lRank: §f" + rankDisplay,
                "§7Your current guard rank",
                "§7Determines available equipment"));

        // Kit information
        String kitName = plugin.getGuardRankManager().getKitNameForPlayer(player);
        gui.setItem(14, createItem(
                Material.CHEST,
                "§e§lKit: §f" + kitName,
                "§7Your assigned equipment kit",
                "§7Received when going on duty"));

        // Placeholder for more equipment management
        gui.setItem(19, createItem(
                Material.DIAMOND_SWORD,
                "§7§lWeapons",
                "§7Standard guard weapons:",
                "§8- Iron Sword",
                "§8- Bow & Arrows",
                "§8- Shield"));

        gui.setItem(21, createItem(
                Material.DIAMOND_CHESTPLATE,
                "§7§lArmor",
                "§7Standard guard armor:",
                "§8- Iron Armor Set"));

        gui.setItem(23, createItem(
                Material.ENDER_PEARL,
                "§7§lUtility Items",
                "§7Standard guard utilities:",
                "§8- Handcuffs",
                "§8- Metal Detector",
                "§8- Radio"));

        gui.setItem(25, createItem(
                Material.BREWING_STAND,
                "§7§lConsumables",
                "§7Standard guard consumables:",
                "§8- Food",
                "§8- Potions"));

        // Equipment instructions
        gui.setItem(30, createItem(
                Material.PAPER,
                "§e§lEquipment Instructions",
                "§7How to use guard equipment:",
                "",
                "§71. Go on duty to receive kit",
                "§72. Use items as needed during duty",
                "§73. All items are lost when going off duty",
                "§74. Death may cause some items to drop",
                "§75. Higher ranks receive better equipment"));

        // Death loot information
        gui.setItem(32, createItem(
                Material.SKELETON_SKULL,
                "§e§lDeath Loot Information",
                "§7When you die as a guard:",
                "",
                "§71. You'll drop loot based on your rank",
                "§72. Better ranks drop better loot",
                "§73. After death, you'll have a cooldown",
                "§74. You'll be locked in base temporarily",
                "§75. You'll receive token compensation"));

        // Active death cooldown/penalty display if applicable
        int cooldown = plugin.getGuardLootManager().getPlayerCooldown(player.getUniqueId());
        int penalty = plugin.getGuardPenaltyManager().getPlayerLockTime(player.getUniqueId());

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

            gui.setItem(34, createItem(
                    Material.REDSTONE_BLOCK,
                    "§c§lActive Penalties",
                    penaltyLore.toArray(new String[0])));
        }
    }

    /**
     * Loads the actions section content
     */
    private void loadActionsSectionContent(Inventory gui, Player player) {
        // Clear the content area
        clearContentArea(gui);

        UUID uuid = player.getUniqueId();
        boolean isOnDuty = plugin.getDutyManager().isOnDuty(uuid);

        // Section title
        gui.setItem(10, createItem(Material.IRON_SWORD, "§6§lGuard Actions",
                "§7Record guard actions while on duty"));

        // Status display
        Material statusMaterial = isOnDuty ? Material.LIME_DYE : Material.RED_DYE;
        String statusTitle = isOnDuty ? "§a§lOn Duty - Actions Available" : "§c§lOff Duty - Actions Unavailable";

        List<String> statusLore = new ArrayList<>();
        if (isOnDuty) {
            statusLore.add("§7You are currently on duty");
            statusLore.add("§7Guard actions are available");
        } else {
            statusLore.add("§cYou must go on duty to perform actions");
            statusLore.add("§7Use the Duty Management tab");
            statusLore.add("§7to go on duty first");
        }

        gui.setItem(13, createItem(statusMaterial, statusTitle, statusLore.toArray(new String[0])));

        // Guard action buttons - only available when on duty
        if (isOnDuty && player.hasPermission("edencorrections.duty.actions")) {
            // Search option
            gui.setItem(19, createItem(Material.IRON_BARS, "§e§lRecord Search",
                    "§7Record a prisoner search",
                    "",
                    "§7Total searches this session: §e" + plugin.getDataManager().getSearchCount(uuid),
                    "",
                    "§7Click to record a search"));

            // Successful search option
            gui.setItem(21, createItem(Material.GOLD_NUGGET, "§e§lRecord Contraband Found",
                    "§7Record finding contraband",
                    "",
                    "§7Total contraband found: §e" + plugin.getDataManager().getSuccessfulSearchCount(uuid),
                    "",
                    "§7Click to record found contraband"));

            // Metal detection option
            gui.setItem(23, createItem(Material.TRIPWIRE_HOOK, "§e§lRecord Metal Detection",
                    "§7Record a successful metal detection",
                    "",
                    "§7Total detections: §e" + plugin.getDataManager().getMetalDetectCount(uuid),
                    "",
                    "§7Click to record metal detection"));
        } else {
            // Unavailable actions (grayed out)
            gui.setItem(19, createItem(Material.BARRIER, "§8§lRecord Search",
                    "§cAction unavailable",
                    "§7You must be on duty to perform",
                    "§7this action"));

            gui.setItem(21, createItem(Material.BARRIER, "§8§lRecord Contraband Found",
                    "§cAction unavailable",
                    "§7You must be on duty to perform",
                    "§7this action"));

            gui.setItem(23, createItem(Material.BARRIER, "§8§lRecord Metal Detection",
                    "§cAction unavailable",
                    "§7You must be on duty to perform",
                    "§7this action"));
        }

        // Action instructions
        gui.setItem(31, createItem(Material.PAPER, "§e§lAction Instructions",
                "§7How to record guard actions:",
                "",
                "§71. Go on duty using the Duty tab",
                "§72. Perform your guard actions in-game",
                "§73. Record each action using these buttons",
                "§74. Actions contribute to your statistics",
                "§75. Actions may affect rewards"));
    }

    /**
     * Loads the tokens section content
     */
    private void loadTokensSectionContent(Inventory gui, Player player) {
        // Clear the content area
        clearContentArea(gui);

        UUID uuid = player.getUniqueId();
        int offDutyMinutes = plugin.getDutyManager().getRemainingOffDutyMinutes(uuid);
        int ratio = plugin.getConfig().getInt("conversion.tokens.ratio", 100);
        int minimum = plugin.getConfig().getInt("conversion.tokens.minimum", 5);
        boolean canConvert = player.hasPermission("edencorrections.converttime") && offDutyMinutes >= minimum;

        // Section title
        gui.setItem(10, createItem(Material.GOLD_INGOT, "§6§lToken Conversion",
                "§7Convert off-duty time to tokens"));

        // Balance display
        List<String> balanceLore = new ArrayList<>();
        balanceLore.add("§7Your current off-duty time:");
        balanceLore.add("§e" + formatTime(offDutyMinutes * 60));
        balanceLore.add("");
        balanceLore.add("§7Minimum conversion: §e" + minimum + " minutes");
        balanceLore.add("§7Conversion ratio: §e" + ratio + " tokens §7per minute");
        balanceLore.add("");

        if (canConvert) {
            balanceLore.add("§a✓ §7You can convert your time to tokens");
            balanceLore.add("§7Maximum available: §e" + (offDutyMinutes * ratio) + " tokens");
        } else {
            balanceLore.add("§c✗ §7You need at least §e" + minimum + " minutes§7 to convert");
        }

        gui.setItem(13, createItem(Material.CLOCK, "§e§lTime Balance", balanceLore.toArray(new String[0])));

        // Conversion options
        if (canConvert) {
            // Small conversion
            int smallAmount = Math.min(15, offDutyMinutes);
            gui.setItem(19, createItem(Material.GOLD_NUGGET, "§e§lConvert §f" + smallAmount + " §e§lMinutes",
                    "§7Convert §e" + smallAmount + " minutes §7to tokens",
                    "§7You will receive: §e" + (smallAmount * ratio) + " tokens",
                    "",
                    "§7Click to convert"));

            // Medium conversion
            int mediumAmount = Math.min(30, offDutyMinutes);
            gui.setItem(21, createItem(Material.GOLD_INGOT, "§e§lConvert §f" + mediumAmount + " §e§lMinutes",
                    "§7Convert §e" + mediumAmount + " minutes §7to tokens",
                    "§7You will receive: §e" + (mediumAmount * ratio) + " tokens",
                    "",
                    "§7Click to convert"));

            // Large conversion
            int largeAmount = Math.min(60, offDutyMinutes);
            gui.setItem(23, createItem(Material.GOLD_BLOCK, "§e§lConvert §f" + largeAmount + " §e§lMinutes",
                    "§7Convert §e" + largeAmount + " minutes §7to tokens",
                    "§7You will receive: §e" + (largeAmount * ratio) + " tokens",
                    "",
                    "§7Click to convert"));

            // Max conversion
            gui.setItem(25, createItem(Material.NETHERITE_INGOT, "§e§lConvert §fAll §e§lMinutes",
                    "§7Convert §eall " + offDutyMinutes + " minutes §7to tokens",
                    "§7You will receive: §e" + (offDutyMinutes * ratio) + " tokens",
                    "",
                    "§c§lWARNING: §7This will convert all your time",
                    "§7Click to convert"));

            // Custom conversion
            gui.setItem(31, createItem(Material.PAPER, "§e§lCustom Conversion",
                    "§7To convert a custom amount of time,",
                    "§7use the command:",
                    "",
                    "§f/cor convert <minutes>",
                    "",
                    "§7This allows you to specify exactly",
                    "§7how many minutes to convert"));
        } else {
            // Conversion unavailable
            gui.setItem(22, createItem(Material.BARRIER, "§c§lConversion Unavailable",
                    "§7You cannot convert time to tokens because:",
                    offDutyMinutes < minimum ?
                            "§7- You need at least §e" + minimum + " minutes" :
                            "§7- You don't have permission"));
        }

        // Conversion instructions
        gui.setItem(40, createItem(Material.BOOK, "§e§lConversion Information",
                "§7About token conversion:",
                "",
                "§71. Conversion is permanent",
                "§72. Tokens can be used in the server shop",
                "§73. The minimum conversion is §e" + minimum + " minutes",
                "§74. You get §e" + ratio + " tokens §7per minute",
                "§75. You earn time by serving as a guard"));
    }

    /**
     * Opens the Shop GUI for a player
     */
    public void openShopGui(Player player) {
        // Check if player is eligible (has permission and is on duty)
        if (!player.hasPermission("edencorrections.shop")) {
            player.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("§cYou don't have permission to access the guard shop.")));
            return;
        }

        if (!plugin.getDutyManager().isOnDuty(player.getUniqueId())) {
            player.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("§cYou must be on duty to access the guard shop.")));
            return;
        }

        // Create inventory with custom holder
        GuiHolder holder = new GuiHolder(plugin, GuiHolder.GuiType.SHOP_VIEW);
        Inventory gui = Bukkit.createInventory(holder, MAIN_GUI_SIZE, SHOP_TITLE);

        // Fill with glass panes for decoration
        fillBackgroundPattern(gui);

        // Add navigation buttons at the top
        addNavigationButtons(gui, player, SHOP_SECTION);

        // Load the shop content
        loadShopContent(gui, player);

        // Play sound if enabled
        playOpenSound(player);

        // Open the GUI
        player.openInventory(gui);
    }

    /**
     * Loads the shop section content
     */
    private void loadShopContent(Inventory gui, Player player) {
        // Clear the content area
        clearContentArea(gui);

        UUID uuid = player.getUniqueId();

        // Get player's token balance (converting from minutes if needed)
        int tokenBalance = getPlayerTokenBalance(player);

        // Section title
        gui.setItem(10, createItem(Material.EMERALD, "§6§lGuard Equipment Shop",
                "§7Purchase special equipment with tokens"));

        // Token balance display
        ItemStack balanceItem = createItem(
                Material.GOLD_INGOT,
                "§e§lToken Balance: §f" + tokenBalance,
                "§7Your current token balance",
                "§7Use these to purchase equipment");
        gui.setItem(11, balanceItem);

        // Item shop categories
        gui.setItem(12, createItem(
                Material.FIRE_CHARGE,
                "§e§lUtility Items",
                "§7Special tools and utilities for guards"));

        gui.setItem(13, createItem(
                Material.POTION,
                "§e§lPotions & Consumables",
                "§7Potions and consumable items"));

        gui.setItem(14, createItem(
                Material.GOLDEN_APPLE,
                "§e§lPermanent Upgrades",
                "§7Powerful upgrades that last until death"));

        // Shop items
        // Row 1: Utility items
        createShopItem(gui, 19, Material.FIRE_CHARGE, "§e§lSmoke Bomb", 300,
                "§7Throws a bomb that causes smoke in the area",
                "§7Anyone within 5 blocks receives blindness",
                "§7for 15 seconds, followed by darkness for",
                "§7an additional 30 seconds");

        createShopItem(gui, 20, Material.SOUL_TORCH, "§e§lGuard Taser", 1000,
                "§7Stuns the affected player for 2.5 seconds",
                "§7Guards have infinite charges",
                "§7Drops a 3-charge version on death");

        // Row 2: Potions
        createShopItem(gui, 28, Material.POTION, "§e§lStrength Potion", 500,
                "§7Strength effect for 3 minutes",
                "§7Dropped upon death");
        setGlowing(gui.getItem(28));

        createShopItem(gui, 29, Material.POTION, "§e§lSwiftness Potion", 500,
                "§7Speed effect for 5 minutes",
                "§7Dropped upon death");
        setGlowing(gui.getItem(29));

        createShopItem(gui, 30, Material.POTION, "§e§lFire Resistance Potion", 500,
                "§7Fire resistance for 5 minutes",
                "§7Dropped upon death");
        setGlowing(gui.getItem(30));

        // Row 3: Permanent upgrades
        createShopItem(gui, 37, Material.GOLDEN_APPLE, "§e§lHealth Upgrade", 5000,
                "§7Provides 2 extra hearts until death",
                "§7Max 12 hearts total (not stackable)",
                "§7Removed on death, restored when on duty");
        setGlowing(gui.getItem(37));

        createShopItem(gui, 38, Material.BLAZE_POWDER, "§e§lStrength Upgrade", 5000,
                "§7Permanent Strength I effect until death",
                "§7Not stackable with potions",
                "§7Removed on death, restored when on duty");
        setGlowing(gui.getItem(38));

        createShopItem(gui, 39, Material.SUGAR, "§e§lSpeed Upgrade", 5000,
                "§7Permanent Speed I effect until death",
                "§7Not stackable with potions",
                "§7Removed on death, restored when on duty");
        setGlowing(gui.getItem(39));

        // Information item
        gui.setItem(44, createItem(Material.PAPER, "§e§lShop Information",
                "§7About the guard shop:",
                "",
                "§71. All purchases are immediate",
                "§72. Items can be used immediately",
                "§73. Permanent upgrades last until death",
                "§74. Upgrades persist through duty cycles",
                "§75. Upgrades can't be stacked"));
    }

    /**
     * Creates a shop item with cost information
     */
    private void createShopItem(Inventory gui, int slot, Material material, String name, int cost, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(name);

            List<String> fullLore = new ArrayList<>(Arrays.asList(lore));
            fullLore.add("");
            fullLore.add("§7Cost: §e" + cost + " tokens");
            fullLore.add("");
            fullLore.add("§7Click to purchase");

            meta.setLore(fullLore);
            item.setItemMeta(meta);
        }

        gui.setItem(slot, item);
    }

    /**
     * Add enchant glow effect to an item
     */
    private void setGlowing(ItemStack item) {
        if (item != null && item.getItemMeta() != null) {
            ItemMeta meta = item.getItemMeta();
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
    }

    /**
     * Gets a player's token balance
     * This is a placeholder - implement actual token economy integration
     */
    private int getPlayerTokenBalance(Player player) {
        // Placeholder - integrate with your token economy system
        // For example:
        // return plugin.getTokenManager().getBalance(player.getUniqueId());

        // For now, assume 5000 tokens
        return 5000;
    }

    /**
     * Handles shop item purchases
     */
    private void handleShopPurchase(Player player, int slot, Inventory inventory) {
        // Get player's token balance
        int tokenBalance = getPlayerTokenBalance(player);

        // Handle purchase based on slot
        switch (slot) {
            // Smoke Bomb
            case 19:
                if (tokenBalance >= 300) {
                    purchaseItem(player, "Smoke Bomb", 300, Material.FIRE_CHARGE, 1);
                } else {
                    sendInsufficientTokensMessage(player, 300);
                }
                break;

            // Guard Taser
            case 20:
                if (tokenBalance >= 1000) {
                    purchaseItem(player, "Guard Taser", 1000, Material.SOUL_TORCH, 1);
                } else {
                    sendInsufficientTokensMessage(player, 1000);
                }
                break;

            // Strength Potion
            case 28:
                if (tokenBalance >= 500) {
                    purchasePotion(player, "Strength Potion", 500, PotionType.STRENGTH, 3*60);
                } else {
                    sendInsufficientTokensMessage(player, 500);
                }
                break;

            // Swiftness Potion
            case 29:
                if (tokenBalance >= 500) {
                    purchasePotion(player, "Swiftness Potion", 500, PotionType.SPEED, 5*60);
                } else {
                    sendInsufficientTokensMessage(player, 500);
                }
                break;

            // Fire Resistance Potion
            case 30:
                if (tokenBalance >= 500) {
                    purchasePotion(player, "Fire Resistance Potion", 500, PotionType.FIRE_RESISTANCE, 5*60);
                } else {
                    sendInsufficientTokensMessage(player, 500);
                }
                break;

            // Health Upgrade
            case 37:
                if (tokenBalance >= 5000) {
                    purchasePermanentUpgrade(player, "Health Upgrade", 5000, "health");
                } else {
                    sendInsufficientTokensMessage(player, 5000);
                }
                break;

            // Strength Upgrade
            case 38:
                if (tokenBalance >= 5000) {
                    purchasePermanentUpgrade(player, "Strength Upgrade", 5000, "strength");
                } else {
                    sendInsufficientTokensMessage(player, 5000);
                }
                break;

            // Speed Upgrade
            case 39:
                if (tokenBalance >= 5000) {
                    purchasePermanentUpgrade(player, "Speed Upgrade", 5000, "speed");
                } else {
                    sendInsufficientTokensMessage(player, 5000);
                }
                break;
        }

        // Refresh the shop GUI after purchase
        loadShopContent(inventory, player);
    }

    /**
     * Purchase a regular item
     */
    private void purchaseItem(Player player, String itemName, int cost, Material material, int amount) {
        // Deduct tokens
        // plugin.getTokenManager().removeTokens(player.getUniqueId(), cost);

        // Create item
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§e" + itemName);

            // Add lore for special items
            List<String> lore = new ArrayList<>();
            if (material == Material.FIRE_CHARGE) {
                lore.add("§7Right-click to throw a smoke bomb");
                lore.add("§7Causes blindness in a 5-block radius");
            } else if (material == Material.SOUL_TORCH) {
                lore.add("§7Right-click to tase a prisoner");
                lore.add("§7Stuns them for 2.5 seconds");
                lore.add("§7Unlimited charges for guards");
            }

            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        // Give item to player
        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        if (!leftover.isEmpty()) {
            player.getWorld().dropItemNaturally(player.getLocation(), item);
        }

        // Confirmation message
        player.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("§aPurchased §e" + itemName + " §afor §e" + cost + " tokens§a!")));

        // Play sound
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
    }

    /**
     * Purchase a potion
     */
    private void purchasePotion(Player player, String potionName, int cost, PotionType type, int durationSeconds) {
        // Deduct tokens
        // plugin.getTokenManager().removeTokens(player.getUniqueId(), cost);

        // Create potion
        ItemStack potion = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) potion.getItemMeta();
        if (meta != null) {
            meta.setBasePotionData(new PotionData(type));
            meta.setDisplayName("§e" + potionName);

            List<String> lore = new ArrayList<>();
            lore.add("§7Effect: " + type.name());
            lore.add("§7Duration: " + formatTime(durationSeconds));
            lore.add("§7Dropped upon death");

            meta.setLore(lore);
            potion.setItemMeta(meta);
        }

        // Give potion to player
        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(potion);
        if (!leftover.isEmpty()) {
            player.getWorld().dropItemNaturally(player.getLocation(), potion);
        }

        // Confirmation message
        player.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("§aPurchased §e" + potionName + " §afor §e" + cost + " tokens§a!")));

        // Play sound
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
    }

    /**
     * Purchase a permanent upgrade
     */
    private void purchasePermanentUpgrade(Player player, String upgradeName, int cost, String upgradeType) {
        // Check if player already has this upgrade
        if (hasUpgrade(player, upgradeType)) {
            player.sendMessage(MessageUtils.getPrefix(plugin).append(
                    MessageUtils.parseMessage("§cYou already have the " + upgradeName + "!")));
            return;
        }

        // Deduct tokens
        // plugin.getTokenManager().removeTokens(player.getUniqueId(), cost);

        // Apply upgrade effect
        applyUpgradeEffect(player, upgradeType);

        // Store upgrade in player data
        storePlayerUpgrade(player, upgradeType);

        // Confirmation message
        player.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("§aPurchased §e" + upgradeName + " §afor §e" + cost + " tokens§a!")));

        // Play sound
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
    }

    /**
     * Check if a player has a specific upgrade
     */
    private boolean hasUpgrade(Player player, String upgradeType) {
        // Check with the upgrade manager
        return plugin.getUpgradeManager().hasUpgrade(player.getUniqueId(), upgradeType);
    }

    /**
     * Apply the effect of an upgrade to a player
     */
    private void applyUpgradeEffect(Player player, String upgradeType) {
        // This will be delegated to the upgrade manager
        plugin.getUpgradeManager().addUpgrade(player.getUniqueId(), upgradeType);
    }

    /**
     * Store upgrade data for a player
     */
    private void storePlayerUpgrade(Player player, String upgradeType) {
        // This is handled in the addUpgrade method already
    }

    /**
     * Send insufficient tokens message
     */
    private void sendInsufficientTokensMessage(Player player, int cost) {
        player.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("§cYou don't have enough tokens! This item costs §e" + cost + " tokens§c.")));
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
    }

    /**
     * Handles clicks in the duty selection GUI (legacy GUI)
     */
    public void handleDutySelectionGuiClick(Player player, int slot) {
        // Get player's duty status
        boolean isOnDuty = plugin.getDutyManager().isOnDuty(player.getUniqueId());

        // Handle button clicks
        if (slot == ON_DUTY_SLOT) {
            // Go on duty if not already
            if (!isOnDuty) {
                player.closeInventory();

                // Check if player is in a valid duty region
                if (!isInDutyRegion(player)) {
                    // Teleport to locker room first, then toggle duty
                    teleportToLockerRoom(player);
                    // Toggle duty after teleport (can be done in a delayed task if necessary)
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        plugin.getDutyManager().toggleDuty(player);
                    }, 5L); // Short delay to ensure teleport completes
                } else {
                    // Already in valid region, just toggle duty
                    plugin.getDutyManager().toggleDuty(player);
                }
            } else {
                player.closeInventory();
                player.sendMessage(MessageUtils.getPrefix(plugin).append(
                        MessageUtils.parseMessage(plugin.getConfig().getString("gui.messages.continuing-duty",
                                "<green>You're continuing your guard shift!</green>"))));
            }
        } else if (slot == OFF_DUTY_SLOT) {
            // Go off duty if not already
            if (isOnDuty) {
                player.closeInventory();
                plugin.getDutyManager().toggleDuty(player);
            } else {
                player.closeInventory();
                player.sendMessage(MessageUtils.getPrefix(plugin).append(
                        MessageUtils.parseMessage(plugin.getConfig().getString("gui.messages.remaining-off-duty",
                                "<yellow>You're remaining off duty.</yellow>"))));
            }
        } else if (slot == CLOSE_SLOT) {
            // Just close the inventory
            player.closeInventory();
        }
    }

    /**
     * Handles clicks in the enhanced GUI
     */
    public void handleEnhancedGuiClick(Player player, int slot, Inventory inventory) {
        // Get the holder to determine the GUI type
        if (!(inventory.getHolder() instanceof GuiHolder)) {
            return;
        }

        GuiHolder holder = (GuiHolder) inventory.getHolder();

        // Handle navigation buttons
        if (slot == 1) {
            // Duty status section
            loadDutySectionContent(inventory, player);
            addNavigationButtons(inventory, player, DUTY_SECTION);
            playTabSound(player);
            return;
        } else if (slot == 2) {
            // Stats section
            loadStatsSectionContent(inventory, player);
            addNavigationButtons(inventory, player, STATS_SECTION);
            playTabSound(player);
            return;
        } else if (slot == 3) {
            // Equipment section
            loadEquipmentSectionContent(inventory, player);
            addNavigationButtons(inventory, player, EQUIPMENT_SECTION);
            playTabSound(player);
            return;
        } else if (slot == 4) {
            // Actions section
            loadActionsSectionContent(inventory, player);
            addNavigationButtons(inventory, player, ACTIONS_SECTION);
            playTabSound(player);
            return;
        } else if (slot == 5) {
            // Tokens section
            loadTokensSectionContent(inventory, player);
            addNavigationButtons(inventory, player, TOKENS_SECTION);
            playTabSound(player);
            return;
        } else if (slot == 6) {
            // Shop section
            if (player.hasPermission("edencorrections.shop") && plugin.getDutyManager().isOnDuty(player.getUniqueId())) {
                loadShopContent(inventory, player);
                addNavigationButtons(inventory, player, SHOP_SECTION);
                playTabSound(player);
            } else {
                player.sendMessage(MessageUtils.getPrefix(plugin).append(
                        MessageUtils.parseMessage("§cYou must be on duty to access the shop.")));
            }
            return;
        } else if (slot == 8) {
            // Close button
            player.closeInventory();
            return;
        }

        // Handle page-specific buttons
        if (holder.getGuiType() == GuiHolder.GuiType.ENHANCED_MAIN) {
            // Get current page by checking nav buttons
            ItemStack navButton = inventory.getItem(1);
            int currentSection = DUTY_SECTION;

            if (navButton != null && navButton.getItemMeta() != null) {
                if (navButton.getItemMeta().hasEnchant(Enchantment.UNBREAKING)) {
                    currentSection = DUTY_SECTION;
                } else if (inventory.getItem(2) != null && inventory.getItem(2).getItemMeta() != null &&
                        inventory.getItem(2).getItemMeta().hasEnchant(Enchantment.UNBREAKING)) {
                    currentSection = STATS_SECTION;
                } else if (inventory.getItem(3) != null && inventory.getItem(3).getItemMeta() != null &&
                        inventory.getItem(3).getItemMeta().hasEnchant(Enchantment.UNBREAKING)) {
                    currentSection = EQUIPMENT_SECTION;
                } else if (inventory.getItem(4) != null && inventory.getItem(4).getItemMeta() != null &&
                        inventory.getItem(4).getItemMeta().hasEnchant(Enchantment.UNBREAKING)) {
                    currentSection = ACTIONS_SECTION;
                } else if (inventory.getItem(5) != null && inventory.getItem(5).getItemMeta() != null &&
                        inventory.getItem(5).getItemMeta().hasEnchant(Enchantment.UNBREAKING)) {
                    currentSection = TOKENS_SECTION;
                } else if (inventory.getItem(6) != null && inventory.getItem(6).getItemMeta() != null &&
                        inventory.getItem(6).getItemMeta().hasEnchant(Enchantment.UNBREAKING)) {
                    currentSection = SHOP_SECTION;
                }
            }

            // Handle duty section buttons
            if (currentSection == DUTY_SECTION) {
                handleDutySectionClick(player, slot, inventory);
            }
            // Handle stats section buttons
            else if (currentSection == STATS_SECTION) {
                // Stats section has no interactive buttons currently
            }
            // Handle equipment section buttons
            else if (currentSection == EQUIPMENT_SECTION) {
                // Equipment section has no interactive buttons currently
            }
            // Handle actions section buttons
            else if (currentSection == ACTIONS_SECTION) {
                handleActionsSectionClick(player, slot, inventory);
            }
            // Handle tokens section buttons
            else if (currentSection == TOKENS_SECTION) {
                handleTokensSectionClick(player, slot, inventory);
            }
            // Handle shop section buttons
            else if (currentSection == SHOP_SECTION) {
                handleShopSectionClick(player, slot, inventory);
            }
        }
    }

    /**
     * Handles clicks in the shop GUI
     */
    public void handleShopGuiClick(Player player, int slot, Inventory inventory) {
        // Navigation button handling
        if (slot == 1) {
            // Duty section
            loadDutySectionContent(inventory, player);
            addNavigationButtons(inventory, player, DUTY_SECTION);
            playTabSound(player);
            return;
        } else if (slot == 2) {
            // Stats section
            loadStatsSectionContent(inventory, player);
            addNavigationButtons(inventory, player, STATS_SECTION);
            playTabSound(player);
            return;
        } else if (slot == 3) {
            // Equipment section
            loadEquipmentSectionContent(inventory, player);
            addNavigationButtons(inventory, player, EQUIPMENT_SECTION);
            playTabSound(player);
            return;
        } else if (slot == 4) {
            // Actions section
            loadActionsSectionContent(inventory, player);
            addNavigationButtons(inventory, player, ACTIONS_SECTION);
            playTabSound(player);
            return;
        } else if (slot == 5) {
            // Tokens section
            loadTokensSectionContent(inventory, player);
            addNavigationButtons(inventory, player, TOKENS_SECTION);
            playTabSound(player);
            return;
        } else if (slot == 6) {
            // Shop section (refresh current view)
            loadShopContent(inventory, player);
            addNavigationButtons(inventory, player, SHOP_SECTION);
            playTabSound(player);
            return;
        } else if (slot == 8) {
            // Close button
            player.closeInventory();
            return;
        }

        // Handle shop item purchases
        // These are the item slots we defined in loadShopContent
        if (slot == 19 || slot == 20 || // Utility items
                slot == 28 || slot == 29 || slot == 30 || // Potions
                slot == 37 || slot == 38 || slot == 39) { // Permanent upgrades

            // Check if player is on duty
            if (!plugin.getDutyManager().isOnDuty(player.getUniqueId())) {
                player.sendMessage(MessageUtils.getPrefix(plugin).append(
                        MessageUtils.parseMessage("§cYou must be on duty to purchase shop items.")));
                return;
            }

            // Process the purchase
            handleShopPurchase(player, slot, inventory);
        }
    }

    /**
     * Handle shop section clicks
     */
    private void handleShopSectionClick(Player player, int slot, Inventory inventory) {
        // This is a shortcut to the full shop GUI
        if (slot == 40) {
            openShopGui(player);
            return;
        }
    }

    /**
     * Handle clicks in the duty section
     */
    private void handleDutySectionClick(Player player, int slot, Inventory inventory) {
        UUID playerId = player.getUniqueId();
        boolean isOnDuty = plugin.getDutyManager().isOnDuty(playerId);

        if (slot == 22) {
            // Toggle duty button
            player.closeInventory();
            plugin.getDutyManager().toggleDuty(player);

            // Re-open GUI after a delay
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    openEnhancedCorrectionsGui(player);
                }
            }, 5L);
            return;
        } else if (slot == 30 && player.hasPermission("edencorrections.converttime")) {
            // Quick convert button
            int offDutyMinutes = plugin.getDutyManager().getRemainingOffDutyMinutes(playerId);
            if (offDutyMinutes >= 5) {
                // Convert up to 30 minutes
                int minutesToConvert = Math.min(30, offDutyMinutes);
                plugin.getDutyManager().convertOffDutyMinutes(player, minutesToConvert);

                // Play sound
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.0f);

                // Refresh GUI
                loadDutySectionContent(inventory, player);
            }
            return;
        } else if (slot == 24 && isOnDuty && player.hasPermission("edencorrections.duty.actions")) {
            // Record search button
            if (hasCooldown(playerId, "search")) {
                player.sendMessage("§cPlease wait before recording another search.");
                return;
            }

            plugin.getDutyManager().recordSearch(player);
            setCooldown(playerId, "search", 5); // 5 second cooldown

            // Refresh GUI
            loadDutySectionContent(inventory, player);
            return;
        } else if (slot == 25 && isOnDuty && player.hasPermission("edencorrections.duty.actions")) {
            // Record successful search button
            if (hasCooldown(playerId, "found")) {
                player.sendMessage("§cPlease wait before recording another successful search.");
                return;
            }

            plugin.getDutyManager().recordSuccessfulSearch(player);
            setCooldown(playerId, "found", 5); // 5 second cooldown

            // Refresh GUI
            loadDutySectionContent(inventory, player);
            return;
        } else if (slot == 26 && isOnDuty && player.hasPermission("edencorrections.duty.actions")) {
            // Record metal detection button
            if (hasCooldown(playerId, "detect")) {
                player.sendMessage("§cPlease wait before recording another metal detection.");
                return;
            }

            plugin.getDutyManager().recordMetalDetect(player);
            setCooldown(playerId, "detect", 5); // 5 second cooldown

            // Refresh GUI
            loadDutySectionContent(inventory, player);
            return;
        } else if (slot == 40 && isOnDuty && player.hasPermission("edencorrections.shop")) {
            // Shop shortcut
            openShopGui(player);
            return;
        }
    }

    /**
     * Handle clicks in the actions section
     */
    private void handleActionsSectionClick(Player player, int slot, Inventory inventory) {
        UUID playerId = player.getUniqueId();
        boolean isOnDuty = plugin.getDutyManager().isOnDuty(playerId);

        if (!isOnDuty) {
            // Not on duty, no actions available
            return;
        }

        if (slot == 19 && player.hasPermission("edencorrections.duty.actions")) {
            // Record search
            if (hasCooldown(playerId, "search")) {
                player.sendMessage("§cPlease wait before recording another search.");
                return;
            }

            plugin.getDutyManager().recordSearch(player);
            setCooldown(playerId, "search", 5); // 5 second cooldown

            // Refresh GUI
            loadActionsSectionContent(inventory, player);
            return;
        } else if (slot == 21 && player.hasPermission("edencorrections.duty.actions")) {
            // Record successful search
            if (hasCooldown(playerId, "found")) {
                player.sendMessage("§cPlease wait before recording another successful search.");
                return;
            }

            plugin.getDutyManager().recordSuccessfulSearch(player);
            setCooldown(playerId, "found", 5); // 5 second cooldown

            // Refresh GUI
            loadActionsSectionContent(inventory, player);
            return;
        } else if (slot == 23 && player.hasPermission("edencorrections.duty.actions")) {
            // Record metal detection
            if (hasCooldown(playerId, "detect")) {
                player.sendMessage("§cPlease wait before recording another metal detection.");
                return;
            }

            plugin.getDutyManager().recordMetalDetect(player);
            setCooldown(playerId, "detect", 5); // 5 second cooldown

            // Refresh GUI
            loadActionsSectionContent(inventory, player);
            return;
        }
    }

    /**
     * Handle clicks in the tokens section
     */
    private void handleTokensSectionClick(Player player, int slot, Inventory inventory) {
        UUID playerId = player.getUniqueId();
        int offDutyMinutes = plugin.getDutyManager().getRemainingOffDutyMinutes(playerId);

        if (!player.hasPermission("edencorrections.converttime") || offDutyMinutes < 5) {
            // No permission or not enough time
            return;
        }

        if (slot == 19) {
            // Small conversion (15 min)
            int amount = Math.min(15, offDutyMinutes);
            if (amount >= 5) {
                plugin.getDutyManager().convertOffDutyMinutes(player, amount);

                // Play sound
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.0f);

                // Refresh GUI
                loadTokensSectionContent(inventory, player);
            }
            return;
        } else if (slot == 21) {
            // Medium conversion (30 min)
            int amount = Math.min(30, offDutyMinutes);
            if (amount >= 5) {
                plugin.getDutyManager().convertOffDutyMinutes(player, amount);

                // Play sound
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.0f);

                // Refresh GUI
                loadTokensSectionContent(inventory, player);
            }
            return;
        } else if (slot == 23) {
            // Large conversion (60 min)
            int amount = Math.min(60, offDutyMinutes);
            if (amount >= 5) {
                plugin.getDutyManager().convertOffDutyMinutes(player, amount);

                // Play sound
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.0f);

                // Refresh GUI
                loadTokensSectionContent(inventory, player);
            }
            return;
        } else if (slot == 25) {
            // Max conversion (all min)
            if (offDutyMinutes >= 5) {
                plugin.getDutyManager().convertOffDutyMinutes(player, offDutyMinutes);

                // Play sound
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.0f);

                // Refresh GUI
                loadTokensSectionContent(inventory, player);
            }
            return;
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
     * Play a sound when opening the GUI
     */
    private void playOpenSound(Player player) {
        String soundName = plugin.getConfig().getString("gui.open-sound", "minecraft:block.chest.open");
        try {
            Sound sound = Sound.valueOf(soundName.replace("minecraft:", "").toUpperCase());
            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        } catch (Exception ignored) {
            // Invalid sound, just skip
        }
    }

    /**
     * Play a sound when changing tabs
     */
    private void playTabSound(Player player) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
    }

    /**
     * Check if a player is in a valid duty region
     */
    private boolean isInDutyRegion(Player player) {
        // Use RegionUtils to check if player is in a valid duty region
        return plugin.getRegionUtils().isInDutyRegion(player.getLocation());
    }

    /**
     * Teleport a player to the locker room location
     */
    private void teleportToLockerRoom(Player player) {
        // Get locker room location from config
        String worldName = plugin.getConfig().getString("duty.locker-room.world", player.getWorld().getName());
        double x = plugin.getConfig().getDouble("duty.locker-room.x", player.getLocation().getX());
        double y = plugin.getConfig().getDouble("duty.locker-room.y", player.getLocation().getY());
        double z = plugin.getConfig().getDouble("duty.locker-room.z", player.getLocation().getZ());
        float yaw = (float) plugin.getConfig().getDouble("duty.locker-room.yaw", player.getLocation().getYaw());
        float pitch = (float) plugin.getConfig().getDouble("duty.locker-room.pitch", player.getLocation().getPitch());

        // Create location object
        World world = plugin.getServer().getWorld(worldName);
        if (world == null) {
            // If world not found, use player's current world
            world = player.getWorld();
            plugin.getLogger().warning("Locker room world '" + worldName + "' not found, using player's current world");
        }

        Location lockerRoomLocation = new Location(world, x, y, z, yaw, pitch);

        // Teleport player
        player.teleport(lockerRoomLocation);

        // Play teleport sound if configured
        String soundName = plugin.getConfig().getString("duty.teleport-sound", "ENTITY_ENDERMAN_TELEPORT");
        try {
            Sound sound = Sound.valueOf(soundName);
            player.playSound(lockerRoomLocation, sound, 1.0f, 1.0f);
        } catch (IllegalArgumentException e) {
            // Invalid sound, just skip
        }

        // Send message
        player.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage(plugin.getConfig().getString("duty.messages.teleported-to-locker",
                        "<green>You have been teleported to the locker room!</green>"))));
    }

    /**
     * Create a basic inventory item
     */
    private ItemStack createItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore.length > 0) {
                meta.setLore(Arrays.asList(lore));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Clears the content area of the GUI
     */
    private void clearContentArea(Inventory gui) {
        // Clear slots 10-44, excluding borders
        for (int row = 1; row < 5; row++) {
            for (int col = 1; col < 8; col++) {
                gui.setItem(row * 9 + col, null);
            }
        }
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
     * Format time in seconds to a readable string
     */
    private String formatTime(int seconds) {
        if (seconds < 60) {
            return seconds + "s";
        }

        int minutes = seconds / 60;
        seconds = seconds % 60;

        if (minutes < 60) {
            return minutes + "m" + (seconds > 0 ? " " + seconds + "s" : "");
        }

        int hours = minutes / 60;
        minutes = minutes % 60;

        if (hours < 24) {
            return hours + "h" + (minutes > 0 ? " " + minutes + "m" : "");
        }

        int days = hours / 24;
        hours = hours % 24;

        return days + "d" + (hours > 0 ? " " + hours + "h" : "");
    }

    /**
     * Check if a player should see the GUI on join
     */
    public boolean shouldShowOnJoin(Player player) {
        // First check if feature is enabled (default to false)
        if (!plugin.getConfig().getBoolean("gui.show-on-join", false)) {
            return false;
        }

        // Check if player has permission
        return player.hasPermission("edencorrections.duty") || hasAnyRankPermission(player);
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
}