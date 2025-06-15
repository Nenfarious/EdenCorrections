package dev.lsdmc.edencorrections.items;

import dev.lsdmc.edencorrections.EdenCorrections;
import org.bukkit.*;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class GuardBaton implements Listener {
    private final EdenCorrections plugin;
    private static final double STUN_CHANCE = 0.3; // 30% chance to stun
    private static final int STUN_DURATION = 60; // 3 seconds (20 ticks * 3)
    private static final double KNOCKBACK_MULTIPLIER = 1.5;
    private static final double DAMAGE = 2.0;

    public GuardBaton(EdenCorrections plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public static boolean isGuardBaton(ItemStack item) {
        return item != null && item.hasItemMeta() &&
               item.getType() == Material.STICK &&
               item.getItemMeta().hasDisplayName() &&
               item.getItemMeta().getDisplayName().equals("Guard Baton");
    }

    @EventHandler
    public void onBatonHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return;
        if (!(event.getEntity() instanceof LivingEntity)) return;

        Player attacker = (Player) event.getDamager();
        LivingEntity target = (LivingEntity) event.getEntity();
        ItemStack weapon = attacker.getInventory().getItemInMainHand();

        if (!isGuardBaton(weapon)) return;

        // Cancel default damage
        event.setCancelled(true);

        // Apply custom damage
        double finalDamage = DAMAGE;
        if (attacker.hasPermission("edencorrections.baton.damage.2")) {
            finalDamage *= 1.5;
        }
        target.damage(finalDamage, attacker);

        // Apply knockback
        Vector knockback = target.getLocation().toVector().subtract(attacker.getLocation().toVector()).normalize();
        knockback.multiply(KNOCKBACK_MULTIPLIER);
        knockback.setY(0.2); // Small upward knockback
        target.setVelocity(knockback);

        // Play hit sound
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 1.0f, 0.8f);

        // Visual effect
        target.getWorld().spawnParticle(
            Particle.CRIT,
            target.getLocation().add(0, 1, 0),
            10, 0.5, 0.5, 0.5, 0.1
        );

        // Stun effect
        if (Math.random() < STUN_CHANCE) {
            applyStunEffect(target);
        }

        // Cooldown effect on the baton
        applyCooldownEffect(attacker);
    }

    private void applyStunEffect(LivingEntity target) {
        // Apply stun effects
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, STUN_DURATION, 2));
        target.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, STUN_DURATION, 2));
        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, STUN_DURATION, 1));

        // Visual stun effect
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= STUN_DURATION) {
                    this.cancel();
                    return;
                }

                target.getWorld().spawnParticle(
                    Particle.WITCH,
                    target.getLocation().add(0, 1, 0),
                    5, 0.3, 0.3, 0.3, 0
                );

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    private void applyCooldownEffect(Player player) {
        // Add a small cooldown to prevent spam
        player.setCooldown(Material.STICK, 20); // 1 second cooldown
    }
} 