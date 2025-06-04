package dev.lsdmc.edencorrections.managers.loot;

import net.kyori.adventure.text.format.TextColor;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Enhanced item quality system with visual effects, announcements, and special properties
 */
public enum ItemQuality {
    DAMAGED("§8", "Damaged", 0.5, 0.1, 0.0, 0.0, false, null, 0),
    STANDARD("§f", "Standard", 1.0, 0.3, 0.0, 0.0, false, null, 0),
    ENHANCED("§a", "Enhanced", 1.3, 0.5, 0.2, 0.0, false, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1),
    SUPERIOR("§9", "Superior", 1.6, 0.7, 0.4, 0.1, false, Sound.ENTITY_PLAYER_LEVELUP, 2),
    LEGENDARY("§6", "Legendary", 2.0, 0.9, 0.6, 0.25, true, Sound.UI_TOAST_CHALLENGE_COMPLETE, 3),
    MYTHIC("§d", "Mythic", 2.5, 1.0, 0.8, 0.5, true, Sound.ENTITY_ENDER_DRAGON_GROWL, 5),
    DIVINE("§c", "Divine", 3.0, 1.0, 1.0, 0.8, true, Sound.ENTITY_WITHER_SPAWN, 10);
    
    private final String colorCode;
    private final String displayName;
    private final double durabilityMultiplier;
    private final double enchantChance;
    private final double multiEnchantChance;
    private final double specialEffectChance;
    private final boolean broadcastDrop;
    private final Sound dropSound;
    private final int particleIntensity;
    
    // Static mappings for quality distribution
    private static final Map<String, QualityDistribution> RANK_DISTRIBUTIONS = new HashMap<>();
    private static final Random RANDOM = new Random();
    
    static {
        // Define quality distributions for each guard rank
        RANK_DISTRIBUTIONS.put("trainee", new QualityDistribution()
            .add(DAMAGED, 40)
            .add(STANDARD, 50)
            .add(ENHANCED, 10));
            
        RANK_DISTRIBUTIONS.put("private", new QualityDistribution()
            .add(DAMAGED, 20)
            .add(STANDARD, 50)
            .add(ENHANCED, 25)
            .add(SUPERIOR, 5));
            
        RANK_DISTRIBUTIONS.put("officer", new QualityDistribution()
            .add(STANDARD, 30)
            .add(ENHANCED, 40)
            .add(SUPERIOR, 25)
            .add(LEGENDARY, 5));
            
        RANK_DISTRIBUTIONS.put("sergeant", new QualityDistribution()
            .add(ENHANCED, 25)
            .add(SUPERIOR, 45)
            .add(LEGENDARY, 25)
            .add(MYTHIC, 5));
            
        RANK_DISTRIBUTIONS.put("warden", new QualityDistribution()
            .add(SUPERIOR, 30)
            .add(LEGENDARY, 50)
            .add(MYTHIC, 20));
    }
    
    ItemQuality(String colorCode, String displayName, double durabilityMultiplier, 
                double enchantChance, double multiEnchantChance, double specialEffectChance,
                boolean broadcastDrop, Sound dropSound, int particleIntensity) {
        this.colorCode = colorCode;
        this.displayName = displayName;
        this.durabilityMultiplier = durabilityMultiplier;
        this.enchantChance = enchantChance;
        this.multiEnchantChance = multiEnchantChance;
        this.specialEffectChance = specialEffectChance;
        this.broadcastDrop = broadcastDrop;
        this.dropSound = dropSound;
        this.particleIntensity = particleIntensity;
    }
    
