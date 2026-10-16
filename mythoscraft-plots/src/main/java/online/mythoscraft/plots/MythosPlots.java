package online.mythoscraft.plots;

import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * MythosCraft plot server: an endless plot world (CityBuild style), /plot to claim and manage plots, full protection,
 * the same resource pack as the main server and /smp (/mythos, /hub) back to the main server.
 */
public final class MythosPlots extends JavaPlugin {

    private PlotGrid grid;
    private PlotGenerator generator;
    private PlotStore store;
    private final Msg msg = new Msg();
    private PackHost pack;
    private World world;
    private Material borderFree;
    private Material borderClaimed;
    private RankLimits ranks;
    private PlotBoard board;
    private SyncClient sync;
    /** who used /smp recently – the quit message then says "went back to MythosCraft" */
    private final java.util.Map<java.util.UUID, Long> hops = new java.util.concurrent.ConcurrentHashMap<>();

    boolean hopped(Player p) {
        Long at = hops.remove(p.getUniqueId());
        return at != null && System.currentTimeMillis() - at < 120_000;
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        // settings added in newer versions are written into the existing config.yml (otherwise e.g. the
        // address of the survival server would be missing and the sync could not connect)
        getConfig().options().copyDefaults(true);
        saveConfig();
        // config of an older version: survival + same inventory as on MythosCraft from now on
        if (getConfig().getInt("config-version", 1) < 2) {
            getConfig().set("world.gamemode", "SURVIVAL");
            getConfig().set("config-version", 2);
            saveConfig();
        }
        msg.load(this);
        grid = new PlotGrid(getConfig().getInt("plots.size", 48), getConfig().getInt("plots.road", 7));
        borderFree = material("plots.border-free", Material.SMOOTH_STONE_SLAB);
        borderClaimed = material("plots.border-claimed", Material.QUARTZ_SLAB);
        generator = new PlotGenerator(grid, material("plots.road-block", Material.POLISHED_ANDESITE),
                material("plots.road-edge-block", Material.STONE_BRICKS), borderFree);
        store = new PlotStore(getDataFolder(), getLogger());
        store.load();
        world = createWorld();
        PlotCommand cmd = new PlotCommand(this);
        var plot = getCommand("plot");
        plot.setExecutor(cmd);
        plot.setTabCompleter(cmd);
        BackCommand back = new BackCommand(this);
        getCommand("smp").setExecutor(back);
        getServer().getPluginManager().registerEvents(new ProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        ranks = new RankLimits(this);
        board = new PlotBoard(this);
        sync = new SyncClient(this);
        getServer().getPluginManager().registerEvents(sync, this);
        getServer().getPluginManager().registerEvents(ranks, this);
        getServer().getPluginManager().registerEvents(board, this);
        getCommand("gm").setExecutor(new GameModeCommand(this));
        BalanceCommand balance = new BalanceCommand(this);
        getCommand("balance").setExecutor(balance);
        getServer().getPluginManager().registerEvents(new BorderMenu(this), this);
        pack = new PackHost(this);
        pack.start();
        getLogger().info("Plot world '" + world.getName() + "' ready – " + store.all().size() + " plots claimed.");
    }

    @Override
    public void onDisable() {
        if (sync != null) {
            sync.shutdown();
        }
        if (store != null) {
            store.save();
        }
        if (pack != null) {
            pack.stop();
        }
    }

    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        if (generator == null) {
            grid = new PlotGrid(getConfig().getInt("plots.size", 48), getConfig().getInt("plots.road", 7));
            generator = new PlotGenerator(grid, Material.POLISHED_ANDESITE, Material.STONE_BRICKS, Material.SMOOTH_STONE_SLAB);
        }
        return generator;
    }

    private Material material(String path, Material def) {
        Material m = Material.matchMaterial(getConfig().getString(path, def.name()));
        return m == null || !m.isBlock() ? def : m;
    }

