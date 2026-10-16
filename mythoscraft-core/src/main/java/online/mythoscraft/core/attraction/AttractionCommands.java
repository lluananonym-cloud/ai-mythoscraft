package online.mythoscraft.core.attraction;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.command.BaseCommand;
import online.mythoscraft.core.command.CommandFail;
import online.mythoscraft.core.item.SeasonService;
import online.mythoscraft.core.util.Text;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /ringe, /musik, /stream, /season and /haendler. */
public final class AttractionCommands extends BaseCommand {

    public AttractionCommands(MythosCraft plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) throws CommandFail {
        String a = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        switch (label) {
            case "ringe", "rings", "elytra" -> plugin.attractions().rings().info(player(sender));
            case "dropper" -> plugin.attractions().games().dropperInfo(player(sender));
            case "aufgaben", "quests" -> plugin.quests().open(player(sender));
            case "labyrinth", "maze" -> plugin.attractions().games().mazeInfo(player(sender));
            case "musik", "music" -> music(sender, a);
            case "snowman" -> snowman(sender);
            case "kisten", "crates" -> plugin.crateCarousel().info(sender);
            case "booster", "boosters" -> plugin.boosters().info(player(sender));
            case "magie", "magic" -> magic(sender, args);
            case "blockparty" -> plugin.floorGames().info(player(sender), FloorGamesService.Game.PARTY);
            case "spleef" -> plugin.floorGames().info(player(sender), FloorGamesService.Game.SPLEEF);
            case "zielschiessen", "targets" -> plugin.floorGames().info(player(sender), FloorGamesService.Game.RANGE);
            case "stream", "streamer" -> stream(player(sender), a);
            case "season", "saison" -> season(sender, args);
            case "haendler", "merchant" -> {
                permission(sender, "mythos.season");
                plugin.attractions().merchants().arrive();
            }
            default -> {
            }
        }
    }

    /** Admin: plays "Snowman" (sung by a villager, real instruments) to everyone; again = stop. */
    private void snowman(CommandSender sender) throws CommandFail {
        permission(sender, "mythos.season");
        MusicService music = plugin.attractions().music();
        if (music.stopRecording()) {
            plugin.messages().send(sender, "music.special-stopped");
            return;
        }
        // real recording from the resource pack: original instruments + vocals in a villager voice
        music.playRecording("mythoscraft:music.snowman", "Snowman (Villager-Version)", 167);
        plugin.messages().send(sender, "music.special-started", "song", "Snowman");
    }

    /** /magie – help; /magie lernen|vergessen <player> <spell|alle> (admin). */
    private void magic(CommandSender sender, String[] args) throws CommandFail {
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        if (!sub.equals("lernen") && !sub.equals("learn") && !sub.equals("vergessen") && !sub.equals("forget")) {
            plugin.messages().send(sender, "magic.help");
            return;
        }
        permission(sender, "mythos.magic.admin");
        if (args.length < 3) {
            throw CommandFail.usage("/magie lernen <Spieler> <Zauber|alle>");
        }
        Player target = plugin.getServer().getPlayerExact(args[1]);
        if (target == null) {
            throw new CommandFail("general.player-not-found", "player", args[1]);
        }
        java.util.List<online.mythoscraft.core.item.MagicService.Spell> spells = new ArrayList<>();
        if (args[2].equalsIgnoreCase("alle") || args[2].equalsIgnoreCase("all")) {
            spells.addAll(java.util.List.of(online.mythoscraft.core.item.MagicService.Spell.values()));
        } else {
            online.mythoscraft.core.item.MagicService.Spell found = plugin.magic().spell(args[2]);
            if (found == null) {
                throw new CommandFail("magic.unknown-spell", "spells", String.join(", ", plugin.magic().spellIds()));
            }
            spells.add(found);
        }
        boolean learn = sub.equals("lernen") || sub.equals("learn");
        for (online.mythoscraft.core.item.MagicService.Spell s : spells) {
            plugin.magic().setKnown(target, s, learn);
        }
        plugin.messages().send(sender, learn ? "magic.admin-learned" : "magic.admin-forgot", "player", target.getName(), "count", spells.size());
    }

