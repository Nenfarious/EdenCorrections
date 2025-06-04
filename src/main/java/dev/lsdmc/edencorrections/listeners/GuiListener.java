package dev.lsdmc.edencorrections.listeners;

import dev.lsdmc.edencorrections.EdenCorrections;
import dev.lsdmc.edencorrections.gui.GuiHolder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Listener for GUI-related events
 * FIXED: Removed PlayerJoinEvent handler to prevent conflicts - consolidated in PlayerListener
 */
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

            // Handle based on the GUI type - route to the appropriate handler
            switch (guiHolder.getGuiType()) {
                case ENHANCED_MAIN:
                    // Main menu handler
                    plugin.getGuiManager().handleMainMenuClick(player, event.getSlot());
                    break;
                case DUTY_SELECTION:
                    // Duty management menu handler
                    plugin.getGuiManager().handleDutyMenuClick(player, event.getSlot());
                    break;
                case STATS_VIEW:
                    // Stats menu handler
                    plugin.getGuiManager().handleStatsMenuClick(player, event.getSlot());
                    break;
                case ACTIONS_VIEW:
                    // Actions menu handler
                    plugin.getGuiManager().handleActionsMenuClick(player, event.getSlot());
                    break;
                case EQUIPMENT_VIEW:
                    // Equipment menu handler
                    plugin.getGuiManager().handleEquipmentMenuClick(player, event.getSlot());
                    break;
                case SHOP_VIEW:
                    // Shop menu handler
                    plugin.getGuiManager().handleShopMenuClick(player, event.getSlot());
                    break;
                case TOKENS_VIEW:
                    // Token management handler
                    plugin.getGuiManager().handleTokensViewClick(player, event.getSlot());
                    break;
                default:
                    // Unknown GUI type - do nothing
                    break;
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