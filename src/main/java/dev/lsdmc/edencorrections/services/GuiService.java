package dev.lsdmc.edencorrections.services;

import dev.lsdmc.edencorrections.EdenCorrections;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;

public class GuiService {
    private final EdenCorrections plugin;

    public GuiService(EdenCorrections plugin) {
        this.plugin = plugin;
    }

    public void showMainMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, Component.text("Guard Menu"));
        
        // Duty Status Button
        ItemStack dutyButton = createGuiItem(Material.EMERALD, "Duty Status", 
            "Click to toggle your duty status");
        gui.setItem(11, dutyButton);

        // Rules Button
        ItemStack rulesButton = createGuiItem(Material.BOOK, "Guard Rules", 
            "View guard rules and guidelines");
        gui.setItem(13, rulesButton);

        // Admin Button (only for admins)
        if (player.hasPermission("edencorrections.guard.admin")) {
            ItemStack adminButton = createGuiItem(Material.COMMAND_BLOCK, "Admin Settings", 
                "Configure guard settings");
            gui.setItem(15, adminButton);
        }

        player.openInventory(gui);
    }

    public void showRulesMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, Component.text("Guard Rules"));
        
        ItemStack generalRules = createGuiItem(Material.PAPER, "General Rules", 
            "Basic guard guidelines and protocols");
        gui.setItem(11, generalRules);

        ItemStack dutyRules = createGuiItem(Material.CLOCK, "Duty Rules", 
            "Rules specific to guard duty");
        gui.setItem(13, dutyRules);

        ItemStack backButton = createGuiItem(Material.ARROW, "Back", 
            "Return to main menu");
        gui.setItem(22, backButton);

        player.openInventory(gui);
    }

    public void showAdminMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, Component.text("Guard Admin"));
        
        ItemStack loungeButton = createGuiItem(Material.BEACON, "Set Guard Lounge", 
            "Set the guard lounge region");
        gui.setItem(11, loungeButton);

        ItemStack backButton = createGuiItem(Material.ARROW, "Back", 
            "Return to main menu");
        gui.setItem(22, backButton);

        player.openInventory(gui);
    }

    private ItemStack createGuiItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.GOLD));
        meta.lore(Arrays.stream(lore)
            .map(line -> Component.text(line, NamedTextColor.GRAY))
            .toList());
        item.setItemMeta(meta);
        return item;
    }
} 