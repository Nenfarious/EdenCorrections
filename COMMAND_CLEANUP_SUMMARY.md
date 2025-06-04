# EdenCorrections Command System Cleanup Summary

## Overview
This document summarizes the command system cleanup and standardization performed on the EdenCorrections plugin to ensure consistency, proper tab completion, and remove unnecessary commands.

## Commands Removed

### 🗑️ **Removed: `/cor fixguards`**
- **Reason**: This command was redundant as guard counts should be automatically recalculated
- **Impact**: The `recalculateOnlineGuards()` functionality is still available but shouldn't need manual triggering
- **Files Modified**:
  - `AdminCommandHandler.java` - Removed switch case and handler method
  - `BaseCommandHandler.java` - Removed from routing logic and tab completion

## Command Structure Overview

### **Main Command: `/cor` (corrections/edencorrections)**
Handled by `BaseCommandHandler.java` which routes to specialized handlers:

#### **Admin Commands** (Handled by `AdminCommandHandler.java`)
**Basic Admin Commands:**
- `checkguards` - Check online guard count
- `checkdeathcooldown <player>` - Check player's death cooldown
- `cleardeathcooldown <player>` - Clear player's death cooldown
- `checkpenalty <player>` - Check player's penalty status
- `clearpenalty <player>` - Clear player's penalty
- `checkperms <player>` - Check player's permissions
- `checkrank <player>` - Check player's guard rank

**Guard Management:**
- `givehandcuffs <player> [amount]` - Give handcuffs to player
- `giveguarditems <player> [amount]` - Give guard items to player
- `givespyglass <player> [amount]` - Give spyglass to player
- `setwanted <player> <level>` - Set player's wanted level
- `clearwanted <player>` - Clear player's wanted level
- `getwanted <player>` - Get player's wanted level
- `clearglow <player>` - Clear player's glow effect

**Location Management:**
- `setguardlounge` - Set guard lounge location
- `setspawn` - Set spawn location  
- `setwardenoffice` - Set warden office location
- `locations` - List all configured locations
- `tpguardlounge` - Teleport to guard lounge
- `removelocation <type>` - Remove a location
- `migratelocations` - Migrate old config-based locations

**Integration Management:**
- `checkitem` - Check ExecutableItems integration for held item
- `integrationstatus` - Check ExecutableItems integration status
- `reloadintegration` - Reload ExecutableItems integration

**Contraband Management:**
- `tagcontraband <type>` - Tag held item as contraband (types: drug, weapon, communication, tool, general)
- `removecontrabandtag` - Remove contraband tag from held item
- `listcontraband [type]` - List contraband items by type
- `clearcontraband <type>` - Clear all contraband of specified type

**Guard Rank Management:**
- `setguardrank <group> <rank>` - Map LuckPerms group to guard rank
- `listguardranks` - List guard rank group mappings
- `createguardrank <rank>` - Create new guard rank
- `deleteguardrank <rank>` - Delete guard rank
- `setplayerrank <player> <rank>` - Set player's guard rank
- `removeplayerrank <player>` - Remove player from all guard ranks
- `listranks` - List all guard ranks

#### **Guard Duty Commands** (Handled by `GuardDutyCommandHandler.java`)
- `duty` - Toggle guard duty status
- `status [player]` - Check duty status
- `time` - Check off-duty time balance
- `addtime <player> <minutes>` - Add off-duty time (admin)
- `settime <player> <minutes>` - Set off-duty time (admin)
- `convert <minutes>` - Convert off-duty time to tokens

#### **GUI Commands** (Handled by `BaseCommandHandler.java`)
- `gui` - Open main GUI menu
- `dutymenu` - Open duty management menu
- `statsmenu` - Open statistics menu
- `actionsmenu` - Open actions menu
- `equipmentmenu` - Open equipment menu
- `shopmenu` - Open shop menu

#### **Other Commands:**
- `help` - Show help information (handled by HelpManager)
- `explain` - Show plugin explanation
- `reload` - Reload plugin configuration
- `npc <subcommand>` - NPC management (delegated to NPCCommandHandler)

### **Guard Commands: `/g`**
Handled by `GuardCommandHandler.java`:

#### **Chase Commands:**
- `chase <player>` / `pursue <player>` - Start chase against player
- `endchase <player>` / `stop <player>` - End chase against player

#### **Jail Commands:**
- `jail <player> [time]` - Jail player (requires close proximity)
- `jailoffline <player>` - Queue offline player for jail when they join

