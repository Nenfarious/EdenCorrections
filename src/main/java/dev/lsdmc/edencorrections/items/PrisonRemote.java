package dev.lsdmc.edencorrections.items;

import dev.lsdmc.edencorrections.EdenCorrections;
import dev.lsdmc.edencorrections.utils.RegionUtils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class PrisonRemote implements Listener {
    private final EdenCorrections plugin;
    private static final int MAX_RANGE = 20;
    private static final int COOLDOWN_TICKS = 40; // 2 seconds
    private final Map<UUID, Long> lastUseTime = new HashMap<>();
    private final Set<Location> activeCells = new HashSet<>();

    public PrisonRemote(EdenCorrections plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public static boolean isPrisonRemote(ItemStack item) {
        return item != null && item.hasItemMeta() &&
               item.getType() == Material.REPEATER &&
               item.getItemMeta().hasDisplayName() &&
               item.getItemMeta().getDisplayName().equals("Prison Remote");
    }

    @EventHandler
    public void onRemoteUse(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (!isGuardRemote(item)) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        // Check cooldown
        if (isOnCooldown(player)) {
            player.sendMessage(ChatColor.RED + "The remote is recharging...");
            return;
        }

        // Cancel the event
        event.setCancelled(true);

        // Get target block
        Block targetBlock = player.getTargetBlockExact(MAX_RANGE);
        if (targetBlock == null) {
            player.sendMessage(ChatColor.RED + "No valid cell door in range!");
            return;
        }

        // Check if it's a valid cell door
        if (!isValidCellDoor(targetBlock)) {
            player.sendMessage(ChatColor.RED + "This is not a valid cell door!");
            return;
        }

        // Toggle the cell
        toggleCell(player, targetBlock);
        
        // Set cooldown
        setCooldown(player);
    }

    private boolean isGuardRemote(ItemStack item) {
        return isPrisonRemote(item) && item.getItemMeta().hasLore() &&
               item.getItemMeta().getLore().contains("Guard Remote");
    }

    private boolean isOnCooldown(Player player) {
        long lastUse = lastUseTime.getOrDefault(player.getUniqueId(), 0L);
        return System.currentTimeMillis() - lastUse < COOLDOWN_TICKS * 50;
    }

    private void setCooldown(Player player) {
        lastUseTime.put(player.getUniqueId(), System.currentTimeMillis());
    }

    private boolean isValidCellDoor(Block block) {
        // Check if it's an iron door
        if (block.getType() != Material.IRON_DOOR) return false;

        // Check if it's in a cell region (you can implement your own region check)
        return RegionUtils.isLocationInRegion(block.getLocation(), "prison_cell");
    }

    private void toggleCell(Player player, Block doorBlock) {
        Location doorLoc = doorBlock.getLocation();
        boolean isOpen = activeCells.contains(doorLoc);

        if (isOpen) {
            // Close the cell
            closeCell(doorBlock);
            activeCells.remove(doorLoc);
            player.sendMessage(ChatColor.GREEN + "Cell door locked!");
        } else {
            // Open the cell
            openCell(doorBlock);
            activeCells.add(doorLoc);
            player.sendMessage(ChatColor.GREEN + "Cell door unlocked!");
        }

        // Play sound effect
        doorBlock.getWorld().playSound(
            doorBlock.getLocation(),
            isOpen ? Sound.BLOCK_IRON_DOOR_CLOSE : Sound.BLOCK_IRON_DOOR_OPEN,
            1.0f,
            1.0f
        );

        // Visual effect
        doorBlock.getWorld().spawnParticle(
            Particle.DUST,
            doorBlock.getLocation().add(0.5, 1, 0.5),
            10,
            0.3, 0.3, 0.3,
            new Particle.DustOptions(
                isOpen ? Color.RED : Color.GREEN,
                1
            )
        );
    }

    private void openCell(Block doorBlock) {
        // Find the door blocks
        Block[] doorBlocks = findDoorBlocks(doorBlock);
        
        // Open the door
        for (Block block : doorBlocks) {
            block.setType(Material.AIR);
        }

        // Schedule door closing
        new BukkitRunnable() {
            @Override
            public void run() {
                if (activeCells.contains(doorBlock.getLocation())) {
                    closeCell(doorBlock);
                    activeCells.remove(doorBlock.getLocation());
                }
            }
        }.runTaskLater(plugin, 200L); // Close after 10 seconds
    }

    private void closeCell(Block doorBlock) {
        // Find the door blocks
        Block[] doorBlocks = findDoorBlocks(doorBlock);
        
        // Close the door
        for (Block block : doorBlocks) {
            block.setType(Material.IRON_DOOR);
        }
    }

    private Block[] findDoorBlocks(Block doorBlock) {
        // Find both parts of the door
        Block topBlock = doorBlock;
        Block bottomBlock = doorBlock;

        if (doorBlock.getRelative(BlockFace.UP).getType() == Material.IRON_DOOR) {
            topBlock = doorBlock.getRelative(BlockFace.UP);
        } else if (doorBlock.getRelative(BlockFace.DOWN).getType() == Material.IRON_DOOR) {
            bottomBlock = doorBlock.getRelative(BlockFace.DOWN);
        }

        return new Block[]{topBlock, bottomBlock};
    }
} 