package online.mythoscraft.core.attraction;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.command.BaseCommand;
import online.mythoscraft.core.command.CommandFail;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.economy.Currency;
import online.mythoscraft.core.economy.EconomyManager;
import online.mythoscraft.core.player.PlayerData;
import online.mythoscraft.core.util.Numbers;
import online.mythoscraft.core.util.SoundSpec;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Friendly bets without PvP: challenge a friend for coins – coin flip, dice duel, or a race in the parkour, dropper,
 * ring course or maze (first to finish wins the pot). Both stakes are held by the server until the bet is decided.
 */
public final class BetService extends BaseCommand {

    public enum Game {
        COIN("muenze"), DICE("wuerfel"), PARKOUR("parkour"), DROPPER("dropper"), RINGS("ringe"), MAZE("labyrinth");

        final String id;

        Game(String id) {
            this.id = id;
        }

        static Game byId(String s) {
            String l = s.toLowerCase(Locale.ROOT).replace("ü", "ue");
            for (Game g : values()) {
                if (g.id.equals(l) || g.name().equalsIgnoreCase(l)) {
                    return g;
                }
            }
            return switch (l) {
                case "coin", "coinflip", "munze" -> COIN;
                case "dice", "wurfel" -> DICE;
                case "rings", "elytra" -> RINGS;
                case "maze" -> MAZE;
                default -> null;
            };
        }

        /** Activity key used by {@link ActivityRewards} for the race games. */
        String activity() {
            return switch (this) {
                case PARKOUR -> "parkour";
                case DROPPER -> "dropper";
                case RINGS -> "rings";
                case MAZE -> "maze";
                default -> null;
            };
        }
    }

    record Challenge(UUID from, UUID to, long stake, Game game, long until) {
    }

    record Race(UUID a, UUID b, long pot, Game game, long until) {
    }

    private final Map<UUID, Challenge> challenges = new HashMap<>();
    private final Map<UUID, Race> races = new HashMap<>();
    private long minStake = 10;
    private long maxStake = 100_000;
    private int raceMinutes = 10;

    public BetService(MythosCraft plugin) {
        super(plugin);
    }

    public void reload(ConfigReader root) {
        ConfigReader cfg = root.sub("bets");
        minStake = cfg.longValue("min-stake", 10, 1, 1_000_000_000L);
        maxStake = cfg.longValue("max-stake", 100_000, 1, 1_000_000_000_000L);
        raceMinutes = cfg.integer("race-minutes", 10, 1, 120);
        plugin.tasks().cancel(this);
        plugin.tasks().repeat(this, 200, 200, "bet timeouts", this::timeouts);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) throws CommandFail {
        Player p = player(sender);
        if (args.length == 0) {
            plugin.messages().send(p, "bets.help", "min", Numbers.format(minStake), "max", Numbers.format(maxStake));
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "annehmen", "accept", "ja" -> accept(p);
            case "ablehnen", "deny", "nein" -> {
                Challenge c = challenges.remove(p.getUniqueId());
                if (c == null) {
                    throw new CommandFail("bets.none");
                }
                plugin.messages().send(p, "bets.denied");
                Player from = Bukkit.getPlayer(c.from());
                if (from != null) {
                    plugin.messages().send(from, "bets.denied-other", "player", p.getName());
                }
            }
            default -> challenge(p, args);
        }
    }

