package online.mythoscraft.core.attraction;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.economy.Currency;
import online.mythoscraft.core.economy.EconomyManager;
import online.mythoscraft.core.player.PlayerData;
import online.mythoscraft.core.util.Keys;
import online.mythoscraft.core.util.SoundSpec;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * All spawn attractions together: statues, wheel of fortune, elytra rings, wishing well, merchants, day/night lamps,
 * season decoration and the spawn music. Routes clicks on their entities and respawns them when their chunks load.
 */
public final class Attractions implements Listener {

    private final MythosCraft plugin;
    private final StatueService statues;
    private final WheelService wheel;
    private final RingService rings;
    private final WellService well;
    private final MerchantService merchants;
    private final DayNightService dayNight;
    private final SeasonDecoService deco;
    private final MusicService music;
    private final SpawnGlideService glide;
    private final SeasonAmbienceService ambience;
    private final SpawnGamesService games;
    private boolean rebuildQueued;
    private final java.util.List<Entity> mentor = new java.util.ArrayList<>();

    /** The mentor on the starter square: opens the quests (starter help for new players) + a notice board. */
    private void spawnMentor() {
        mentor.forEach(e -> {
            if (e.isValid()) {
                e.remove();
            }
        });
        mentor.clear();
        Location at = plugin.attractionStore().first("mentor");
        if (at == null || !at.isChunkLoaded()) {
            return;
        }
        mentor.add(at.getWorld().spawn(at, org.bukkit.entity.Villager.class, v -> {
            v.setPersistent(false);
            v.setInvulnerable(true);
            v.setSilent(true);
            v.setAI(false);
            v.setCollidable(false);
            v.setProfession(org.bukkit.entity.Villager.Profession.LIBRARIAN);
            v.setVillagerLevel(5);
            v.customName(plugin.messages().get("attractions.mentor.name"));
            v.setCustomNameVisible(true);
            v.getPersistentDataContainer().set(Keys.ENTITY, PersistentDataType.STRING, "mentor");
        }));
        Location board = plugin.attractionStore().first("starter_board");
        if (board != null) {
            mentor.add(board.getWorld().spawn(board, org.bukkit.entity.TextDisplay.class, t -> {
                t.setPersistent(false);
                t.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
                t.setShadowed(true);
                t.setBackgroundColor(org.bukkit.Color.fromARGB(0x70, 0x1b, 0x10, 0x22));
                t.text(plugin.messages().get("attractions.mentor.board"));
                t.getPersistentDataContainer().set(Keys.ENTITY, PersistentDataType.STRING, "mentor");
            }));
        }
    }

    private boolean mentorPresent() {
        return !mentor.isEmpty() && mentor.get(0).isValid();
    }

    public Attractions(MythosCraft plugin) {
        this.plugin = plugin;
        this.statues = new StatueService(plugin);
        this.wheel = new WheelService(plugin);
        this.rings = new RingService(plugin);
        this.well = new WellService(plugin);
        this.merchants = new MerchantService(plugin);
        this.dayNight = new DayNightService(plugin);
        this.deco = new SeasonDecoService(plugin);
        this.music = new MusicService(plugin);
        this.glide = new SpawnGlideService(plugin);
        this.ambience = new SeasonAmbienceService(plugin);
        this.games = new SpawnGamesService(plugin);
    }

    public List<Listener> listeners() {
        return List.of(this, rings, deco, glide, games);
    }

    public void reload(ConfigReader root) {
        statues.reload(root);
        wheel.reload(root);
        rings.reload(root);
        well.reload(root);
        merchants.reload(root);
        dayNight.reload(root);
        deco.reload(root);
        music.reload(root);
        glide.reload(root);
        ambience.reload(root);
        games.reload(root);
        plugin.tasks().cancel(this);
        plugin.tasks().repeat(this, 200, 200, "attraction watchdog", this::watchdog);
        plugin.tasks().later(40, "attractions", this::rebuild);
    }

    /** Respawns every attraction entity (after a reload or a new spawn build). */
    public void rebuild() {
        rebuild(true);
    }

    /** @param force false = only attractions that are missing (chunk was unloaded) */
    public void rebuild(boolean force) {
        rebuildQueued = false;
        if (force || !wheel.present()) {
            wheel.rebuild();
        }
        if (force || !well.present()) {
            well.rebuild();
        }
        if (force || !merchants.present()) {
            merchants.rebuild();
        }
        if (force || !statues.present()) {
            statues.refresh();
        }
        if (force || !mentorPresent()) {
            spawnMentor();
        }
        dayNight.refresh();
        rings.sweep();
    }

