package online.mythoscraft.core.reward;

import online.mythoscraft.core.config.ConfigReader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Rewards by placement plus a participation reward:
 * <pre>
 * rewards:
 *   places:
 *     1: ["coins 500", "key mythos 1"]
 *     2: ["coins 300"]
 *   participation: ["coins 50"]
 *   participation-min-score: 1
 * </pre>
 */
public final class RewardTable {

    private final Map<Integer, List<Reward>> places = new TreeMap<>();
    private final List<Reward> participation = new ArrayList<>();
    private double minScore;

    public static RewardTable parse(ConfigReader cfg) {
        RewardTable table = new RewardTable();
        ConfigReader placeSection = cfg.sub("places");
        for (String key : placeSection.keys()) {
            int place;
            try {
                place = Integer.parseInt(key);
            } catch (NumberFormatException e) {
                placeSection.warn(key, "Platz muss eine Zahl sein");
                continue;
            }
            table.places.put(place, parseList(placeSection, key));
        }
        table.participation.addAll(parseList(cfg, "participation"));
        table.minScore = cfg.decimal("participation-min-score", 1, 0, Double.MAX_VALUE);
        return table;
    }

    public static List<Reward> parseList(ConfigReader cfg, String path) {
        List<Reward> list = new ArrayList<>();
        for (String line : cfg.strings(path)) {
            try {
                list.add(Reward.parse(line));
            } catch (IllegalArgumentException e) {
                cfg.warn(path, e.getMessage());
            }
        }
        return list;
    }

    public List<Reward> forPlace(int place) {
        return places.getOrDefault(place, Collections.emptyList());
    }

    public int placesCount() {
        return places.isEmpty() ? 0 : Collections.max(places.keySet());
    }

    public List<Reward> participation() {
        return participation;
    }

    public double minScore() {
        return minScore;
    }
}
