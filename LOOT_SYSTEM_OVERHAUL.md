# EdenCorrections Loot System Overhaul

## Overview
This document outlines a complete overhaul of the EdenCorrections loot system, transforming it from a static config-based system to a modern, context-aware, and highly flexible system.

## 🚀 **Key Improvements**

### **1. Context-Aware Loot Generation**
- **Rich Context System**: Every loot generation considers 20+ factors including guard rank, duty time, location, fight circumstances, and performance metrics
- **Dynamic Multipliers**: Loot quality and quantity automatically adjust based on player performance and circumstances
- **Environmental Awareness**: Different loot in PvP areas, special regions, and during events

### **2. Item Quality System**
- **6 Quality Tiers**: Damaged → Standard → Enhanced → Superior → Legendary → Mythic
- **Progressive Enhancement**: Higher quality items get better durability, enchantments, and special abilities
- **Rank-Based Distribution**: Higher guard ranks get better quality items naturally
- **Smart Enchanting**: Context-aware enchantment selection and levels

### **3. Modern Loot Table Architecture**
- **Modular Design**: Separate loot pools for armor, weapons, resources, and special items
- **Conditional Logic**: Items only drop when specific conditions are met
- **Pluggable System**: Easy to add custom item providers and special effects
- **Builder Pattern**: Clean, readable loot table definitions

### **4. Performance & Balance**
- **Intelligent Cooldowns**: Prevents loot farming while rewarding active gameplay
- **Performance Bonuses**: Guards with long duty sessions and high arrests get better loot
- **Anti-Griefing**: Protections against exploitation and farming

## 📋 **System Components**

### **Core Classes**

#### `LootContext`
```java
// Rich context information for every loot generation
LootContext context = new LootContext.Builder()
    .victim(guard)
    .killer(prisoner) 
    .location(deathLocation)
    .guardRank("sergeant")
    .dutyTime(75) // minutes
    .arrests(3)   // this session
    .inPvpArea(true)
    .specialEvent(false)
    .lootMultiplier(1.4) // 40% bonus
    .build();
```

#### `ItemQuality`
```java
// Transform basic items into enhanced versions
ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
ItemStack legendarysword = ItemQuality.LEGENDARY.enhanceItem(sword, context);
// Result: "§6Legendary Diamond Sword" with special enchantments and abilities
```

#### `LootTable`
```java
// Modern loot table with conditional logic
LootTable table = new LootTable.Builder("captain_loot", "Captain Guard Loot")
    .requireRank("captain")
    .requireMinDutyTime(30)
    .pool(armorPool)
    .pool(weaponPool)
    .pool(resourcePool)
    .build();
```

## 🎯 **Loot Examples by Rank**

### **Trainee Guard**
- **Basic Items**: Iron armor, iron sword, bow
- **Quality Range**: Damaged to Enhanced (rarely)
- **Resources**: 16-32 arrows, 4-8 food, 5-15 iron
- **Special**: None

### **Captain Guard**  
- **Elite Items**: Diamond armor, diamond sword, trident
- **Quality Range**: Enhanced to Mythic
- **Resources**: 64-128 arrows, 3-8 diamonds, 15-30 XP bottles
- **Special**: Performance bonuses, special abilities on Legendary/Mythic items

### **PvP Area Bonus** (All Ranks)
- **Exclusive**: Totem of Undying (rare), Ender Pearls, Golden Apples
- **Trigger**: Death in designated PvP areas
- **Purpose**: Encourage PvP engagement

### **Performance Bonus** (Sergeant+)
- **Requirements**: 30+ minutes on duty
- **Rewards**: Extra diamonds, emeralds, XP bottles
- **Scaling**: More arrests = better rewards

## 🔧 **Configuration Examples**

### **New Config Structure**
```yaml
loot-system:
  enabled: true
  use-modern-system: true # Toggle between old and new
  
  # Quality system
  quality:
    enabled: true
    rank-distributions:
      trainee: "damaged:30,standard:60,enhanced:10"
      captain: "superior:30,legendary:50,mythic:20"
  
  # Context bonuses
  bonuses:
    long-duty-session: 0.3  # 30% bonus for 60+ min duty
    pvp-area-death: 0.2     # 20% bonus in PvP areas
    high-arrests: 0.2       # 20% bonus for 3+ arrests
    outnumbered: 0.1        # 10% bonus when outnumbered
  
  # Special events
  events:
    enabled: true
    global-multiplier: 1.5  # 50% better loot during events
```

### **Migration Strategy**
```yaml
# Gradual migration support
loot-system:
  migration:
    use-legacy-fallback: true  # Fallback to old system if new fails
    log-comparisons: true      # Log old vs new loot for testing
    admin-test-mode: true      # Admins can test new system
```

