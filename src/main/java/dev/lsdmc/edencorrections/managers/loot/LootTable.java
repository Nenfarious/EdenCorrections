package dev.lsdmc.edencorrections.managers.loot;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;

/**
 * Modern loot table system with conditional logic and flexible generation
 */
public class LootTable {
    private final String id;
    private final String name;
    private final List<InternalLootPool> pools = new ArrayList<>();
    private final List<Predicate<LootContext>> conditions = new ArrayList<>();
    private final Random random = new Random();
    
    public LootTable(String id, String name) {
        this.id = id;
        this.name = name;
    }
    
    /**
     * Add a loot pool to this table
     */
    public LootTable addPool(InternalLootPool pool) {
        pools.add(pool);
        return this;
    }
    
    /**
     * Add a condition that must be met for this table to be used
     */
    public LootTable addCondition(Predicate<LootContext> condition) {
        conditions.add(condition);
        return this;
    }
    
    /**
     * Check if this loot table can be used for the given context
     */
    public boolean canUse(LootContext context) {
        return conditions.stream().allMatch(condition -> condition.test(context));
    }
    
    /**
     * Generate loot for the given context
     */
    public List<ItemStack> generateLoot(LootContext context) {
        if (!canUse(context)) {
            return new ArrayList<>();
        }
        
        List<ItemStack> loot = new ArrayList<>();
        
        for (InternalLootPool pool : pools) {
            loot.addAll(pool.generateLoot(context, random));
        }
        
        return loot;
    }
    
    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public List<InternalLootPool> getPools() { return pools; }
    
    /**
     * Builder for creating loot tables
     */
    public static class Builder {
        private final String id;
        private final String name;
        private final List<InternalLootPool> pools = new ArrayList<>();
        private final List<Predicate<LootContext>> conditions = new ArrayList<>();
        
        public Builder(String id, String name) {
            this.id = id;
            this.name = name;
        }
        
        public Builder pool(InternalLootPool pool) {
            pools.add(pool);
            return this;
        }
        
        public Builder condition(Predicate<LootContext> condition) {
            conditions.add(condition);
            return this;
        }
        
        // Convenience condition methods
        public Builder requireRank(String rank) {
            return condition(ctx -> rank.equalsIgnoreCase(ctx.getGuardRank()));
        }
        
        public Builder requireMinRank(String minRank) {
            return condition(ctx -> {
                String[] ranks = {"trainee", "private", "officer", "sergeant", "warden"};
                int playerRankIndex = -1;
                int minRankIndex = -1;
                
                for (int i = 0; i < ranks.length; i++) {
                    if (ranks[i].equalsIgnoreCase(ctx.getGuardRank())) playerRankIndex = i;
                    if (ranks[i].equalsIgnoreCase(minRank)) minRankIndex = i;
                }
                
                return playerRankIndex >= minRankIndex;
            });
        }
        
        public Builder requireOnDuty() {
            return condition(LootContext::wasOnDuty);
        }
        
        public Builder requireRegion(String region) {
            return condition(ctx -> region.equalsIgnoreCase(ctx.getRegionName()));
        }
        
        public Builder requirePvpArea() {
            return condition(LootContext::isInPvpArea);
        }
        
        public Builder requireDamageCause(org.bukkit.event.entity.EntityDamageEvent.DamageCause cause) {
            return condition(ctx -> ctx.getDamageCause() == cause);
        }
        
        public Builder requireMinDutyTime(int minutes) {
            return condition(ctx -> ctx.getDutyTimeMinutes() >= minutes);
        }
        
        public Builder requireSpecialEvent() {
            return condition(LootContext::isSpecialEvent);
        }
        
        public LootTable build() {
            LootTable table = new LootTable(id, name);
            pools.forEach(table::addPool);
            conditions.forEach(table::addCondition);
            return table;
        }
    }
}

/**
 * A pool of potential loot within a loot table
 */
class InternalLootPool {
    private final String id;
    private final int minRolls;
    private final int maxRolls;
    private final List<LootEntry> entries = new ArrayList<>();
    private final List<Predicate<LootContext>> conditions = new ArrayList<>();
    
    public InternalLootPool(String id, int minRolls, int maxRolls) {
        this.id = id;
        this.minRolls = minRolls;
        this.maxRolls = maxRolls;
    }
    
    public InternalLootPool addEntry(LootEntry entry) {
        entries.add(entry);
        return this;
    }
    
    public InternalLootPool addCondition(Predicate<LootContext> condition) {
        conditions.add(condition);
        return this;
    }
    
