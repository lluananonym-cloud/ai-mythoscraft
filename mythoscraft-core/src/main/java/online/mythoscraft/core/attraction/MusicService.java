package online.mythoscraft.core.attraction;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.item.SeasonService;
import online.mythoscraft.core.util.SoundSpec;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Spawn radio: plays note block songs to every player at the spawn, in sync. Each season has its own playlist.
 * Players can switch it off (/musik aus); streamers (/stream on) only hear songs marked stream-safe. Songs come from
 * the built-in {@link SongBook} and from .nbs files in plugins/MythosCraft/music/.
 */
public final class MusicService {

    private final MythosCraft plugin;
    private final Map<String, Song> songs = new LinkedHashMap<>();
    private final Map<String, List<String>> playlists = new LinkedHashMap<>();
    private final Set<String> safeFiles = new HashSet<>();
    private boolean enabled = true;
    private float volume = 0.6f;
    private int pauseTicks = 60;
    private double extraRadius = 10;

    private Song current;
    private String currentList;
    private int tick;
    private int pause;
    private int index = -1;

    public MusicService(MythosCraft plugin) {
        this.plugin = plugin;
    }

    public void reload(ConfigReader root) {
        ConfigReader cfg = root.sub("music");
        enabled = cfg.bool("enabled", true);
        volume = (float) cfg.decimal("volume", 0.6, 0, 2);
        pauseTicks = cfg.integer("pause-seconds", 3, 0, 120) * 20;
        extraRadius = cfg.decimal("extra-radius", 10, 0, 200);
        safeFiles.clear();
        cfg.strings("stream-safe-files").forEach(s -> safeFiles.add(s.toLowerCase(Locale.ROOT)));
        songs.clear();
        songs.putAll(SongBook.all());
        songs.putAll(GrooveBook.all());
        loadFolder();
        playlists.clear();
        ConfigReader lists = cfg.sub("playlists");
        for (String key : lists.keys()) {
            List<String> ids = new ArrayList<>();
            for (String id : lists.strings(key)) {
                if (songs.containsKey(id.toLowerCase(Locale.ROOT))) {
                    ids.add(id.toLowerCase(Locale.ROOT));
                } else {
                    lists.warn(key, "Lied '" + id + "' gibt es nicht (eingebaut: " + String.join(", ", songs.keySet())
                            + " – eigene: Dateiname ohne .nbs aus plugins/MythosCraft/music/)");
                }
            }
            playlists.put(key.toLowerCase(Locale.ROOT), ids);
        }
        current = null;
        plugin.tasks().cancel(this);
        plugin.tasks().repeat(this, 40, 1, "spawn music", this::tick);
    }

