package dev.lsdmc.edencorrections.listeners;

import dev.lsdmc.edencorrections.EdenCorrections;
import dev.lsdmc.edencorrections.gui.GuiHolder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class GuiListener implements Listener {
    private final EdenCorrections plugin;

    public GuiListener(EdenCorrections plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Inventory clickedInventory = event.getClickedInventory();
        if (clickedInventory == null) {
            return;
        }

        // Check if the inventory has our custom holder
        InventoryHolder holder = clickedInventory.getHolder();
        if (holder instanceof GuiHolder guiHolder) {
            // Cancel the event to prevent item movement
            event.setCancelled(true);

            // Handle based on the GUI type
            if (guiHolder.getGuiType() == GuiHolder.GuiType.DUTY_SELECTION) {
                plugin.getGuiManager().handleDutySelectionGuiClick(player, event.getSlot());
            } else if (guiHolder.getGuiType() == GuiHolder.GuiType.ENHANCED_MAIN) {
                plugin.getGuiManager().handleEnhancedGuiClick(player, event.getSlot(), clickedInventory);
            } else if (guiHolder.getGuiType() == GuiHolder.GuiType.SHOP_VIEW) {
                // New handler for shop GUI
                plugin.getGuiManager().handleShopGuiClick(player, event.getSlot(), clickedInventory);
            }
        } else {
            // Check if the top inventory is one of our GUIs (even if clicking in the bottom inventory)
            // This prevents moving items when clicking in player inventory while our GUI is open
            Inventory topInventory = event.getView().getTopInventory();
            if (topInventory != null && topInventory.getHolder() instanceof GuiHolder) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Check if this player should see the duty selection GUI
        if (plugin.getGuiManager().shouldShowOnJoin(player)) {
            // Get delay from config (default: 20 ticks = 1 second)
            long delay = plugin.getConfig().getLong("gui.join-delay", 20L);

            // Schedule GUI to open after the delay
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                // Only show if they're still online
                if (player.isOnline()) {
                    // Use the enhanced GUI if enabled, otherwise use the old one
                    if (plugin.getConfig().getBoolean("gui.use-enhanced-gui", true)) {
                        plugin.getGuiManager().openEnhancedCorrectionsGui(player);
                    } else {
                        plugin.getGuiManager().openDutySelectionGui(player);
                    }
                }
            }, delay);
        }
    }

    /**
     * This prevents inventory-related bugs when a player with an open GUI is re-logging
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        // No specific action needed here - just a hook for future needs
        Inventory inventory = event.getInventory();
        if (inventory.getHolder() instanceof GuiHolder) {
            // GUI was closed - could add tracking here if needed in the future
        }
    }
}