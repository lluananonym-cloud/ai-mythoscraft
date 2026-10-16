package online.mythoscraft.core.item;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every item is obtainable from a crate and has a name in messages.yml; armour sets differ from each other. */
class CatalogueTest {

    private static final Set<String> NOT_IN_CRATES = Set.of("region_wand", "trophy_daily", "trophy_weekly", "trophy_monthly");

    private static YamlConfiguration resource(String name) {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(
                CatalogueTest.class.getClassLoader().getResourceAsStream(name), StandardCharsets.UTF_8));
    }

    @Test
    void everyItemIsInACrateAndHasAText() {
        YamlConfiguration crates = resource("crates.yml");
        YamlConfiguration messages = resource("messages.yml");
        Set<String> inCrates = new HashSet<>();
        for (String crate : crates.getConfigurationSection("crates").getKeys(false)) {
            for (Map<?, ?> reward : crates.getMapList("crates." + crate + ".rewards")) {
                String[] parts = String.valueOf(reward.get("reward")).split("\\s+");
                if (parts[0].equals("tool")) {
                    inCrates.add(parts[1]);
                }
            }
        }
        for (CustomItem item : CustomItem.values()) {
            if (!item.giveable()) {
                continue;
            }
            assertNotNull(messages.getString("items." + item.id() + ".name"), "no name for " + item.id());
            if (!NOT_IN_CRATES.contains(item.id())) {
                assertTrue(inCrates.contains(item.id()), item.id() + " is in no crate");
            }
        }
    }

    @Test
    void armourSetsAreNotAllTheSame() {
        Set<String> profiles = new HashSet<>();
        for (String set : List.of("mythos", "pumpkin", "frost", "inferno", "sculk", "dragon", "storm", "angel", "ninja", "knight", "miner", "viking", "ocean", "druid", "sky", "crystal", "pharaoh", "astronaut")) {
            CustomItem chest = CustomItem.byId(set + "_chestplate");
            assertNotNull(chest, set);
            CustomItem.Spec s = chest.spec();
            profiles.add(s.armor + "/" + s.toughness + "/" + s.knockback + "/" + s.health + "/" + s.attackBonus + "/" + s.speedBonus);
            assertTrue(CustomItem.armorSet(set).size() == 4, set + " needs 4 pieces");
        }
        assertTrue(profiles.size() == 18, "every set needs its own stats: " + profiles);
    }
}
