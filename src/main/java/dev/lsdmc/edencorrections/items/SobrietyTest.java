package dev.lsdmc.edencorrections.items;

import org.bukkit.event.Listener;

public class SobrietyTest implements Listener {
    // TODO: Implement Sobriety Test logic

    public static boolean isSobrietyTest(org.bukkit.inventory.ItemStack item) {
        return item != null && item.hasItemMeta() &&
               item.getType() == org.bukkit.Material.GLASS_BOTTLE &&
               item.getItemMeta().hasDisplayName() &&
               item.getItemMeta().getDisplayName().equals("Sobriety Test");
    }
} 