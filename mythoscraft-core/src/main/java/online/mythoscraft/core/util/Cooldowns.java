package online.mythoscraft.core.util;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Per-player cooldowns grouped by key. Main-thread only. */
public final class Cooldowns {

    private final Map<String, Map<UUID, Long>> until = new HashMap<>();

    public long remainingMillis(String key, UUID player) {
        Map<UUID, Long> map = until.get(key);
        if (map == null) {
            return 0;
        }
        Long end = map.get(player);
        if (end == null) {
            return 0;
        }
        long left = end - System.currentTimeMillis();
        if (left <= 0) {
            map.remove(player);
            return 0;
        }
        return left;
    }

    public long remainingSeconds(String key, UUID player) {
        return (remainingMillis(key, player) + 999) / 1000;
    }

    public boolean active(String key, UUID player) {
        return remainingMillis(key, player) > 0;
    }

    public void set(String key, UUID player, long millis) {
        if (millis <= 0) {
            return;
        }
        until.computeIfAbsent(key, k -> new HashMap<>()).put(player, System.currentTimeMillis() + millis);
    }

    public void clear(UUID player) {
        until.values().forEach(m -> m.remove(player));
    }
}