#### **Contraband Commands (Separate Commands):**
- `/sword <player>` - Request weapon drop
- `/armor <player>` - Request armor removal  
- `/bow <player>` - Request bow drop
- `/contraband <player>` - Request contraband drop

### **NPC Commands: `/cor npc`**
Handled by `NPCCommandHandler.java`:
- `create <name> <type> [section]` - Create NPC (types: DUTY, GUI)
- `remove [id]` - Remove NPC by ID or closest one
- `list` - List all Corrections NPCs
- `help` - Show NPC help

## Tab Completion Improvements

### **AdminCommandHandler.java**
- ✅ **Added comprehensive tab completion** for all admin commands
- ✅ **Player name completion** for player-specific commands
- ✅ **Type-specific completion** for contraband, location, and rank commands
- ✅ **LuckPerms group integration** for rank management commands
- ✅ **Numeric suggestions** for amounts and levels

### **Other Handlers**
- ✅ **GuardCommandHandler.java** - Complete tab completion for all guard commands
- ✅ **GuardDutyCommandHandler.java** - Complete tab completion for duty commands
- ✅ **NPCCommandHandler.java** - Complete tab completion for NPC commands

## Permission Structure

### **Base Permissions:**
- `edencorrections.admin` - Access to admin commands
- `edencorrections.guard` - Access to guard commands
- `edencorrections.duty` - Access to duty toggle
- `edencorrections.duty.check` - Check duty status
- `edencorrections.converttime` - Convert off-duty time

### **Specific Admin Permissions:**
- `edencorrections.admin.settime` - Modify player time
- `edencorrections.admin.jail` - Jail offline players
- `edencorrections.admin.npc` - NPC management
- `edencorrections.admin.locations` - Location management
- `edencorrections.admin.checkitem` - Item checking
- `edencorrections.admin.integrationstatus` - Integration status
- `edencorrections.admin.reloadintegration` - Reload integration

### **Guard Permissions:**
- `edencorrections.guard.chase` - Start/end chases
- `edencorrections.guard.jail` - Jail players

## Code Quality Improvements

### **Consistency:**
- ✅ **Consistent error messages** across all command handlers
- ✅ **Standardized permission checks** with proper messages
- ✅ **Uniform command routing** in BaseCommandHandler
- ✅ **Consistent tab completion patterns**

### **Performance:**
- ✅ **Removed unnecessary commands** that caused confusion
- ✅ **Proper command delegation** to specialized handlers
- ✅ **Optimized tab completion** with appropriate filtering

### **Maintainability:**
- ✅ **Clear separation of concerns** between command handlers
- ✅ **Comprehensive documentation** of command structure
- ✅ **Proper error handling** in all command methods
- ✅ **Future-proof tab completion** system

## Testing Recommendations

### **Commands to Test:**
1. **Verify removed commands no longer work:**
   - `/cor fixguards` should show help or unknown command

2. **Test tab completion:**
   - All admin commands should have proper tab completion
   - Player names should complete correctly
   - Type-specific completions should work

3. **Test command routing:**
   - GUI commands should open correct menus
   - Admin commands should route to AdminCommandHandler
   - Duty commands should route to GuardDutyCommandHandler

4. **Test permissions:**
   - Commands should respect permission requirements
   - Error messages should be consistent

## Future Maintenance

### **Adding New Commands:**
1. Add to appropriate handler class
2. Update switch statement in handler
3. Add tab completion in `onTabComplete()`
4. Update this documentation
5. Add appropriate permissions

### **Command Handler Guidelines:**
- Use consistent error message patterns
- Always check permissions first
- Provide helpful usage messages
- Include comprehensive tab completion
- Document any new permission nodes

## Files Modified

### **Primary Changes:**
- `src/main/java/dev/lsdmc/edencorrections/commands/admin/AdminCommandHandler.java`
  - Removed `fixguards` command and handler
  - Added comprehensive tab completion

- `src/main/java/dev/lsdmc/edencorrections/commands/base/BaseCommandHandler.java` 
  - Removed `fixguards` from routing logic
  - Cleaned up duplicate GUI handling
  - Removed `fixguards` from tab completion

### **Documentation:**
- `COMMAND_CLEANUP_SUMMARY.md` - This document

## Conclusion

The command system is now properly organized, consistent, and maintainable. All unnecessary commands have been removed, tab completion is comprehensive, and the routing logic is clean and efficient. This provides a solid foundation for future command additions and maintenance. 