    private void challenge(Player p, String[] args) throws CommandFail {
        if (args.length < 3) {
            throw CommandFail.usage("/wette <Spieler> <Coins> <muenze|wuerfel|parkour|dropper|ringe|labyrinth>");
        }
        Player target = online(p, args[0]);
        if (target.equals(p)) {
            throw new CommandFail("bets.self");
        }
        long stake;
        try {
            stake = Long.parseLong(args[1].replace(".", "").replace("_", ""));
        } catch (NumberFormatException e) {
            throw new CommandFail("bets.bad-stake", "min", Numbers.format(minStake), "max", Numbers.format(maxStake));
        }
        if (stake < minStake || stake > maxStake) {
            throw new CommandFail("bets.bad-stake", "min", Numbers.format(minStake), "max", Numbers.format(maxStake));
        }
        Game game = Game.byId(args[2]);
        if (game == null) {
            throw CommandFail.usage("/wette <Spieler> <Coins> <muenze|wuerfel|parkour|dropper|ringe|labyrinth>");
        }
        if (busy(p) || busy(target)) {
            throw new CommandFail("bets.busy");
        }
        PlayerData data = data(p);
        if (data.coins() < stake) {
            throw new CommandFail("bets.not-enough", "stake", Numbers.format(stake));
        }
        challenges.put(target.getUniqueId(), new Challenge(p.getUniqueId(), target.getUniqueId(), stake, game,
                System.currentTimeMillis() + 60_000));
        Component gameName = plugin.messages().in(target, () -> plugin.messages().get("bets.games." + game.id));
        plugin.messages().send(p, "bets.sent", "player", target.getName(), "stake", Numbers.format(stake),
                "game", plugin.messages().get("bets.games." + game.id));
        plugin.messages().send(target, "bets.received", "player", p.getName(), "stake", Numbers.format(stake), "game", gameName);
        target.sendMessage(plugin.messages().in(target, () -> plugin.messages().get("bets.buttons"))
                .clickEvent(ClickEvent.runCommand("/wette annehmen")));
        SoundSpec.of("block.note_block.pling", 0.8f, 1.4f).play(target);
    }

    private boolean busy(Player p) {
        for (Race r : races.values()) {
            if (r.a().equals(p.getUniqueId()) || r.b().equals(p.getUniqueId())) {
                return true;
            }
        }
        return false;
    }

    private void accept(Player p) throws CommandFail {
        Challenge c = challenges.remove(p.getUniqueId());
        if (c == null || c.until() < System.currentTimeMillis()) {
            throw new CommandFail("bets.none");
        }
        Player from = Bukkit.getPlayer(c.from());
        if (from == null) {
            throw new CommandFail("general.player-not-found", "player", "?");
        }
        PlayerData a = plugin.playerData().get(from);
        PlayerData b = plugin.playerData().get(p);
        if (a == null || b == null || a.coins() < c.stake() || b.coins() < c.stake()) {
            throw new CommandFail("bets.not-enough", "stake", Numbers.format(c.stake()));
        }
        if (plugin.economy().withdraw(a, Currency.COINS, c.stake()) != EconomyManager.Result.SUCCESS) {
            throw new CommandFail("bets.not-enough", "stake", Numbers.format(c.stake()));
        }
        if (plugin.economy().withdraw(b, Currency.COINS, c.stake()) != EconomyManager.Result.SUCCESS) {
            plugin.economy().deposit(a, Currency.COINS, c.stake());
            throw new CommandFail("bets.not-enough", "stake", Numbers.format(c.stake()));
        }
        plugin.playerData().save(a);
        plugin.playerData().save(b);
        long pot = c.stake() * 2;
        switch (c.game()) {
            case COIN, DICE -> instant(from, p, pot, c.game());
            default -> {
                Race race = new Race(from.getUniqueId(), p.getUniqueId(), pot, c.game(), System.currentTimeMillis() + raceMinutes * 60_000L);
                races.put(from.getUniqueId(), race);
                races.put(p.getUniqueId(), race);
                for (Player x : List.of(from, p)) {
                    plugin.messages().send(x, "bets.race-start", "game", plugin.messages().in(x, () -> plugin.messages().get("bets.games." + c.game().id)),
                            "pot", Numbers.format(pot), "minutes", raceMinutes);
                    SoundSpec.of("entity.ender_dragon.growl", 0.4f, 1.6f).play(x);
                }
            }
        }
    }