    private void music(CommandSender sender, String a) throws CommandFail {
        MusicService music = plugin.attractions().music();
        if (a.equals("skip")) {
            permission(sender, "mythos.season");
            music.skip();
            plugin.messages().send(sender, "music.skipped");
            return;
        }
        Player p = player(sender);
        switch (a) {
            case "an", "on" -> {
                music.setMusic(p, true);
                plugin.messages().send(p, "music.turned-on");
            }
            case "aus", "off" -> {
                music.setMusic(p, false);
                plugin.messages().send(p, "music.turned-off");
            }
            default -> {
                Song s = music.current();
                plugin.messages().send(p, "music.info", "song", s == null ? "-" : s.title(),
                        "state", plugin.messages().get(music.musicOn(p) ? "music.state-on" : "music.state-off"));
            }
        }
    }

    private void stream(Player p, String a) {
        MusicService music = plugin.attractions().music();
        boolean on = switch (a) {
            case "on", "an" -> true;
            case "off", "aus" -> false;
            default -> !music.streaming(p);
        };
        music.setStreaming(p, on);
        plugin.messages().send(p, on ? "stream.enabled" : "stream.disabled");
    }

    private void season(CommandSender sender, String[] args) throws CommandFail {
        SeasonService seasons = plugin.seasons();
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "info";
        if (sub.equals("info")) {
            SeasonService.Season s = seasons.active();
            plugin.messages().send(sender, "seasons.info", "season", s == null ? plugin.messages().get("seasons.none") : Text.parse(s.name()),
                    "mode", seasons.mode());
            return;
        }
        permission(sender, "mythos.season");
        switch (sub) {
            case "start" -> {
                if (args.length < 2) {
                    throw CommandFail.usage("/season start <" + String.join("|", ids()) + ">");
                }
                if (!seasons.setMode(args[1])) {
                    throw new CommandFail("seasons.unknown", "season", args[1], "seasons", String.join(", ", ids()));
                }
                plugin.messages().broadcast("seasons.started", "season", Text.parse(seasons.byId(args[1]).name()));
            }
            case "stop" -> {
                seasons.setMode("none");
                plugin.messages().send(sender, "seasons.stopped");
            }
            case "auto" -> {
                seasons.setMode("auto");
                plugin.messages().send(sender, "seasons.auto");
            }
            case "silvester-aus", "silvester-stop", "silvesteraus" -> {
                permission(sender, "mythos.season");
                plugin.attractions().ambience().endSilvester();
                plugin.messages().send(sender, "seasons.silvester-ended");
            }
            case "silvester-an", "silvester-resume" -> {
                permission(sender, "mythos.season");
                plugin.attractions().ambience().resumeSilvester();
                plugin.messages().send(sender, "seasons.silvester-resumed");
            }
            case "silvester", "countdown", "neujahr-test" -> {
                plugin.attractions().ambience().testShow();
                plugin.messages().send(sender, "seasons.test-show");
            }
            default -> throw CommandFail.usage("/season <info|start|stop|auto|silvester|silvester-aus|silvester-an>");
        }
    }

    private List<String> ids() {
        List<String> ids = new ArrayList<>();
        plugin.seasons().all().forEach(s -> ids.add(s.id()));
        return ids;
    }

    @Override
    protected List<String> complete(CommandSender sender, String label, String[] args) {
        return switch (label) {
            case "musik", "music" -> args.length == 1 ? List.of("an", "aus", "skip") : List.of();
            case "magie", "magic" -> args.length == 1 ? List.of("lernen", "vergessen")
                    : args.length == 2 ? plugin.getServer().getOnlinePlayers().stream().map(Player::getName).toList()
                    : args.length == 3 ? java.util.stream.Stream.concat(java.util.stream.Stream.of("alle"), plugin.magic().spellIds().stream()).toList()
                    : List.of();
            case "stream", "streamer" -> args.length == 1 ? List.of("on", "off") : List.of();
            case "season", "saison" -> args.length == 1 ? List.of("info", "start", "stop", "auto", "silvester", "silvester-aus", "silvester-an")
                    : args.length == 2 && args[0].equalsIgnoreCase("start") ? ids() : List.of();
            default -> List.of();
        };
    }
}
