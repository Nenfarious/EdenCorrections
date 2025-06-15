package dev.lsdmc.edencorrections.listeners;

import dev.lsdmc.edencorrections.EdenCorrections;
import dev.lsdmc.edencorrections.managers.ContrabandManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;

public class ContrabandListener implements Listener {
    private final EdenCorrections plugin;
    private final ContrabandManager contrabandManager;

    public ContrabandListener(EdenCorrections plugin) {
        this.plugin = plugin;
        this.contrabandManager = plugin.getContrabandManager();
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item == null) return;

        Player player = event.getPlayer();
        
        // Allow guards to use contraband items
        if (plugin.getGuardDutyManager().isOnDuty(player)) {
            return;
        }

        if (contrabandManager.isDrug(item)) {
            event.setCancelled(true);
            contrabandManager.handleDrugUse(player, item);
            item.setAmount(item.getAmount() - 1);
        } else if (contrabandManager.isContraband(item)) {
            event.setCancelled(true);
            player.sendMessage(Component.text("This item is contraband and cannot be used.", NamedTextColor.RED));
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (item == null) return;

        Player player = event.getPlayer();
        
        // Allow guards to place contraband items
        if (plugin.getGuardDutyManager().isOnDuty(player)) {
            return;
        }

        if (contrabandManager.isContraband(item)) {
            event.setCancelled(true);
            player.sendMessage(Component.text("This item is contraband and cannot be placed.", NamedTextColor.RED));
        }
    }

    @EventHandler
    public void onItemDrop(PlayerDropItemEvent event) {
        ItemStack item = event.getItemDrop().getItemStack();
        if (item == null) return;

        Player player = event.getPlayer();
        
        // Allow guards to drop contraband items
        if (plugin.getGuardDutyManager().isOnDuty(player)) {
            return;
        }

        if (contrabandManager.isContraband(item)) {
            event.setCancelled(true);
            player.sendMessage(Component.text("This item is contraband and cannot be dropped.", NamedTextColor.RED));
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        
        // Allow guards to move contraband items
        if (plugin.getGuardDutyManager().isOnDuty(player)) {
            return;
        }

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem != null && contrabandManager.isContraband(clickedItem)) {
            event.setCancelled(true);
            player.sendMessage(Component.text("This item is contraband and cannot be moved.", NamedTextColor.RED));
        }

        ItemStack cursorItem = event.getCursor();
        if (cursorItem != null && contrabandManager.isContraband(cursorItem)) {
            event.setCancelled(true);
            player.sendMessage(Component.text("This item is contraband and cannot be moved.", NamedTextColor.RED));
        }
    }

    @EventHandler
    public void onItemConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        if (item == null) return;

        Player player = event.getPlayer();
        
        // Allow guards to consume contraband items
        if (plugin.getGuardDutyManager().isOnDuty(player)) {
            return;
        }

        if (contrabandManager.isContraband(item)) {
            event.setCancelled(true);
            player.sendMessage(Component.text("This item is contraband and cannot be consumed.", NamedTextColor.RED));
        }
    }
} 