    private World createWorld() {
        String name = getConfig().getString("world.name", "plots");
        World w = Bukkit.getWorld(name);
        if (w == null) {
            w = new WorldCreator(name).generator(generator).generateStructures(false).createWorld();
        }
        w.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        w.setGameRule(GameRule.DO_FIRE_TICK, false);
        w.setGameRule(GameRule.MOB_GRIEFING, false);
        w.setGameRule(GameRule.KEEP_INVENTORY, true);
        w.setGameRule(GameRule.DO_INSOMNIA, false);
        w.setGameRule(GameRule.DO_TRADER_SPAWNING, false);
        w.setGameRule(GameRule.DO_PATROL_SPAWNING, false);
        if (getConfig().getBoolean("world.always-day", true)) {
            w.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            w.setTime(6000);
        }
        if (getConfig().getBoolean("world.no-weather", true)) {
            w.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
            w.setStorm(false);
            w.setThundering(false);
        }
        w.setSpawnLocation(spawn(w));
        return w;
    }

    private Location spawn(World w) {
        double c = grid.road() / 2.0;
        return new Location(w, c, PlotGrid.SURFACE + 1, c, -45, 0);
    }

    // ------------------------------------------------------------------------------------------- helpers
    public PlotGrid grid() {
        return grid;
    }

    public PlotStore store() {
        return store;
    }

    public Msg msg() {
        return msg;
    }

    public World world() {
        return world;
    }

    public Location spawn() {
        return spawn(world);
    }

    public boolean isPlotWorld(World w) {
        return w != null && w.equals(world);
    }

    public PlotId plotAt(Location l) {
        return isPlotWorld(l.getWorld()) ? grid.plotAt(l.getBlockX(), l.getBlockZ()) : null;
    }

    public boolean bypass(Player p) {
        return p.hasPermission("mythosplots.admin");
    }

    /** May the player change blocks / entities at this spot? */
    public boolean mayBuild(Player p, Location l) {
        if (!isPlotWorld(l.getWorld()) || bypass(p)) {
            return true;
        }
        Plot plot = store.get(plotAt(l));
        return plot != null && plot.mayBuild(p.getUniqueId());
    }

    public int maxPlots(Player p) {
        if (p.hasPermission("mythosplots.admin")) {
            return Integer.MAX_VALUE;
        }
        int max = getConfig().getInt("plots.max-per-player", 1);
        max = Math.max(max, ranks == null ? 0 : ranks.plots(p));      // MythosCraft rank (rank-perks.<rank>.plots)
        for (int n = 1; n <= 100; n++) {
            if (p.hasPermission("mythosplots.plots." + n)) {
                max = Math.max(max, n);
            }
        }
        return max;
    }

    /** admins & co.: OP / mythosplots.gamemode, or one of the ranks in world.gamemode-ranks on MythosCraft */
    public boolean mayChangeGameMode(Player p) {
        if (bypass(p) || p.hasPermission("mythosplots.gamemode")) {
            return true;
        }
        RankLimits.Info i = ranks == null ? null : ranks.info(p.getUniqueId());
        return i != null && getConfig().getStringList("world.gamemode-ranks").stream().anyMatch(r -> r.equalsIgnoreCase(i.rank()));
    }

    public PlotBoard board() {
        return board;
    }

    public SyncClient sync() {
        return sync;
    }

    public RankLimits ranks() {
        return ranks;
    }

    /**
     * The plot entrance: an invisible, fixed spot just inside the plot (middle of the side facing the road), looking
     * into the plot. Everyone – the owner too – arrives here; it can't be moved. If something was built on it, the
     * player stands on top of it.
     */
    public Location entrance(PlotId id) {
        int x = grid.centerX(id);
        int z = grid.minZ(id) + 1;
        int y = PlotGrid.SURFACE + 1;
        int top = world.getMaxHeight() - 2;
        while (y < top && (!world.getBlockAt(x, y, z).isPassable() || !world.getBlockAt(x, y + 1, z).isPassable())) {
            y++;
        }
        return new Location(world, x + 0.5, y, z + 0.5, 0, 10);
    }

