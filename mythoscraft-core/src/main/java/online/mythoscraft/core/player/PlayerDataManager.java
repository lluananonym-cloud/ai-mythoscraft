package online.mythoscraft.core.player;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Write-back cache of player data.
 * <ul>
 *   <li>Loaded in {@code AsyncPlayerPreLoginEvent} (off the main thread). If loading fails the login is refused,
 *   so a player can never play on empty data that would later overwrite the real row.</li>
 *   <li>Saved on quit, periodically (dirty entries only), immediately after economy transfers and on disable.</li>
 *   <li>Every load/save runs on the single database thread, so a save queued on quit is always applied before a
 *   load queued by a fast re-join.</li>
 *   <li>Saves that fail are kept and retried; a pending retry is preferred over the (older) database row on load.</li>
 * </ul>
 */
public final class PlayerDataManager {

    private final MythosCraft plugin;
    private final DatabaseManager db;
    private final PlayerRepository repo;
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerData.Snapshot> retry = new ConcurrentHashMap<>();
    private int autosaveSeconds = 300;

    public PlayerDataManager(MythosCraft plugin, DatabaseManager db) {
        this.plugin = plugin;
        this.db = db;
        this.repo = new PlayerRepository(db.dialect());
    }

    public PlayerRepository repository() {
        return repo;
    }

    public void reload(ConfigReader config) {
        autosaveSeconds = config.integer("autosave.interval-seconds", 300, 30, 3600);
        plugin.tasks().cancel(this);
        long ticks = autosaveSeconds * 20L;
        plugin.tasks().repeat(this, ticks, ticks, "autosave", this::autosave);
    }

    // ---- access -------------------------------------------------------------------------------------------

    /** Null while the data of this player is still loading (only possible right after /reload). */
    public PlayerData get(Player player) {
        return cache.get(player.getUniqueId());
    }

    public PlayerData get(UUID uuid) {
        return cache.get(uuid);
    }

    public Collection<PlayerData> cached() {
        return cache.values();
    }

    // ---- lifecycle ----------------------------------------------------------------------------------------

    /** Called from AsyncPlayerPreLoginEvent. Returns false if the data could not be loaded. */
    public boolean loadBlocking(UUID uuid, String name) {
        try {
            db.await(db.query("load player " + name, c -> loadInto(c, uuid, name)), 15);
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Could not load data of " + name + " (" + uuid + "): " + e.getMessage());
            return false;
        }
    }

    /** Fallback for players that are online without a pre-login (plugin reload, duplicate login). */
    public void loadAsync(Player player) {
        UUID uuid = player.getUniqueId();
        String name = player.getName();
        db.query("load player " + name, c -> loadInto(c, uuid, name)).whenComplete((data, ex) -> plugin.tasks().sync(() -> {
            Player online = Bukkit.getPlayer(uuid);
            if (ex != null) {
                if (online != null) {
                    plugin.messages().send(online, "general.data-load-failed");
                }
                return;
            }
            if (online != null) {
                attach(online, data);
            }
        }));
    }

    /** Runs on the database thread. */
    private PlayerData loadInto(Connection c, UUID uuid, String name) throws SQLException {
        PlayerData existing = cache.get(uuid);
        if (existing != null) {
            return existing;
        }
        PlayerData data;
        PlayerData.Snapshot pending = retry.remove(uuid);
        if (pending != null) {
            data = PlayerData.fromSnapshot(pending);
            data.markDirty();
        } else {
            PlayerData.Snapshot row = repo.load(c, uuid);
            if (row == null) {
                data = new PlayerData(uuid, name);
                data.fresh(true);
                repo.save(c, data.snapshot());
            } else {
                data = PlayerData.fromSnapshot(row);
            }
        }
        data.loadHomes(repo.loadHomes(c, uuid));
        cache.put(uuid, data);
        return data;
    }

    /** Join handling on the main thread. */
    public void handleJoin(Player player) {
        PlayerData data = cache.get(player.getUniqueId());
        if (data == null) {
            loadAsync(player);
            return;
        }
        attach(player, data);
    }

    private void attach(Player player, PlayerData data) {
        long now = System.currentTimeMillis();
        data.name(player.getName());
        data.startSession(now);
        data.lastSeen(now);
        boolean firstJoin = data.firstJoin() == 0;
        if (firstJoin) {
            data.firstJoin(now);
        }
        plugin.onPlayerDataReady(player, data, firstJoin);
    }

    public void handleQuit(Player player) {
        PlayerData data = cache.remove(player.getUniqueId());
        if (data == null) {
            return;
        }
        long now = System.currentTimeMillis();
        data.endSession(now);
        data.lastSeen(now);
        save(data);
    }

    /** Pre-login got denied by someone else: drop the cached entry once all queued main-thread work ran. */
    public void handleDeniedLogin(UUID uuid) {
        plugin.tasks().sync(() -> {
            if (Bukkit.getPlayer(uuid) != null) {
                return;
            }
            PlayerData data = cache.remove(uuid);
            if (data != null && data.isDirty()) {
                save(data);
            }
        });
    }

    // ---- saving -------------------------------------------------------------------------------------------

