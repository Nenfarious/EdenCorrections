package dev.lsdmc.edencorrections.managers.loot;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Predicate;

/**
 * Advanced loot pool system with conditional entries, weights, and quality modifiers
 */
public class LootPool {
    private final String name;
    private final int rolls; // Number of items to generate from this pool
    private final int bonusRolls; // Additional rolls based on context
    private final List<LootEntry> entries = new ArrayList<>();
    private final List<Predicate<LootContext>> conditions = new ArrayList<>();
    private final Map<String, Double> contextMultipliers = new HashMap<>();
    
    public LootPool(String name, int rolls) {
        this.name = name;
        this.rolls = rolls;
        this.bonusRolls = 0;
    }
    
    public LootPool(String name, int rolls, int bonusRolls) {
        this.name = name;
        this.rolls = rolls;
        this.bonusRolls = bonusRolls;
    }
    
    /**
     * Add a loot entry to this pool
     */
    public LootPool addEntry(LootEntry entry) {
        entries.add(entry);
        return this;
    }
    
    /**
     * Add a simple material entry with weight
     */
    public LootPool addEntry(Material material, int minAmount, int maxAmount, double weight) {
        return addEntry(new LootEntry(material, minAmount, maxAmount, weight));
    }
    
    /**
     * Add a condition for when this pool should be used
     */
    public LootPool addCondition(Predicate<LootContext> condition) {
        conditions.add(condition);
        return this;
    }
    
    /**
     * Add context-based multiplier
     */
    public LootPool addContextMultiplier(String contextKey, double multiplier) {
        contextMultipliers.put(contextKey, multiplier);
        return this;
    }
    
    /**
     * Generate loot from this pool
     */
    public List<ItemStack> generateLoot(LootContext context, Random random) {
        List<ItemStack> result = new ArrayList<>();
        
        // Check conditions
        for (Predicate<LootContext> condition : conditions) {
            if (!condition.test(context)) {
                return result; // Empty list if conditions not met
            }
        }
        
        // Calculate actual rolls with bonuses
        int actualRolls = rolls;
        
        // Add bonus rolls based on context
        if (context.getDutyTimeMinutes() >= 60) actualRolls += 1; // Long duty bonus
        if (context.getArrestsThisSession() >= 5) actualRolls += 1; // Active guard bonus
        if (context.isSpecialEvent()) actualRolls += bonusRolls;
        
        // Apply context multipliers
        double totalMultiplier = 1.0;
        for (Map.Entry<String, Double> mult : contextMultipliers.entrySet()) {
            if (hasContextFlag(context, mult.getKey())) {
                totalMultiplier *= mult.getValue();
            }
        }
        
        // Generate items
        for (int i = 0; i < actualRolls; i++) {
            LootEntry selectedEntry = selectWeightedEntry(context, random);
            if (selectedEntry != null) {
                ItemStack item = selectedEntry.generateItem(context, random);
                if (item != null) {
                    // Apply total multiplier to quantity
                    int newAmount = (int) Math.max(1, item.getAmount() * totalMultiplier);
                    item.setAmount(Math.min(newAmount, item.getMaxStackSize()));
                    result.add(item);
                }
            }
        }
        
        return result;
    }
    
    /**
     * Select a weighted entry from this pool
     */
    private LootEntry selectWeightedEntry(LootContext context, Random random) {
        if (entries.isEmpty()) return null;
        
        // Calculate total weight with context modifications
        double totalWeight = 0.0;
        List<Double> weights = new ArrayList<>();
        
        for (LootEntry entry : entries) {
            double weight = entry.getContextualWeight(context);
            weights.add(weight);
            totalWeight += weight;
        }
        
        if (totalWeight <= 0) return null;
        
        // Select random entry
        double randomValue = random.nextDouble() * totalWeight;
        double currentWeight = 0.0;
        
        for (int i = 0; i < entries.size(); i++) {
            currentWeight += weights.get(i);
            if (randomValue <= currentWeight) {
                return entries.get(i);
            }
        }
        
        return entries.get(entries.size() - 1); // Fallback
    }
    
