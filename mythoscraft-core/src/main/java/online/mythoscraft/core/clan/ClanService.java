package online.mythoscraft.core.clan;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.config.YamlStore;
import online.mythoscraft.core.util.LocationCodec;
import online.mythoscraft.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Clans: a name, a short tag shown in chat and the tab list, leader / officers / members, a shared coin bank, a clan
 * home, clan chat (/cc) and no friendly fire. Stored in data/clans.yml.
 */
public final class ClanService implements Listener {

    public enum Role { MEMBER, OFFICER, LEADER }

    public static final class Clan {
        final String id;
        String name;
        String tag;
        String color;
        long bank;
        long created;
        Location home;
        final Map<UUID, Role> members = new ConcurrentHashMap<>();
        final Map<UUID, String> names = new ConcurrentHashMap<>();

        Clan(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public String name() {
            return name;
        }

        public String tag() {
            return tag;
        }

        public NamedTextColor color() {
            NamedTextColor c = NamedTextColor.NAMES.value(color == null ? "aqua" : color);
            return c == null ? NamedTextColor.AQUA : c;
        }

        public long bank() {
            return bank;
        }

        public Location home() {
            return home == null ? null : home.clone();
        }

        public Map<UUID, Role> members() {
            return members;
        }

        public String nameOf(UUID id) {
            return names.getOrDefault(id, "?");
        }

        public UUID leader() {
            for (Map.Entry<UUID, Role> e : members.entrySet()) {
                if (e.getValue() == Role.LEADER) {
                    return e.getKey();
                }
            }
            return null;
        }

        public Role role(UUID id) {
            return members.get(id);
        }
    }

    record Invite(String clan, long until) {
    }

    static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
    static final Pattern TAG = Pattern.compile("[A-Za-z0-9]{2,5}");

    private final MythosCraft plugin;
    private final YamlStore store;
    private final Map<String, Clan> clans = new ConcurrentHashMap<>();
    private final Map<UUID, Clan> byPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, Invite> invites = new ConcurrentHashMap<>();
    private final Set<UUID> chatMode = ConcurrentHashMap.newKeySet();
    private boolean enabled = true;
    private long createCost = 10_000;
    private int maxMembers = 20;
    private boolean friendlyFire;

    public ClanService(MythosCraft plugin) {
        this.plugin = plugin;
        this.store = new YamlStore(plugin, "data/clans.yml");
        load();
    }

    public void reload(ConfigReader root) {
        ConfigReader cfg = root.sub("clans");
        enabled = cfg.bool("enabled", true);
        createCost = cfg.longValue("create-cost", 10_000, 0, 1_000_000_000);
        maxMembers = cfg.integer("max-members", 20, 2, 500);
        friendlyFire = cfg.bool("friendly-fire", false);
    }

    public boolean enabled() {
        return enabled;
    }

    public long createCost() {
        return createCost;
    }

    public int maxMembers() {
        return maxMembers;
    }

    // ---- storage -------------------------------------------------------------------------------------------

    private void load() {
        YamlConfiguration yaml = store.load(false);
        ConfigurationSection sec = yaml.getConfigurationSection("clans");
        if (sec == null) {
            return;
        }
        for (String id : sec.getKeys(false)) {
            ConfigurationSection c = sec.getConfigurationSection(id);
            if (c == null) {
                continue;
            }
            Clan clan = new Clan(id);
            clan.name = c.getString("name", id);
            clan.tag = c.getString("tag", id.substring(0, Math.min(4, id.length())).toUpperCase(Locale.ROOT));
            clan.color = c.getString("color", "aqua");
            clan.bank = c.getLong("bank", 0);
            clan.created = c.getLong("created", 0);
            clan.home = LocationCodec.fromString(c.getString("home"));
            ConfigurationSection m = c.getConfigurationSection("members");
            if (m != null) {
                for (String u : m.getKeys(false)) {
                    try {
                        UUID uuid = UUID.fromString(u);
                        clan.members.put(uuid, Role.valueOf(m.getString(u + ".role", "MEMBER")));
                        clan.names.put(uuid, m.getString(u + ".name", "?"));
                        byPlayer.put(uuid, clan);
                    } catch (IllegalArgumentException ignored) {
                        // broken entry
                    }
                }
            }
            if (!clan.members.isEmpty()) {
                clans.put(id, clan);
            }
        }
    }

