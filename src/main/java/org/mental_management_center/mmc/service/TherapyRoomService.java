package org.mental_management_center.mmc.service;

import org.springframework.stereotype.Service;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TherapyRoomService {

    // Потокобезпечний список ID клієнтів, для яких зараз відкрита кімната
    private final Set<UUID> activeRooms = ConcurrentHashMap.newKeySet();

    // Терапевт заходить -> вмикаємо кнопку для клієнта
    public void activateRoom(UUID clientId) {
        activeRooms.add(clientId);
    }

    // Терапевт виходить -> ховаємо кнопку
    public void deactivateRoom(UUID clientId) {
        activeRooms.remove(clientId);
    }

    // Перевірка: чи горить зелене світло?
    public boolean isRoomActive(UUID clientId) {
        return activeRooms.contains(clientId);
    }
}