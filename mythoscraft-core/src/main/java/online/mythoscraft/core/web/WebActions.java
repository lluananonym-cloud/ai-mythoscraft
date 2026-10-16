package online.mythoscraft.core.web;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.item.CustomItem;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The buttons of the web panel: every custom command as a form with choices. The command is always built from a
 * template and validated parameters – no free command text (that is the console's job, admins only).
 */
final class WebActions {

    record Param(String key, String label, String type, List<String> options, String def) {
    }

    record Action(String id, String category, String label, String icon, String template, boolean admin, List<Param> params) {
    }

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final Pattern NUMBER = Pattern.compile("-?\\d{1,12}(\\.\\d{1,3})?");
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_.:+-]{1,48}");
    private static final Pattern DURATION = Pattern.compile("(perm|\\d{1,4}[smhdw])");

    private static List<Action> actions = List.of();

    private WebActions() {
    }

    private static Param player(String label) {
        return new Param("player", label, "player", List.of(), "");
    }

    private static Param select(String key, String label, List<String> options) {
        return new Param(key, label, "select", options, options.isEmpty() ? "" : options.get(0));
    }

    private static Param number(String key, String label, String def) {
        return new Param(key, label, "number", List.of(), def);
    }

    private static Param text(String key, String label, String def) {
        return new Param(key, label, "text", List.of(), def);
    }

    static List<Map<String, Object>> definitions(MythosCraft plugin) {
        List<String> events = new ArrayList<>();
        plugin.events().all().forEach(e -> events.add(e.id()));
        List<String> ranks = new ArrayList<>();
        plugin.ranks().ranks().forEach(r -> ranks.add(r.id()));
        List<String> crates = new ArrayList<>();
        plugin.crates().types().forEach(t -> crates.add(t.id()));
        List<String> items = new ArrayList<>();
        for (CustomItem i : CustomItem.values()) {
            if (i.giveable()) {
                items.add(i.id());
            }
        }
        List<String> seasons = new ArrayList<>();
        var seasonSec = plugin.getConfig().getConfigurationSection("seasons");
        if (seasonSec != null) {
            seasons.addAll(seasonSec.getKeys(false));
        }
        List<String> spells = plugin.magic().spellIds();
        List<String> songs = new ArrayList<>(plugin.attractions().music().songs().keySet());
        List<Action> a = new ArrayList<>();
        // players & economy
        a.add(new Action("eco", "Spieler", "Coins / Gems ändern", "💰", "mythos eco {op} {player} {currency} {amount}", false,
                List.of(player("Spieler"), select("op", "Aktion", List.of("give", "take", "set")), select("currency", "Währung", List.of("coins", "gems")),
                        number("amount", "Betrag", "1000"))));
        a.add(new Action("rank", "Spieler", "Rang setzen", "👑", "rank set {player} {rank} {duration}", false,
                List.of(player("Spieler"), select("rank", "Rang", ranks), select("duration", "Dauer", List.of("perm", "1d", "7d", "10d", "30d", "90d")))));
        a.add(new Action("rank-remove", "Spieler", "Rang entfernen", "✖", "rank remove {player}", false, List.of(player("Spieler"))));
        a.add(new Action("item", "Spieler", "Custom-Item geben", "🎁", "mythos item give {player} {item} {amount}", false,
                List.of(player("Spieler"), select("item", "Item", items), number("amount", "Anzahl", "1"))));
        a.add(new Action("key", "Spieler", "Kisten-Schlüssel geben", "🔑", "mythos crate key {player} {crate} {amount}", false,
                List.of(player("Spieler"), select("crate", "Kiste", crates), number("amount", "Anzahl", "1"))));
        a.add(new Action("magic", "Spieler", "Zauber freischalten", "✦", "magie {op} {player} {spell}", false,
                List.of(select("op", "Aktion", List.of("lernen", "vergessen")), player("Spieler"),
                        select("spell", "Zauber", concat(List.of("alle"), spells)))));
        a.add(new Action("kick", "Spieler", "Spieler kicken", "🚪", "kick {player} {reason}", false,
                List.of(player("Spieler"), text("reason", "Grund", "Vom Admin-Panel"))));
        a.add(new Action("gamemode", "Spieler", "Spielmodus", "🎮", "gamemode {mode} {player}", true,
                List.of(player("Spieler"), select("mode", "Modus", List.of("survival", "creative", "adventure", "spectator")))));
        // events & fun
        a.add(new Action("event-start", "Events", "Event starten", "⚡", "mythos event start {event} {countdown}", false,
                List.of(select("event", "Event", events), select("countdown", "Countdown (s)", List.of("30", "10", "60", "0")))));
        a.add(new Action("event-stop", "Events", "Event stoppen", "⏹", "mythos event stop", false, List.of()));
        a.add(new Action("giveaway", "Events", "Verlosung starten", "🎉", "verlosung start {minutes} {winners} {reward}", false,
                List.of(number("minutes", "Minuten", "5"), number("winners", "Gewinner", "1"),
                        select("reward", "Preis", List.of("voucher VIP 7d", "voucher HELD 10d", "voucher ELITE 30d", "key mythos 1", "key elite 2",
                                "gems 50", "coins 25000", "tool mythos_wand 1")))));
        a.add(new Action("giveaway-stop", "Events", "Verlosung jetzt ziehen", "🎲", "verlosung stop", false, List.of()));
        a.add(new Action("boost", "Events", "Server-Booster", "🚀", "mythos boost {type} {factor} {duration}", false,
                List.of(select("type", "Art", List.of("coins", "gems", "events")), select("factor", "Faktor", List.of("2", "1.5", "3")),
                        select("duration", "Dauer", List.of("1h", "30m", "2h", "6h", "1d")))));
        a.add(new Action("season", "Events", "Saison starten", "🍂", "season start {season}", false, List.of(select("season", "Saison", seasons))));
        a.add(new Action("season-stop", "Events", "Saison stoppen", "⏹", "season stop", false, List.of()));
        a.add(new Action("silvester", "Events", "Silvester-Show testen", "🎆", "season silvester", false, List.of()));
        a.add(new Action("merchant", "Events", "Händler rufen", "🐪", "haendler", false, List.of()));
        a.add(new Action("snowman", "Events", "Snowman abspielen/stoppen", "☃", "snowman", false, List.of()));
        a.add(new Action("music-skip", "Events", "Nächstes Spawn-Lied", "♪", "musik skip", false, List.of()));
        a.add(new Action("say", "Server", "Nachricht an alle", "📢", "say {text}", false, List.of(text("text", "Text", "Hallo MythosCraft!"))));
        a.add(new Action("reload", "Server", "Plugin neu laden", "🔄", "mythos reload", true, List.of()));
        a.add(new Action("time", "Server", "Zeit setzen", "🌗", "time set {time}", true, List.of(select("time", "Zeit", List.of("day", "noon", "night", "midnight")))));
        a.add(new Action("weather", "Server", "Wetter", "⛅", "weather {weather}", true, List.of(select("weather", "Wetter", List.of("clear", "rain", "thunder")))));
        a.add(new Action("save", "Server", "Welten speichern", "💾", "save-all", true, List.of()));
        actions = a;
        List<Map<String, Object>> out = new ArrayList<>();
        List<String> online = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            online.add(p.getName());
        }
        for (Action act : a) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", act.id());
            m.put("category", act.category());
            m.put("label", act.label());
            m.put("icon", act.icon());
            m.put("admin", act.admin());
            List<Map<String, Object>> ps = new ArrayList<>();
            for (Param p : act.params()) {
                Map<String, Object> pm = new LinkedHashMap<>();
                pm.put("key", p.key());
                pm.put("label", p.label());
                pm.put("type", p.type());
                pm.put("options", p.type().equals("player") ? online : p.options());
                pm.put("def", p.def());
                ps.add(pm);
            }
            m.put("params", ps);
            out.add(m);
        }
        return out;
    }

    private static List<String> concat(List<String> a, List<String> b) {
        List<String> out = new ArrayList<>(a);
        out.addAll(b);
        return out;
    }

    /** Builds the command for a button – every parameter is checked against its type / choices. */
    static String build(String id, Map<String, String> params, boolean admin) {
        Action act = null;
        for (Action a : actions) {
            if (a.id().equals(id)) {
                act = a;
            }
        }
        if (act == null) {
            throw new IllegalArgumentException("Unbekannte Aktion");
        }
        if (act.admin() && !admin) {
            throw new IllegalArgumentException("Nur für Admins");
        }
        String cmd = act.template();
        for (Param p : act.params()) {
            String v = params.getOrDefault(p.key(), p.def()).trim();
            switch (p.type()) {
                case "player" -> {
                    if (!NAME.matcher(v).matches()) {
                        throw new IllegalArgumentException("Ungültiger Spielername");
                    }
                }
                case "number" -> {
                    if (!NUMBER.matcher(v).matches()) {
                        throw new IllegalArgumentException(p.label() + ": keine Zahl");
                    }
                }
                case "select" -> {
                    if (!p.options().contains(v)) {
                        throw new IllegalArgumentException(p.label() + ": ungültige Auswahl");
                    }
                }
                default -> {
                    v = v.replaceAll("[\\r\\n\\u0000-\\u001f]", " ");
                    if (v.isEmpty() || v.length() > 200) {
                        throw new IllegalArgumentException(p.label() + ": leer oder zu lang");
                    }
                }
            }
            if (p.key().equals("duration") && !DURATION.matcher(v).matches()) {
                throw new IllegalArgumentException("Ungültige Dauer");
            }
            if (!p.type().equals("text") && !p.key().equals("reward") && !TOKEN.matcher(v).matches()) {
                throw new IllegalArgumentException(p.label() + ": ungültig");
            }
            cmd = cmd.replace("{" + p.key() + "}", v);
        }
        if (id.equals("rank")) {
            cmd = cmd.replace(" perm", "");
        }
        return cmd.trim();
    }
}