    void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Clan c : clans.values()) {
            String p = "clans." + c.id + ".";
            yaml.set(p + "name", c.name);
            yaml.set(p + "tag", c.tag);
            yaml.set(p + "color", c.color);
            yaml.set(p + "bank", c.bank);
            yaml.set(p + "created", c.created);
            if (c.home != null && c.home.getWorld() != null) {
                yaml.set(p + "home", LocationCodec.toString(c.home));
            }
            for (Map.Entry<UUID, Role> e : c.members.entrySet()) {
                yaml.set(p + "members." + e.getKey() + ".role", e.getValue().name());
                yaml.set(p + "members." + e.getKey() + ".name", c.nameOf(e.getKey()));
            }
        }
        store.replace(yaml);
        store.saveAsync(plugin.tasks());
    }

    // ---- queries -------------------------------------------------------------------------------------------

    public Clan of(UUID player) {
        return byPlayer.get(player);
    }

    public Clan byName(String name) {
        return clans.get(name.toLowerCase(Locale.ROOT));
    }

    public Collection<Clan> all() {
        return clans.values();
    }

    /** Clan tag for chat / tab list, empty when the player has no clan. */
    public Component tag(UUID player) {
        Clan c = byPlayer.get(player);
        if (c == null) {
            return Component.empty();
        }
        Component tag = Component.text(c.tag, c.color());
        if (plugin.clanWar() != null && c.id().equals(plugin.clanWar().champion())) {
            tag = Component.text("👑", net.kyori.adventure.text.format.NamedTextColor.GOLD).append(tag);
        }
        return plugin.messages().format(plugin.messages().raw("clans.tag-format"), "tag", tag);
    }

    public List<Player> online(Clan c) {
        List<Player> out = new ArrayList<>();
        for (UUID id : c.members.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                out.add(p);
            }
        }
        return out;
    }

    public void broadcast(Clan c, String key, Object... placeholders) {
        for (Player p : online(c)) {
            plugin.messages().send(p, key, placeholders);
        }
    }

    // ---- changes -------------------------------------------------------------------------------------------

    Clan create(Player leader, String name, String tag) {
        Clan c = new Clan(name.toLowerCase(Locale.ROOT));
        c.name = name;
        c.tag = tag.toUpperCase(Locale.ROOT);
        c.color = "aqua";
        c.created = System.currentTimeMillis();
        c.members.put(leader.getUniqueId(), Role.LEADER);
        c.names.put(leader.getUniqueId(), leader.getName());
        clans.put(c.id, c);
        byPlayer.put(leader.getUniqueId(), c);
        save();
        plugin.display().refresh(leader);
        return c;
    }

    boolean tagTaken(String tag) {
        return clans.values().stream().anyMatch(c -> c.tag.equalsIgnoreCase(tag));
    }

    void invite(Clan c, Player target) {
        invites.put(target.getUniqueId(), new Invite(c.id, System.currentTimeMillis() + 5 * 60_000L));
    }

    Clan pendingInvite(UUID player) {
        Invite i = invites.get(player);
        if (i == null || i.until() < System.currentTimeMillis()) {
            invites.remove(player);
            return null;
        }
        return clans.get(i.clan());
    }

    void clearInvite(UUID player) {
        invites.remove(player);
    }

    void join(Clan c, Player p) {
        invites.remove(p.getUniqueId());
        c.members.put(p.getUniqueId(), Role.MEMBER);
        c.names.put(p.getUniqueId(), p.getName());
        byPlayer.put(p.getUniqueId(), c);
        save();
        plugin.display().refresh(p);
    }

    void remove(Clan c, UUID member) {
        c.members.remove(member);
        c.names.remove(member);
        byPlayer.remove(member);
        chatMode.remove(member);
        if (c.members.isEmpty()) {
            clans.remove(c.id);
        }
        save();
        Player p = Bukkit.getPlayer(member);
        if (p != null) {
            plugin.display().refresh(p);
        }
    }

    void disband(Clan c) {
        for (UUID id : List.copyOf(c.members.keySet())) {
            byPlayer.remove(id);
            chatMode.remove(id);
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                plugin.display().refresh(p);
            }
        }
        c.members.clear();
        clans.remove(c.id);
        save();
    }

    void setRole(Clan c, UUID member, Role role) {
        c.members.put(member, role);
        save();
    }

    void setColor(Clan c, String color) {
        c.color = color;
        save();
        online(c).forEach(plugin.display()::refresh);
    }

    void setHome(Clan c, Location l) {
        c.home = l.clone();
        save();
    }

    void addBank(Clan c, long delta) {
        c.bank = Math.max(0, c.bank + delta);
        save();
    }

    public boolean toggleChat(UUID player) {
        if (!chatMode.remove(player)) {
            chatMode.add(player);
            return true;
        }
        return false;
    }

    /** Sends a message to the clan chat. */
    public void chat(Player from, String message) {
        Clan c = byPlayer.get(from.getUniqueId());
        if (c == null) {
            plugin.messages().send(from, "clans.not-in-clan");
            return;
        }
        Component line = plugin.messages().get("clans.chat-format", "tag", Component.text(c.tag, c.color()), "player", from.getName(),
                "message", Component.text(message));
        for (Player p : online(c)) {
            p.sendMessage(line);
        }
        plugin.getLogger().info("[Clan " + c.name + "] " + from.getName() + ": " + message);
    }

    // ---- events ----------------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player p = event.getPlayer();
        if (!chatMode.contains(p.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        String message = Text.plain(event.message());
        plugin.tasks().sync(() -> chat(p, message));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (friendlyFire || !(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = null;
        if (event.getDamager() instanceof Player p) {
            attacker = p;
        } else if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            attacker = p;
        }
        if (attacker == null || attacker.equals(victim) || plugin.arenas().isInArena(victim) || plugin.tournaments().isParticipant(victim)) {
            return;
        }
        Clan a = byPlayer.get(attacker.getUniqueId());
        if (a != null && a == byPlayer.get(victim.getUniqueId())) {
            event.setCancelled(true);
            plugin.messages().actionBar(attacker, "clans.friendly-fire");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        invites.remove(event.getPlayer().getUniqueId());
        Clan c = byPlayer.get(event.getPlayer().getUniqueId());
        if (c != null && !event.getPlayer().getName().equals(c.nameOf(event.getPlayer().getUniqueId()))) {
            c.names.put(event.getPlayer().getUniqueId(), event.getPlayer().getName());
            save();
        }
    }

    public void shutdown() {
        store.saveNow();
    }
}
