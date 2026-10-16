package online.mythoscraft.core.progress;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.command.BaseCommand;
import online.mythoscraft.core.command.CommandFail;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.economy.Currency;
import online.mythoscraft.core.player.PlayerData;
import online.mythoscraft.core.reward.Reward;
import online.mythoscraft.core.util.SoundSpec;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Statistic;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Keeps new players on the server:
 * <ul>
 *   <li>[NEW] tag behind the name for the first hours of play time,</li>
 *   <li>a chat helper – "what can I do?", "how do I get money?" … is answered by Lia with clickable commands,</li>
 *   <li>/thank &lt;player&gt; – a new player thanks someone who helped; the helper earns coins,</li>
 *   <li>the daily reward is claimed automatically on join (login streak with a title),</li>
 *   <li>a reward for finishing the guide (and a reminder for those who skipped it).</li>
 * </ul>
 */
public final class NewbieService extends BaseCommand implements Listener {

    /** what players ask → which answer (messages newbie.answers.&lt;id&gt;) */
    private record Intent(String id, Pattern pattern) {
    }

    private static final List<Intent> INTENTS = List.of(
            new Intent("money", Pattern.compile("(how|wie).{0,25}(money|coins|geld|reich|rich)|(make|earn|get|verdien|bekomm).{0,12}(money|coins|geld)")),
            new Intent("home", Pattern.compile("(how|wie).{0,25}(home|claim|base|haus|grundst|protect|schütz|schuetz)|(sethome|my base|meine base)")),
            new Intent("wild", Pattern.compile("(where|wo|how|wie).{0,25}(build|bauen|wild|survival|farm(en)?\\b)|\\b(rtp|wilderness)\\b")),
            new Intent("crates", Pattern.compile("(how|wie|where|wo).{0,25}(crate|kiste|key|schl(ü|ue)ssel)")),
            new Intent("plot", Pattern.compile("\\b(plot|plots|citybuild|creative|kreativ)\\b")),
            new Intent("rank", Pattern.compile("(how|wie).{0,25}(rank|rang|vip)")),
            new Intent("general", Pattern.compile("(what|wat|was)\\s*(can|should|do|to|kann|soll|mach).{0,20}(i|ich|do|tun|machen|here|hier)?"
                    + "|\\bhelp\\b|\\bhilfe\\b|i'?m new|bin neu|what now|was jetzt|bored|langweilig|^\\?+$")));

    private final Map<UUID, Long> lastAnswer = new HashMap<>();
    private boolean enabled = true;
    private long newbieTicks = 2 * 3600 * 20L;
    private long thankCoins = 250;
    private int thankMaxPerNewbie = 3;
    private int thankMaxPerHelperDay = 5;
    private boolean autoDaily = true;
    private final List<Reward> guideRewards = new ArrayList<>();

    public NewbieService(MythosCraft plugin) {
        super(plugin);
    }

    public void reload(ConfigReader root) {
        ConfigReader cfg = root.sub("newbies");
        enabled = cfg.bool("enabled", true);
        newbieTicks = (long) (cfg.decimal("new-tag-hours", 2, 0, 1000) * 3600 * 20);
        thankCoins = cfg.longValue("thank.coins", 250, 0, 1_000_000_000);
        thankMaxPerNewbie = cfg.integer("thank.max-per-new-player", 3, 0, 100);
        thankMaxPerHelperDay = cfg.integer("thank.max-per-helper-per-day", 5, 0, 1000);
        autoDaily = cfg.bool("auto-daily-on-join", true);
        guideRewards.clear();
        for (String line : cfg.strings("guide-rewards")) {
            try {
                guideRewards.add(Reward.parse(line));
            } catch (IllegalArgumentException e) {
                cfg.warn("guide-rewards", e.getMessage());
            }
        }
    }

    /** Still in the first hours of play time? */
    public boolean isNew(Player p) {
        return enabled && newbieTicks > 0 && p.getStatistic(Statistic.PLAY_ONE_MINUTE) < newbieTicks;
    }

    /** "[NEW]" tag shown behind the name (chat, tab, name tag). */
    public Component tag(Player p) {
        return isNew(p) ? Component.space().append(plugin.messages().get("newbie.tag")) : Component.empty();
    }