    public Material defaultBorder(boolean claimed) {
        return claimed ? borderClaimed : borderFree;
    }

    /** Border slabs around a plot: different block for claimed plots, or the one the owner chose. */
    public void updateBorder(PlotId id, boolean claimed) {
        Plot plot = claimed ? store.get(id) : null;
        Material m = plot != null && plot.border != null ? plot.border : claimed ? borderClaimed : borderFree;
        int y = PlotGrid.SURFACE + 1;
        for (int x = grid.minX(id) - 1; x <= grid.maxX(id) + 1; x++) {
            world.getBlockAt(x, y, grid.minZ(id) - 1).setType(m, false);
            world.getBlockAt(x, y, grid.maxZ(id) + 1).setType(m, false);
        }
        for (int z = grid.minZ(id); z <= grid.maxZ(id); z++) {
            world.getBlockAt(grid.minX(id) - 1, y, z).setType(m, false);
            world.getBlockAt(grid.maxX(id) + 1, y, z).setType(m, false);
        }
    }

    /** Resets a plot to fresh grass, a few columns per tick (no lag). */
    public void reset(PlotId id, Runnable done) {
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();
        Deque<int[]> columns = new ArrayDeque<>();
        for (int x = grid.minX(id); x <= grid.maxX(id); x++) {
            for (int z = grid.minZ(id); z <= grid.maxZ(id); z++) {
                columns.add(new int[]{x, z});
            }
        }
        for (Entity e : world.getNearbyEntities(new org.bukkit.util.BoundingBox(grid.minX(id), minY, grid.minZ(id),
                grid.maxX(id) + 1, maxY, grid.maxZ(id) + 1))) {
            if (!(e instanceof Player)) {
                e.remove();
            }
        }
        int perTick = Math.max(1, getConfig().getInt("plots.reset-columns-per-tick", 12));
        new BukkitRunnable() {
            @Override
            public void run() {
                for (int i = 0; i < perTick && !columns.isEmpty(); i++) {
                    int[] c = columns.poll();
                    for (int y = minY; y < maxY; y++) {
                        Material want = generator.blockAt(c[0], y, c[1], minY);
                        var b = world.getBlockAt(c[0], y, c[1]);
                        if (b.getType() != want) {
                            b.setType(want, false);
                        }
                    }
                }
                if (columns.isEmpty()) {
                    cancel();
                    if (done != null) {
                        done.run();
                    }
                }
            }
        }.runTaskTimer(this, 1, 1);
    }

    /** Back to the main server: proxy "Connect" first, after 2 s the /mk join button. */
    public void backToMain(Player p) {
        hops.put(p.getUniqueId(), System.currentTimeMillis());
        String server = getConfig().getString("main-server.proxy-server", "mythoscraftsmp").trim();
        String cmd = getConfig().getString("main-server.join-command", "mk join mythoscraftsmp").trim();
        String full = cmd.startsWith("/") ? cmd : "/" + cmd;
        p.showTitle(Title.title(msg.get("transfer.title"), msg.get("transfer.subtitle"),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1500), Duration.ofMillis(300))));
        try {
            if (!getServer().getMessenger().isOutgoingChannelRegistered(this, "BungeeCord")) {
                getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
            }
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            java.io.DataOutputStream out = new java.io.DataOutputStream(bytes);
            out.writeUTF("Connect");
            out.writeUTF(server);
            p.sendPluginMessage(this, "BungeeCord", bytes.toByteArray());
        } catch (Exception e) {
            getLogger().warning("Proxy connect to " + server + " failed: " + e.getMessage());
        }
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (p.isOnline()) {
                BackCommand.offer(this, p, full);
            }
        }, 40);
    }
}
