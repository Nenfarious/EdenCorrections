package dev.lsdmc.edencorrections.items;

import dev.lsdmc.edencorrections.EdenCorrections;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class SmokeBomb implements Listener {
    private final EdenCorrections plugin;
    private static final int SMOKE_DURATION = 100; // 5 seconds (20 ticks * 5)
    private static final double SMOKE_RADIUS = 3.0;
    private static final int PARTICLES_PER_TICK = 20;

    public SmokeBomb(EdenCorrections plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public static boolean isSmokeBomb(ItemStack item) {
        return item != null && item.hasItemMeta() &&
               item.getType() == Material.FIREWORK_STAR &&
               item.getItemMeta().hasDisplayName() &&
               item.getItemMeta().getDisplayName().equals("Smoke Bomb");
    }

    @EventHandler
    public void onSmokeBombUse(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (!isSmokeBomb(item)) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        // Cancel the event to prevent item usage
        event.setCancelled(true);

        // Remove one smoke bomb from inventory
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }

        // Play throw sound
        player.playSound(player.getLocation(), Sound.ENTITY_SNOWBALL_THROW, 1.0f, 1.0f);

        // Calculate throw trajectory
        Vector direction = player.getLocation().getDirection();
        Location startLoc = player.getEyeLocation();
        Location currentLoc = startLoc.clone();

        // Create smoke effect
        new BukkitRunnable() {
            int ticks = 0;
            Location smokeLoc = startLoc.clone();

            @Override
            public void run() {
                if (ticks >= SMOKE_DURATION) {
                    this.cancel();
                    return;
                }

                // Update smoke location
                if (ticks < 10) { // Initial throw
                    smokeLoc.add(direction.multiply(0.5));
                    smokeLoc.add(0, -0.05, 0); // Gravity effect
                }

                // Create smoke particles
                for (int i = 0; i < PARTICLES_PER_TICK; i++) {
                    double angle = Math.random() * Math.PI * 2;
                    double radius = Math.random() * SMOKE_RADIUS;
                    double x = Math.cos(angle) * radius;
                    double z = Math.sin(angle) * radius;
                    
                    Location particleLoc = smokeLoc.clone().add(x, 0, z);
                    smokeLoc.getWorld().spawnParticle(
                        Particle.CLOUD,
                        particleLoc,
                        1, 0, 0, 0, 0
                    );
                }

                // Apply blindness effect to nearby players
                smokeLoc.getWorld().getNearbyPlayers(smokeLoc, SMOKE_RADIUS).forEach(nearbyPlayer -> {
                    if (nearbyPlayer != player) { // Don't affect the thrower
                        nearbyPlayer.addPotionEffect(new org.bukkit.potion.PotionEffect(
                            org.bukkit.potion.PotionEffectType.BLINDNESS,
                            40, // 2 seconds
                            0
                        ));
                    }
                });

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
} 