    private void loadFolder() {
        File dir = new File(plugin.getDataFolder(), "music");
        if (!dir.isDirectory() && !dir.mkdirs()) {
            return;
        }
        File[] files = dir.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".nbs"));
        if (files == null) {
            return;
        }
        for (File f : files) {
            String id = f.getName().substring(0, f.getName().length() - 4).toLowerCase(Locale.ROOT).replace(' ', '_');
            try (InputStream in = new FileInputStream(f)) {
                Song s = Song.readNbs(id, in, safeFiles.contains(id));
                songs.put(id, s);
            } catch (IOException | RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "music/" + f.getName() + " konnte nicht gelesen werden: " + e.getMessage());
            }
        }
    }

    public Map<String, Song> songs() {
        return songs;
    }

    /** Playlist of the active season, or "default". */
    private String listKey() {
        if (playlists.containsKey("silvester") && plugin.attractions().ambience().silvesterTime()) {
            return "silvester";
        }
        SeasonService.Season s = plugin.seasons().active();
        if (s != null && playlists.containsKey(s.id().toLowerCase(Locale.ROOT))) {
            return s.id().toLowerCase(Locale.ROOT);
        }
        return "default";
    }

    // ---- recorded songs for everyone (/snowman) -------------------------------------------------------------

    private String recorded;
    private String recordedTitle;
    private int recordedLeft;

    /**
     * Plays a real recording from the resource pack (e.g. {@code mythoscraft:music.snowman}) once to every online
     * player with music on; not to streamers. The spawn radio pauses meanwhile.
     */
    public void playRecording(String soundKey, String title, int seconds) {
        stopRecording();
        recorded = soundKey;
        recordedTitle = title;
        recordedLeft = seconds * 20;
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (hearsRecording(p)) {
                p.playSound(p, soundKey, SoundCategory.RECORDS, 1f, 1f);
                plugin.messages().actionBar(p, "music.now-playing", "song", title);
            }
        }
    }

    public boolean stopRecording() {
        if (recorded == null) {
            return false;
        }
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            p.stopSound(recorded, SoundCategory.RECORDS);
        }
        recorded = null;
        return true;
    }

    public String recording() {
        return recordedTitle != null && recorded != null ? recordedTitle : null;
    }

    private boolean hearsRecording(Player p) {
        return plugin.progress().loaded(p) && plugin.progress().get(p, "music.off") != 1 && !streaming(p);
    }

    private Song special;
    private int specialTick;

    /** Plays a song once to every online player (music on, no streamers if not stream-safe); the radio pauses. */
    public void playForEveryone(Song song) {
        special = song;
        specialTick = 0;
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (hearsSpecial(p)) {
                plugin.messages().actionBar(p, "music.now-playing", "song", song.title());
            }
        }
    }

    public boolean stopSpecial() {
        boolean was = special != null;
        special = null;
        return was;
    }

    public Song special() {
        return special;
    }

    private boolean hearsSpecial(Player p) {
        return plugin.progress().loaded(p) && plugin.progress().get(p, "music.off") != 1 && allowed(p, special);
    }

    private void tickSpecial() {
        for (Song.Note n : notesAt(special, specialTick)) {
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                if (hearsSpecial(p)) {
                    p.playSound(p.getLocation(), n.sound(), SoundCategory.RECORDS, n.volume() * volume, n.pitch());
                }
            }
        }
        if (++specialTick > special.length()) {
            special = null;
        }
    }

    private void tick() {
        if (recorded != null) {
            if (--recordedLeft <= 0) {
                recorded = null;
            }
            return;
        }
        if (special != null) {
            tickSpecial();
            return;
        }
        if (!enabled) {
            return;
        }
        String key = listKey();
        List<String> list = playlists.getOrDefault(key, List.of());
        if (list.isEmpty()) {
            current = null;
            return;
        }
        if (!key.equals(currentList)) {
            currentList = key;
            current = null;
            index = -1;
            pause = 20;
        }
        if (current == null) {
            if (pause-- > 0) {
                return;
            }
            index = (index + 1) % list.size();
            if (index == 0 && list.size() > 2) {
                // light shuffle every round, but never the same song twice in a row
                String last = list.get(list.size() - 1);
                List<String> copy = new ArrayList<>(list);
                java.util.Collections.shuffle(copy, ThreadLocalRandom.current());
                if (copy.get(0).equals(last)) {
                    java.util.Collections.swap(copy, 0, copy.size() - 1);
                }
                playlists.put(key, copy);
                list = copy;
            }
            current = songs.get(list.get(index));
            tick = 0;
            if (current == null) {
                return;
            }
            for (Player p : listeners()) {
                if (allowed(p, current)) {
                    plugin.messages().actionBar(p, "music.now-playing", "song", current.title());
                }
            }
        }
        List<Player> listeners = listeners();
        if (!listeners.isEmpty()) {
            for (Song.Note n : notesAt(current, tick)) {
                for (Player p : listeners) {
                    if (allowed(p, current)) {
                        p.playSound(p.getLocation(), n.sound(), SoundCategory.RECORDS, n.volume() * volume, n.pitch());
                    }
                }
            }
        }
        tick++;
        if (tick > current.length()) {
            current = null;
            pause = pauseTicks;
        }
    }

    /** Notes of the song at exactly this tick (binary search, songs are sorted). */
    private static List<Song.Note> notesAt(Song song, int tick) {
        List<Song.Note> notes = song.notes();
        int lo = 0;
        int hi = notes.size();
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (notes.get(mid).tick() < tick) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        int end = lo;
        while (end < notes.size() && notes.get(end).tick() == tick) {
            end++;
        }
        return notes.subList(lo, end);
    }

    private List<Player> listeners() {
        List<Player> out = new ArrayList<>();
        Location c = plugin.attractionStore().center();
        Location spawn = plugin.spawn().spawn();
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            boolean near = c != null ? plugin.attractionStore().inside(p.getLocation(), extraRadius)
                    : spawn != null && spawn.getWorld().equals(p.getWorld()) && spawn.distanceSquared(p.getLocation()) < 80 * 80;
            if (near && plugin.progress().loaded(p) && plugin.progress().get(p, "music.off") != 1) {
                out.add(p);
            }
        }
        return out;
    }

    private boolean allowed(Player p, Song song) {
        return song.streamSafe() || !streaming(p);
    }

    public Song current() {
        return current;
    }

    public boolean musicOn(Player p) {
        return plugin.progress().get(p, "music.off") != 1;
    }

    public void setMusic(Player p, boolean on) {
        plugin.progress().set(p, "music.off", on ? 0 : 1);
    }

    public void skip() {
        current = null;
        pause = 10;
    }

    // ---- streamer mode -------------------------------------------------------------------------------------

    public boolean streaming(Player p) {
        return plugin.progress().get(p, "stream.on") == 1;
    }

    public boolean asked(Player p) {
        return plugin.progress().has(p, "stream.on");
    }

    public void setStreaming(Player p, boolean on) {
        plugin.progress().set(p, "stream.on", on ? 1 : 0);
        plugin.display().refresh(p);
    }

    // ---- effects ---------------------------------------------------------------------------------------------

    /** Sleigh bells: a short shimmering jingle made of bells and chimes. */
    public static void sleighBells(MythosCraft plugin, Player p) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = 0; i < 16; i++) {
            int delay = i * 2 + rnd.nextInt(2);
            float pitch = 1.5f + rnd.nextFloat() * 0.5f;
            Sound sound = i % 3 == 0 ? Sound.BLOCK_NOTE_BLOCK_BELL : Sound.BLOCK_NOTE_BLOCK_CHIME;
            plugin.tasks().later(delay + 1, "sleigh bells", () -> {
                if (p.isOnline()) {
                    p.playSound(p.getLocation(), sound, SoundCategory.AMBIENT, 0.45f, pitch);
                }
            });
        }
        plugin.tasks().later(36, "sleigh bells end", () -> {
            if (p.isOnline()) {
                SoundSpec.of("block.amethyst_block.chime", 0.8f, 1.6f).play(p);
            }
        });
    }

    public void shutdown() {
        plugin.tasks().cancel(this);
    }
}