    /** Attractions whose chunk was unloaded are spawned again as soon as someone is near. */
    private void watchdog() {
        Location c = plugin.attractionStore().center();
        if (c == null || !c.isChunkLoaded()) {
            return;
        }
        Location w = plugin.attractionStore().first("wheel");
        boolean missing = (w != null && w.isChunkLoaded() && !wheel.present())
                || (plugin.attractionStore().first("well") != null && plugin.attractionStore().first("well").isChunkLoaded() && !well.present())
                || (!plugin.attractionStore().all("merchant").isEmpty() && plugin.attractionStore().all("merchant").get(0).isChunkLoaded()
                && !merchants.present());
        if (missing || (!plugin.attractionStore().all("statue").isEmpty() && plugin.attractionStore().all("statue").get(0).isChunkLoaded()
                && !statues.present())) {
            rebuild(false);
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (rebuildQueued || plugin.attractionStore().center() == null) {
            return;
        }
        Chunk chunk = event.getChunk();
        Location c = plugin.attractionStore().center();
        if (!chunk.getWorld().equals(c.getWorld())) {
            return;
        }
        int r = (plugin.attractionStore().radius() >> 4) + 1;
        if (Math.abs(chunk.getX() - (c.getBlockX() >> 4)) <= r && Math.abs(chunk.getZ() - (c.getBlockZ() >> 4)) <= r) {
            rebuildQueued = true;
            plugin.tasks().later(40, "attractions after chunk load", () -> rebuild(false));
        }
    }

    private static String tag(Entity e) {
        return e.getPersistentDataContainer().get(Keys.ENTITY, PersistentDataType.STRING);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Entity e = event.getRightClicked();
        String tag = tag(e);
        if (tag == null) {
            return;
        }
        Player p = event.getPlayer();
        switch (tag) {
            case WheelService.TAG -> {
                event.setCancelled(true);
                wheel.use(p);
            }
            case WellService.TAG -> {
                event.setCancelled(true);
                well.open(p);
            }
            case MerchantService.TAG -> {
                event.setCancelled(true);
                merchants.open(p, e);
            }
            case StatueService.TAG -> event.setCancelled(true);
            case "mentor" -> {
                event.setCancelled(true);
                plugin.quests().open(p);
            }
            default -> {
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteractAt(PlayerInteractAtEntityEvent event) {
        String tag = tag(event.getRightClicked());
        if (tag != null && (tag.equals(StatueService.TAG) || tag.equals(MerchantService.TAG) || tag.equals("mentor"))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onHit(EntityDamageByEntityEvent event) {
        if ("season_firework".equals(tag(event.getDamager()))) {
            event.setCancelled(true);
            return;
        }
        String tag = tag(event.getEntity());
        if (tag == null) {
            return;
        }
        switch (tag) {
            case WheelService.TAG, WellService.TAG, MerchantService.TAG, StatueService.TAG, "season_bat", "mentor" -> {
                event.setCancelled(true);
                if (tag.equals(WheelService.TAG) && event.getDamager() instanceof Player p) {
                    wheel.showPrizes(p);
                }
            }
            default -> {
            }
        }
    }

    /** Takes the price from the player; sends a message and returns false if they cannot pay. */
    public boolean charge(Player p, Currency currency, long amount) {
        if (amount <= 0) {
            return true;
        }
        PlayerData data = plugin.playerData().get(p);
        if (data == null) {
            plugin.messages().send(p, "general.data-loading");
            return false;
        }
        if (plugin.economy().withdraw(data, currency, amount) != EconomyManager.Result.SUCCESS) {
            plugin.messages().send(p, "attractions.not-enough", "price", amount,
                    "currency", plugin.messages().get("economy.names." + currency.id()), "balance", currency.get(data));
            SoundSpec.of("entity.villager.no", 0.8f, 1f).play(p);
            return false;
        }
        plugin.playerData().save(data);
        plugin.economy().log(p.getUniqueId(), null, currency, amount, "attraction");
        return true;
    }

    public StatueService statues() {
        return statues;
    }

    public WheelService wheel() {
        return wheel;
    }

    public RingService rings() {
        return rings;
    }

    public WellService well() {
        return well;
    }

    public MerchantService merchants() {
        return merchants;
    }

    public SeasonDecoService deco() {
        return deco;
    }

    public SpawnGamesService games() {
        return games;
    }

    public SeasonAmbienceService ambience() {
        return ambience;
    }

    public MusicService music() {
        return music;
    }

    public void shutdown() {
        plugin.tasks().cancel(this);
        mentor.forEach(e -> {
            if (e.isValid()) {
                e.remove();
            }
        });
        statues.shutdown();
        wheel.shutdown();
        rings.shutdown();
        well.shutdown();
        merchants.shutdown();
        dayNight.shutdown();
        deco.shutdown();
        music.shutdown();
        glide.shutdown();
        ambience.shutdown();
    }
}
