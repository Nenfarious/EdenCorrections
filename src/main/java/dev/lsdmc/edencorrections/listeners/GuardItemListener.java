package dev.lsdmc.edencorrections.listeners;

import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import dev.lsdmc.edencorrections.EdenCorrections;
import dev.lsdmc.edencorrections.items.Handcuffs;
import dev.lsdmc.edencorrections.items.DrugSniffer;
import dev.lsdmc.edencorrections.items.MetalDetector;
import dev.lsdmc.edencorrections.items.GuardSpyglass;
import dev.lsdmc.edencorrections.items.SobrietyTest;
import dev.lsdmc.edencorrections.items.PrisonRemote;
import dev.lsdmc.edencorrections.items.GuardBaton;
import dev.lsdmc.edencorrections.items.SmokeBomb;
import dev.lsdmc.edencorrections.items.GuardTaser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import com.sk89q.worldguard.protection.flags.StateFlag;

public class GuardItemListener implements Listener {
    private final EdenCorrections plugin;

    public GuardItemListener(EdenCorrections plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onGuardItemUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null) return;

        // Check if it's a guard item
        if (isGuardItem(item)) {
            // Check if player is in a safezone
            if (!isInSafezone(player)) {
                event.setCancelled(true);
                player.sendMessage(Component.text("You can only use guard items in safezones!", NamedTextColor.RED));
            }
        }
    }

    @EventHandler
    public void onGuardItemUseEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null) return;

        // Check if it's a guard item
        if (isGuardItem(item)) {
            // Check if player is in a safezone
            if (!isInSafezone(player)) {
                event.setCancelled(true);
                player.sendMessage(Component.text("You can only use guard items in safezones!", NamedTextColor.RED));
            }
        }
    }

    private boolean isGuardItem(ItemStack item) {
        // Check for handcuffs
        if (plugin.getHandcuffs().isHandcuffs(item)) return true;
        // Drug Sniffer
        if (DrugSniffer.isDrugSniffer(item)) return true;
        // Metal Detector
        if (MetalDetector.isMetalDetector(item)) return true;
        // Guard Spyglass
        if (GuardSpyglass.isGuardSpyglass(item)) return true;
        // Sobriety Test
        if (SobrietyTest.isSobrietyTest(item)) return true;
        // Prison Remote
        if (PrisonRemote.isPrisonRemote(item)) return true;
        // Guard Baton
        if (GuardBaton.isGuardBaton(item)) return true;
        // Smoke Bomb
        if (SmokeBomb.isSmokeBomb(item)) return true;
        // Guard Taser
        if (GuardTaser.isGuardTaser(item)) return true;
        return false;
    }

    private boolean isInSafezone(Player player) {
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionQuery query = container.createQuery();
        com.sk89q.worldedit.util.Location wgLoc = new com.sk89q.worldedit.util.Location(
            WorldGuard.getInstance().getPlatform().getMatcher().getWorldByName(player.getWorld().getName()),
            player.getLocation().getX(),
            player.getLocation().getY(),
            player.getLocation().getZ()
        );
        return query.getApplicableRegions(wgLoc).getRegions().stream()
            .anyMatch(region -> region.getFlag(Flags.PVP) == StateFlag.State.DENY);
    }
} 