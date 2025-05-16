package dev.lsdmc.edencorrections.items;

import dev.lsdmc.edencorrections.EdenCorrections;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SpecialItemManager implements Listener {
    private final EdenCorrections plugin;

    // Map to track thrown smoke bombs
    private final Map<UUID, ItemStack> thrownSmokeBombs = new HashMap<>();

    // Map to track taser cooldowns
    private final Map<UUID, Long> taserCooldowns = new HashMap<>();

    public SpecialItemManager(EdenCorrections plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null || !item.hasItemMeta() || item.getItemMeta().getDisplayName() == null) {
            return;
        }

        // Check for right-click air or block
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        // Check if player is on duty
        if (!plugin.getDutyManager().isOnDuty(player.getUniqueId())) {
            return;
        }

        String itemName = item.getItemMeta().getDisplayName();

        // Smoke Bomb
        if (item.getType() == Material.FIRE_CHARGE && itemName.contains("Smoke Bomb")) {
            event.setCancelled(true);
            throwSmokeBomb(player, item);
            return;
        }

        // Guard Taser
        if (item.getType() == Material.SOUL_TORCH && itemName.contains("Guard Taser")) {
            event.setCancelled(true);
            useTaser(player);
            return;
        }
    }

    /**
     * Handle throwing a smoke bomb
     */
    private void throwSmokeBomb(Player player, ItemStack smokeBomb) {
        // Check if player can throw (not in safe zone, etc.)
        if (!canUseGuardItem(player)) {
            return;
        }

        // Create a snowball projectile
        Snowball projectile = player.launchProjectile(Snowball.class);
        projectile.setVelocity(player.getLocation().getDirection().multiply(1.5));

        // Store the original item for reference
        thrownSmokeBombs.put(projectile.getUniqueId(), smokeBomb);

        // Consume one smoke bomb
        if (smokeBomb.getAmount() > 1) {
            smokeBomb.setAmount(smokeBomb.getAmount() - 1);
        } else {
            player.getInventory().remove(smokeBomb);
        }

        // Play throw sound
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_SNOWBALL_THROW, 1.0f, 0.5f);
    }

    /**
     * Handle using a taser
     */
    private void useTaser(Player player) {
        // Check cooldown
        if (hasTaserCooldown(player)) {
            player.sendMessage("§cYour taser is recharging!");
            return;
        }

        // Check if player can use (not in safe zone, etc.)
        if (!canUseGuardItem(player)) {
            return;
        }

        // Find target - raycast to find the nearest player in line of sight
        Player target = getTaserTarget(player);

        if (target == null) {
            player.sendMessage("§cNo valid target in range!");
            return;
        }

        // Apply stun effect
        stunPlayer(target, 50); // 2.5 seconds (50 ticks)

        // Play effects
        playTaserEffects(player, target);

        // Set cooldown
        setTaserCooldown(player, 5); // 5 second cooldown
    }

    /**
     * Handle a projectile hitting something
     */
    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();

        // Check if it's a smoke bomb
        if (projectile instanceof Snowball && thrownSmokeBombs.containsKey(projectile.getUniqueId())) {
            // Trigger smoke bomb effect
            triggerSmokeBomb(projectile.getLocation());

            // Remove from tracking
            thrownSmokeBombs.remove(projectile.getUniqueId());
        }
    }

    /**
     * Create smoke bomb effect at location
     */
    private void triggerSmokeBomb(Location location) {
        World world = location.getWorld();
        if (world == null) return;

        // Play explosion sound
        world.playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);

        // Create smoke particles
        for (int i = 0; i < 50; i++) {
            Vector randomVector = new Vector(
                    Math.random() * 2 - 1,
                    Math.random() * 2,
                    Math.random() * 2 - 1
            );

            Location particleLoc = location.clone().add(randomVector);
            world.spawnParticle(Particle.SMOKE_LARGE, particleLoc, 5, 0.2, 0.2, 0.2, 0.05);
        }

        // Apply effects to nearby players
        for (Entity entity : world.getNearbyEntities(location, 5, 5, 5)) {
            if (entity instanceof Player) {
                Player target = (Player) entity;

                // Skip guards if configured
                if (plugin.getDutyManager().isOnDuty(target.getUniqueId()) &&
                        plugin.getConfig().getBoolean("items.smoke-bomb.guard-immunity", false)) {
                    continue;
                }

                // Apply blindness
                target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 15*20, 0));

                // Apply darkness after blindness ends
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 30*20, 0));
                }, 15*20 + 5);

                // Message
                target.sendMessage("§c§lYou've been hit by a smoke bomb!");
            }
        }
    }

    /**
     * Check if player can use guard items
     */
    private boolean canUseGuardItem(Player player) {
        // Check if in safe zone
        if (plugin.getRegionUtils().isInSafeZone(player.getLocation())) {
            player.sendMessage("§cYou cannot use this item in a safe zone!");
            return false;
        }

        // Check if on duty
        if (!plugin.getDutyManager().isOnDuty(player.getUniqueId())) {
            player.sendMessage("§cYou must be on duty to use this item!");
            return false;
        }

        return true;
    }

    /**
     * Find a valid taser target
     */
    private Player getTaserTarget(Player player) {
        // Get max range
        double maxRange = 10.0;

        // Get player's line of sight
        Vector direction = player.getLocation().getDirection();
        Location eyeLocation = player.getEyeLocation();

        Player closestTarget = null;
        double closestDistance = maxRange + 1;

        // Check for players in range
        for (Player target : player.getWorld().getPlayers()) {
            // Skip self
            if (target.equals(player)) continue;

            // Skip other guards
            if (plugin.getDutyManager().isOnDuty(target.getUniqueId())) continue;

            // Check distance
            double distance = target.getLocation().distance(player.getLocation());
            if (distance > maxRange) continue;

            // Check if in line of sight
            Location targetEye = target.getEyeLocation();
            Vector toTarget = targetEye.toVector().subtract(eyeLocation.toVector());

            double dot = toTarget.normalize().dot(direction);
            if (dot < 0.8) continue; // Not looking at target

            // Check for obstructions
            if (!player.hasLineOfSight(target)) continue;

            // Check if this is the closest target
            if (distance < closestDistance) {
                closestTarget = target;
                closestDistance = distance;
            }
        }

        return closestTarget;
    }

    /**
     * Apply stun effect to player
     */
    private void stunPlayer(Player target, int ticks) {
        // Apply immobilization
        target.setVelocity(new Vector(0, 0, 0));

        // Apply slowness and weakness
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, ticks, 255, false, false));
        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, ticks, 255, false, false));
        target.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, ticks, 128, false, false)); // Prevents jumping

        // Prevent movement with a task
        final Location location = target.getLocation();
        new BukkitRunnable() {
            int remaining = ticks;

            @Override
            public void run() {
                if (remaining <= 0 || !target.isOnline()) {
                    this.cancel();
                    return;
                }

                // Force player back to same location
                target.teleport(location);
                target.sendMessage("§c§lYou've been tased! §c(" + (remaining / 20.0) + "s)");

                remaining -= 5;
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    /**
     * Play visual and sound effects for taser
     */
    private void playTaserEffects(Player user, Player target) {
        // Play sound
        user.getWorld().playSound(user.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 2.0f);
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 0.5f);

        // Visual beam effect
        Vector direction = target.getLocation().toVector().subtract(user.getLocation().toVector()).normalize();
        Location current = user.getLocation().add(0, 1, 0);

        for (int i = 0; i < 20; i++) {
            current.add(direction.clone().multiply(0.5));
            user.getWorld().spawnParticle(Particle.REDSTONE, current, 1,
                    new Particle.DustOptions(Color.YELLOW, 1));
        }
    }

    /**
     * Set taser cooldown
     */
    private void setTaserCooldown(Player player, int seconds) {
        taserCooldowns.put(player.getUniqueId(), System.currentTimeMillis() + (seconds * 1000L));
    }

    /**
     * Check if player has taser cooldown
     */
    private boolean hasTaserCooldown(Player player) {
        if (!taserCooldowns.containsKey(player.getUniqueId())) {
            return false;
        }

        long cooldownEnd = taserCooldowns.get(player.getUniqueId());
        if (System.currentTimeMillis() >= cooldownEnd) {
            taserCooldowns.remove(player.getUniqueId());
            return false;
        }

        return true;
    }

    /**
     * Creates a prisoner-usable taser with limited charges
     */
    public ItemStack createPrisonerTaser() {
        ItemStack taser = new ItemStack(Material.SOUL_TORCH);
        ItemMeta meta = taser.getItemMeta();

        if (meta != null) {
            meta.setDisplayName("§e§lWeakened Taser");

            List<String> lore = new ArrayList<>();
            lore.add("§7A taser with limited charges");
            lore.add("§7Right-click to stun a guard");
            lore.add("");
            lore.add("§7Charges: §e3§7/3");

            meta.setLore(lore);
            taser.setItemMeta(meta);
        }

        return taser;
    }

    /**
     * Handle guard death to drop special items
     */
    public void handleGuardDeath(Player guard, Player killer) {
        // Check if killer exists and isn't a guard
        if (killer == null || plugin.getDutyManager().isOnDuty(killer.getUniqueId())) {
            return;
        }

        // Check for taser drop chance
        if (Math.random() < 0.5) { // 50% chance
            // Drop a prisoner taser
            ItemStack prisonerTaser = createPrisonerTaser();
            guard.getWorld().dropItemNaturally(guard.getLocation(), prisonerTaser);
        }
    }
}