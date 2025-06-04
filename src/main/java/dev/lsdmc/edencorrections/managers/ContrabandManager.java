package dev.lsdmc.edencorrections.managers;

import dev.lsdmc.edencorrections.EdenCorrections;
import dev.lsdmc.edencorrections.utils.MessageUtils;
import dev.lsdmc.edencorrections.storage.SQLiteStorage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages manual contraband tagging system that integrates with ExecutableItems integration
 */
public class ContrabandManager {
    private final EdenCorrections plugin;
    private final SQLiteStorage sqliteStorage;
    private final File contrabandFile;
    private FileConfiguration contrabandConfig;
    
    // Contraband types
    public enum ContrabandType {
        DRUG("drug", "Drugs and narcotics"),
        WEAPON("weapon", "Weapons and dangerous items"),
        COMMUNICATION("communication", "Communication devices"),
        TOOL("tool", "Escape tools and equipment"),
        GENERAL("general", "General contraband");
        
        private final String key;
        private final String description;
        
        ContrabandType(String key, String description) {
            this.key = key;
            this.description = description;
        }
        
        public String getKey() { return key; }
        public String getDescription() { return description; }
        
        public static ContrabandType fromKey(String key) {
            for (ContrabandType type : values()) {
                if (type.key.equalsIgnoreCase(key)) {
                    return type;
                }
            }
            return null;
        }
    }
    
    // Registry of manually tagged items
    private final Map<String, Set<ContrabandItem>> contrabandRegistry = new ConcurrentHashMap<>();
    
    // NamespacedKeys for NBT tagging
    private final NamespacedKey contrabandTypeKey;
    private final NamespacedKey contrabandTimeKey;
    private final NamespacedKey contrabandAdminKey;
    
    public ContrabandManager(EdenCorrections plugin) {
        this.plugin = plugin;
        this.sqliteStorage = (SQLiteStorage) plugin.getStorageManager();
        this.contrabandFile = new File(plugin.getDataFolder(), "contraband_registry.yml");
        this.contrabandTypeKey = new NamespacedKey(plugin, "contraband_type");
        this.contrabandTimeKey = new NamespacedKey(plugin, "contraband_time");
        this.contrabandAdminKey = new NamespacedKey(plugin, "contraband_admin");
        
        // Initialize registry for each type
        for (ContrabandType type : ContrabandType.values()) {
            contrabandRegistry.put(type.getKey(), ConcurrentHashMap.newKeySet());
        }
        
        loadContrabandRegistry();
    }
    
    /**
     * Load contraband registry from file
     */
    private void loadContrabandRegistry() {
        contrabandRegistry.values().forEach(Set::clear);
        Map<String, Set<ContrabandItem>> loaded = sqliteStorage.loadContrabandRegistry();
        for (Map.Entry<String, Set<ContrabandItem>> entry : loaded.entrySet()) {
            Set<ContrabandItem> set = contrabandRegistry.get(entry.getKey());
            if (set == null) continue;
            set.addAll(entry.getValue());
        }
        plugin.getLogger().info("Loaded " + getTotalContrabandCount() + " manually tagged contraband items");
    }
    
    /**
     * Save contraband registry to file
     */
    private void saveContrabandRegistry() {
        sqliteStorage.saveContrabandRegistry(contrabandRegistry);
    }
    
    /**
     * Tag a held item as contraband
     */
    public boolean tagHeldItem(Player admin, ContrabandType type) {
        ItemStack item = admin.getInventory().getItemInMainHand();
        
        if (item == null || item.getType() == Material.AIR) {
            admin.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>You must be holding an item to tag it as contraband!</red>")));
            return false;
        }
        
        // Check if already tagged
        if (isManuallyTagged(item)) {
            admin.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>This item is already tagged as contraband!</red>")));
            return false;
        }
        
        // Tag the item with NBT data
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            admin.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>Cannot tag this item type!</red>")));
            return false;
        }
        
        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(contrabandTypeKey, PersistentDataType.STRING, type.getKey());
        container.set(contrabandTimeKey, PersistentDataType.LONG, System.currentTimeMillis());
        container.set(contrabandAdminKey, PersistentDataType.STRING, admin.getName());
        
        // Also set legacy contraband tag for compatibility
        NamespacedKey legacyKey = new NamespacedKey(plugin, "contraband");
        container.set(legacyKey, PersistentDataType.BYTE, (byte) 1);
        
        item.setItemMeta(meta);
        
        // Add to registry
        ContrabandItem contrabandItem = new ContrabandItem(
            item.getType(),
            meta.hasDisplayName() ? meta.getDisplayName() : null,
            meta.hasLore() ? meta.getLore() : null,
            admin.getName(),
            System.currentTimeMillis()
        );
        
        Set<ContrabandItem> items = contrabandRegistry.get(type.getKey());
        if (items == null) return false;
        items.add(contrabandItem);
        saveContrabandRegistry();
        
        admin.sendMessage(MessageUtils.getPrefix(plugin).append(
            MessageUtils.parseMessage("<green>Successfully tagged item as " + type.getDescription().toLowerCase() + "!</green>")));
        
