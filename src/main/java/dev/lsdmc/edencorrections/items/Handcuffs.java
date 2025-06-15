package dev.lsdmc.edencorrections.items;

import dev.lsdmc.edencorrections.EdenCorrections;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.List;

public class Handcuffs {
    private final EdenCorrections plugin;
    private final NamespacedKey handcuffKey;

    public Handcuffs(EdenCorrections plugin) {
        this.plugin = plugin;
        this.handcuffKey = new NamespacedKey(plugin, "handcuffs");
    }

    public ItemStack createHandcuffs() {
        ItemStack handcuffs = new ItemStack(Material.IRON_INGOT);
        ItemMeta meta = handcuffs.getItemMeta();
        
        meta.displayName(Component.text("Handcuffs", NamedTextColor.GOLD));
        meta.lore(Arrays.asList(
            Component.text("Used to restrain prisoners", NamedTextColor.GRAY),
            Component.text("Right-click to use", NamedTextColor.GRAY)
        ));
        
        meta.getPersistentDataContainer().set(handcuffKey, PersistentDataType.BOOLEAN, true);
        handcuffs.setItemMeta(meta);
        
        return handcuffs;
    }

    public boolean isHandcuffs(ItemStack item) {
        if (item == null || item.getType() != Material.IRON_INGOT) {
            return false;
        }
        
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(handcuffKey, PersistentDataType.BOOLEAN);
    }

    public void applyHandcuffs(Player guard, Player target) {
        if (!plugin.getGuardService().isOnDuty(guard)) {
            guard.sendMessage(Component.text("You must be on duty to use handcuffs.", NamedTextColor.RED));
            return;
        }

        if (target.hasPermission("edencorrections.guard")) {
            guard.sendMessage(Component.text("You cannot handcuff other guards.", NamedTextColor.RED));
            return;
        }

        // Apply handcuff effects
        target.setWalkSpeed(0.0f);
        target.setFlySpeed(0.0f);
        target.setInvulnerable(true);
        
        // Notify players
        guard.sendMessage(Component.text("You have handcuffed " + target.getName(), NamedTextColor.GREEN));
        target.sendMessage(Component.text("You have been handcuffed by " + guard.getName(), NamedTextColor.RED));
        
        // Schedule removal of handcuffs after 5 minutes
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> removeHandcuffs(target), 6000L);
    }

    public void removeHandcuffs(Player target) {
        target.setWalkSpeed(0.2f);
        target.setFlySpeed(0.1f);
        target.setInvulnerable(false);
        target.sendMessage(Component.text("Your handcuffs have been removed.", NamedTextColor.GREEN));
    }
} 