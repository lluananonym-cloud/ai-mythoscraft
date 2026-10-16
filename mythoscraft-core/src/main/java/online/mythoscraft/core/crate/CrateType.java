package online.mythoscraft.core.crate;

import online.mythoscraft.core.reward.Reward;
import online.mythoscraft.core.util.WeightedRandom;
import org.bukkit.Color;
import org.bukkit.Material;

import java.util.List;

/**
 * A crate definition from crates.yml.
 *
 * @param model    custom model id of the crate (resource pack: &lt;model&gt;, &lt;model&gt;_base, &lt;model&gt;_lid)
 * @param keyModel custom model id of its key
 */
public record CrateType(String id, String name, String keyName, List<String> keyLore, String model, String keyModel,
                        Color color, Rarity broadcastFrom, List<CrateReward> rewards, WeightedRandom<CrateReward> table) {

    public record CrateReward(Reward reward, double weight, Rarity rarity, String name, Material icon) {
        @Override
        public String name() {
            return online.mythoscraft.core.config.ConfigText.tr(name);
        }
    }

    @Override
    public String name() {
        return online.mythoscraft.core.config.ConfigText.tr(name);
    }

    @Override
    public String keyName() {
        return online.mythoscraft.core.config.ConfigText.tr(keyName);
    }

    @Override
    public List<String> keyLore() {
        return online.mythoscraft.core.config.ConfigText.tr(keyLore);
    }
}
