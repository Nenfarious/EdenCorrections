package dev.lsdmc.edencorrections.managers;

import dev.lsdmc.edencorrections.EdenCorrections;
import dev.lsdmc.edencorrections.managers.ContrabandManager.DrugEffect;
import dev.lsdmc.edencorrections.utils.MessageUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GUIManager {
    private final EdenCorrections plugin;

    public GUIManager(EdenCorrections plugin) {
        this.plugin = plugin;
    }

    public void showMainMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, Component.text("Eden Corrections", NamedTextColor.DARK_BLUE));

        // Duty Status
        ItemStack dutyItem = new ItemStack(Material.SHIELD);
        ItemMeta dutyMeta = dutyItem.getItemMeta();
        dutyMeta.displayName(Component.text("Duty Status", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        List<Component> dutyLore = new ArrayList<>();
        dutyLore.add(Component.text("Click to view your duty status", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        dutyLore.add(Component.text("and manage your duty time.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        dutyMeta.lore(dutyLore);
        dutyItem.setItemMeta(dutyMeta);
        gui.setItem(11, dutyItem);

        // Break Time
        ItemStack breakItem = new ItemStack(Material.CLOCK);
        ItemMeta breakMeta = breakItem.getItemMeta();
        breakMeta.displayName(Component.text("Break Time", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        List<Component> breakLore = new ArrayList<>();
        breakLore.add(Component.text("Click to view your remaining", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        breakLore.add(Component.text("break time.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        breakMeta.lore(breakLore);
        breakItem.setItemMeta(breakMeta);
        gui.setItem(13, breakItem);

        // Rules
        ItemStack rulesItem = new ItemStack(Material.BOOK);
        ItemMeta rulesMeta = rulesItem.getItemMeta();
        rulesMeta.displayName(Component.text("Rules", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        List<Component> rulesLore = new ArrayList<>();
        rulesLore.add(Component.text("Click to view guard rules", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        rulesLore.add(Component.text("and guidelines.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        rulesMeta.lore(rulesLore);
        rulesItem.setItemMeta(rulesMeta);
        gui.setItem(15, rulesItem);

        // Fill empty slots with glass panes
        fillEmptySlots(gui);

        player.openInventory(gui);
    }

    public void showDutyMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, Component.text("Duty Status", NamedTextColor.DARK_BLUE));

        // Current Status
        ItemStack statusItem = new ItemStack(Material.LIME_DYE);
        ItemMeta statusMeta = statusItem.getItemMeta();
        statusMeta.displayName(Component.text("Current Status: On Duty", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        List<Component> statusLore = new ArrayList<>();
        statusLore.add(Component.text("Click to toggle duty status", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        statusMeta.lore(statusLore);
        statusItem.setItemMeta(statusMeta);
        gui.setItem(13, statusItem);

        // Back Button
        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.displayName(Component.text("Back to Main Menu", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        backItem.setItemMeta(backMeta);
        gui.setItem(26, backItem);

        fillEmptySlots(gui);
        player.openInventory(gui);
    }

    public void showRulesMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54, Component.text("Guard Rules", NamedTextColor.DARK_BLUE));

        // General Rules
        ItemStack generalItem = new ItemStack(Material.BOOK);
        ItemMeta generalMeta = generalItem.getItemMeta();
        generalMeta.displayName(Component.text("General Rules", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        List<Component> generalLore = new ArrayList<>();
        generalLore.add(Component.text("• Always remain professional", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        generalLore.add(Component.text("• Follow proper procedures", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        generalLore.add(Component.text("• Report any issues to superiors", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        generalMeta.lore(generalLore);
        generalItem.setItemMeta(generalMeta);
        gui.setItem(10, generalItem);

        // Duty Rules
        ItemStack dutyItem = new ItemStack(Material.SHIELD);
        ItemMeta dutyMeta = dutyItem.getItemMeta();
        dutyMeta.displayName(Component.text("Duty Rules", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        List<Component> dutyLore = new ArrayList<>();
        dutyLore.add(Component.text("• Must be in guard lounge to toggle duty", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        dutyLore.add(Component.text("• Proper kit will be assigned", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        dutyLore.add(Component.text("• Follow break time guidelines", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        dutyMeta.lore(dutyLore);
        dutyItem.setItemMeta(dutyMeta);
        gui.setItem(12, dutyItem);

        // Back Button
        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.displayName(Component.text("Back to Main Menu", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        backItem.setItemMeta(backMeta);
        gui.setItem(49, backItem);

        fillEmptySlots(gui);
        player.openInventory(gui);
    }

    public void openMainMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, Component.text("Eden Corrections", NamedTextColor.DARK_BLUE));

        // Current Status
        ItemStack statusItem = new ItemStack(plugin.getGuardDutyManager().isOnDuty(player) ? Material.LIME_DYE : Material.RED_DYE);
        ItemMeta statusMeta = statusItem.getItemMeta();
        statusMeta.displayName(Component.text("Current Status: " + (plugin.getGuardDutyManager().isOnDuty(player) ? "On Duty" : "Off Duty"), 
            plugin.getGuardDutyManager().isOnDuty(player) ? NamedTextColor.GREEN : NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        List<Component> statusLore = new ArrayList<>();
        statusLore.add(Component.text("Click to toggle duty status", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        statusMeta.lore(statusLore);
        statusItem.setItemMeta(statusMeta);
        gui.setItem(11, statusItem);

        // Break Time
        ItemStack breakTimeItem = new ItemStack(Material.CLOCK);
        ItemMeta breakTimeMeta = breakTimeItem.getItemMeta();
        breakTimeMeta.displayName(Component.text("Break Time", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        List<Component> breakTimeLore = new ArrayList<>();
        breakTimeLore.add(Component.text("Click to view your break time", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        breakTimeMeta.lore(breakTimeLore);
        breakTimeItem.setItemMeta(breakTimeMeta);
        gui.setItem(13, breakTimeItem);

        // Rules
        ItemStack rulesItem = new ItemStack(Material.BOOK);
        ItemMeta rulesMeta = rulesItem.getItemMeta();
        rulesMeta.displayName(Component.text("Guard Rules", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        List<Component> rulesLore = new ArrayList<>();
        rulesLore.add(Component.text("Click to view guard rules", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        rulesMeta.lore(rulesLore);
        rulesItem.setItemMeta(rulesMeta);
        gui.setItem(15, rulesItem);

        player.openInventory(gui);
    }

    public void openBreakTimeMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, Component.text("Break Time", NamedTextColor.DARK_BLUE));

        int timeLeft = plugin.getGuardDutyManager().getBreakTime(player);
        int hours = timeLeft / 60;
        int minutes = timeLeft % 60;

        ItemStack timeItem = new ItemStack(Material.CLOCK);
        ItemMeta timeMeta = timeItem.getItemMeta();
        timeMeta.displayName(Component.text("Time Remaining", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        List<Component> timeLore = new ArrayList<>();
        timeLore.add(Component.text(String.format("%02d:%02d", hours, minutes), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        timeMeta.lore(timeLore);
        timeItem.setItemMeta(timeMeta);
        gui.setItem(13, timeItem);

        player.openInventory(gui);
    }

    public void openRulesMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54, Component.text("Guard Rules", NamedTextColor.DARK_BLUE));

        // Add rules items
        String[] rules = {
            "1. Always be respectful to prisoners",
            "2. No abuse of power",
            "3. Follow proper procedure for searches",
            "4. Report any suspicious activity",
            "5. Maintain order in the prison",
            "6. No favoritism towards prisoners",
            "7. Keep guard items secure",
            "8. Follow chain of command",
            "9. No sharing of guard items",
            "10. Stay in designated areas"
        };

        for (int i = 0; i < rules.length; i++) {
            ItemStack ruleItem = new ItemStack(Material.PAPER);
            ItemMeta ruleMeta = ruleItem.getItemMeta();
            ruleMeta.displayName(Component.text("Rule " + (i + 1), NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            List<Component> ruleLore = new ArrayList<>();
            ruleLore.add(Component.text(rules[i], NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            ruleMeta.lore(ruleLore);
            ruleItem.setItemMeta(ruleMeta);
            gui.setItem(i, ruleItem);
        }

        player.openInventory(gui);
    }

    public void openDrugEffectsGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, Component.text("Active Drug Effects", NamedTextColor.DARK_PURPLE));

        ContrabandManager.DrugEffect effect = plugin.getContrabandManager().getActiveDrugEffect(player);
        if (effect != null) {
            ItemStack effectItem = new ItemStack(Material.POTION);
            ItemMeta meta = effectItem.getItemMeta();
            meta.displayName(Component.text(effect.drug.name, NamedTextColor.LIGHT_PURPLE));
            
            List<Component> lore = new ArrayList<>();
            long timeLeft = (effect.drug.duration * 1000L) - (System.currentTimeMillis() - effect.startTime);
            int secondsLeft = (int) (timeLeft / 1000);
            lore.add(Component.text("Time Remaining: " + secondsLeft + " seconds", NamedTextColor.GRAY));
            lore.add(Component.text("Token Reward: " + effect.drug.tokenReward, NamedTextColor.GOLD));
            meta.lore(lore);
            
            effectItem.setItemMeta(meta);
            gui.setItem(13, effectItem);
        } else {
            ItemStack noEffectItem = new ItemStack(Material.BARRIER);
            ItemMeta meta = noEffectItem.getItemMeta();
            meta.displayName(Component.text("No Active Effects", NamedTextColor.RED));
            noEffectItem.setItemMeta(meta);
            gui.setItem(13, noEffectItem);
        }

        player.openInventory(gui);
    }

    public void showTimeGUI(Player player) {
        int timeLeft = plugin.getGuardDutyManager().getBreakTime(player);
        int hours = timeLeft / 60;
        int minutes = timeLeft % 60;
        String timeString = String.format("%d hours and %d minutes", hours, minutes);
        player.sendMessage(Component.text("You have " + timeString + " of break time remaining.", NamedTextColor.GREEN));
    }

    public void showDrugEffectsGUI(Player player) {
        if (!plugin.getContrabandManager().isUnderDrugEffect(player)) {
            player.sendMessage(Component.text("You are not under any drug effects.", NamedTextColor.RED));
            return;
        }

        DrugEffect effect = plugin.getContrabandManager().getActiveDrugEffect(player);
        if (effect != null) {
            long timeLeft = (effect.drug.duration * 1000L) - (System.currentTimeMillis() - effect.startTime);
            int minutesLeft = (int) (timeLeft / 60000);
            int secondsLeft = (int) ((timeLeft % 60000) / 1000);

            player.sendMessage(Component.text("Current drug effect: " + effect.drug.name, NamedTextColor.YELLOW));
            player.sendMessage(Component.text("Time remaining: " + minutesLeft + "m " + secondsLeft + "s", NamedTextColor.YELLOW));
        }
    }

    public void showAdminMenu(Player player) {
        if (!player.hasPermission("edencorrections.admin")) {
            player.sendMessage(Component.text("You don't have permission to access the admin menu.", NamedTextColor.RED));
            return;
        }

        Inventory gui = Bukkit.createInventory(null, 54, Component.text("EdenCorrections Admin", NamedTextColor.DARK_BLUE));

        // Guard Rank Management
        ItemStack rankItem = new ItemStack(Material.IRON_HELMET);
        ItemMeta rankMeta = rankItem.getItemMeta();
        rankMeta.displayName(Component.text("Guard Rank Management", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        List<Component> rankLore = new ArrayList<>();
        rankLore.add(Component.text("Manage guard ranks and their hierarchy", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        rankLore.add(Component.text("Set permissions and LuckPerms groups", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        rankMeta.lore(rankLore);
        rankItem.setItemMeta(rankMeta);
        gui.setItem(10, rankItem);

        // Player Management
        ItemStack playerItem = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta playerMeta = playerItem.getItemMeta();
        playerMeta.displayName(Component.text("Player Management", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        List<Component> playerLore = new ArrayList<>();
        playerLore.add(Component.text("Manage player ranks and permissions", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        playerLore.add(Component.text("View player statistics and progression", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        playerMeta.lore(playerLore);
        playerItem.setItemMeta(playerMeta);
        gui.setItem(11, playerItem);

        // Location Management
        ItemStack locationItem = new ItemStack(Material.COMPASS);
        ItemMeta locationMeta = locationItem.getItemMeta();
        locationMeta.displayName(Component.text("Location Management", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        List<Component> locationLore = new ArrayList<>();
        locationLore.add(Component.text("Set and manage important locations", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        locationLore.add(Component.text("Configure duty areas and spawn points", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        locationMeta.lore(locationLore);
        locationItem.setItemMeta(locationMeta);
        gui.setItem(12, locationItem);

        // Item Management
        ItemStack itemItem = new ItemStack(Material.CHEST);
        ItemMeta itemMeta = itemItem.getItemMeta();
        itemMeta.displayName(Component.text("Item Management", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        List<Component> itemLore = new ArrayList<>();
        itemLore.add(Component.text("Manage guard items and equipment", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        itemLore.add(Component.text("Configure contraband and drug items", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        itemMeta.lore(itemLore);
        itemItem.setItemMeta(itemMeta);
        gui.setItem(13, itemItem);

        // NPC Management
        ItemStack npcItem = new ItemStack(Material.VILLAGER_SPAWN_EGG);
        ItemMeta npcMeta = npcItem.getItemMeta();
        npcMeta.displayName(Component.text("NPC Management", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        List<Component> npcLore = new ArrayList<>();
        npcLore.add(Component.text("Create and manage duty NPCs", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        npcLore.add(Component.text("Configure NPC locations and types", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        npcMeta.lore(npcLore);
        npcItem.setItemMeta(npcMeta);
        gui.setItem(14, npcItem);

        // Integration Status
        ItemStack integrationItem = new ItemStack(Material.COMPARATOR);
        ItemMeta integrationMeta = integrationItem.getItemMeta();
        integrationMeta.displayName(Component.text("Integration Status", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        List<Component> integrationLore = new ArrayList<>();
        integrationLore.add(Component.text("View plugin integration status", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        integrationLore.add(Component.text("Check LuckPerms and Citizens status", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        integrationMeta.lore(integrationLore);
        integrationItem.setItemMeta(integrationMeta);
        gui.setItem(15, integrationItem);

        // Configuration
        ItemStack configItem = new ItemStack(Material.BOOK);
        ItemMeta configMeta = configItem.getItemMeta();
        configMeta.displayName(Component.text("Configuration", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        List<Component> configLore = new ArrayList<>();
        configLore.add(Component.text("Edit plugin configuration", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        configLore.add(Component.text("Reload plugin settings", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        configMeta.lore(configLore);
        configItem.setItemMeta(configMeta);
        gui.setItem(16, configItem);

        // Statistics
        ItemStack statsItem = new ItemStack(Material.BARRIER);
        ItemMeta statsMeta = statsItem.getItemMeta();
        statsMeta.displayName(Component.text("Statistics", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        List<Component> statsLore = new ArrayList<>();
        statsLore.add(Component.text("View plugin statistics", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        statsLore.add(Component.text("Monitor guard activity and progression", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        statsMeta.lore(statsLore);
        statsItem.setItemMeta(statsMeta);
        gui.setItem(20, statsItem);

        // Emergency Controls
        ItemStack emergencyItem = new ItemStack(Material.BARRIER);
        ItemMeta emergencyMeta = emergencyItem.getItemMeta();
        emergencyMeta.displayName(Component.text("Emergency Controls", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        List<Component> emergencyLore = new ArrayList<>();
        emergencyLore.add(Component.text("Emergency shutdown procedures", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        emergencyLore.add(Component.text("Force all guards off duty", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        emergencyMeta.lore(emergencyLore);
        emergencyItem.setItemMeta(emergencyMeta);
        gui.setItem(21, emergencyItem);

        // Fill empty slots
        fillEmptySlots(gui);

        player.openInventory(gui);
    }

    @EventHandler
    public void onAdminMenuClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (!event.getView().title().equals(Component.text("EdenCorrections Admin", NamedTextColor.DARK_BLUE))) return;
        
        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();
        
        if (clickedItem == null || clickedItem.getType().isAir()) return;

        switch (event.getSlot()) {
            case 10 -> showGuardRankMenu(player);
            case 11 -> showPlayerManagementMenu(player);
            case 12 -> showLocationManagementMenu(player);
            case 13 -> showItemManagementMenu(player);
            case 14 -> showNPCManagementMenu(player);
            case 15 -> showIntegrationStatusMenu(player);
            case 16 -> showConfigMenu(player);
            case 20 -> showStatisticsMenu(player);
            case 21 -> showEmergencyMenu(player);
        }
    }

    public void showNPCManagementMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, Component.text("NPC Management", NamedTextColor.DARK_BLUE));

        // Create NPC
        ItemStack createItem = new ItemStack(Material.VILLAGER_SPAWN_EGG);
        ItemMeta createMeta = createItem.getItemMeta();
        createMeta.displayName(Component.text("Create NPC", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        List<Component> createLore = new ArrayList<>();
        createLore.add(Component.text("Create a new duty NPC", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        createLore.add(Component.text("Set location and type", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        createMeta.lore(createLore);
        createItem.setItemMeta(createMeta);
        gui.setItem(11, createItem);

        // List NPCs
        ItemStack listItem = new ItemStack(Material.PAPER);
        ItemMeta listMeta = listItem.getItemMeta();
        listMeta.displayName(Component.text("List NPCs", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        List<Component> listLore = new ArrayList<>();
        listLore.add(Component.text("View all duty NPCs", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        listLore.add(Component.text("Check locations and types", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        listMeta.lore(listLore);
        listItem.setItemMeta(listMeta);
        gui.setItem(13, listItem);

        // Remove NPC
        ItemStack removeItem = new ItemStack(Material.BARRIER);
        ItemMeta removeMeta = removeItem.getItemMeta();
        removeMeta.displayName(Component.text("Remove NPC", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        List<Component> removeLore = new ArrayList<>();
        removeLore.add(Component.text("Remove a duty NPC", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        removeLore.add(Component.text("Clear data and entity", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        removeMeta.lore(removeLore);
        removeItem.setItemMeta(removeMeta);
        gui.setItem(15, removeItem);

        // Back Button
        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.displayName(Component.text("Back to Admin Menu", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        backItem.setItemMeta(backMeta);
        gui.setItem(22, backItem);

        fillEmptySlots(gui);
        player.openInventory(gui);
    }

    public void showStatisticsMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, Component.text("Statistics", NamedTextColor.DARK_BLUE));

        // Guard Statistics
        ItemStack guardItem = new ItemStack(Material.IRON_HELMET);
        ItemMeta guardMeta = guardItem.getItemMeta();
        guardMeta.displayName(Component.text("Guard Statistics", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        List<Component> guardLore = new ArrayList<>();
        guardLore.add(Component.text("View guard activity stats", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        guardLore.add(Component.text("Duty time and progression", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        guardMeta.lore(guardLore);
        guardItem.setItemMeta(guardMeta);
        gui.setItem(11, guardItem);

        // System Statistics
        ItemStack systemItem = new ItemStack(Material.COMPARATOR);
        ItemMeta systemMeta = systemItem.getItemMeta();
        systemMeta.displayName(Component.text("System Statistics", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        List<Component> systemLore = new ArrayList<>();
        systemLore.add(Component.text("View system performance", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        systemLore.add(Component.text("Integration status and metrics", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        systemMeta.lore(systemLore);
        systemItem.setItemMeta(systemMeta);
        gui.setItem(13, systemItem);

        // Back Button
        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.displayName(Component.text("Back to Admin Menu", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        backItem.setItemMeta(backMeta);
        gui.setItem(22, backItem);

        fillEmptySlots(gui);
        player.openInventory(gui);
    }

    public void showEmergencyMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, Component.text("Emergency Controls", NamedTextColor.DARK_BLUE));

        // Force Off Duty
        ItemStack offDutyItem = new ItemStack(Material.BARRIER);
        ItemMeta offDutyMeta = offDutyItem.getItemMeta();
        offDutyMeta.displayName(Component.text("Force All Guards Off Duty", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        List<Component> offDutyLore = new ArrayList<>();
        offDutyLore.add(Component.text("Force all guards off duty", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        offDutyLore.add(Component.text("Emergency situation only", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        offDutyMeta.lore(offDutyLore);
        offDutyItem.setItemMeta(offDutyMeta);
        gui.setItem(11, offDutyItem);

        // Emergency Shutdown
        ItemStack shutdownItem = new ItemStack(Material.BARRIER);
        ItemMeta shutdownMeta = shutdownItem.getItemMeta();
        shutdownMeta.displayName(Component.text("Emergency Shutdown", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        List<Component> shutdownLore = new ArrayList<>();
        shutdownLore.add(Component.text("Shutdown plugin safely", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        shutdownLore.add(Component.text("Save all data and disable", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        shutdownMeta.lore(shutdownLore);
        shutdownItem.setItemMeta(shutdownMeta);
        gui.setItem(13, shutdownItem);

        // Back Button
        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.displayName(Component.text("Back to Admin Menu", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        backItem.setItemMeta(backMeta);
        gui.setItem(22, backItem);

        fillEmptySlots(gui);
        player.openInventory(gui);
    }

    private void handleNPCManagementClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();
        
        if (clickedItem == null || clickedItem.getType().isAir()) return;

        switch (event.getSlot()) {
            case 11 -> {
                player.closeInventory();
                player.performCommand("eco npc create");
            }
            case 13 -> {
                player.closeInventory();
                player.performCommand("eco npc list");
            }
            case 15 -> {
                player.closeInventory();
                player.performCommand("eco npc remove");
            }
            case 22 -> showAdminMenu(player);
        }
    }

    private void handleStatisticsClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();
        
        if (clickedItem == null || clickedItem.getType().isAir()) return;

        switch (event.getSlot()) {
            case 11 -> {
                player.closeInventory();
                player.performCommand("eco stats guards");
            }
            case 13 -> {
                player.closeInventory();
                player.performCommand("eco stats system");
            }
            case 22 -> showAdminMenu(player);
        }
    }

    private void handleEmergencyClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();
        
        if (clickedItem == null || clickedItem.getType().isAir()) return;

        switch (event.getSlot()) {
            case 11 -> {
                player.closeInventory();
                player.performCommand("eco emergency offduty");
            }
            case 13 -> {
                player.closeInventory();
                player.performCommand("eco emergency shutdown");
            }
            case 22 -> showAdminMenu(player);
        }
    }

    public void showPlayerManagementMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54, Component.text("Player Management", NamedTextColor.DARK_BLUE));

        int slot = 0;
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            ItemStack playerHead = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) playerHead.getItemMeta();
            meta.setOwningPlayer(onlinePlayer);
            meta.displayName(Component.text(onlinePlayer.getName(), NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
            
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Click to manage this player", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            if (plugin.getGuardDutyManager().isOnDuty(onlinePlayer)) {
                lore.add(Component.text("On Duty: Yes", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
            } else {
                lore.add(Component.text("On Duty: No", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
            playerHead.setItemMeta(meta);
            
            gui.setItem(slot++, playerHead);
        }

        fillEmptySlots(gui);
        player.openInventory(gui);
    }

    public void showLocationManagementMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, Component.text("Location Management", NamedTextColor.DARK_BLUE));

        // Set Guard Lounge
        ItemStack loungeItem = new ItemStack(Material.BEACON);
        ItemMeta loungeMeta = loungeItem.getItemMeta();
        loungeMeta.displayName(Component.text("Set Guard Lounge", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        List<Component> loungeLore = new ArrayList<>();
        loungeLore.add(Component.text("Click to set guard lounge location", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        loungeMeta.lore(loungeLore);
        loungeItem.setItemMeta(loungeMeta);
        gui.setItem(11, loungeItem);

        // View Current Locations
        ItemStack viewItem = new ItemStack(Material.MAP);
        ItemMeta viewMeta = viewItem.getItemMeta();
        viewMeta.displayName(Component.text("View Locations", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        List<Component> viewLore = new ArrayList<>();
        viewLore.add(Component.text("Click to view current locations", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        viewMeta.lore(viewLore);
        viewItem.setItemMeta(viewMeta);
        gui.setItem(15, viewItem);

        fillEmptySlots(gui);
        player.openInventory(gui);
    }

    public void showIntegrationStatusMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, Component.text("Integration Status", NamedTextColor.DARK_RED));

        // ExecutableItems Status
        ItemStack eiItem = new ItemStack(Material.COMMAND_BLOCK);
        ItemMeta eiMeta = eiItem.getItemMeta();
        eiMeta.displayName(Component.text("ExecutableItems", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        List<Component> eiLore = new ArrayList<>();
        eiLore.add(Component.text("Status: " + (plugin.hasExecutableItems() ? "Enabled" : "Disabled"), 
            plugin.hasExecutableItems() ? NamedTextColor.GREEN : NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        eiMeta.lore(eiLore);
        eiItem.setItemMeta(eiMeta);
        gui.setItem(11, eiItem);

        // WorldGuard Status
        ItemStack wgItem = new ItemStack(Material.SHIELD);
        ItemMeta wgMeta = wgItem.getItemMeta();
        wgMeta.displayName(Component.text("WorldGuard", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        List<Component> wgLore = new ArrayList<>();
        boolean wgEnabled = plugin.getServer().getPluginManager().getPlugin("WorldGuard") != null;
        wgLore.add(Component.text("Status: " + (wgEnabled ? "Enabled" : "Disabled"), 
            wgEnabled ? NamedTextColor.GREEN : NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        wgMeta.lore(wgLore);
        wgItem.setItemMeta(wgMeta);
        gui.setItem(15, wgItem);

        fillEmptySlots(gui);
        player.openInventory(gui);
    }

    public void showGuardRankMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54, Component.text("Guard Rank Management", NamedTextColor.DARK_BLUE));

        // Create Rank
        ItemStack createItem = new ItemStack(Material.EMERALD);
        ItemMeta createMeta = createItem.getItemMeta();
        createMeta.displayName(Component.text("Create Rank", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        List<Component> createLore = new ArrayList<>();
        createLore.add(Component.text("Create a new guard rank", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        createLore.add(Component.text("Set permissions and hierarchy", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        createMeta.lore(createLore);
        createItem.setItemMeta(createMeta);
        gui.setItem(10, createItem);

        // List Ranks
        ItemStack listItem = new ItemStack(Material.PAPER);
        ItemMeta listMeta = listItem.getItemMeta();
        listMeta.displayName(Component.text("List Ranks", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        List<Component> listLore = new ArrayList<>();
        listLore.add(Component.text("View all guard ranks", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        listLore.add(Component.text("See hierarchy and permissions", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        listMeta.lore(listLore);
        listItem.setItemMeta(listMeta);
        gui.setItem(11, listItem);

        // Set Rank
        ItemStack setItem = new ItemStack(Material.IRON_HELMET);
        ItemMeta setMeta = setItem.getItemMeta();
        setMeta.displayName(Component.text("Set Rank", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        List<Component> setLore = new ArrayList<>();
        setLore.add(Component.text("Set a player's guard rank", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        setLore.add(Component.text("Assign permissions and groups", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        setMeta.lore(setLore);
        setItem.setItemMeta(setMeta);
        gui.setItem(12, setItem);

        // Remove Rank
        ItemStack removeItem = new ItemStack(Material.BARRIER);
        ItemMeta removeMeta = removeItem.getItemMeta();
        removeMeta.displayName(Component.text("Remove Rank", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        List<Component> removeLore = new ArrayList<>();
        removeLore.add(Component.text("Remove a guard rank", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        removeLore.add(Component.text("Clear permissions and mappings", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        removeMeta.lore(removeLore);
        removeItem.setItemMeta(removeMeta);
        gui.setItem(13, removeItem);

        // Move Rank
        ItemStack moveItem = new ItemStack(Material.ARROW);
        ItemMeta moveMeta = moveItem.getItemMeta();
        moveMeta.displayName(Component.text("Move Rank", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        List<Component> moveLore = new ArrayList<>();
        moveLore.add(Component.text("Change rank position in hierarchy", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        moveLore.add(Component.text("Adjust permissions and authority", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        moveMeta.lore(moveLore);
        moveItem.setItemMeta(moveMeta);
        gui.setItem(14, moveItem);

        // Back Button
        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.displayName(Component.text("Back to Admin Menu", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        backItem.setItemMeta(backMeta);
        gui.setItem(49, backItem);

        fillEmptySlots(gui);
        player.openInventory(gui);
    }

    public void showTokenManagementMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54, Component.text("Token Management", NamedTextColor.DARK_RED));

        // Online Players
        int slot = 0;
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            ItemStack playerHead = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) playerHead.getItemMeta();
            meta.setOwningPlayer(onlinePlayer);
            meta.displayName(Component.text(onlinePlayer.getName(), NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
            
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Current Balance: " + plugin.getGuardDutyManager().getTokens(onlinePlayer), NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Click to manage tokens", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            playerHead.setItemMeta(meta);
            
            gui.setItem(slot++, playerHead);
        }

        fillEmptySlots(gui);
        player.openInventory(gui);
    }

    public void showItemManagementMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, Component.text("Item Management", NamedTextColor.DARK_BLUE));

        // Guard Items
        ItemStack guardItem = new ItemStack(Material.SHIELD);
        ItemMeta guardMeta = guardItem.getItemMeta();
        guardMeta.displayName(Component.text("Guard Items", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        List<Component> guardLore = new ArrayList<>();
        guardLore.add(Component.text("Manage guard equipment", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        guardLore.add(Component.text("Configure duty items and kits", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        guardMeta.lore(guardLore);
        guardItem.setItemMeta(guardMeta);
        gui.setItem(10, guardItem);

        // Contraband Items
        ItemStack contrabandItem = new ItemStack(Material.CHEST);
        ItemMeta contrabandMeta = contrabandItem.getItemMeta();
        contrabandMeta.displayName(Component.text("Contraband Items", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        List<Component> contrabandLore = new ArrayList<>();
        contrabandLore.add(Component.text("Manage contraband items", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        contrabandLore.add(Component.text("Configure searchable items", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        contrabandMeta.lore(contrabandLore);
        contrabandItem.setItemMeta(contrabandMeta);
        gui.setItem(11, contrabandItem);

        // Drug Items
        ItemStack drugItem = new ItemStack(Material.POTION);
        ItemMeta drugMeta = drugItem.getItemMeta();
        drugMeta.displayName(Component.text("Drug Items", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        List<Component> drugLore = new ArrayList<>();
        drugLore.add(Component.text("Manage drug items", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        drugLore.add(Component.text("Configure effects and rewards", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        drugMeta.lore(drugLore);
        drugItem.setItemMeta(drugMeta);
        gui.setItem(12, drugItem);

        // Back button
        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.displayName(Component.text("Back to Admin Menu", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        backItem.setItemMeta(backMeta);
        gui.setItem(22, backItem);

        fillEmptySlots(gui);
        player.openInventory(gui);
    }

    public void showContrabandItemsMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54, Component.text("Contraband Items", NamedTextColor.RED));

        int slot = 0;
        // Get contraband items from ExecutableItems integration
        if (plugin.hasExecutableItems()) {
            Set<String> contrabandIds = plugin.getExternalPluginIntegration().getContrabandItemIds();
            for (String contrabandId : contrabandIds) {
                ItemStack displayItem = new ItemStack(Material.CHEST);
                ItemMeta meta = displayItem.getItemMeta();
                meta.displayName(Component.text(contrabandId, NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
                
                List<Component> lore = new ArrayList<>();
                lore.add(Component.text("ID: " + contrabandId, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("Use metal detector to scan for items", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
                meta.lore(lore);
                displayItem.setItemMeta(meta);
                
                gui.setItem(slot++, displayItem);
            }
        }

        // Back Button
        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.displayName(Component.text("Back", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        backItem.setItemMeta(backMeta);
        gui.setItem(53, backItem);

        fillEmptySlots(gui);
        player.openInventory(gui);
    }

    public void showDrugItemsMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54, Component.text("Drug Items", NamedTextColor.DARK_PURPLE));

        int slot = 0;
        // Get drug items from ExecutableItems integration
        if (plugin.hasExecutableItems()) {
            Set<String> drugIds = plugin.getExternalPluginIntegration().getDrugItemIds();
            for (String drugId : drugIds) {
                ItemStack displayItem = new ItemStack(Material.POTION);
                ItemMeta meta = displayItem.getItemMeta();
                meta.displayName(Component.text(drugId, NamedTextColor.DARK_PURPLE).decoration(TextDecoration.ITALIC, false));
                
                List<Component> lore = new ArrayList<>();
                lore.add(Component.text("ID: " + drugId, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("Use sobriety test to detect effects", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
                meta.lore(lore);
                displayItem.setItemMeta(meta);
                
                gui.setItem(slot++, displayItem);
            }
        }

        // Back Button
        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.displayName(Component.text("Back", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        backItem.setItemMeta(backMeta);
        gui.setItem(53, backItem);

        fillEmptySlots(gui);
        player.openInventory(gui);
    }

    public void showGuardItemsMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54, Component.text("Guard Items", NamedTextColor.BLUE));

        // Handcuffs
        ItemStack handcuffsItem = new ItemStack(Material.IRON_INGOT);
        ItemMeta handcuffsMeta = handcuffsItem.getItemMeta();
        handcuffsMeta.displayName(Component.text("Handcuffs", NamedTextColor.BLUE).decoration(TextDecoration.ITALIC, false));
        
        List<Component> handcuffsLore = new ArrayList<>();
        handcuffsLore.add(Component.text("Used to restrain players", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        handcuffsLore.add(Component.text("Right-click to cuff", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        handcuffsMeta.lore(handcuffsLore);
        handcuffsItem.setItemMeta(handcuffsMeta);
        gui.setItem(10, handcuffsItem);

        // Drug Sniffer
        ItemStack snifferItem = new ItemStack(Material.SPYGLASS);
        ItemMeta snifferMeta = snifferItem.getItemMeta();
        snifferMeta.displayName(Component.text("Drug Sniffer", NamedTextColor.BLUE).decoration(TextDecoration.ITALIC, false));
        
        List<Component> snifferLore = new ArrayList<>();
        snifferLore.add(Component.text("Detects drugs on players", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        snifferLore.add(Component.text("Right-click to scan", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        snifferMeta.lore(snifferLore);
        snifferItem.setItemMeta(snifferMeta);
        gui.setItem(11, snifferItem);

        // Metal Detector
        ItemStack detectorItem = new ItemStack(Material.COMPASS);
        ItemMeta detectorMeta = detectorItem.getItemMeta();
        detectorMeta.displayName(Component.text("Metal Detector", NamedTextColor.BLUE).decoration(TextDecoration.ITALIC, false));
        
        List<Component> detectorLore = new ArrayList<>();
        detectorLore.add(Component.text("Detects contraband on players", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        detectorLore.add(Component.text("Right-click to scan", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        detectorMeta.lore(detectorLore);
        detectorItem.setItemMeta(detectorMeta);
        gui.setItem(12, detectorItem);

        // Guard Spyglass
        ItemStack spyglassItem = new ItemStack(Material.SPYGLASS);
        ItemMeta spyglassMeta = spyglassItem.getItemMeta();
        spyglassMeta.displayName(Component.text("Guard Spyglass", NamedTextColor.BLUE).decoration(TextDecoration.ITALIC, false));
        
        List<Component> spyglassLore = new ArrayList<>();
        spyglassLore.add(Component.text("Enhanced vision for guards", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        spyglassLore.add(Component.text("Right-click to use", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        spyglassMeta.lore(spyglassLore);
        spyglassItem.setItemMeta(spyglassMeta);
        gui.setItem(13, spyglassItem);

        // Sobriety Test
        ItemStack sobrietyItem = new ItemStack(Material.PAPER);
        ItemMeta sobrietyMeta = sobrietyItem.getItemMeta();
        sobrietyMeta.displayName(Component.text("Sobriety Test", NamedTextColor.BLUE).decoration(TextDecoration.ITALIC, false));
        
        List<Component> sobrietyLore = new ArrayList<>();
        sobrietyLore.add(Component.text("Test players for drug effects", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        sobrietyLore.add(Component.text("Right-click to test", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        sobrietyMeta.lore(sobrietyLore);
        sobrietyItem.setItemMeta(sobrietyMeta);
        gui.setItem(14, sobrietyItem);

        // Prison Remote
        ItemStack remoteItem = new ItemStack(Material.COMPARATOR);
        ItemMeta remoteMeta = remoteItem.getItemMeta();
        remoteMeta.displayName(Component.text("Prison Remote", NamedTextColor.BLUE).decoration(TextDecoration.ITALIC, false));
        
        List<Component> remoteLore = new ArrayList<>();
        remoteLore.add(Component.text("Control prison doors and cells", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        remoteLore.add(Component.text("Right-click to use", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        remoteMeta.lore(remoteLore);
        remoteItem.setItemMeta(remoteMeta);
        gui.setItem(15, remoteItem);

        // Guard Baton
        ItemStack batonItem = new ItemStack(Material.STICK);
        ItemMeta batonMeta = batonItem.getItemMeta();
        batonMeta.displayName(Component.text("Guard Baton", NamedTextColor.BLUE).decoration(TextDecoration.ITALIC, false));
        
        List<Component> batonLore = new ArrayList<>();
        batonLore.add(Component.text("Non-lethal weapon for guards", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        batonLore.add(Component.text("Right-click to use", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        batonMeta.lore(batonLore);
        batonItem.setItemMeta(batonMeta);
        gui.setItem(16, batonItem);

        // Smoke Bomb
        ItemStack smokeItem = new ItemStack(Material.ENDER_PEARL);
        ItemMeta smokeMeta = smokeItem.getItemMeta();
        smokeMeta.displayName(Component.text("Smoke Bomb", NamedTextColor.BLUE).decoration(TextDecoration.ITALIC, false));
        
        List<Component> smokeLore = new ArrayList<>();
        smokeLore.add(Component.text("Creates a smoke screen", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        smokeLore.add(Component.text("Right-click to throw", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        smokeMeta.lore(smokeLore);
        smokeItem.setItemMeta(smokeMeta);
        gui.setItem(20, smokeItem);

        // Guard Taser
        ItemStack taserItem = new ItemStack(Material.BLAZE_ROD);
        ItemMeta taserMeta = taserItem.getItemMeta();
        taserMeta.displayName(Component.text("Guard Taser", NamedTextColor.BLUE).decoration(TextDecoration.ITALIC, false));
        
        List<Component> taserLore = new ArrayList<>();
        taserLore.add(Component.text("Non-lethal ranged weapon", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        taserLore.add(Component.text("Right-click to use", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        taserMeta.lore(taserLore);
        taserItem.setItemMeta(taserMeta);
        gui.setItem(21, taserItem);

        // Back Button
        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.displayName(Component.text("Back", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        backItem.setItemMeta(backMeta);
        gui.setItem(53, backItem);

        fillEmptySlots(gui);
        player.openInventory(gui);
    }

    public void showGuardMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, Component.text("Guard Menu", NamedTextColor.DARK_RED));
        GuardDutyManager dutyManager = plugin.getGuardDutyManager();

        // Duty Status
        ItemStack dutyItem = new ItemStack(dutyManager.isOnDuty(player) ? Material.GREEN_WOOL : Material.RED_WOOL);
        ItemMeta dutyMeta = dutyItem.getItemMeta();
        dutyMeta.displayName(Component.text("Duty Status: " + (dutyManager.isOnDuty(player) ? "On Duty" : "Off Duty"), 
            dutyManager.isOnDuty(player) ? NamedTextColor.GREEN : NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        
        List<Component> dutyLore = new ArrayList<>();
        if (dutyManager.isOnDuty(player)) {
            dutyLore.add(Component.text("Click to go off duty", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            dutyLore.add(Component.text("Time on duty: " + formatTime(dutyManager.getDutyTime(player)), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        } else {
            dutyLore.add(Component.text("Click to go on duty", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        }
        dutyMeta.lore(dutyLore);
        dutyItem.setItemMeta(dutyMeta);
        gui.setItem(11, dutyItem);

        // Break Status
        ItemStack breakItem = new ItemStack(dutyManager.isOnBreak(player) ? Material.CLOCK : Material.BARRIER);
        ItemMeta breakMeta = breakItem.getItemMeta();
        breakMeta.displayName(Component.text("Break Status: " + (dutyManager.isOnBreak(player) ? "On Break" : "No Break"), 
            dutyManager.isOnBreak(player) ? NamedTextColor.GREEN : NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        
        List<Component> breakLore = new ArrayList<>();
        if (dutyManager.isOnBreak(player)) {
            breakLore.add(Component.text("Click to end break", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            breakLore.add(Component.text("Time remaining: " + dutyManager.getBreakTimeDisplay(player), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        } else {
            int breakTime = dutyManager.getBreakTime(player);
            if (breakTime > 0) {
                breakLore.add(Component.text("Click to start break", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
                breakLore.add(Component.text("Available break time: " + dutyManager.getBreakTimeDisplay(player), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            } else {
                breakLore.add(Component.text("No break time available", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
            }
        }
        breakMeta.lore(breakLore);
        breakItem.setItemMeta(breakMeta);
        gui.setItem(13, breakItem);

        // Token Status
        ItemStack tokenItem = new ItemStack(Material.EMERALD);
        ItemMeta tokenMeta = tokenItem.getItemMeta();
        tokenMeta.displayName(Component.text("Tokens: " + dutyManager.getTokens(player), NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        
        List<Component> tokenLore = new ArrayList<>();
        tokenLore.add(Component.text("Click to convert break time to tokens", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        tokenLore.add(Component.text("Rate: 10 tokens per minute", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        tokenMeta.lore(tokenLore);
        tokenItem.setItemMeta(tokenMeta);
        gui.setItem(15, tokenItem);

        // Fill empty slots
        fillEmptySlots(gui);
        player.openInventory(gui);
    }

    public void showConfigMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, Component.text("Configuration", NamedTextColor.DARK_BLUE));

        // Reload Config
        ItemStack reloadItem = new ItemStack(Material.BARRIER);
        ItemMeta reloadMeta = reloadItem.getItemMeta();
        reloadMeta.displayName(Component.text("Reload Configuration", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        List<Component> reloadLore = new ArrayList<>();
        reloadLore.add(Component.text("Reload plugin configuration", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        reloadLore.add(Component.text("Apply new settings", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        reloadMeta.lore(reloadLore);
        reloadItem.setItemMeta(reloadMeta);
        gui.setItem(13, reloadItem);

        // Back Button
        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.displayName(Component.text("Back to Admin Menu", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        backItem.setItemMeta(backMeta);
        gui.setItem(22, backItem);

        fillEmptySlots(gui);
        player.openInventory(gui);
    }

    private String formatTime(long seconds) {
        return MessageUtils.formatTime((int) seconds);
    }

    private void fillEmptySlots(Inventory gui) {
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        meta.displayName(Component.empty());
        filler.setItemMeta(meta);
        
        for (int i = 0; i < gui.getSize(); i++) {
            if (gui.getItem(i) == null) {
                gui.setItem(i, filler);
            }
        }
    }

    @EventHandler
    public void onSubMenuClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        String title = event.getView().title().toString();
        
        if (title.contains("Guard Rank Management")) {
            event.setCancelled(true);
            handleGuardRankClick(event);
        } else if (title.contains("Player Management")) {
            event.setCancelled(true);
            handlePlayerManagementClick(event);
        } else if (title.contains("Location Management")) {
            event.setCancelled(true);
            handleLocationManagementClick(event);
        } else if (title.contains("Item Management")) {
            event.setCancelled(true);
            handleItemManagementClick(event);
        } else if (title.contains("NPC Management")) {
            event.setCancelled(true);
            handleNPCManagementClick(event);
        } else if (title.contains("Statistics")) {
            event.setCancelled(true);
            handleStatisticsClick(event);
        } else if (title.contains("Emergency Controls")) {
            event.setCancelled(true);
            handleEmergencyClick(event);
        } else if (title.contains("Configuration")) {
            event.setCancelled(true);
            handleConfigClick(event);
        }
    }

    private void handleGuardRankClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();
        
        if (clickedItem == null || clickedItem.getType().isAir()) return;

        switch (event.getSlot()) {
            case 10 -> {
                player.closeInventory();
                player.performCommand("eco rank create");
            }
            case 11 -> {
                player.closeInventory();
                player.performCommand("eco rank list");
            }
            case 12 -> {
                player.closeInventory();
                player.performCommand("eco rank set");
            }
            case 13 -> {
                player.closeInventory();
                player.performCommand("eco rank remove");
            }
            case 14 -> {
                player.closeInventory();
                player.performCommand("eco rank move");
            }
            case 49 -> showAdminMenu(player);
        }
    }

    private void handlePlayerManagementClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();
        
        if (clickedItem == null || clickedItem.getType().isAir()) return;

        if (event.getSlot() == 49) {
            showAdminMenu(player);
            return;
        }

        if (clickedItem.getType() == Material.PLAYER_HEAD) {
            SkullMeta meta = (SkullMeta) clickedItem.getItemMeta();
            if (meta != null && meta.getOwningPlayer() != null) {
                String targetName = meta.getOwningPlayer().getName();
                player.closeInventory();
                player.performCommand("eco player " + targetName);
            }
        }
    }

    private void handleLocationManagementClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();
        
        if (clickedItem == null || clickedItem.getType().isAir()) return;

        switch (event.getSlot()) {
            case 11 -> {
                player.closeInventory();
                player.performCommand("eco location set guard_lounge");
            }
            case 13 -> {
                player.closeInventory();
                player.performCommand("eco location list");
            }
            case 15 -> {
                player.closeInventory();
                player.performCommand("eco location remove");
            }
            case 22 -> showAdminMenu(player);
        }
    }

    private void handleItemManagementClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();
        
        if (clickedItem == null || clickedItem.getType().isAir()) return;

        switch (event.getSlot()) {
            case 10 -> {
                player.closeInventory();
                player.performCommand("eco items guard");
            }
            case 11 -> {
                player.closeInventory();
                player.performCommand("eco items contraband");
            }
            case 12 -> {
                player.closeInventory();
                player.performCommand("eco items drug");
            }
            case 22 -> showAdminMenu(player);
        }
    }

    private void handleConfigClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();
        
        if (clickedItem == null || clickedItem.getType().isAir()) return;

        switch (event.getSlot()) {
            case 13 -> {
                player.closeInventory();
                player.performCommand("eco reload");
            }
            case 22 -> showAdminMenu(player);
        }
    }
} 