        return true;
    }
    
    /**
     * Remove contraband tag from held item
     */
    public boolean removeTagFromHeldItem(Player admin) {
        ItemStack item = admin.getInventory().getItemInMainHand();
        
        if (item == null || item.getType() == Material.AIR) {
            admin.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>You must be holding an item to remove its contraband tag!</red>")));
            return false;
        }
        
        if (!isManuallyTagged(item)) {
            admin.sendMessage(MessageUtils.getPrefix(plugin).append(
                MessageUtils.parseMessage("<red>This item is not manually tagged as contraband!</red>")));
            return false;
        }
        
        // Remove NBT tags
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();
        
        String typeKey = container.get(contrabandTypeKey, PersistentDataType.STRING);
        
        container.remove(contrabandTypeKey);
        container.remove(contrabandTimeKey);
        container.remove(contrabandAdminKey);
        
        // Remove legacy tag
        NamespacedKey legacyKey = new NamespacedKey(plugin, "contraband");
        container.remove(legacyKey);
        
        item.setItemMeta(meta);
        
        // Remove from registry
        if (typeKey != null) {
            Set<ContrabandItem> items = contrabandRegistry.get(typeKey);
            if (items == null) return false;
            items.removeIf(ci -> ci.matches(item));
        }
        
        saveContrabandRegistry();
        
        admin.sendMessage(MessageUtils.getPrefix(plugin).append(
            MessageUtils.parseMessage("<green>Successfully removed contraband tag from item!</green>")));
        
        return true;
    }
    
    /**
     * Check if an item is manually tagged as contraband
     */
    public boolean isManuallyTagged(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();
        
        return container.has(contrabandTypeKey, PersistentDataType.STRING);
    }
    
    /**
     * Get the contraband type of an item
     */
    public ContrabandType getContrabandType(ItemStack item) {
        if (!isManuallyTagged(item)) return null;
        
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();
        
        String typeKey = container.get(contrabandTypeKey, PersistentDataType.STRING);
        return ContrabandType.fromKey(typeKey);
    }
    
    /**
     * Check if an item matches any manually tagged contraband
     */
    public boolean isContraband(ItemStack item) {
        if (isManuallyTagged(item)) {
            return true;
        }
        
        // Check registry by material and display name
        for (Set<ContrabandItem> items : contrabandRegistry.values()) {
            for (ContrabandItem contrabandItem : items) {
                if (contrabandItem.matches(item)) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * List contraband items for a specific type
     */
    public List<String> listContraband(ContrabandType type) {
        Set<ContrabandItem> items = contrabandRegistry.get(type.getKey());
        if (items == null) return Collections.emptyList();
        List<String> result = new ArrayList<>();
        
        for (ContrabandItem item : items) {
            result.add(item.getDisplayString());
        }
        
        return result;
    }
    
    /**
     * Clear all items of a specific type
     */
    public int clearContrabandType(ContrabandType type) {
        Set<ContrabandItem> items = contrabandRegistry.get(type.getKey());
        if (items == null) return 0;
        int count = items.size();
        items.clear();
        saveContrabandRegistry();
        return count;
    }
    
    /**
     * Get total count of manually tagged contraband items
     */
    public int getTotalContrabandCount() {
        return contrabandRegistry.values().stream().mapToInt(Set::size).sum();
    }
    
    /**
     * Get count for specific type
     */
    public int getContrabandCount(ContrabandType type) {
        Set<ContrabandItem> items = contrabandRegistry.get(type.getKey());
        if (items == null) return 0;
        return items.size();
    }
    
    /**
     * Data class for contraband items
     */
    public static class ContrabandItem {
        public final Material material;
        public final String displayName;
        public final List<String> lore;
        public final String addedBy;
        public final long addedTime;
        public final int loreHash;
        
        public ContrabandItem(Material material, String displayName, List<String> lore, String addedBy, long addedTime) {
            this.material = material;
            this.displayName = displayName;
            this.lore = lore != null ? new ArrayList<>(lore) : null;
            this.addedBy = addedBy;
            this.addedTime = addedTime;
            this.loreHash = lore != null ? lore.hashCode() : 0;
        }
        
        public boolean matches(ItemStack item) {
            if (item.getType() != material) return false;
            
            ItemMeta meta = item.getItemMeta();
            if (meta == null) return displayName == null && lore == null;
            
            // Check display name
            String itemDisplayName = meta.hasDisplayName() ? meta.getDisplayName() : null;
            if (!Objects.equals(displayName, itemDisplayName)) return false;
            
            // Check lore
            List<String> itemLore = meta.hasLore() ? meta.getLore() : null;
            if (lore == null && itemLore == null) return true;
            if (lore == null || itemLore == null) return false;
            
            return lore.equals(itemLore);
        }
        
        public String getDisplayString() {
            StringBuilder sb = new StringBuilder();
            sb.append(material.name());
            if (displayName != null) {
                sb.append(" (").append(displayName).append(")");
            }
            sb.append(" - Added by ").append(addedBy);
            sb.append(" on ").append(new Date(addedTime));
            return sb.toString();
        }
        
        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("material", material.name());
            map.put("display_name", displayName);
            map.put("lore", lore);
            map.put("added_by", addedBy);
            map.put("added_time", addedTime);
            map.put("lore_hash", loreHash);
            return map;
        }
        
        @SuppressWarnings("unchecked")
        public static ContrabandItem fromMap(Map<?, ?> map) {
            Material material = Material.valueOf((String) map.get("material"));
            String displayName = (String) map.get("display_name");
            List<String> lore = (List<String>) map.get("lore");
            String addedBy = (String) map.get("added_by");
            long addedTime = ((Number) map.get("added_time")).longValue();
            
            return new ContrabandItem(material, displayName, lore, addedBy, addedTime);
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof ContrabandItem other)) return false;
            
            return material == other.material &&
                   Objects.equals(displayName, other.displayName) &&
                   Objects.equals(lore, other.lore);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(material, displayName, loreHash);
        }
    }
} 