    /** Coin flip / dice duel: a short countdown, then the winner gets the pot. */
    private void instant(Player a, Player b, long pot, Game game) {
        for (int i = 3; i >= 1; i--) {
            int n = i;
            plugin.tasks().later((3 - i) * 20L + 1, "bet countdown", () -> {
                for (Player x : List.of(a, b)) {
                    if (x.isOnline()) {
                        x.sendActionBar(plugin.messages().in(x, () -> plugin.messages().get("bets.countdown", "seconds", n)));
                        SoundSpec.of("block.note_block.hat", 1f, 1f + n * 0.1f).play(x);
                    }
                }
            });
        }
        plugin.tasks().later(61, "bet result", () -> {
            ThreadLocalRandom rnd = ThreadLocalRandom.current();
            Player winner;
            String detail;
            if (game == Game.COIN) {
                winner = rnd.nextBoolean() ? a : b;
                detail = winner == a ? "Kopf" : "Zahl";
            } else {
                int ra;
                int rb;
                do {
                    ra = 1 + rnd.nextInt(6);
                    rb = 1 + rnd.nextInt(6);
                } while (ra == rb);
                winner = ra > rb ? a : b;
                detail = ra + " : " + rb;
            }
            payout(winner, winner == a ? b : a, pot, detail);
        });
    }

    private void payout(Player winner, Player loser, long pot, String detail) {
        if (winner.isOnline()) {
            PlayerData d = plugin.playerData().get(winner);
            if (d != null) {
                plugin.economy().deposit(d, Currency.COINS, pot);
                plugin.playerData().save(d);
            } else {
                plugin.economy().credit(winner.getUniqueId(), Currency.COINS, pot, null, "bet win");
            }
            plugin.messages().send(winner, "bets.won", "pot", Numbers.format(pot), "detail", detail, "player", loser.getName());
            winner.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, winner.getLocation().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.2);
            SoundSpec.of("ui.toast.challenge_complete", 0.8f, 1.2f).play(winner);
        } else {
            plugin.economy().credit(winner.getUniqueId(), Currency.COINS, pot, null, "bet win");
        }
        if (loser.isOnline()) {
            plugin.messages().send(loser, "bets.lost", "pot", Numbers.format(pot), "detail", detail, "player", winner.getName());
        }
        plugin.economy().log(loser.getUniqueId(), winner.getUniqueId(), Currency.COINS, pot / 2, "bet");
    }

    /** Called when a player finishes a spawn activity – decides a running race. */
    public void finished(Player p, String activity) {
        Race r = races.get(p.getUniqueId());
        if (r == null || !activity.equals(r.game().activity())) {
            return;
        }
        races.remove(r.a());
        races.remove(r.b());
        Player other = Bukkit.getPlayer(r.a().equals(p.getUniqueId()) ? r.b() : r.a());
        if (other == null) {
            plugin.economy().credit(p.getUniqueId(), Currency.COINS, r.pot(), null, "bet win");
            plugin.messages().send(p, "bets.won", "pot", Numbers.format(r.pot()), "detail", "🏁", "player", "?");
            return;
        }
        payout(p, other, r.pot(), "🏁");
    }

    private void timeouts() {
        long now = System.currentTimeMillis();
        challenges.values().removeIf(c -> c.until() < now);
        for (Race r : List.copyOf(races.values())) {
            if (r.until() < now && races.remove(r.a()) != null) {
                races.remove(r.b());
                for (UUID id : List.of(r.a(), r.b())) {
                    plugin.economy().credit(id, Currency.COINS, r.pot() / 2, null, "bet refund");
                    Player x = Bukkit.getPlayer(id);
                    if (x != null) {
                        plugin.messages().send(x, "bets.timeout", "stake", Numbers.format(r.pot() / 2));
                    }
                }
            }
        }
    }

    @Override
    protected List<String> complete(CommandSender sender, String label, String[] args) {
        if (args.length == 1) {
            List<String> names = new java.util.ArrayList<>(List.of("annehmen", "ablehnen"));
            Bukkit.getOnlinePlayers().forEach(x -> names.add(x.getName()));
            return names;
        }
        if (args.length == 2) {
            return List.of("100", "500", "1000", "5000");
        }
        if (args.length == 3) {
            return List.of("muenze", "wuerfel", "parkour", "dropper", "ringe", "labyrinth");
        }
        return List.of();
    }

    public void shutdown() {
        plugin.tasks().cancel(this);
        // refund running bets
        for (Race r : List.copyOf(races.values())) {
            if (races.remove(r.a()) != null) {
                races.remove(r.b());
                plugin.economy().credit(r.a(), Currency.COINS, r.pot() / 2, null, "bet refund");
                plugin.economy().credit(r.b(), Currency.COINS, r.pot() / 2, null, "bet refund");
            }
        }
    }
}
