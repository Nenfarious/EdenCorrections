package dev.lsdmc.edencorrections.managers;

import java.util.Map;
import java.util.UUID;

public interface StorageManager {
    /**
     * Initialize the storage system
     */
    void initialize();

    /**
     * Reload the storage system
     */
    void reload();

    /**
     * Shutdown the storage system
     */
    void shutdown();

    /**
     * Save a player's duty status
     * @param playerId The player's UUID
     * @param isOnDuty Whether the player is on duty
     */
    void saveDutyStatus(UUID playerId, boolean isOnDuty);

    /**
     * Save all players' duty status
     * @param dutyStatus Map of player UUIDs to duty status
     */
    void saveDutyStatus(Map<UUID, Boolean> dutyStatus);

    /**
     * Load all players' duty status
     * @return Map of player UUIDs to duty status
     */
    Map<UUID, Boolean> loadDutyStatus();

    /**
     * Save a player's duty start time
     * @param playerId The player's UUID
     * @param startTime The start time in milliseconds
     */
    void saveDutyStartTime(UUID playerId, long startTime);

    /**
     * Save all players' duty start times
     * @param dutyStartTimes Map of player UUIDs to duty start times
     */
    void saveDutyStartTimes(Map<UUID, Long> dutyStartTimes);

    /**
     * Load all players' duty start times
     * @return Map of player UUIDs to duty start times
     */
    Map<UUID, Long> loadDutyStartTimes();

    /**
     * Save a player's off-duty minutes
     * @param playerId The player's UUID
     * @param minutes The number of off-duty minutes
     */
    void saveOffDutyMinutes(UUID playerId, int minutes);

    /**
     * Save all players' off-duty minutes
     * @param offDutyMinutes Map of player UUIDs to off-duty minutes
     */
    void saveOffDutyMinutes(Map<UUID, Integer> offDutyMinutes);

    /**
     * Load all players' off-duty minutes
     * @return Map of player UUIDs to off-duty minutes
     */
    Map<UUID, Integer> loadOffDutyMinutes();
}