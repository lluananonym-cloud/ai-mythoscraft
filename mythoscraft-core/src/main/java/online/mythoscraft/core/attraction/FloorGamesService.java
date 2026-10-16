package online.mythoscraft.core.attraction;

import net.kyori.adventure.title.Title;
import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.util.ItemBuilder;
import online.mythoscraft.core.util.Keys;
import online.mythoscraft.core.util.SoundSpec;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Three spawn mini games for friends (no PvP):
 * <ul>
 * <li>Block-Party – stand on the shown colour before all other colours vanish; rounds get faster.</li>
 * <li>Spleef – dig the snow away under the others; the last one on the floor wins.</li>
 * <li>Target range – 45 seconds, one target lights up at a time, hit as many as you can.</li>
 * </ul>
 * Everybody standing on the floor when the countdown ends plays. Falling = out (into the water, then back to the
 * stairs). Rewards like every spawn activity (gems for the first activities per day).
 */
public final class FloorGamesService implements Listener {

    public enum Game { PARTY, SPLEEF, RANGE }

    private enum State { IDLE, LOBBY, SHOW, CLEARED, RUNNING }

    private static final Material[] COLORS = {Material.RED_CONCRETE, Material.ORANGE_CONCRETE, Material.YELLOW_CONCRETE,
            Material.LIME_CONCRETE, Material.LIGHT_BLUE_CONCRETE, Material.BLUE_CONCRETE, Material.PURPLE_CONCRETE, Material.PINK_CONCRETE};
    private static final String[] COLOR_KEYS = {"red", "orange", "yellow", "lime", "light-blue", "blue", "purple", "pink"};
    private static final int PARTY_R = 8;
    private static final int SPLEEF_R = 9;

    private final MythosCraft plugin;
    private long partyGems = 2;
    private long partyCoins = 500;
    private long spleefGems = 2;
    private long spleefCoins = 500;
    private long rangeGems = 2;
    private long rangeCoins = 400;
    private int rangeSeconds = 45;
    private int rangeRewardHits = 10;

    // block party
    private State party = State.IDLE;
    private final Set<UUID> partyPlayers = new LinkedHashSet<>();
    private int partyStarters;
    private int partyRound;
    private int partyTimer;
    private Material partyColor;

    // spleef
    private State spleef = State.IDLE;
    private final Set<UUID> spleefPlayers = new LinkedHashSet<>();
    private int spleefTimer;
    private int spleefSeconds;

    // target range
    private UUID shooter;
    private int rangeLeft;
    private int rangeHits;
    private Location lit;

    public FloorGamesService(MythosCraft plugin) {
        this.plugin = plugin;
    }

    public void reload(ConfigReader root) {
        ConfigReader cfg = root.sub("attractions.floor-games");
        partyGems = cfg.longValue("block-party.daily-gems", 2, 0, 100_000);
        partyCoins = cfg.longValue("block-party.daily-coins", 500, 0, 100_000_000);
        spleefGems = cfg.longValue("spleef.daily-gems", 2, 0, 100_000);
        spleefCoins = cfg.longValue("spleef.daily-coins", 500, 0, 100_000_000);
        rangeGems = cfg.longValue("target-range.daily-gems", 2, 0, 100_000);
        rangeCoins = cfg.longValue("target-range.daily-coins", 400, 0, 100_000_000);
        rangeSeconds = cfg.integer("target-range.seconds", 45, 10, 600);
        rangeRewardHits = cfg.integer("target-range.hits-for-reward", 10, 1, 1000);
        plugin.tasks().cancel(this);
        plugin.tasks().repeat(this, 40, 5, "floor games", this::tick);
    }

    // ---- geometry -----------------------------------------------------------------------------------------

    private Location center(Game g) {
        return plugin.attractionStore().first(g == Game.PARTY ? "party_center" : "spleef_center");
    }

    private Location exit(Game g) {
        return plugin.attractionStore().first(g == Game.PARTY ? "party_exit" : "spleef_exit");
    }