    /**
     * Apply quality modifications to an item
     */
    public ItemStack enhanceItem(ItemStack item, String context, Player recipient) {
        if (item == null) return null;

        ItemStack enhanced = item.clone();
        ItemMeta meta = enhanced.getItemMeta();
        if (meta == null) return enhanced;

        Random random = new Random();

        // Apply quality prefix to display name
        String originalName = meta.hasDisplayName() ? 
            meta.getDisplayName() : 
            enhanced.getType().toString().toLowerCase().replace('_', ' ');
        
        meta.setDisplayName(colorCode + "§l" + displayName + " §r" + colorCode + originalName);

        // Simplified quality lore - only show tier and guard loot label
        List<String> lore = new ArrayList<>();
        
        // Add quality tier
        lore.add("");
        lore.add(colorCode + "Tier: " + displayName);
        
        // Add special quality indicator for legendary+ items
        if (this.ordinal() >= LEGENDARY.ordinal()) {
            lore.add("§6✦ Special Properties ✦");
        }
        
        // Add guard loot label at the bottom (similar to guard items)
        lore.add("");
        lore.add("§c§lGuard Loot");
        
        meta.setLore(lore);

        // Apply enchantments based on quality
        applyQualityEnchantments(enhanced, meta, random);

        // Set unbreakable for divine items
        if (this == DIVINE) {
            meta.setUnbreakable(true);
        }

        enhanced.setItemMeta(meta);

        // Apply visual and audio effects
        if (recipient.isOnline()) {
            playQualityEffects(recipient, enhanced);
        }

        // Broadcast rare drops
        if (broadcastDrop) {
            broadcastRareDrop(recipient, enhanced);
        }

        return enhanced;
    }
    
    /**
     * Apply enchantments based on quality level
     */
    private void applyQualityEnchantments(ItemStack item, ItemMeta meta, Random random) {
        if (random.nextDouble() > enchantChance) return;

        List<Enchantment> applicableEnchants = getApplicableEnchantments(item.getType());
        if (applicableEnchants.isEmpty()) return;

        // Primary enchantment
        Enchantment primaryEnchant = applicableEnchants.get(random.nextInt(applicableEnchants.size()));
        int level = getEnchantmentLevel(primaryEnchant, random);
        meta.addEnchant(primaryEnchant, level, true);

        // Additional enchantments for higher qualities
        if (random.nextDouble() <= multiEnchantChance) {
            int additionalEnchants = this.ordinal() >= LEGENDARY.ordinal() ? 
                random.nextInt(3) + 1 : random.nextInt(2) + 1;
            
            for (int i = 0; i < additionalEnchants && applicableEnchants.size() > 1; i++) {
                Enchantment enchant;
                do {
                    enchant = applicableEnchants.get(random.nextInt(applicableEnchants.size()));
                } while (meta.hasEnchant(enchant));
                
                int enchantLevel = getEnchantmentLevel(enchant, random);
                meta.addEnchant(enchant, enchantLevel, true);
            }
        }
    }
    
    /**
     * Get appropriate enchantment level based on quality
     */
    private int getEnchantmentLevel(Enchantment enchant, Random random) {
        int maxLevel = Math.min(enchant.getMaxLevel(), this.ordinal() + 1);
        int minLevel = Math.max(1, this.ordinal() - 1);
        
        // Ensure minLevel doesn't exceed maxLevel
        minLevel = Math.min(minLevel, maxLevel);
        
        // Ensure we have a valid range
        if (maxLevel <= 0) {
            return 1; // Fallback to level 1
        }
        
        if (minLevel >= maxLevel) {
            return maxLevel; // Return max level if range is invalid
        }
        
        // Calculate the range - ensure it's positive
        int range = maxLevel - minLevel + 1;
        if (range <= 0) {
            return Math.max(1, maxLevel); // Fallback to max level or 1
        }
        
        return random.nextInt(range) + minLevel;
    }
    
    /**
     * Get enchantments that can be applied to this item type
     */
    private List<Enchantment> getApplicableEnchantments(Material material) {
        List<Enchantment> enchants = new ArrayList<>();
        
        // Weapon enchantments
        if (isWeapon(material)) {
            enchants.addAll(List.of(
                Enchantment.SHARPNESS, Enchantment.BANE_OF_ARTHROPODS, Enchantment.SMITE,
                Enchantment.FIRE_ASPECT, Enchantment.KNOCKBACK, Enchantment.LOOTING,
                Enchantment.SWEEPING_EDGE, Enchantment.MENDING, Enchantment.UNBREAKING
            ));
        }
        
        // Armor enchantments
        if (isArmor(material)) {
            enchants.addAll(List.of(
                Enchantment.PROTECTION, Enchantment.FIRE_PROTECTION,
                Enchantment.BLAST_PROTECTION, Enchantment.PROJECTILE_PROTECTION,
                Enchantment.THORNS, Enchantment.MENDING, Enchantment.UNBREAKING
            ));
        }
        
        // Tool enchantments
        if (isTool(material)) {
            enchants.addAll(List.of(
                Enchantment.EFFICIENCY, Enchantment.SILK_TOUCH, Enchantment.FORTUNE,
                Enchantment.MENDING, Enchantment.UNBREAKING
            ));
        }
        
        // Bow enchantments
        if (material == Material.BOW || material == Material.CROSSBOW) {
            enchants.addAll(List.of(
                Enchantment.POWER, Enchantment.FLAME, Enchantment.PUNCH,
                Enchantment.INFINITY, Enchantment.MENDING, Enchantment.UNBREAKING
            ));
        }
        
        return enchants;
    }
    
