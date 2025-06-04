package dev.lsdmc.edencorrections.managers.loot;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Rich context information for loot generation
 * Allows for sophisticated conditional loot based on circumstances
 */
public class LootContext {
    // Core entities
    private final Player victim;
    private final Player killer;
    private final Location deathLocation;
    
    // Death circumstances
    private final EntityDamageEvent.DamageCause damageCause;
    private final long fightDuration; // How long the fight lasted
    private final boolean wasOnDuty;
    private final boolean wasImmobilized;
    
    // Guard information
    private final String guardRank;
    private final int dutyTimeMinutes;
    private final int arrestsThisSession;
    private final int timeSinceLastDeath;
    
    // Environmental factors
    private final String regionName;
    private final boolean isInPvpArea;
    private final int nearbyGuards;
    private final int nearbyPrisoners;
    
    // Special modifiers
    private final Map<String, Object> customModifiers = new HashMap<>();
    private final boolean isSpecialEvent;
    private final double lootMultiplier;
    
    private LootContext(Builder builder) {
        this.victim = builder.victim;
        this.killer = builder.killer;
        this.deathLocation = builder.deathLocation;
        this.damageCause = builder.damageCause;
        this.fightDuration = builder.fightDuration;
        this.wasOnDuty = builder.wasOnDuty;
        this.wasImmobilized = builder.wasImmobilized;
        this.guardRank = builder.guardRank;
        this.dutyTimeMinutes = builder.dutyTimeMinutes;
        this.arrestsThisSession = builder.arrestsThisSession;
        this.timeSinceLastDeath = builder.timeSinceLastDeath;
        this.regionName = builder.regionName;
        this.isInPvpArea = builder.isInPvpArea;
        this.nearbyGuards = builder.nearbyGuards;
        this.nearbyPrisoners = builder.nearbyPrisoners;
        this.isSpecialEvent = builder.isSpecialEvent;
        this.lootMultiplier = builder.lootMultiplier;
        this.customModifiers.putAll(builder.customModifiers);
    }
    
    // Getters
    public Player getVictim() { return victim; }
    public Player getKiller() { return killer; }
    public Location getDeathLocation() { return deathLocation; }
    public EntityDamageEvent.DamageCause getDamageCause() { return damageCause; }
    public long getFightDuration() { return fightDuration; }
    public boolean wasOnDuty() { return wasOnDuty; }
    public boolean wasImmobilized() { return wasImmobilized; }
    public String getGuardRank() { return guardRank; }
    public int getDutyTimeMinutes() { return dutyTimeMinutes; }
    public int getArrestsThisSession() { return arrestsThisSession; }
    public int getTimeSinceLastDeath() { return timeSinceLastDeath; }
    public String getRegionName() { return regionName; }
    public boolean isInPvpArea() { return isInPvpArea; }
    public int getNearbyGuards() { return nearbyGuards; }
    public int getNearbyPrisoners() { return nearbyPrisoners; }
    public boolean isSpecialEvent() { return isSpecialEvent; }
    public double getLootMultiplier() { return lootMultiplier; }
    public Map<String, Object> getCustomModifiers() { return customModifiers; }
    
    @SuppressWarnings("unchecked")
    public <T> T getCustomModifier(String key, Class<T> type) {
        Object value = customModifiers.get(key);
        return type.isInstance(value) ? (T) value : null;
    }
    
    /**
     * Builder pattern for creating LootContext
     */
    public static class Builder {
        private Player victim;
        private Player killer;
        private Location deathLocation;
        private EntityDamageEvent.DamageCause damageCause;
        private long fightDuration = 0;
        private boolean wasOnDuty = false;
        private boolean wasImmobilized = false;
        private String guardRank = "trainee";
        private int dutyTimeMinutes = 0;
        private int arrestsThisSession = 0;
        private int timeSinceLastDeath = 0;
        private String regionName = "unknown";
        private boolean isInPvpArea = false;
        private int nearbyGuards = 0;
        private int nearbyPrisoners = 0;
        private boolean isSpecialEvent = false;
        private double lootMultiplier = 1.0;
        private final Map<String, Object> customModifiers = new HashMap<>();
        
        public Builder victim(Player victim) { this.victim = victim; return this; }
        public Builder killer(Player killer) { this.killer = killer; return this; }
        public Builder location(Location location) { this.deathLocation = location; return this; }
        public Builder damageCause(EntityDamageEvent.DamageCause cause) { this.damageCause = cause; return this; }
        public Builder fightDuration(long duration) { this.fightDuration = duration; return this; }
        public Builder wasOnDuty(boolean onDuty) { this.wasOnDuty = onDuty; return this; }
        public Builder wasImmobilized(boolean immobilized) { this.wasImmobilized = immobilized; return this; }
        public Builder guardRank(String rank) { this.guardRank = rank; return this; }
        public Builder dutyTime(int minutes) { this.dutyTimeMinutes = minutes; return this; }
        public Builder arrests(int arrests) { this.arrestsThisSession = arrests; return this; }
        public Builder timeSinceLastDeath(int seconds) { this.timeSinceLastDeath = seconds; return this; }
        public Builder region(String region) { this.regionName = region; return this; }
        public Builder inPvpArea(boolean inPvp) { this.isInPvpArea = inPvp; return this; }
        public Builder nearbyGuards(int count) { this.nearbyGuards = count; return this; }
        public Builder nearbyPrisoners(int count) { this.nearbyPrisoners = count; return this; }
        public Builder specialEvent(boolean special) { this.isSpecialEvent = special; return this; }
        public Builder lootMultiplier(double multiplier) { this.lootMultiplier = multiplier; return this; }
        public Builder customModifier(String key, Object value) { this.customModifiers.put(key, value); return this; }
        
        public LootContext build() {
            if (victim == null) throw new IllegalStateException("Victim cannot be null");
            if (deathLocation == null) throw new IllegalStateException("Death location cannot be null");
            return new LootContext(this);
        }
    }
} 