## 🛠 **Implementation Benefits**

### **For Server Admins**
- **Easy Customization**: Add new loot tables without touching code
- **Event Management**: Temporary loot bonuses for special events
- **Balance Control**: Fine-tune rewards based on actual gameplay data
- **Performance Monitoring**: Detailed logging of loot generation

### **For Players**
- **Meaningful Progression**: Better loot as you advance in rank
- **Performance Rewards**: Active guards get better rewards
- **Variety**: No more predictable, boring loot drops
- **Special Items**: Rare and powerful items with unique abilities

### **For Developers**
- **Maintainable Code**: Clean, modular architecture
- **Extensible System**: Easy to add new features and item types
- **Test-Friendly**: Each component can be tested independently
- **Documentation**: Self-documenting builder patterns

## 📊 **Loot Quality Examples**

### **Standard Iron Sword**
```
Iron Sword
Quality: Standard
```

### **Legendary Diamond Sword**
```
§6Legendary Diamond Sword
Quality: §6Legendary
Durability: 180%

§6§l⚔ Legendary Weapon
§7+10% damage in PvP

Enchantments:
- Sharpness IV
- Unbreaking III
- Fire Aspect II
```

### **Mythic Diamond Chestplate**
```
§dMythic Diamond Chestplate  
Quality: §dMythic
Durability: 220%

§d§l⛨ Mythic Armor
§7+10% damage reduction
§7+5% movement speed
§75% chance to reflect damage

Enchantments:
- Protection V
- Unbreaking III
- Thorns III
```

## 🎪 **Special Event Examples**

### **Prison Riot Event**
```java
// Temporary 2x loot multiplier
modernLootManager.addSpecialModifier("riot_event", (loot, context) -> {
    // Double all loot during riot
    List<ItemStack> doubled = new ArrayList<>();
    for (ItemStack item : loot) {
        doubled.add(item);
        doubled.add(item.clone()); // Add duplicate
    }
    return doubled;
});
```

### **Elite Guard Training**
```java
// Special training weapons that disappear after event
modernLootManager.addLootTable(new LootTable.Builder("training_weapons", "Training Event")
    .requireSpecialEvent()
    .pool(new LootPool.Builder("training_gear", 1, 1)
        .entry(new CustomLootEntry("training_sword", 100, (context, random) -> {
            ItemStack sword = ItemQuality.MYTHIC.enhanceItem(
                new ItemStack(Material.NETHERITE_SWORD), context);
            // Add special lore indicating it's temporary
            return sword;
        }))
        .build())
    .build());
```

## 🔄 **Migration Path**

### **Phase 1: Preparation**
1. Deploy new loot system alongside existing system
2. Enable testing mode for admins
3. Configure loot tables to match current balance
4. Monitor and compare outputs

### **Phase 2: Testing**
1. Enable new system for specific ranks/regions
2. Collect player feedback
3. Fine-tune balance based on data
4. Fix any issues discovered

### **Phase 3: Full Deployment**
1. Enable new system server-wide
2. Disable legacy system
3. Monitor performance and balance
4. Celebrate improved gameplay!

## 🎨 **Customization Examples**

### **Custom Prison Workshop Loot**
```java
LootTable workshopLoot = new LootTable.Builder("workshop_death", "Prison Workshop Loot")
    .requireRegion("prison_workshop")
    .pool(new LootPool.Builder("workshop_tools", 1, 2)
        .entry(new MaterialLootEntry.Builder("pickaxe", 40, Material.IRON_PICKAXE).build())
        .entry(new MaterialLootEntry.Builder("crafting_table", 30, Material.CRAFTING_TABLE).build())
        .entry(new MaterialLootEntry.Builder("furnace", 20, Material.FURNACE).build())
        .build())
    .build();
```

### **Seasonal Event Loot**
```java
// Halloween event with special items
LootTable halloweenLoot = new LootTable.Builder("halloween_event", "Halloween Special")
    .requireSpecialEvent()
    .pool(new LootPool.Builder("spooky_items", 1, 1)
        .entry(new CustomLootEntry("pumpkin_helmet", 30, (context, random) -> {
            ItemStack pumpkin = new ItemStack(Material.CARVED_PUMPKIN);
            // Add special Halloween enchantments and lore
            return pumpkin;
        }))
        .build())
    .build();
```

## 🚀 **Future Enhancements**

### **Planned Features**
- **Player Statistics Integration**: Loot affected by long-term performance
- **Economic Integration**: Rare items affect prison economy
- **Achievement System**: Special loot for completing achievements  
- **Faction Bonuses**: Different loot for different guard factions
- **Dynamic Events**: Loot tables that change based on server events

