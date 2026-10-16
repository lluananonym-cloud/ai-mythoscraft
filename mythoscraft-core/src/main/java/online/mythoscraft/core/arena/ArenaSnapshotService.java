package online.mythoscraft.core.arena;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.util.Cuboid;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Projectile;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Block snapshots of arenas. Capturing and restoring are spread over many ticks (configurable block budget per
 * tick) so even large arenas never freeze the server; file IO runs off the main thread. Only block states are
 * restored – container contents are not part of a snapshot.
 */
public final class ArenaSnapshotService {

    private static final String MAGIC = "MCSNAP1";

    private final MythosCraft plugin;
    private final File folder;
    private long maxVolume = 2_000_000;
    private int capturePerTick = 20_000;
    private int restorePerTick = 4_000;

    public ArenaSnapshotService(MythosCraft plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "arenas");
    }

    public void reload(ConfigReader cfg) {
        maxVolume = cfg.longValue("arena.snapshot.max-volume", 2_000_000, 1_000, 50_000_000);
        capturePerTick = cfg.integer("arena.snapshot.capture-blocks-per-tick", 20_000, 500, 1_000_000);
        restorePerTick = cfg.integer("arena.snapshot.restore-blocks-per-tick", 4_000, 100, 200_000);
    }

    public boolean exists(Arena arena) {
        return file(arena).exists();
    }

    public long maxVolume() {
        return maxVolume;
    }

    private File file(Arena arena) {
        return new File(folder, arena.name() + ".snapshot");
    }

    /** Reads all blocks of the arena and writes them to disk. {@code done} receives null or an error text. */
    public void capture(Arena arena, Consumer<String> done) {
        World world = Bukkit.getWorld(arena.world());
        Cuboid b = arena.bounds();
        if (world == null) {
            done.accept("Welt nicht geladen");
            return;
        }
        if (b.volume() > maxVolume) {
            done.accept("Arena zu groß (" + b.volume() + " > " + maxVolume + " Blöcke)");
            return;
        }
        int volume = (int) b.volume();
        int[] data = new int[volume];
        Map<String, Integer> palette = new HashMap<>();
        List<String> paletteList = new ArrayList<>();
        withChunks(world, b, release -> new BukkitRunnable() {
            int index;

            @Override
            public void run() {
                try {
                    int end = Math.min(volume, index + capturePerTick);
                    for (; index < end; index++) {
                        Block block = blockAt(world, b, index);
                        String state = block.getBlockData().getAsString();
                        Integer id = palette.get(state);
                        if (id == null) {
                            id = paletteList.size();
                            palette.put(state, id);
                            paletteList.add(state);
                        }
                        data[index] = id;
                    }
                    if (index >= volume) {
                        cancel();
                        release.run();
                        plugin.tasks().io("write snapshot " + arena.name(), () -> {
                            String error = write(file(arena), b, paletteList, data);
                            plugin.tasks().sync(() -> done.accept(error));
                        });
                    }
                } catch (RuntimeException e) {
                    cancel();
                    release.run();
                    plugin.getLogger().severe("Snapshot of arena " + arena.name() + " failed: " + e.getMessage());
                    done.accept(e.getMessage());
                }
            }
        }.runTaskTimer(plugin, 1, 1));
    }

    /** Restores the arena from its snapshot. {@code done} receives null or an error text. */
    public void restore(Arena arena, Consumer<String> done) {
        World world = Bukkit.getWorld(arena.world());
        if (world == null) {
            done.accept("Welt nicht geladen");
            return;
        }
        File file = file(arena);
        CompletableFuture.supplyAsync(() -> read(file), r -> plugin.tasks().io("read snapshot " + arena.name(), r))
                .whenComplete((snap, ex) -> plugin.tasks().sync(() -> {
                    if (ex != null || snap == null) {
                        done.accept(ex != null ? ex.getMessage() : "Snapshot fehlt oder ist beschädigt");
                        return;
                    }
                    Cuboid b = arena.bounds();
                    if (snap.sizeX != b.sizeX() || snap.sizeY != b.sizeY() || snap.sizeZ != b.sizeZ()) {
                        done.accept("Arena-Größe hat sich seit dem Snapshot geändert – bitte neuen Snapshot erstellen");
                        return;
                    }
                    BlockData[] palette = new BlockData[snap.palette.size()];
                    for (int i = 0; i < palette.length; i++) {
                        palette[i] = Bukkit.createBlockData(snap.palette.get(i));
                    }
                    int volume = snap.data.length;
                    withChunks(world, b, release -> new BukkitRunnable() {
                        int index;

                        @Override
                        public void run() {
                            try {
                                int end = Math.min(volume, index + restorePerTick);
                                for (; index < end; index++) {
                                    Block block = blockAt(world, b, index);
                                    BlockData target = palette[snap.data[index]];
                                    if (!block.getBlockData().equals(target)) {
                                        block.setBlockData(target, false);
                                    }
                                }
                                if (index >= volume) {
                                    cancel();
                                    clearLooseEntities(world, b);
                                    release.run();
                                    done.accept(null);
                                }
                            } catch (RuntimeException e) {
                                cancel();
                                release.run();
                                plugin.getLogger().severe("Reset of arena " + arena.name() + " failed: " + e.getMessage());
                                done.accept(e.getMessage());
                            }
                        }
                    }.runTaskTimer(plugin, 1, 1));
                }));
    }

    private void clearLooseEntities(World world, Cuboid b) {
        for (Entity e : world.getEntities()) {
            if ((e instanceof Item || e instanceof Projectile) && b.contains(e.getLocation().getBlockX(),
                    e.getLocation().getBlockY(), e.getLocation().getBlockZ())) {
                e.remove();
            }
        }
    }

    private void withChunks(World world, Cuboid b, Consumer<Runnable> job) {
        online.mythoscraft.core.util.Chunks.withLoaded(plugin, world, b.minX(), b.minZ(), b.maxX(), b.maxZ(), job);
    }

    private static Block blockAt(World world, Cuboid b, int index) {
        int sx = b.sizeX();
        int sz = b.sizeZ();
        int x = index % sx;
        int z = (index / sx) % sz;
        int y = index / (sx * sz);
        return world.getBlockAt(b.minX() + x, b.minY() + y, b.minZ() + z);
    }

    private String write(File target, Cuboid b, List<String> palette, int[] data) {
        try {
            Files.createDirectories(target.getParentFile().toPath());
            File tmp = new File(target.getParentFile(), target.getName() + ".tmp");
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(tmp.toPath()))))) {
                out.writeUTF(MAGIC);
                out.writeInt(b.sizeX());
                out.writeInt(b.sizeY());
                out.writeInt(b.sizeZ());
                out.writeInt(palette.size());
                for (String s : palette) {
                    out.writeUTF(s);
                }
                for (int v : data) {
                    writeVarInt(out, v);
                }
            }
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return null;
        } catch (IOException e) {
            plugin.getLogger().severe("Could not write snapshot " + target.getName() + ": " + e.getMessage());
            return e.getMessage();
        }
    }

    private record Snapshot(int sizeX, int sizeY, int sizeZ, List<String> palette, int[] data) {
    }

    private Snapshot read(File file) {
        if (!file.exists()) {
            return null;
        }
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(Files.newInputStream(file.toPath()))))) {
            if (!MAGIC.equals(in.readUTF())) {
                return null;
            }
            int sx = in.readInt();
            int sy = in.readInt();
            int sz = in.readInt();
            long volume = (long) sx * sy * sz;
            if (sx <= 0 || sy <= 0 || sz <= 0 || volume > 100_000_000L) {
                return null;
            }
            int paletteSize = in.readInt();
            List<String> palette = new ArrayList<>(paletteSize);
            for (int i = 0; i < paletteSize; i++) {
                palette.add(in.readUTF());
            }
            int[] data = new int[(int) volume];
            for (int i = 0; i < data.length; i++) {
                int v = readVarInt(in);
                if (v < 0 || v >= paletteSize) {
                    return null;
                }
                data[i] = v;
            }
            return new Snapshot(sx, sy, sz, palette, data);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not read snapshot " + file.getName() + ": " + e.getMessage());
            return null;
        }
    }

    private static void writeVarInt(DataOutputStream out, int value) throws IOException {
        while ((value & ~0x7F) != 0) {
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.writeByte(value);
    }

    private static int readVarInt(DataInputStream in) throws IOException {
        int value = 0;
        int shift = 0;
        byte b;
        do {
            b = in.readByte();
            value |= (b & 0x7F) << shift;
            shift += 7;
            if (shift > 35) {
                throw new IOException("VarInt too long");
            }
        } while ((b & 0x80) != 0);
        return value;
    }
}