    // ------------------------------------------------------------------------------------------- join: login streak
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        if (!enabled) {
            return;
        }
        if (isNew(p) && p.hasPlayedBefore()) {
            plugin.tasks().later(100, "newbie hint", () -> {
                if (p.isOnline()) {
                    plugin.messages().sendRaw(p, "newbie.welcome-back");
                }
            });
        }
        if (autoDaily && plugin.daily() != null && plugin.daily().enabled()) {
            plugin.tasks().later(80, "auto daily", () -> {
                if (!p.isOnline() || !autoDaily) {
                    return;
                }
                if (plugin.serverSync() != null && plugin.serverSync().busy(p)) {
                    return;             // inventory still coming from the plot server – claimed on the next join
                }
                PlayerData data = plugin.playerData().get(p);
                if (!plugin.daily().canClaim(data)) {
                    return;
                }
                plugin.daily().claim(p);
                PlayerData after = plugin.playerData().get(p);
                int streak = after == null ? 1 : after.dailyStreak();
                p.showTitle(Title.title(plugin.messages().get("newbie.streak-title", "streak", streak),
                        plugin.messages().get("newbie.streak-subtitle", "streak", streak),
                        Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(2500), Duration.ofMillis(600))));
                p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 30, 0.4, 0.6, 0.4, 0.3);
            });
        }
    }

    // ------------------------------------------------------------------------------------------- chat helper
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!enabled) {
            return;
        }
        Player p = event.getPlayer();
        if (!isNew(p)) {
            return;                         // the chat helper only answers new players
        }
        String text = PlainTextComponentSerializer.plainText().serialize(event.message()).toLowerCase(Locale.ROOT).trim();
        if (text.length() > 80 || text.startsWith("/")) {
            return;
        }
        String found = null;
        for (Intent i : INTENTS) {
            if (i.pattern().matcher(text).find()) {
                found = i.id();
                break;
            }
        }
        if (found == null) {
            return;
        }
        long now = System.currentTimeMillis();
        synchronized (lastAnswer) {
            Long last = lastAnswer.get(p.getUniqueId());
            if (last != null && now - last < 45_000) {
                return;
            }
            lastAnswer.put(p.getUniqueId(), now);
        }
        String id = found;
        plugin.tasks().later(25, "chat helper", () -> {
            if (p.isOnline()) {
                plugin.messages().sendRaw(p, "newbie.answers." + id);
                SoundSpec.of("entity.allay.ambient_with_item", 0.6f, 1.4f).play(p);
            }
        });
    }

    // ------------------------------------------------------------------------------------------- guide
    /** The guide was finished: the promised reward. */
    public void guideFinished(Player p) {
        for (Reward r : guideRewards) {
            r.give(plugin, p, 1.0);
        }
        if (!guideRewards.isEmpty()) {
            plugin.messages().send(p, "newbie.guide-reward", "reward", describe());
        }
    }

    /** The guide was stopped early: what the player is missing. */
    public void guideSkipped(Player p) {
        if (!guideRewards.isEmpty()) {
            plugin.messages().send(p, "newbie.guide-skipped", "reward", describe());
        }
    }

    /** Reward text for the guide's boss bar / chat. */
    public Component describe() {
        Component out = Component.empty();
        for (int i = 0; i < guideRewards.size(); i++) {
            out = out.append(i == 0 ? Component.empty() : Component.text(" + ")).append(guideRewards.get(i).describe(plugin, 1.0));
        }
        return out;
    }

    // ------------------------------------------------------------------------------------------- /thank <player>
    @Override
    protected void execute(CommandSender sender, String label, String[] args) throws CommandFail {
        Player p = player(sender);
        if (args.length < 1) {
            throw CommandFail.usage("/thank <player>");
        }
        if (!isNew(p) && !p.hasPermission("mythos.admin")) {
            plugin.messages().send(p, "newbie.thank-only-new");
            return;
        }
        Player helper = Bukkit.getPlayerExact(args[0]);
        if (helper == null) {
            plugin.messages().send(p, "general.player-not-found", "player", args[0]);
            return;
        }
        if (helper.equals(p)) {
            plugin.messages().send(p, "newbie.thank-self");
            return;
        }
        String key = "thank.given." + helper.getUniqueId();
        if (plugin.progress().get(p, key) > 0) {
            plugin.messages().send(p, "newbie.thank-twice", "player", helper.getName());
            return;
        }
        if (plugin.progress().get(p, "thank.count") >= thankMaxPerNewbie) {
            plugin.messages().send(p, "newbie.thank-max", "max", thankMaxPerNewbie);
            return;
        }
        long today = plugin.multipliers().today().toEpochDay();
        long got = plugin.progress().get(helper, "thank.day") == today ? plugin.progress().get(helper, "thank.today") : 0;
        plugin.progress().set(p, key, 1);
        plugin.progress().add(p, "thank.count", 1);
        boolean paid = got < thankMaxPerHelperDay;
        if (paid) {
            plugin.progress().set(helper, "thank.day", today);
            plugin.progress().set(helper, "thank.today", got + 1);
            PlayerData data = plugin.playerData().get(helper);
            if (data != null) {
                plugin.economy().earn(data, Currency.COINS, thankCoins, false);
                plugin.playerData().save(data);
            }
        }
        plugin.progress().add(helper, "thank.received", 1);
        plugin.messages().send(p, "newbie.thank-sent", "player", helper.getName());
        plugin.messages().send(helper, paid ? "newbie.thanked" : "newbie.thanked-nocoins", "player", p.getName(), "coins", thankCoins);
        plugin.messages().broadcast("newbie.thank-broadcast", "player", p.getName(), "helper", helper.getName());
        SoundSpec.of("entity.player.levelup", 0.7f, 1.6f).play(helper);
        helper.getWorld().spawnParticle(Particle.HEART, helper.getLocation().add(0, 2.1, 0), 8, 0.4, 0.2, 0.4, 0);
    }

    @Override
    protected List<String> complete(CommandSender sender, String label, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (!o.equals(sender) && o.getName().toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))) {
                out.add(o.getName());
            }
        }
        return out;
    }
}
