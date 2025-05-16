package dev.lsdmc.edencorrections.gui;

import dev.lsdmc.edencorrections.EdenCorrections;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Custom InventoryHolder for plugin GUIs
 */
public class GuiHolder implements InventoryHolder {
    private final EdenCorrections plugin;
    private final GuiType guiType;

    public GuiHolder(EdenCorrections plugin, GuiType guiType) {
        this.plugin = plugin;
        this.guiType = guiType;
    }

    @Override
    public Inventory getInventory() {
        return null; // This is just a placeholder - the actual inventory is created elsewhere
    }

    public EdenCorrections getPlugin() {
        return plugin;
    }

    public GuiType getGuiType() {
        return guiType;
    }

    /**
     * Enum to differentiate between different types of GUIs
     */
    public enum GuiType {
        DUTY_SELECTION,  // Original simple duty toggle GUI
        ENHANCED_MAIN,   // New enhanced main GUI
        STATS_VIEW,      // Stats detail view
        EQUIPMENT_VIEW,  // Equipment management view
        ACTIONS_VIEW,    // Guard actions view
        TOKENS_VIEW,     // Token conversion view
        SHOP_VIEW        // New shop view for purchasing items
    }
}