    private boolean onFloorArea(Game g, Location c, Location l) {
        if (c == null || !l.getWorld().equals(c.getWorld())) {
            return false;
        }
        double dx = l.getX() - (c.getBlockX() + 0.5);
        double dz = l.getZ() - (c.getBlockZ() + 0.5);
        return g == Game.PARTY ? Math.abs(dx) <= PARTY_R + 0.5 && Math.abs(dz) <= PARTY_R + 0.5 : dx * dx + dz * dz <= (SPLEEF_R + 0.6) * (SPLEEF_R + 0.6);
    }

    /** Standing on the game floor (not in the pool below). */
    private boolean onFloor(Game g, Player p) {
        Location c = center(g);
        Location l = p.getLocation();
        return c != null && onFloorArea(g, c, l) && l.getY() >= c.getY() - 0.6 && l.getY() < c.getY() + 4;
    }

    /** Fell into the pool. */
    private boolean fell(Game g, Player p) {
        Location c = center(g);
        Location l = p.getLocation();
        return c != null && onFloorArea(g, c, l) && l.getY() < c.getY() - 1.6;
    }

    private List<Block> floor(Game g) {
        List<Block> out = new ArrayList<>();
        Location c = center(g);
        if (c == null || !c.isChunkLoaded()) {
            return out;
        }
        World w = c.getWorld();
        int y = c.getBlockY() - 1;
        int r = g == Game.PARTY ? PARTY_R : SPLEEF_R;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (g == Game.SPLEEF && dx * dx + dz * dz > (r + 0.5) * (r + 0.5)) {
                    continue;
                }
                out.add(w.getBlockAt(c.getBlockX() + dx, y, c.getBlockZ() + dz));
            }
        }
        return out;
    }

    private void paintParty() {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        Location c = center(Game.PARTY);
        if (c == null) {
            return;
        }
        Material[][] tiles = new Material[PARTY_R + 1][PARTY_R + 1];
        for (Block b : floor(Game.PARTY)) {
            int tx = (b.getX() - c.getBlockX() + PARTY_R) / 2;
            int tz = (b.getZ() - c.getBlockZ() + PARTY_R) / 2;
            if (tiles[tx][tz] == null) {
                tiles[tx][tz] = COLORS[rnd.nextInt(COLORS.length)];
            }
            b.setType(tiles[tx][tz], false);
        }
    }

    private void restoreSpleef() {
        for (Block b : floor(Game.SPLEEF)) {
            b.setType(Material.SNOW_BLOCK, false);
        }
    }

    private List<Player> online(Set<UUID> ids) {
        List<Player> out = new ArrayList<>();
        for (UUID id : ids) {
            Player p = plugin.getServer().getPlayer(id);
            if (p != null) {
                out.add(p);
            }
        }
        return out;
    }

    private void sendOut(Game g, Player p) {
        Location exit = exit(g);
        if (exit != null) {
            p.setFallDistance(0);
            p.teleport(exit, PlayerTeleportEvent.TeleportCause.PLUGIN);
        }
    }

    // ---- loop ---------------------------------------------------------------------------------------------

    private void tick() {
        try {
            tickParty();
            tickSpleef();
            tickRange();
        } catch (RuntimeException e) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "floor games", e);
        }
    }

    private List<Player> standing(Game g) {
        List<Player> out = new ArrayList<>();
        if (center(g) == null) {
            return out;
        }
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (onFloor(g, p) && p.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
                out.add(p);
            }
            if (fell(g, p) && !(g == Game.PARTY ? partyPlayers : spleefPlayers).contains(p.getUniqueId())) {
                sendOut(g, p); // somebody in the pool who is not playing
            }
        }
        return out;
    }

    // Block-Party: 4 ticks per second (task every 5 ticks)
    private void tickParty() {
        List<Player> on = standing(Game.PARTY);
        switch (party) {
            case IDLE -> {
                if (!on.isEmpty()) {
                    party = State.LOBBY;
                    partyTimer = 10 * 4;
                }
            }
            case LOBBY -> {
                if (on.isEmpty()) {
                    party = State.IDLE;
                    return;
                }
                if (partyTimer % 4 == 0) {
                    for (Player p : on) {
                        plugin.messages().actionBar(p, "attractions.party.lobby", "seconds", partyTimer / 4, "players", on.size());
                    }
                }
                if (--partyTimer <= 0) {
                    partyPlayers.clear();
                    on.forEach(p -> partyPlayers.add(p.getUniqueId()));
                    partyStarters = partyPlayers.size();
                    partyRound = 0;
                    paintParty();
                    nextPartyRound();
                }
            }
            case SHOW -> {
                checkPartyFalls();
                if (partyTimer % 4 == 0) {
                    for (Player p : online(partyPlayers)) {
                        SoundSpec.of("block.note_block.pling", 0.8f, 0.8f + (partyTimer / 4) * 0.1f).play(p);
                    }
                }
                if (--partyTimer <= 0) {
                    Material keep = partyColor;
                    for (Block b : floor(Game.PARTY)) {
                        if (b.getType() != keep) {
                            b.setType(Material.AIR, false);
                        }
                    }
                    for (Player p : online(partyPlayers)) {
                        SoundSpec.of("entity.illusioner.mirror_move", 1f, 1.2f).play(p);
                    }
                    party = State.CLEARED;
                    partyTimer = 10;
                }
            }
            case CLEARED -> {
                checkPartyFalls();
                if (--partyTimer <= 0) {
                    if (partyOver()) {
                        return;
                    }
                    paintParty();
                    nextPartyRound();
                }
            }
            default -> {
            }
        }
    }

    private void nextPartyRound() {
        partyRound++;
        int i = ThreadLocalRandom.current().nextInt(COLORS.length);
        partyColor = COLORS[i];
        party = State.SHOW;
        // 5 s at the start, every round faster, never below 1.5 s
        partyTimer = Math.max(6, 20 - partyRound);
        ItemStack show = new ItemStack(partyColor);
        for (Player p : online(partyPlayers)) {
            p.showTitle(Title.title(plugin.messages().in(p, () -> plugin.messages().get("attractions.party.color." + COLOR_KEYS[i])),
                    plugin.messages().in(p, () -> plugin.messages().get("attractions.party.round", "round", partyRound)),
                    Title.Times.times(Duration.ZERO, Duration.ofMillis(partyTimer * 250L), Duration.ofMillis(150))));
            p.sendEquipmentChange(p, org.bukkit.inventory.EquipmentSlot.HAND, show);
        }
    }

    private void checkPartyFalls() {
        for (Player p : online(partyPlayers)) {
            if (fell(Game.PARTY, p) || !p.getWorld().equals(center(Game.PARTY).getWorld())
                    || p.getLocation().distanceSquared(center(Game.PARTY)) > 30 * 30) {
                partyPlayers.remove(p.getUniqueId());
                plugin.messages().send(p, "attractions.party.out", "round", partyRound);
                long best = plugin.progress().get(p, "blockparty.best");
                if (partyRound - 1 > best) {
                    plugin.progress().set(p, "blockparty.best", partyRound - 1);
                }
                sendOut(Game.PARTY, p);
            }
        }
        partyPlayers.removeIf(id -> plugin.getServer().getPlayer(id) == null);
    }

    /** True when the game ended (and the floor was reset). */
    private boolean partyOver() {
        List<Player> left = online(partyPlayers);
        boolean multi = partyStarters >= 2;
        if (left.isEmpty() || multi && left.size() == 1) {
            for (Player p : left) {
                plugin.progress().add(p, "blockparty.wins", 1);
                long best = plugin.progress().get(p, "blockparty.best");
                if (partyRound > best) {
                    plugin.progress().set(p, "blockparty.best", partyRound);
                }
                plugin.messages().broadcast("attractions.party.winner", "player", p.getName(), "round", partyRound);
                SeasonAmbienceService.fireworks(p.getLocation().add(0, 1, 0), 1);
                ActivityRewards.pay(plugin, p, "blockparty", partyGems, partyCoins);
                sendOut(Game.PARTY, p);
            }
            partyPlayers.clear();
            paintParty();
            party = State.IDLE;
            return true;
        }
        if (!multi && partyRound >= 15) {
            // solo: 15 rounds survived = done
            Player p = left.get(0);
            plugin.messages().send(p, "attractions.party.solo-done", "round", partyRound);
            ActivityRewards.pay(plugin, p, "blockparty", partyGems, partyCoins);
            plugin.progress().set(p, "blockparty.best", Math.max(partyRound, plugin.progress().get(p, "blockparty.best")));
            partyPlayers.clear();
            paintParty();
            sendOut(Game.PARTY, p);
            party = State.IDLE;
            return true;
        }
        return false;
    }

    // Spleef
    private void tickSpleef() {
        List<Player> on = standing(Game.SPLEEF);
        switch (spleef) {
            case IDLE -> {
                if (!on.isEmpty()) {
                    spleef = State.LOBBY;
                    spleefTimer = 10 * 4;
                }
            }
            case LOBBY -> {
                if (on.isEmpty()) {
                    spleef = State.IDLE;
                    return;
                }
                if (on.size() < 2) {
                    spleefTimer = 10 * 4;
                    for (Player p : on) {
                        plugin.messages().actionBar(p, "attractions.spleef.waiting");
                    }
                    return;
                }
                if (spleefTimer % 4 == 0) {
                    for (Player p : on) {
                        plugin.messages().actionBar(p, "attractions.spleef.lobby", "seconds", spleefTimer / 4, "players", on.size());
                    }
                }
                if (--spleefTimer <= 0) {
                    restoreSpleef();
                    spleefPlayers.clear();
                    spleefSeconds = 0;
                    for (Player p : on) {
                        spleefPlayers.add(p.getUniqueId());
                        p.getInventory().addItem(shovel());
                        p.showTitle(Title.title(plugin.messages().in(p, () -> plugin.messages().get("attractions.spleef.go")),
                                plugin.messages().in(p, () -> plugin.messages().get("attractions.spleef.go-sub")),
                                Title.Times.times(Duration.ZERO, Duration.ofMillis(1500), Duration.ofMillis(300))));
                        SoundSpec.of("entity.ender_dragon.growl", 0.6f, 1.6f).play(p);
                    }
                    spleef = State.RUNNING;
                }
            }
            case RUNNING -> {
                if (++spleefTimer % 4 == 0) {
                    spleefSeconds++;
                    // after 2 minutes the floor starts to melt
                    if (spleefSeconds > 120) {
                        List<Block> snow = floor(Game.SPLEEF);
                        snow.removeIf(b -> b.getType() != Material.SNOW_BLOCK);
                        for (int i = 0; i < 6 && !snow.isEmpty(); i++) {
                            snow.remove(ThreadLocalRandom.current().nextInt(snow.size())).setType(Material.AIR, false);
                        }
                    }
                }
                for (Player p : online(spleefPlayers)) {
                    if (fell(Game.SPLEEF, p) || p.getLocation().distanceSquared(center(Game.SPLEEF)) > 30 * 30) {
                        spleefPlayers.remove(p.getUniqueId());
                        takeShovel(p);
                        plugin.messages().send(p, "attractions.spleef.out");
                        sendOut(Game.SPLEEF, p);
                    }
                }
                spleefPlayers.removeIf(id -> plugin.getServer().getPlayer(id) == null);
                List<Player> left = online(spleefPlayers);
                if (left.size() <= 1) {
                    for (Player w : left) {
                        takeShovel(w);
                        plugin.progress().add(w, "spleef.wins", 1);
                        plugin.messages().broadcast("attractions.spleef.winner", "player", w.getName());
                        SeasonAmbienceService.fireworks(w.getLocation().add(0, 1, 0), 1);
                        ActivityRewards.pay(plugin, w, "spleef", spleefGems, spleefCoins);
                        sendOut(Game.SPLEEF, w);
                    }
                    spleefPlayers.clear();
                    restoreSpleef();
                    spleef = State.IDLE;
                }
            }
            default -> {
            }
        }
    }

    private ItemStack shovel() {
        ItemStack s = new ItemBuilder(Material.DIAMOND_SHOVEL).name(plugin.messages().raw("attractions.spleef.shovel")).glint(true).build();
        s.editMeta(m -> {
            m.addEnchant(Enchantment.EFFICIENCY, 5, true);
            m.getPersistentDataContainer().set(Keys.key("spleef_tool"), PersistentDataType.BYTE, (byte) 1);
        });
        return s;
    }

    private static boolean tagged(ItemStack i, String key) {
        return i != null && i.hasItemMeta() && i.getItemMeta().getPersistentDataContainer().has(Keys.key(key), PersistentDataType.BYTE);
    }

    private void takeShovel(Player p) {
        for (ItemStack i : p.getInventory().getContents()) {
            if (tagged(i, "spleef_tool")) {
                p.getInventory().remove(i);
            }
        }
    }

    /** Spleef: one click breaks the snow (the spawn protection does not apply to the game floor). */
    @EventHandler(priority = EventPriority.LOW)
    public void onDig(PlayerInteractEvent event) {
        if (event.getAction() == Action.PHYSICAL) {
            onPlate(event);
            return;
        }
        if (event.getAction() != Action.LEFT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        Player p = event.getPlayer();
        Block b = event.getClickedBlock();
        if (spleef != State.RUNNING || !spleefPlayers.contains(p.getUniqueId()) || b.getType() != Material.SNOW_BLOCK) {
            return;
        }
        Location c = center(Game.SPLEEF);
        if (c == null || b.getY() != c.getBlockY() - 1 || !onFloorArea(Game.SPLEEF, c, b.getLocation().add(0.5, 0, 0.5))) {
            return;
        }
        event.setCancelled(true);
        b.setType(Material.AIR, false);
        b.getWorld().spawnParticle(Particle.SNOWFLAKE, b.getLocation().add(0.5, 0.8, 0.5), 6, 0.3, 0.1, 0.3, 0.02);
        SoundSpec.of("block.snow.break", 0.8f, 1.2f).playAt(b.getLocation());
    }

    // ---- target range -------------------------------------------------------------------------------------

    private void onPlate(PlayerInteractEvent event) {
        Block b = event.getClickedBlock();
        Location start = plugin.attractionStore().first("range_start");
        if (b == null || start == null || !b.getWorld().equals(start.getWorld()) || b.getX() != start.getBlockX()
                || b.getY() != start.getBlockY() || b.getZ() != start.getBlockZ()) {
            return;
        }
        Player p = event.getPlayer();
        if (shooter != null) {
            if (!shooter.equals(p.getUniqueId())) {
                plugin.messages().actionBar(p, "attractions.range.busy");
            }
            return;
        }
        shooter = p.getUniqueId();
        rangeLeft = rangeSeconds * 4;
        rangeHits = 0;
        ItemStack bow = new ItemBuilder(Material.BOW).name(plugin.messages().raw("attractions.range.bow")).build();
        bow.editMeta(m -> {
            m.addEnchant(Enchantment.INFINITY, 1, true);
            m.setUnbreakable(true);
            m.getPersistentDataContainer().set(Keys.key("range_tool"), PersistentDataType.BYTE, (byte) 1);
        });
        ItemStack arrow = new ItemStack(Material.ARROW);
        arrow.editMeta(m -> m.getPersistentDataContainer().set(Keys.key("range_tool"), PersistentDataType.BYTE, (byte) 1));
        p.getInventory().addItem(bow, arrow);
        plugin.messages().send(p, "attractions.range.start", "seconds", rangeSeconds);
        SoundSpec.of("entity.arrow.shoot", 1f, 0.8f).play(p);
        lightNext();
    }

    private void lightNext() {
        List<Location> targets = plugin.attractionStore().all("target");
        if (lit != null && lit.isChunkLoaded()) {
            lit.getBlock().setType(Material.TARGET, false);
        }
        if (targets.isEmpty()) {
            lit = null;
            return;
        }
        Location next = targets.get(ThreadLocalRandom.current().nextInt(targets.size()));
        if (lit != null && targets.size() > 1) {
            while (next.getBlockX() == lit.getBlockX() && next.getBlockY() == lit.getBlockY()) {
                next = targets.get(ThreadLocalRandom.current().nextInt(targets.size()));
            }
        }
        lit = next.clone();
        if (lit.isChunkLoaded()) {
            lit.getBlock().setType(Material.GOLD_BLOCK, false);
        }
    }

    private void tickRange() {
        if (shooter == null) {
            return;
        }
        Player p = plugin.getServer().getPlayer(shooter);
        Location stand = plugin.attractionStore().first("range_stand");
        if (p == null || stand == null || !p.getWorld().equals(stand.getWorld()) || p.getLocation().distanceSquared(stand) > 8 * 8) {
            endRange(p, true);
            return;
        }
        if (lit != null && rangeLeft % 2 == 0) {
            lit.getWorld().spawnParticle(Particle.WAX_ON, lit.clone().add(0.5, 0.5, 1.1), 4, 0.3, 0.3, 0.05, 0);
        }
        if (rangeLeft % 4 == 0) {
            plugin.messages().actionBar(p, "attractions.range.status", "hits", rangeHits, "seconds", rangeLeft / 4);
        }
        if (--rangeLeft <= 0) {
            endRange(p, false);
        }
    }

    private void endRange(Player p, boolean aborted) {
        shooter = null;
        if (lit != null && lit.isChunkLoaded()) {
            lit.getBlock().setType(Material.TARGET, false);
        }
        lit = null;
        if (p == null) {
            return;
        }
        for (ItemStack i : p.getInventory().getContents()) {
            if (tagged(i, "range_tool")) {
                p.getInventory().remove(i);
            }
        }
        if (aborted) {
            plugin.messages().send(p, "attractions.range.aborted");
            return;
        }
        long best = plugin.progress().get(p, "range.best");
        boolean record = rangeHits > best;
        if (record) {
            plugin.progress().set(p, "range.best", rangeHits);
        }
        plugin.messages().send(p, record ? "attractions.range.record" : "attractions.range.done", "hits", rangeHits,
                "best", Math.max(best, rangeHits));
        SoundSpec.of("ui.toast.challenge_complete", 0.8f, 1.2f).play(p);
        if (rangeHits >= rangeRewardHits) {
            ActivityRewards.pay(plugin, p, "range", rangeGems, rangeCoins);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (event.getEntity() instanceof Player p && p.getUniqueId().equals(shooter) && event.getProjectile() instanceof AbstractArrow a) {
            a.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
            a.getPersistentDataContainer().set(Keys.key("range_tool"), PersistentDataType.BYTE, (byte) 1);
        }
    }

    @EventHandler
    public void onHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof AbstractArrow a) || !a.getPersistentDataContainer().has(Keys.key("range_tool"), PersistentDataType.BYTE)) {
            return;
        }
        Block b = event.getHitBlock();
        plugin.tasks().later(1, "range arrow", a::remove);
        if (b == null || lit == null || shooter == null || b.getX() != lit.getBlockX() || b.getY() != lit.getBlockY() || b.getZ() != lit.getBlockZ()) {
            return;
        }
        Player p = plugin.getServer().getPlayer(shooter);
        rangeHits++;
        b.getWorld().spawnParticle(Particle.FIREWORK, b.getLocation().add(0.5, 0.5, 1), 20, 0.2, 0.2, 0.2, 0.1);
        if (p != null) {
            SoundSpec.of("entity.experience_orb.pickup", 1f, 1.6f).play(p);
        }
        lightNext();
    }

    // ---- shared -------------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFall(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player p && event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            Location c1 = center(Game.PARTY);
            Location c2 = center(Game.SPLEEF);
            if (c1 != null && onFloorArea(Game.PARTY, c1, p.getLocation()) || c2 != null && onFloorArea(Game.SPLEEF, c2, p.getLocation())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        ItemStack i = event.getItemDrop().getItemStack();
        if (tagged(i, "spleef_tool") || tagged(i, "range_tool")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        takeShovel(p);
        partyPlayers.remove(p.getUniqueId());
        spleefPlayers.remove(p.getUniqueId());
        if (p.getUniqueId().equals(shooter)) {
            endRange(p, true);
        }
    }

    /** /blockparty, /spleef, /zielschiessen – rules, own record and a teleport there. */
    public void info(Player p, Game g) {
        String key = switch (g) {
            case PARTY -> "party";
            case SPLEEF -> "spleef";
            case RANGE -> "range";
        };
        long best = plugin.progress().get(p, g == Game.PARTY ? "blockparty.best" : g == Game.SPLEEF ? "spleef.wins" : "range.best");
        plugin.messages().send(p, "attractions." + key + ".info", "best", best);
        Location to = g == Game.RANGE ? plugin.attractionStore().first("range_stand") : exit(g);
        if (to != null && plugin.attractionStore().inside(p.getLocation(), 40)) {
            plugin.teleports().teleport(p, to, 0, false, plugin.messages().get("attractions." + key + ".place"), null);
        }
    }

    public void shutdown() {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            takeShovel(p);
        }
        if (shooter != null) {
            endRange(plugin.getServer().getPlayer(shooter), true);
        }
    }
}