    /**
     * Play visual and audio effects for quality drops
     */
    private void playQualityEffects(Player player, ItemStack item) {
        Location loc = player.getLocation().add(0, 1, 0);
        
        // Play sound
        if (dropSound != null) {
            player.playSound(loc, dropSound, 0.7f, 1.0f);
        }
        
        // Spawn particles based on quality - DISABLED for compatibility
        // TODO: Re-enable particle effects with proper version detection
        
        // Special effects for legendary+ items
        if (this.ordinal() >= LEGENDARY.ordinal()) {
            // Title notification for recipient
            player.sendTitle(
                colorCode + "✦ " + displayName + " Item! ✦",
                "§7You received a " + displayName.toLowerCase() + " quality item!",
                10, 40, 10
            );
        }
    }
    
    /**
     * Get particle type for this quality - simplified to avoid version conflicts
     */
    private String getQualityEffect() {
        return switch (this) {
            case ENHANCED -> "enhanced";
            case SUPERIOR -> "superior";
            case LEGENDARY -> "legendary";
            case MYTHIC -> "mythic";
            case DIVINE -> "divine";
            default -> "none";
        };
    }
    
    /**
     * Broadcast rare item drops to the server
     */
    private void broadcastRareDrop(Player player, ItemStack item) {
        String message = String.format("§8[§6✦§8] §e%s §7received a %s§7 quality §e%s§7!",
            player.getName(),
            colorCode + displayName,
            item.getType().toString().toLowerCase().replace('_', ' ')
        );
        
        Bukkit.broadcast(net.kyori.adventure.text.Component.text(message));
        
        // Play server-wide sound for mythic+ items
        if (this.ordinal() >= MYTHIC.ordinal()) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                online.playSound(online.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.3f, 1.5f);
            }
        }
    }
    
    /**
     * Utility methods for material type checking
     */
    private boolean isWeapon(Material material) {
        return material.toString().contains("SWORD") || 
               material.toString().contains("AXE") ||
               material == Material.TRIDENT;
    }
    
    private boolean isArmor(Material material) {
        return material.toString().contains("HELMET") ||
               material.toString().contains("CHESTPLATE") ||
               material.toString().contains("LEGGINGS") ||
               material.toString().contains("BOOTS");
    }
    
    private boolean isTool(Material material) {
        return material.toString().contains("PICKAXE") ||
               material.toString().contains("SHOVEL") ||
               material.toString().contains("HOE");
    }
    
    /**
     * Get quality distribution for a rank with context bonuses
     */
    public static ItemQuality selectQuality(String rank, LootContext context, Random random) {
        Map<ItemQuality, Double> distribution = getBaseDistribution(rank);
        
        // Apply context modifiers
        applyContextModifiers(distribution, context);
        
        // Select based on weighted random
        return selectWeightedRandom(distribution, random);
    }
    
    /**
     * Get base quality distribution for rank
     */
    private static Map<ItemQuality, Double> getBaseDistribution(String rank) {
        Map<ItemQuality, Double> distribution = new HashMap<>();
        
        switch (rank.toLowerCase()) {
            case "trainee" -> {
                distribution.put(DAMAGED, 40.0);
                distribution.put(STANDARD, 50.0);
                distribution.put(ENHANCED, 10.0);
            }
            case "private" -> {
                distribution.put(DAMAGED, 20.0);
                distribution.put(STANDARD, 50.0);
                distribution.put(ENHANCED, 25.0);
                distribution.put(SUPERIOR, 5.0);
            }
            case "officer" -> {
                distribution.put(STANDARD, 30.0);
                distribution.put(ENHANCED, 40.0);
                distribution.put(SUPERIOR, 25.0);
                distribution.put(LEGENDARY, 5.0);
            }
            case "sergeant" -> {
                distribution.put(ENHANCED, 25.0);
                distribution.put(SUPERIOR, 45.0);
                distribution.put(LEGENDARY, 25.0);
                distribution.put(MYTHIC, 5.0);
            }
            case "warden" -> {
                distribution.put(SUPERIOR, 30.0);
                distribution.put(LEGENDARY, 50.0);
                distribution.put(MYTHIC, 20.0);
            }
            default -> {
                distribution.put(STANDARD, 100.0);
            }
        }
        
        return distribution;
    }
    
    /**
     * Apply context modifiers to quality distribution
     */
    private static void applyContextModifiers(Map<ItemQuality, Double> distribution, LootContext context) {
        double modifier = 1.0;
        
        // Performance bonuses
        if (context.getDutyTimeMinutes() >= 60) modifier += 0.2; // Long duty
        if (context.getArrestsThisSession() >= 3) modifier += 0.15; // Active guard
        if (context.getFightDuration() >= 30) modifier += 0.1; // Good fight
        
        // Environmental bonuses
        if (context.isInPvpArea()) modifier += 0.15;
        if (context.isSpecialEvent()) modifier += 0.25;
        if (context.getNearbyGuards() <= 1) modifier += 0.2; // Lone guard bonus
        
        // Time-based bonuses
        if (context.getTimeSinceLastDeath() >= 1800) modifier += 0.1; // 30 min no death
        
        // Apply modifier by shifting distribution toward higher qualities
        if (modifier > 1.0) {
            shiftDistributionUp(distribution, modifier - 1.0);
        }
    }
    
    /**
     * Shift quality distribution toward higher qualities
     */
    private static void shiftDistributionUp(Map<ItemQuality, Double> distribution, double shiftAmount) {
        List<ItemQuality> qualities = List.of(values());
        
        for (int i = 0; i < qualities.size() - 1; i++) {
            ItemQuality current = qualities.get(i);
            ItemQuality next = qualities.get(i + 1);
            
            if (distribution.containsKey(current) && distribution.get(current) > 0) {
                double shiftValue = distribution.get(current) * shiftAmount * 0.3;
                distribution.put(current, distribution.get(current) - shiftValue);
                distribution.put(next, distribution.getOrDefault(next, 0.0) + shiftValue);
            }
        }
    }
    
    /**
     * Select quality using weighted random selection
     */
    private static ItemQuality selectWeightedRandom(Map<ItemQuality, Double> distribution, Random random) {
        double total = distribution.values().stream().mapToDouble(Double::doubleValue).sum();
        double randomValue = random.nextDouble() * total;
        
        double cumulative = 0;
        for (Map.Entry<ItemQuality, Double> entry : distribution.entrySet()) {
            cumulative += entry.getValue();
            if (randomValue <= cumulative) {
                return entry.getKey();
            }
        }
        
        return STANDARD; // Fallback
    }
    
    // Getters
    public String getColorCode() { return colorCode; }
    public String getDisplayName() { return displayName; }
    public double getDurabilityMultiplier() { return durabilityMultiplier; }
    public double getEnchantChance() { return enchantChance; }
    public boolean shouldBroadcast() { return broadcastDrop; }
    public Sound getDropSound() { return dropSound; }
    
    /**
     * Helper class for quality distribution
     */
    private static class QualityDistribution {
        private final Map<ItemQuality, Integer> weights = new HashMap<>();
        private int totalWeight = 0;
        
        public QualityDistribution add(ItemQuality quality, int weight) {
            weights.put(quality, weight);
            totalWeight += weight;
            return this;
        }
        
        public ItemQuality rollQuality(double multiplier) {
            // Higher multiplier increases chances of better quality
            int roll = RANDOM.nextInt(totalWeight);
            int currentWeight = 0;
            
            // If multiplier > 1, bias towards higher qualities
            if (multiplier > 1.0) {
                roll = (int) (roll * (2.0 - multiplier)); // This biases towards lower rolls = higher qualities
            }
            
            for (Map.Entry<ItemQuality, Integer> entry : weights.entrySet()) {
                currentWeight += entry.getValue();
                if (roll < currentWeight) {
                    return entry.getKey();
                }
            }
            
            // Fallback
            return STANDARD;
        }
    }
} 