    public List<ItemStack> generateLoot(LootContext context, Random random) {
        if (!conditions.stream().allMatch(condition -> condition.test(context))) {
            return new ArrayList<>();
        }
        
        List<ItemStack> loot = new ArrayList<>();
        int rolls = random.nextInt(maxRolls - minRolls + 1) + minRolls;
        
        // Calculate total weight
        int totalWeight = entries.stream()
            .filter(entry -> entry.canGenerate(context))
            .mapToInt(LootEntry::getWeight)
            .sum();
        
        if (totalWeight == 0) {
            return loot;
        }
        
        for (int i = 0; i < rolls; i++) {
            int roll = random.nextInt(totalWeight);
            int currentWeight = 0;
            
            for (LootEntry entry : entries) {
                if (!entry.canGenerate(context)) continue;
                
                currentWeight += entry.getWeight();
                if (roll < currentWeight) {
                    ItemStack item = entry.generateItem(context, random);
                    if (item != null) {
                        loot.add(item);
                    }
                    break;
                }
            }
        }
        
        return loot;
    }
    
    // Getters
    public String getId() { return id; }
    public int getMinRolls() { return minRolls; }
    public int getMaxRolls() { return maxRolls; }
    
    /**
     * Builder for creating loot pools
     */
    public static class Builder {
        private final String id;
        private final int minRolls;
        private final int maxRolls;
        private final List<LootEntry> entries = new ArrayList<>();
        private final List<Predicate<LootContext>> conditions = new ArrayList<>();
        
        public Builder(String id, int minRolls, int maxRolls) {
            this.id = id;
            this.minRolls = minRolls;
            this.maxRolls = maxRolls;
        }
        
        public Builder entry(LootEntry entry) {
            entries.add(entry);
            return this;
        }
        
        public Builder condition(Predicate<LootContext> condition) {
            conditions.add(condition);
            return this;
        }
        
        public InternalLootPool build() {
            InternalLootPool pool = new InternalLootPool(id, minRolls, maxRolls);
            entries.forEach(pool::addEntry);
            conditions.forEach(pool::addCondition);
            return pool;
        }
    }
}

/**
 * A single potential loot item within a pool
 */
abstract class LootEntry {
    protected final String id;
    protected final int weight;
    protected final List<Predicate<LootContext>> conditions = new ArrayList<>();
    
    public LootEntry(String id, int weight) {
        this.id = id;
        this.weight = weight;
    }
    
    public LootEntry addCondition(Predicate<LootContext> condition) {
        conditions.add(condition);
        return this;
    }
    
    public boolean canGenerate(LootContext context) {
        return conditions.stream().allMatch(condition -> condition.test(context));
    }
    
    public abstract ItemStack generateItem(LootContext context, Random random);
    
    public String getId() { return id; }
    public int getWeight() { return weight; }
}

/**
 * A loot entry for a specific material
 */
class MaterialLootEntry extends LootEntry {
    private final Material material;
    private final int minAmount;
    private final int maxAmount;
    private final boolean useQuality;
    
    public MaterialLootEntry(String id, int weight, Material material, int minAmount, int maxAmount, boolean useQuality) {
        super(id, weight);
        this.material = material;
        this.minAmount = minAmount;
        this.maxAmount = maxAmount;
        this.useQuality = useQuality;
    }
    
    @Override
    public ItemStack generateItem(LootContext context, Random random) {
        int amount = random.nextInt(maxAmount - minAmount + 1) + minAmount;
        ItemStack item = new ItemStack(material, amount);
        
        if (useQuality) {
            ItemQuality quality = ItemQuality.selectQuality(context.getGuardRank(), context, random);
            String lootContext = "Guard Death: " + context.getGuardRank() + " rank";
            item = quality.enhanceItem(item, lootContext, context.getVictim());
        }
        
        return item;
    }
    
    /**
     * Builder for material loot entries
     */
    public static class Builder {
        private final String id;
        private final int weight;
        private final Material material;
        private int minAmount = 1;
        private int maxAmount = 1;
        private boolean useQuality = true;
        private final List<Predicate<LootContext>> conditions = new ArrayList<>();
        
        public Builder(String id, int weight, Material material) {
            this.id = id;
            this.weight = weight;
            this.material = material;
        }
        
        public Builder amount(int amount) {
            this.minAmount = amount;
            this.maxAmount = amount;
            return this;
        }
        
        public Builder amount(int min, int max) {
            this.minAmount = min;
            this.maxAmount = max;
            return this;
        }
        
        public Builder withQuality(boolean useQuality) {
            this.useQuality = useQuality;
            return this;
        }
        
        public Builder condition(Predicate<LootContext> condition) {
            conditions.add(condition);
            return this;
        }
        
        public MaterialLootEntry build() {
            MaterialLootEntry entry = new MaterialLootEntry(id, weight, material, minAmount, maxAmount, useQuality);
            conditions.forEach(entry::addCondition);
            return entry;
        }
    }
}

/**
 * A loot entry that generates custom items
 */
class CustomLootEntry extends LootEntry {
    private final LootItemProvider provider;
    
    public CustomLootEntry(String id, int weight, LootItemProvider provider) {
        super(id, weight);
        this.provider = provider;
    }
    
    @Override
    public ItemStack generateItem(LootContext context, Random random) {
        return provider.generateItem(context, random);
    }
    
    /**
     * Interface for custom item providers
     */
    @FunctionalInterface
    public interface LootItemProvider {
        ItemStack generateItem(LootContext context, Random random);
    }
} 