package online.mythoscraft.core.events.impl;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.economy.Currency;
import online.mythoscraft.core.events.MythosEvent;
import online.mythoscraft.core.player.PlayerData;
import online.mythoscraft.core.util.SoundSpec;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerFishEvent;

import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/** Fishing contest: fish = 1 point, treasure = 3, a rare "Goldfisch" pays extra coins. */
public final class FishingEvent extends MythosEvent {

    private static final Set<Material> FISH = Set.of(Material.COD, Material.SALMON, Material.TROPICAL_FISH, Material.PUFFERFISH);

    private double goldfishChance = 0.04;
    private long goldfishCoins = 150;

    public FishingEvent(MythosCraft plugin) {
        super(plugin, "fishing");
    }

    @Override
    protected int defaultDuration() {
        return 300;
    }

    @Override
    protected void loadSettings(ConfigReader cfg) {
        goldfishChance = cfg.decimal("goldfish-chance", 0.04, 0, 1);
        goldfishCoins = cfg.longValue("goldfish-coins", 150, 0, 1_000_000);
    }

    @Override
    protected String scoreUnitKey() {
        return "events.units.points";
    }

    @Override
    protected void onStart() {
    }

    @Override
    protected void onTick() {
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        Player p = event.getPlayer();
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH || !(event.getCaught() instanceof Item caught) || !eligible(p)) {
            return;
        }
        boolean fish = FISH.contains(caught.getItemStack().getType());
        int points = fish ? 1 : 3;
        addScore(p, points);
        if (ThreadLocalRandom.current().nextDouble() < goldfishChance) {
            PlayerData data = plugin.playerData().get(p);
            if (data != null) {
                long coins = plugin.economy().earn(data, Currency.COINS, goldfishCoins, false);
                addScore(p, 5);
                plugin.messages().send(p, "events.fishing.goldfish", "coins", coins);
                p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, caught.getLocation(), 20, 0.3, 0.3, 0.3, 0.2);
                SoundSpec.of("ui.toast.challenge_complete", 0.6f, 1.6f).play(p);
            }
        }
        plugin.messages().actionBar(p, "events.fishing.catch", "points", points, "total", Math.round(score(p.getUniqueId())));
    }

    @Override
    protected void cleanup() {
    }
}