### **Advanced Customization**
- **JSON Configuration**: External loot table definitions
- **API for Other Plugins**: Allow other plugins to register loot tables
- **Database Integration**: Store loot history and analytics
- **Web Interface**: Admin panel for loot table management

## 🎉 **Summary**

This loot system overhaul transforms EdenCorrections from having basic, predictable loot drops to a sophisticated, engaging system that:

✅ **Rewards Performance** - Active, skilled guards get better loot  
✅ **Encourages Engagement** - Special bonuses for PvP, events, and long duty sessions  
✅ **Provides Progression** - Clear improvement path from trainee to captain  
✅ **Maintains Balance** - Prevents exploitation while rewarding good gameplay  
✅ **Enables Events** - Easy to create special loot for temporary events  
✅ **Supports Customization** - Server admins can easily modify everything  

The new system will make guard gameplay more engaging, provide clear progression incentives, and give server admins powerful tools for creating unique experiences! 

## ✅ **Implementation Status: COMPLETE**

The new loot system has been fully implemented and integrated into EdenCorrections. Both legacy and modern systems are available with seamless switching.

### **🎯 Admin Usage Guide**

#### **Enabling the Modern System**
```bash
# Toggle between legacy and modern loot systems
/cor togglelootsystem

# Check current loot system status
/cor lootinfo

# Test loot generation for comparison
/cor testloot <player> [killer]
```

#### **Configuration**
The modern loot system is controlled via `config.yml`:
```yaml
loot-system:
  use-modern-system: true  # Enable modern system
  quality:
    enabled: true
    multipliers:
      special-event: 1.5
      pvp-area: 1.2
      lone-guard: 1.3
      # ... more multipliers
```

### **🔧 Migration Path**
1. **Current State**: Legacy system active by default
2. **Testing Phase**: Use `/cor testloot` to compare systems  
3. **Gradual Rollout**: Enable modern system with `/cor togglelootsystem`
4. **Full Migration**: Set `use-modern-system: true` in config

### **📊 Technical Implementation**

#### **Core Components**
- `ModernLootManager`: Main orchestrator for context-aware loot
- `LootContext`: Rich context data for generation decisions
- `ItemQuality`: 6-tier quality system with progressive enhancement
- `LootTable`: Flexible loot table system with conditional logic

#### **Integration Points**
- `GuardLootManager`: Handles legacy/modern system switching
- `EdenCorrections`: Initializes and provides access to modern manager
- `AdminCommandHandler`: Provides admin controls for loot system
- `config.yml`: Configuration for modern system features

### **🎮 Key Features Implemented**

#### **Context-Aware Generation**
- **Guard Rank**: Higher ranks get better quality distribution
- **Duty Performance**: Arrests, searches, and time served affect loot
- **Environmental Factors**: PvP areas, regions, and nearby players
- **Situational Modifiers**: Fight duration, damage cause, special events

#### **Item Quality System**
- **6 Quality Tiers**: Damaged → Standard → Enhanced → Superior → Legendary → Mythic
- **Progressive Enhancement**: Better durability, enchantments, and special properties
- **Rank-Based Distribution**: Each guard rank has optimized quality chances
- **Special Properties**: Legendary and Mythic items get unique abilities

#### **Administrative Controls**
- **System Switching**: Toggle between legacy and modern systems
- **Testing Tools**: Compare loot generation between systems
- **Live Configuration**: Change settings without server restart
- **Debug Information**: Detailed loot generation logging

### **🚀 Benefits Achieved**

1. **Enhanced Player Experience**: Context-aware loot makes every death meaningful
2. **Performance Incentive**: Good guard performance directly rewards with better loot
3. **Balanced Progression**: Quality distribution scales appropriately with rank
4. **Administrative Flexibility**: Easy switching and testing capabilities
5. **Future-Proof Design**: Modular system allows for easy expansion

### **💡 Usage Examples**

#### **For Server Admins**
```bash
# Check current system status
/cor lootinfo

# Test both systems on a captain-rank guard
/cor testloot CaptainPlayer AttackerPlayer

# Switch to modern system if satisfied
/cor togglelootsystem
```

#### **Expected Behavior**
- **Trainee Guards**: Mostly standard gear with occasional enhanced items
- **Captain Guards**: Superior/Legendary items with special properties
- **High Performers**: Better quality chances from context bonuses
- **Special Events**: 1.5x quality multiplier during events
- **PvP Deaths**: Additional quality bonuses for competitive scenarios

### **🛠️ Backward Compatibility**

- **Legacy System**: Remains fully functional and default
- **Seamless Switching**: No data loss when changing systems
- **Configuration Preservation**: Existing loot configs unchanged
- **Gradual Migration**: Test before permanent switch 