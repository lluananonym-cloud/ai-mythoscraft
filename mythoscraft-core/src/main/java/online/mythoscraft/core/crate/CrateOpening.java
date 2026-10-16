package online.mythoscraft.core.crate;

import net.kyori.adventure.text.Component;
import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.display.Glyphs;
import online.mythoscraft.core.gui.Menu;
import online.mythoscraft.core.util.ItemBuilder;
import online.mythoscraft.core.util.SoundSpec;
import online.mythoscraft.core.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Spinning reel animation in a locked chest menu. The reward is decided up front; closing the menu or leaving the
 * server finishes instantly and still grants it – exactly once.
 */
public final class CrateOpening extends Menu {

    private static final int[] DELAYS = {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 5, 5, 6, 7, 8, 10, 12, 15};
    private static final int CENTER = 13;

    private final MythosCraft plugin;
    private final CrateManager manager;
    private final Player player;
    private final PlacedCrate crate;
    private final CrateType type;
    private final CrateType.CrateReward result;
    private final List<CrateType.CrateReward> reel = new ArrayList<>();
    private final SoundSpec tick = SoundSpec.of("block.note_block.hat", 0.7f, 1f);
    private int step;
    private boolean done;
    private BukkitTask task;

    CrateOpening(MythosCraft plugin, CrateManager manager, Player player, PlacedCrate crate, CrateType type) {
        super(3, title(manager, player, type));
        this.plugin = plugin;
        this.manager = manager;
        this.player = player;
        this.crate = crate;
        this.type = type;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        this.result = manager.table(type).pick(rnd);
        for (int i = 0; i < DELAYS.length + 9; i++) {
            reel.add(manager.table(type).pick(rnd));
        }
        reel.set(DELAYS.length + 4, result);
    }

    private static Component title(CrateManager manager, Player player, CrateType type) {
        return manager.guiTitle(Glyphs.GUI_CRATE_OPEN, Text.parse(type.name()), player);
    }

    Player player() {
        return player;
    }

    PlacedCrate crate() {
        return crate;
    }

    private final long started = System.currentTimeMillis();

    long age() {
        return System.currentTimeMillis() - started;
    }

    void start() {
        boolean pack = plugin.display().hasPack(player);
        ItemStack filler = pack ? null : new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).hideTooltip().build();
        for (int i = 0; i < 27; i++) {
            if (i < 9 || i > 17) {
                getInventory().setItem(i, filler);
            }
        }
        if (!pack) {
            getInventory().setItem(4, new ItemBuilder(Material.HOPPER).hideTooltip().build());
            getInventory().setItem(22, new ItemBuilder(Material.END_ROD).hideTooltip().build());
        }
        render();
        open(player);
        schedule();
    }

    private void schedule() {
        if (done) {
            return;
        }
        if (step >= DELAYS.length) {
            finish();
            return;
        }
        task = plugin.tasks().later(DELAYS[step], "crate reel", () -> plugin.messages().in(player, () -> {
            if (done) {
                return;
            }
            step++;
            render();
            tick.play(player, 0.6f + step * 0.035f);
            schedule();
        }));
    }

    private void render() {
        for (int i = 0; i < 9; i++) {
            ItemStack icon;
            try {
                icon = manager.rewardIcon(reel.get(step + i));
            } catch (RuntimeException e) {
                icon = new ItemStack(Material.CHEST);
            }
            getInventory().setItem(9 + i, icon);
        }
    }

    private void finish() {
        if (done) {
            return;
        }
        done = true;
        Rarity rarity = result.rarity();
        ItemStack pane = new ItemBuilder(rarity.pane()).name(manager.rewardName(result)).build();
        for (int i = 9; i <= 17; i++) {
            if (i != CENTER) {
                getInventory().setItem(i, pane);
            }
        }
        getInventory().setItem(CENTER, manager.rewardIcon(result));
        SoundSpec sound = SoundSpec.parse(rarity.sound());
        if (sound != null) {
            sound.play(player);
        }
        try {
            manager.grant(player, crate, type, result, false);
        } finally {
            manager.finished(this, crate);
        }
        plugin.tasks().later(40, "close crate menu", () -> {
            if (player.isOnline() && player.getOpenInventory().getTopInventory().getHolder(false) == this) {
                player.closeInventory();
            }
        });
    }

    /** Skips the remaining animation (menu closed, quit, reload). */
    void complete() {
        if (task != null) {
            task.cancel();
        }
        if (!done) {
            step = DELAYS.length;
            finish();
        }
    }

    @Override
    public void onClose(Player p) {
        complete();
    }
}
