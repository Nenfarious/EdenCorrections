package dev.lsdmc.edencorrections.managers;

import dev.lsdmc.edencorrections.EdenCorrections;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.NamespacedKey;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class ExternalPluginIntegration {
    private final EdenCorrections plugin;
    
    // ExecutableItems integration
    private Plugin executableItemsPlugin;
    private boolean executableItemsEnabled;
    private Object executableItemsManager;
    private Method getExecutableItemMethod;
    private Method isValidIDMethod;
    
    // Configuration for drug and contraband items
    private final Set<String> drugItemIds = new HashSet<>();
    private final Set<String> contrabandItemIds = new HashSet<>();
    private final Set<PotionEffectType> drugEffectTypes = new HashSet<>();

    public ExternalPluginIntegration(EdenCorrections plugin) {
        this.plugin = plugin;
        loadIntegrations();
    }

    private void loadIntegrations() {
        loadExecutableItemsIntegration();
        loadDrugAndContrabandConfig();
    }

    /**
     * Load ExecutableItems integration
     */
    private void loadExecutableItemsIntegration() {
        executableItemsEnabled = false;
        
        if (!plugin.getConfig().getBoolean("executable-items.enabled", true)) {
            plugin.getLogger().info("ExecutableItems integration disabled in config");
            return;
        }

        executableItemsPlugin = Bukkit.getPluginManager().getPlugin("ExecutableItems");
        if (executableItemsPlugin == null || !executableItemsPlugin.isEnabled()) {
            plugin.getLogger().info("ExecutableItems plugin not found or not enabled");
            return;
        }

        try {
            // Get the ExecutableItemsAPI class
            Class<?> executableItemsAPIClass = Class.forName("com.ssomar.score.api.executableitems.ExecutableItemsAPI");
            Method getManagerMethod = executableItemsAPIClass.getMethod("getExecutableItemsManager");
            executableItemsManager = getManagerMethod.invoke(null);
            
            // Get the manager methods
            Class<?> managerClass = executableItemsManager.getClass();
            getExecutableItemMethod = managerClass.getMethod("getExecutableItem", ItemStack.class);
            isValidIDMethod = managerClass.getMethod("isValidID", String.class);
            
            executableItemsEnabled = true;
            plugin.getLogger().info("Successfully hooked into ExecutableItems!");
            
            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().info("ExecutableItems API methods loaded successfully");
            }
            
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to hook into ExecutableItems: " + e.getMessage());
            if (plugin.getConfigManager().isDebugEnabled()) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Load drug and contraband configuration
     */
    private void loadDrugAndContrabandConfig() {
        drugItemIds.clear();
        contrabandItemIds.clear();
        drugEffectTypes.clear();
        
        // Load drug ExecutableItems IDs
        List<String> configDrugIds = plugin.getConfig().getStringList("executable-items.drug-items");
        drugItemIds.addAll(configDrugIds);
        
        // Load contraband ExecutableItems IDs
        List<String> configContrabandIds = plugin.getConfig().getStringList("executable-items.contraband-items");
        contrabandItemIds.addAll(configContrabandIds);
        
        // Load drug effect types for sobriety testing
        List<String> configDrugEffects = plugin.getConfig().getStringList("executable-items.drug-effects");
        for (String effectName : configDrugEffects) {
            try {
                PotionEffectType effectType = PotionEffectType.getByName(effectName.toUpperCase());
                if (effectType != null) {
                    drugEffectTypes.add(effectType);
                } else {
                    plugin.getLogger().warning("Invalid drug effect type: " + effectName);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Error loading drug effect type '" + effectName + "': " + e.getMessage());
            }
        }
        
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("Loaded " + drugItemIds.size() + " drug item IDs");
            plugin.getLogger().info("Loaded " + contrabandItemIds.size() + " contraband item IDs");
            plugin.getLogger().info("Loaded " + drugEffectTypes.size() + " drug effect types");
        }
    }

    /**
     * Check if an item is a drug ExecutableItem
     * @param item The item to check
     * @return true if the item is a configured drug ExecutableItem
     */
    public boolean isDrugItem(ItemStack item) {
        if (!executableItemsEnabled || item == null) {
            return false;
        }
        
        try {
            // Get the ExecutableItem from the ItemStack
            Object executableItemOptional = getExecutableItemMethod.invoke(executableItemsManager, item);
            
            // Check if Optional is present (using reflection)
            Method isPresentMethod = executableItemOptional.getClass().getMethod("isPresent");
            boolean isPresent = (boolean) isPresentMethod.invoke(executableItemOptional);
            
            if (!isPresent) {
                return false;
            }
            
            // Get the ExecutableItem
            Method getMethod = executableItemOptional.getClass().getMethod("get");
            Object executableItem = getMethod.invoke(executableItemOptional);
            
            // Get the ID of the ExecutableItem
            Method getIdMethod = executableItem.getClass().getMethod("getId");
            String itemId = (String) getIdMethod.invoke(executableItem);
            
            boolean isDrug = drugItemIds.contains(itemId);
            
            if (plugin.getConfigManager().isDebugEnabled() && isDrug) {
                plugin.getLogger().info("Detected drug ExecutableItem: " + itemId);
            }
            
            return isDrug;
            
        } catch (Exception e) {
            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().warning("Error checking if item is drug ExecutableItem: " + e.getMessage());
            }
            return false;
        }
    }

    /**
     * Check if an item is a contraband ExecutableItem
     * @param item The item to check
     * @return true if the item is a configured contraband ExecutableItem
     */
    public boolean isContraband(ItemStack item) {
        if (!executableItemsEnabled || item == null) {
            return false;
        }
        
        try {
            // Get the ExecutableItem from the ItemStack
            Object executableItemOptional = getExecutableItemMethod.invoke(executableItemsManager, item);
            
            // Check if Optional is present
            Method isPresentMethod = executableItemOptional.getClass().getMethod("isPresent");
            boolean isPresent = (boolean) isPresentMethod.invoke(executableItemOptional);
            
            if (!isPresent) {
                return false;
            }
            
            // Get the ExecutableItem
            Method getMethod = executableItemOptional.getClass().getMethod("get");
            Object executableItem = getMethod.invoke(executableItemOptional);
            
            // Get the ID of the ExecutableItem
            Method getIdMethod = executableItem.getClass().getMethod("getId");
            String itemId = (String) getIdMethod.invoke(executableItem);
            
            boolean isContraband = contrabandItemIds.contains(itemId) || drugItemIds.contains(itemId);
            
            if (plugin.getConfigManager().isDebugEnabled() && isContraband) {
                plugin.getLogger().info("Detected contraband ExecutableItem: " + itemId);
            }
            
            return isContraband;
            
        } catch (Exception e) {
            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().warning("Error checking if item is contraband ExecutableItem: " + e.getMessage());
            }
            return false;
        }
    }

    /**
     * Comprehensive contraband check that includes ExecutableItems, manual tags, and legacy detection
     * This is the main method that should be used by the guard systems
     * @param item The item to check
     * @return true if the item is contraband by any detection method
     */
    public boolean isContrabandComprehensive(ItemStack item) {
        if (item == null) return false;
        
        // 1. Check ExecutableItems integration (highest priority)
        if (executableItemsEnabled && isContraband(item)) {
            return true;
        }
        
        // 2. Check manual contraband tags (secondary priority)
        if (plugin.getContrabandManager() != null && plugin.getContrabandManager().isContraband(item)) {
            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().info("Detected manually tagged contraband item");
            }
            return true;
        }
        
        // 3. Check legacy NBT tags (for backward compatibility)
        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            PersistentDataContainer container = meta.getPersistentDataContainer();
            
            NamespacedKey contrabandKey = new NamespacedKey(plugin, "contraband");
            NamespacedKey drugKey = new NamespacedKey(plugin, "drug");
            
            if (container.has(contrabandKey, PersistentDataType.BYTE) ||
                container.has(drugKey, PersistentDataType.BYTE)) {
                if (plugin.getConfigManager().isDebugEnabled()) {
                    plugin.getLogger().info("Detected legacy tagged contraband/drug item");
                }
                return true;
            }
        }
        
        // 4. Legacy keyword-based detection (lowest priority, fallback)
        if (plugin.getConfig().getBoolean("jail.use-legacy-keyword-detection", false)) {
            String name = item.hasItemMeta() && item.getItemMeta().hasDisplayName() ? 
                item.getItemMeta().getDisplayName().toLowerCase() : 
                item.getType().toString().toLowerCase();
            
            List<String> contrabandKeywords = List.of("contraband", "illegal", "weapon", "shank", "cell phone", "burner");
            for (String keyword : contrabandKeywords) {
                if (name.contains(keyword)) {
                    if (plugin.getConfigManager().isDebugEnabled()) {
                        plugin.getLogger().info("Detected keyword-based contraband item: " + keyword);
                    }
                    return true;
                }
            }
        }
        
        return false;
    }

    /**
     * Comprehensive drug check that includes ExecutableItems, manual tags, and legacy detection
     * @param item The item to check
     * @return true if the item is a drug by any detection method
     */
    public boolean isDrugComprehensive(ItemStack item) {
        if (item == null) return false;
        
        // 1. Check ExecutableItems integration (highest priority)
        if (executableItemsEnabled && isDrugItem(item)) {
            return true;
        }
        
        // 2. Check manual contraband tags for drug type (secondary priority)
        if (plugin.getContrabandManager() != null) {
            ContrabandManager.ContrabandType type = plugin.getContrabandManager().getContrabandType(item);
            if (type == ContrabandManager.ContrabandType.DRUG) {
                if (plugin.getConfigManager().isDebugEnabled()) {
                    plugin.getLogger().info("Detected manually tagged drug item");
                }
                return true;
            }
        }
        
        // 3. Check legacy NBT tags (for backward compatibility)
        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            PersistentDataContainer container = meta.getPersistentDataContainer();
            
            NamespacedKey drugKey = new NamespacedKey(plugin, "drug");
            if (container.has(drugKey, PersistentDataType.BYTE)) {
                if (plugin.getConfigManager().isDebugEnabled()) {
                    plugin.getLogger().info("Detected legacy tagged drug item");
                }
                return true;
            }
        }
        
        // 4. Legacy keyword-based detection (lowest priority, fallback)
        if (plugin.getConfig().getBoolean("jail.use-legacy-keyword-detection", false)) {
            String name = item.hasItemMeta() && item.getItemMeta().hasDisplayName() ? 
                item.getItemMeta().getDisplayName().toLowerCase() : 
                item.getType().toString().toLowerCase();
            
            List<String> drugKeywords = List.of("drug", "pill", "powder", "joint", "syringe");
            for (String keyword : drugKeywords) {
                if (name.contains(keyword)) {
                    if (plugin.getConfigManager().isDebugEnabled()) {
                        plugin.getLogger().info("Detected keyword-based drug item: " + keyword);
                    }
                    return true;
                }
            }
        }
        
        return false;
    }

    /**
     * Check if a player is under the influence of drugs (has drug effects)
     * @param player The player to check
     * @return true if the player has drug effects
     */
    public boolean isUnderInfluence(Player player) {
        if (!executableItemsEnabled || player == null) {
            return false;
        }
        
        // Check if player has any of the configured drug effects
        for (PotionEffect effect : player.getActivePotionEffects()) {
            if (drugEffectTypes.contains(effect.getType())) {
                if (plugin.getConfigManager().isDebugEnabled()) {
                    plugin.getLogger().info("Player " + player.getName() + " is under influence of " + effect.getType().getName());
                }
                return true;
            }
        }
        
        return false;
    }

    /**
     * Check if an ExecutableItem ID is configured as a drug
     * @param itemId The ExecutableItem ID to check
     * @return true if the ID is configured as a drug
     */
    public boolean isDrugItemId(String itemId) {
        return drugItemIds.contains(itemId);
    }

    /**
     * Check if an ExecutableItem ID is configured as contraband
     * @param itemId The ExecutableItem ID to check
     * @return true if the ID is configured as contraband
     */
    public boolean isContrabandItemId(String itemId) {
        return contrabandItemIds.contains(itemId) || drugItemIds.contains(itemId);
    }

    /**
     * Check if ExecutableItems integration is enabled and working
     */
    public boolean isExecutableItemsEnabled() {
        return executableItemsEnabled;
    }

    /**
     * Check if drug detection is enabled and working
     */
    public boolean isDrugDetectionEnabled() {
        return executableItemsEnabled && !drugItemIds.isEmpty();
    }

    /**
     * Check if contraband detection is enabled and working
     */
    public boolean isContrabandDetectionEnabled() {
        return executableItemsEnabled && (!contrabandItemIds.isEmpty() || !drugItemIds.isEmpty());
    }

    /**
     * Get the ExecutableItem ID from an ItemStack
     * @param item The ItemStack to check
     * @return The ExecutableItem ID, or null if not an ExecutableItem
     */
    public String getExecutableItemId(ItemStack item) {
        if (!executableItemsEnabled || item == null) {
            return null;
        }
        
        try {
            // Get the ExecutableItem from the ItemStack
            Object executableItemOptional = getExecutableItemMethod.invoke(executableItemsManager, item);
            
            // Check if Optional is present
            Method isPresentMethod = executableItemOptional.getClass().getMethod("isPresent");
            boolean isPresent = (boolean) isPresentMethod.invoke(executableItemOptional);
            
            if (!isPresent) {
                return null;
            }
            
            // Get the ExecutableItem
            Method getMethod = executableItemOptional.getClass().getMethod("get");
            Object executableItem = getMethod.invoke(executableItemOptional);
            
            // Get the ID of the ExecutableItem
            Method getIdMethod = executableItem.getClass().getMethod("getId");
            return (String) getIdMethod.invoke(executableItem);
            
        } catch (Exception e) {
            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().warning("Error getting ExecutableItem ID: " + e.getMessage());
            }
            return null;
        }
    }

    /**
     * Get all configured drug item IDs
     * @return Set of drug ExecutableItem IDs
     */
    public Set<String> getDrugItemIds() {
        return new HashSet<>(drugItemIds);
    }

    /**
     * Get all configured contraband item IDs
     * @return Set of contraband ExecutableItem IDs
     */
    public Set<String> getContrabandItemIds() {
        return new HashSet<>(contrabandItemIds);
    }

    /**
     * Get all configured drug effect types
     * @return Set of drug effect types
     */
    public Set<PotionEffectType> getDrugEffectTypes() {
        return new HashSet<>(drugEffectTypes);
    }

    /**
     * Reload integrations
     */
    public void reload() {
        executableItemsEnabled = false;
        executableItemsPlugin = null;
        executableItemsManager = null;
        getExecutableItemMethod = null;
        isValidIDMethod = null;
        
        loadIntegrations();
        
        plugin.getLogger().info("ExternalPluginIntegration reloaded");
    }

    /**
     * Get integration status for debugging
     * @return Map containing integration status
     */
    public java.util.Map<String, Object> getIntegrationStatus() {
        java.util.Map<String, Object> status = new java.util.HashMap<>();
        status.put("executableItemsEnabled", executableItemsEnabled);
        status.put("drugItemsCount", drugItemIds.size());
        status.put("contrabandItemsCount", contrabandItemIds.size());
        status.put("drugEffectsCount", drugEffectTypes.size());
        status.put("drugDetectionEnabled", isDrugDetectionEnabled());
        status.put("contrabandDetectionEnabled", isContrabandDetectionEnabled());
        return status;
    }
} 