    public CompletableFuture<Void> save(PlayerData data) {
        PlayerData.Snapshot snapshot = data.snapshot();
        data.clearDirty();
        return db.query("save player " + snapshot.name(), c -> {
            repo.save(c, snapshot);
            retry.remove(snapshot.uuid());
            return (Void) null;
        }).whenComplete((v, ex) -> {
            if (ex != null) {
                retry.put(snapshot.uuid(), snapshot);
                plugin.tasks().sync(data::markDirty);
            }
        });
    }

    /** Saves several players atomically (used for transfers between two players). */
    public CompletableFuture<Void> saveTogether(String operation, List<PlayerData> players, DatabaseManager.SqlConsumer extra) {
        List<PlayerData.Snapshot> snapshots = new ArrayList<>();
        for (PlayerData d : players) {
            snapshots.add(d.snapshot());
            d.clearDirty();
        }
        return db.transaction(operation, c -> {
            repo.saveAll(c, snapshots);
            if (extra != null) {
                extra.accept(c);
            }
        }).whenComplete((v, ex) -> {
            if (ex != null) {
                plugin.tasks().sync(() -> players.forEach(PlayerData::markDirty));
            }
        });
    }

    public void autosave() {
        long now = System.currentTimeMillis();
        List<PlayerData> dirty = new ArrayList<>();
        for (PlayerData d : cache.values()) {
            if (Bukkit.getPlayer(d.uuid()) != null) {
                d.flushPlaytime(now);
            }
            if (d.isDirty()) {
                dirty.add(d);
            }
        }
        List<PlayerData.Snapshot> retries = new ArrayList<>(retry.values());
        if (dirty.isEmpty() && retries.isEmpty()) {
            return;
        }
        List<PlayerData.Snapshot> snapshots = new ArrayList<>(retries);
        for (PlayerData d : dirty) {
            snapshots.add(d.snapshot());
            d.clearDirty();
        }
        db.transaction("autosave (" + snapshots.size() + " players)", c -> repo.saveAll(c, snapshots)).whenComplete((v, ex) -> {
            if (ex == null) {
                retries.forEach(s -> retry.remove(s.uuid(), s));
            } else {
                plugin.tasks().sync(() -> dirty.forEach(PlayerData::markDirty));
            }
        });
    }

    /** Final save on disable. The database shutdown afterwards waits for it. */
    public void saveAllOnShutdown() {
        long now = System.currentTimeMillis();
        List<PlayerData.Snapshot> snapshots = new ArrayList<>(retry.values());
        for (PlayerData d : cache.values()) {
            if (Bukkit.getPlayer(d.uuid()) != null) {
                d.endSession(now);
                d.lastSeen(now);
            }
            snapshots.add(d.snapshot());
        }
        if (!snapshots.isEmpty()) {
            db.transaction("final save (" + snapshots.size() + " players)", c -> repo.saveAll(c, snapshots));
        }
    }

    // ---- offline access -----------------------------------------------------------------------------------

    /**
     * Applies a change to a player regardless of whether they are online. Online players are changed in the cache
     * on the main thread; offline rows are loaded, changed and written on the database thread. Completes with
     * false if the player never joined.
     */
    public CompletableFuture<Boolean> modify(UUID uuid, Consumer<PlayerData> mutation) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        db.query("modify player " + uuid, c -> {
            if (cache.containsKey(uuid)) {
                return Boolean.TRUE;
            }
            PlayerData.Snapshot base = retry.remove(uuid);
            if (base == null) {
                base = repo.load(c, uuid);
            }
            if (base == null) {
                return Boolean.FALSE;
            }
            PlayerData data = PlayerData.fromSnapshot(base);
            mutation.accept(data);
            repo.save(c, data.snapshot());
            return null;
        }).whenComplete((online, ex) -> {
            if (ex != null) {
                result.completeExceptionally(ex);
            } else if (online == null) {
                result.complete(true);
            } else if (!online) {
                result.complete(false);
            } else {
                plugin.tasks().sync(() -> {
                    PlayerData data = cache.get(uuid);
                    if (data != null) {
                        mutation.accept(data);
                        data.markDirty();
                        result.complete(true);
                    } else {
                        modify(uuid, mutation).whenComplete((r, e2) -> {
                            if (e2 != null) {
                                result.completeExceptionally(e2);
                            } else {
                                result.complete(r);
                            }
                        });
                    }
                });
            }
        });
        return result;
    }

    /** Resolves a name to uuid + current data (online first, then database). */
    public CompletableFuture<Optional<PlayerData.Snapshot>> lookup(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null && cache.containsKey(online.getUniqueId())) {
            return CompletableFuture.completedFuture(Optional.of(cache.get(online.getUniqueId()).snapshot()));
        }
        return db.query("lookup " + name, c -> Optional.ofNullable(repo.findByName(c, name)));
    }

    public CompletableFuture<PlayerData.Snapshot> loadSnapshot(UUID uuid) {
        PlayerData cached = cache.get(uuid);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached.snapshot());
        }
        return db.query("load snapshot " + uuid, c -> repo.load(c, uuid));
    }

    public CompletableFuture<Void> saveHome(UUID uuid, Home home) {
        return db.query("save home " + home.name(), c -> {
            repo.saveHome(c, uuid, home);
            return null;
        });
    }

    public CompletableFuture<Void> deleteHome(UUID uuid, String name) {
        return db.query("delete home " + name, c -> {
            repo.deleteHome(c, uuid, name);
            return null;
        });
    }
}