    /**
     * Check if context has a specific flag
     */
    private boolean hasContextFlag(LootContext context, String flag) {
        return switch (flag.toLowerCase()) {
            case "pvp" -> context.isInPvpArea();
            case "event" -> context.isSpecialEvent();
            case "onduty" -> context.wasOnDuty();
            case "immobilized" -> context.wasImmobilized();
            case "loneguard" -> context.getNearbyGuards() <= 1;
            case "outnumbered" -> context.getNearbyPrisoners() > context.getNearbyGuards() * 2;
            default -> false;
        };
    }
    
    // Getters
    public String getName() { return name; }
    public int getRolls() { return rolls; }
    public List<LootEntry> getEntries() { return entries; }
    
    /**
     * Individual loot entry within a pool
     */
    public static class LootEntry {
        private final Material material;
        private final int minAmount;
        private final int maxAmount;
        private final double baseWeight;
        private final Map<String, Double> contextWeightModifiers = new HashMap<>();
        private final List<Predicate<LootContext>> conditions = new ArrayList<>();
        private ItemQuality forcedQuality = null;
        private boolean respectsQuality = true;
        
        public LootEntry(Material material, int minAmount, int maxAmount, double weight) {
            this.material = material;
            this.minAmount = minAmount;
            this.maxAmount = maxAmount;
            this.baseWeight = weight;
        }
        
        /**
         * Set a forced quality for this entry
         */
        public LootEntry setQuality(ItemQuality quality) {
            this.forcedQuality = quality;
            return this;
        }
        
        /**
         * Set whether this entry respects the quality system
         */
        public LootEntry respectsQuality(boolean respects) {
            this.respectsQuality = respects;
            return this;
        }
        
        /**
         * Add weight modifier based on context
         */
        public LootEntry addWeightModifier(String contextKey, double modifier) {
            contextWeightModifiers.put(contextKey, modifier);
            return this;
        }
        
        /**
         * Add condition for this entry
         */
        public LootEntry addCondition(Predicate<LootContext> condition) {
            conditions.add(condition);
            return this;
        }
        
        /**
         * Get contextual weight for this entry
         */
        public double getContextualWeight(LootContext context) {
            // Check conditions first
            for (Predicate<LootContext> condition : conditions) {
                if (!condition.test(context)) {
                    return 0.0; // Entry not available
                }
            }
            
            double weight = baseWeight;
            
            // Apply context modifiers
            for (Map.Entry<String, Double> modifier : contextWeightModifiers.entrySet()) {
                String key = modifier.getKey().toLowerCase();
                double multiplier = modifier.getValue();
                
                boolean applies = switch (key) {
                    case "highrankguard" -> isHighRank(context.getGuardRank());
                    case "longduty" -> context.getDutyTimeMinutes() >= 60;
                    case "activeguard" -> context.getArrestsThisSession() >= 3;
                    case "pvparea" -> context.isInPvpArea();
                    case "specialevent" -> context.isSpecialEvent();
                    case "firstdeath" -> context.getTimeSinceLastDeath() >= 3600; // 1 hour
                    case "loneguard" -> context.getNearbyGuards() <= 1;
                    default -> false;
                };
                
                if (applies) {
                    weight *= multiplier;
                }
            }
            
            return Math.max(0.0, weight);
        }
        
        /**
         * Generate item from this entry
         */
        public ItemStack generateItem(LootContext context, Random random) {
            // Determine amount
            int amount = minAmount;
            if (maxAmount > minAmount) {
                amount = minAmount + random.nextInt(maxAmount - minAmount + 1);
            }
            
            ItemStack item = new ItemStack(material, amount);
            
            // Apply quality if enabled
            if (respectsQuality) {
                ItemQuality quality = forcedQuality;
                if (quality == null) {
                    quality = ItemQuality.selectQuality(context.getGuardRank(), context, random);
                }
                
                String lootContext = "Guard Death: " + context.getGuardRank() + " rank";
                item = quality.enhanceItem(item, lootContext, context.getVictim());
            }
            
            return item;
        }
        
        private boolean isHighRank(String rank) {
            return List.of("sergeant", "warden").contains(rank.toLowerCase());
        }
        
        // Getters
        public Material getMaterial() { return material; }
        public double getBaseWeight() { return baseWeight; }
    }
} 