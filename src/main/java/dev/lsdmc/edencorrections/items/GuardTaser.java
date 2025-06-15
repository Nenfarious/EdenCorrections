package dev.lsdmc.edencorrections.items;

import org.bukkit.event.Listener;

public class GuardTaser implements Listener {
    // TODO: Implement Guard Taser logic

    public static boolean isGuardTaser(org.bukkit.inventory.ItemStack item) {
        return item != null && item.hasItemMeta() &&
               item.getType() == org.bukkit.Material.TRIPWIRE_HOOK &&
               item.getItemMeta().hasDisplayName() &&
               item.getItemMeta().getDisplayName().equals("Guard Taser");
    }
} 