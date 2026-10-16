package online.mythoscraft.core;

import online.mythoscraft.core.arena.ArenaListener;
import online.mythoscraft.core.arena.ArenaManager;
import online.mythoscraft.core.build.BuildService;
import online.mythoscraft.core.command.ArenaCommand;
import online.mythoscraft.core.command.BalanceCommand;
import online.mythoscraft.core.command.BaseCommand;
import online.mythoscraft.core.command.HomeCommand;
import online.mythoscraft.core.command.MarketCommands;
import online.mythoscraft.core.command.MythosCommand;
import online.mythoscraft.core.command.PayCommand;
import online.mythoscraft.core.command.RankCommand;
import online.mythoscraft.core.command.RtpCommand;
import online.mythoscraft.core.command.SocialCommands;
import online.mythoscraft.core.command.SpawnCommand;
import online.mythoscraft.core.command.TournamentCommand;
import online.mythoscraft.core.command.TpaCommand;
import online.mythoscraft.core.config.ConfigProblems;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.config.ConfigUpdater;
import online.mythoscraft.core.config.Messages;
import online.mythoscraft.core.core.Tasks;
import online.mythoscraft.core.crate.CrateListener;
import online.mythoscraft.core.crate.CrateManager;
import online.mythoscraft.core.database.ConnectionProvider;
import online.mythoscraft.core.database.DatabaseManager;
import online.mythoscraft.core.database.MysqlConnectionProvider;
import online.mythoscraft.core.database.SqliteConnectionProvider;
import online.mythoscraft.core.display.ChatListener;
import online.mythoscraft.core.display.DisplayService;
import online.mythoscraft.core.display.Placeholders;
import online.mythoscraft.core.display.ResourcePackService;
import online.mythoscraft.core.economy.Currency;
import online.mythoscraft.core.economy.DailyRewardService;
import online.mythoscraft.core.economy.EconomyManager;
import online.mythoscraft.core.economy.MultiplierService;
import online.mythoscraft.core.events.EventManager;
import online.mythoscraft.core.gui.MenuListener;
import online.mythoscraft.core.item.AbilitySupport;
import online.mythoscraft.core.item.ArmorSetService;
import online.mythoscraft.core.item.BroomRide;
import online.mythoscraft.core.item.CustomItemManager;
import online.mythoscraft.core.item.ExtraAbilityListener;
import online.mythoscraft.core.item.ItemAbilityListener;
import online.mythoscraft.core.item.ItemGuardListener;
import online.mythoscraft.core.item.SeasonService;
import online.mythoscraft.core.item.ToolListener;
import online.mythoscraft.core.item.WaveFiveListener;
import online.mythoscraft.core.item.WaveFourListener;
import online.mythoscraft.core.item.WaveSixListener;
import online.mythoscraft.core.item.WaveSevenListener;
import online.mythoscraft.core.item.WaveEightListener;
import online.mythoscraft.core.item.WaveThreeListener;
import online.mythoscraft.core.market.AuctionManager;
import online.mythoscraft.core.market.OrderManager;
import online.mythoscraft.core.player.PlayerData;
import online.mythoscraft.core.player.PlayerDataListener;
import online.mythoscraft.core.player.PlayerDataManager;
import online.mythoscraft.core.protection.ProtectionService;
import online.mythoscraft.core.protection.RegionManager;
import online.mythoscraft.core.protection.SelectionManager;
import online.mythoscraft.core.protection.listener.BlockProtectionListener;
import online.mythoscraft.core.protection.listener.EntityProtectionListener;
import online.mythoscraft.core.protection.listener.EnvironmentProtectionListener;
import online.mythoscraft.core.rank.MythosOwner;
import online.mythoscraft.core.rank.RankManager;
import online.mythoscraft.core.rank.RankVouchers;
import online.mythoscraft.core.shop.ShopManager;
import online.mythoscraft.core.command.ProgressCommands;
import online.mythoscraft.core.progress.AchievementService;
import online.mythoscraft.core.progress.JobsService;
import online.mythoscraft.core.progress.ParkourService;
import online.mythoscraft.core.progress.ProgressService;
import online.mythoscraft.core.progress.WeeklyRewardService;
import online.mythoscraft.core.stats.ButtonManager;
import online.mythoscraft.core.stats.HologramManager;
import online.mythoscraft.core.stats.LeaderboardService;
import online.mythoscraft.core.stats.StatsListener;
import online.mythoscraft.core.teleport.CombatTracker;
import online.mythoscraft.core.teleport.HomeManager;
import online.mythoscraft.core.teleport.RtpService;
import online.mythoscraft.core.teleport.SpawnManager;
import online.mythoscraft.core.teleport.TeleportService;
import online.mythoscraft.core.teleport.TpaManager;
import online.mythoscraft.core.tournament.TournamentListener;
import online.mythoscraft.core.tournament.TournamentManager;
import online.mythoscraft.core.util.Cooldowns;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.logging.Level;

/**
 * MythosCraft core plugin. This class only wires the systems together and drives their lifecycle; every feature
 * lives in its own package. Listeners are registered exactly once on enable, repeating tasks are owned by
 * {@link Tasks} and recreated per system on reload – so /mythos reload never duplicates anything.
 */
public class MythosCraft extends JavaPlugin {

    private ConfigProblems configProblems;
    private Messages messages;
    private Tasks tasks;
    private Cooldowns cooldowns;
    private DatabaseManager database;
    private PlayerDataManager playerData;
    private RankManager ranks;
    private MultiplierService multipliers;
    private EconomyManager economy;
    private DailyRewardService daily;
    private RegionManager regions;
    private SelectionManager selections;
    private ProtectionService protection;
    private TeleportService teleports;
    private CombatTracker combat;
    private SpawnManager spawn;
    private HomeManager homes;
    private TpaManager tpa;
    private ArenaManager arenas;
    private ResourcePackService resourcePack;
    private DisplayService display;
    private Placeholders placeholders;
    private ChatListener chat;
    private StatsListener stats;
    private CustomItemManager items;
    private ToolListener tools;
    private EventManager events;
    private CrateManager crates;
    private LeaderboardService leaderboards;
    private HologramManager holograms;
    private ItemAbilityListener itemAbilities;
    private ArmorSetService armorSets;
    private SeasonService seasons;
    private TournamentManager tournaments;
    private final online.mythoscraft.core.tournament.TrophyService trophies = new online.mythoscraft.core.tournament.TrophyService(this);
    private BuildService builds;
    private ShopManager shop;
    private AuctionManager auctions;
    private OrderManager orders;
    private RtpService rtp;
    private RankVouchers vouchers;
    private ExtraAbilityListener extraAbilities;
    private BroomRide broom;
    private WaveThreeListener waveThree;
    private WaveFourListener waveFour;
    private WaveFiveListener waveFive;
    private WaveSixListener waveSix;
    private WaveSevenListener waveSeven;
    private WaveEightListener waveEight;
    private online.mythoscraft.core.item.SilvesterListener silvester;
    private online.mythoscraft.core.item.RelicListener relics;
    private online.mythoscraft.core.cosmetic.CompanionService companions;
    private online.mythoscraft.core.cosmetic.CompanionFarmService companionFarms;
    private online.mythoscraft.core.web.WebDailyRewards webDaily;
    private online.mythoscraft.core.web.PlayerSite playerSite;
    private online.mythoscraft.core.progress.NewbieService newbies;
    private online.mythoscraft.core.teleport.StarterBaseService bases;

    public online.mythoscraft.core.cosmetic.CompanionService companions() {
        return companions;
    }
    private ButtonManager buttons;
    private ProgressService progress;
    private JobsService jobs;
    private AchievementService achievements;
    private WeeklyRewardService weekly;
    private ParkourService parkour;
    private online.mythoscraft.core.attraction.AttractionStore attractionStore;
    private online.mythoscraft.core.attraction.Attractions attractions;
    private online.mythoscraft.core.clan.ClanService clans;
    private online.mythoscraft.core.progress.QuestService quests;
    private online.mythoscraft.core.attraction.BetService bets;
    private online.mythoscraft.core.clan.ClanWarService clanWar;
    private online.mythoscraft.core.crate.CrateCarouselService crateCarousel;
    private online.mythoscraft.core.item.BoosterService boosters;
    private online.mythoscraft.core.rank.RankPerksService rankPerks;
    private online.mythoscraft.core.attraction.GiveawayService giveaways;
    private online.mythoscraft.core.web.WebPanelService webPanel;
    private online.mythoscraft.core.item.MagicService magic;
    private online.mythoscraft.core.attraction.SpawnCreatureService creatures;
    private online.mythoscraft.core.attraction.SeasonGroundService ground;
    private online.mythoscraft.core.attraction.SkyDomeService skyDome;
    private online.mythoscraft.core.attraction.FloorGamesService floorGames;
    private online.mythoscraft.core.item.UpgradeService upgrades;

    @Override
    public void onEnable() {
        long start = System.currentTimeMillis();
        try {
            saveDefaultConfig();
            updateFiles();
            reloadConfig();
            configProblems = new ConfigProblems(getLogger());
            tasks = new Tasks(this);
            cooldowns = new Cooldowns();
            messages = new Messages(this);
            messages.load(getConfig().getString("messages.prefix"));
            messages.languageSettings(getConfig().getBoolean("language.per-player", true), getConfig().getString("language.default", "en"));
            online.mythoscraft.core.config.ConfigText.load(this);

            database = new DatabaseManager(getLogger());
            database.start(createProvider(rootConfig()));

            playerData = new PlayerDataManager(this, database);
            ranks = new RankManager(this);
            multipliers = new MultiplierService(this);
            economy = new EconomyManager(this);
            daily = new DailyRewardService(this);
            regions = new RegionManager(this);
            selections = new SelectionManager(this);
            protection = new ProtectionService(this, regions);
            teleports = new TeleportService(this);
            combat = new CombatTracker();
            spawn = new SpawnManager(this);
            homes = new HomeManager(this, cooldowns);
            tpa = new TpaManager(this, cooldowns);
            arenas = new ArenaManager(this);
            resourcePack = new ResourcePackService(this);
            placeholders = new Placeholders(this);
            display = new DisplayService(this);
            chat = new ChatListener(this);
            stats = new StatsListener(this);
            items = new CustomItemManager(this);
            AbilitySupport abilitySupport = new AbilitySupport(this, cooldowns);
            tools = new ToolListener(this, abilitySupport);
            itemAbilities = new ItemAbilityListener(this, abilitySupport);
            extraAbilities = new ExtraAbilityListener(this, abilitySupport);
            broom = new BroomRide(this, abilitySupport);
            waveThree = new WaveThreeListener(this, abilitySupport);
            waveFour = new WaveFourListener(this, abilitySupport);
            waveFive = new WaveFiveListener(this, abilitySupport);
            waveSix = new WaveSixListener(this, abilitySupport);
            waveSeven = new WaveSevenListener(this, abilitySupport);
            waveEight = new WaveEightListener(this, abilitySupport);
            silvester = new online.mythoscraft.core.item.SilvesterListener(this, abilitySupport);
            relics = new online.mythoscraft.core.item.RelicListener(this, abilitySupport);
            companions = new online.mythoscraft.core.cosmetic.CompanionService(this);
            companionFarms = new online.mythoscraft.core.cosmetic.CompanionFarmService(this);
            webDaily = new online.mythoscraft.core.web.WebDailyRewards(this);
            playerSite = new online.mythoscraft.core.web.PlayerSite(this);
            newbies = new online.mythoscraft.core.progress.NewbieService(this);
            bases = new online.mythoscraft.core.teleport.StarterBaseService(this);
            rtp = new RtpService(this, cooldowns);
            vouchers = new RankVouchers(this);
            armorSets = new ArmorSetService(this);
            seasons = new SeasonService(this);
            tournaments = new TournamentManager(this);
            builds = new BuildService(this);
            events = new EventManager(this);
            crates = new CrateManager(this);
            leaderboards = new LeaderboardService(this);
            holograms = new HologramManager(this);
            buttons = new ButtonManager(this);
            progress = new ProgressService(this);
            jobs = new JobsService(this);
            achievements = new AchievementService(this);
            weekly = new WeeklyRewardService(this);
            parkour = new ParkourService(this);
            attractionStore = new online.mythoscraft.core.attraction.AttractionStore(this);
            attractions = new online.mythoscraft.core.attraction.Attractions(this);
            clans = new online.mythoscraft.core.clan.ClanService(this);
            quests = new online.mythoscraft.core.progress.QuestService(this);
            bets = new online.mythoscraft.core.attraction.BetService(this);
            clanWar = new online.mythoscraft.core.clan.ClanWarService(this);
            crateCarousel = new online.mythoscraft.core.crate.CrateCarouselService(this);
            boosters = new online.mythoscraft.core.item.BoosterService(this);
            rankPerks = new online.mythoscraft.core.rank.RankPerksService(this);
            giveaways = new online.mythoscraft.core.attraction.GiveawayService(this);
            webPanel = new online.mythoscraft.core.web.WebPanelService(this);
            magic = new online.mythoscraft.core.item.MagicService(this);
            creatures = new online.mythoscraft.core.attraction.SpawnCreatureService(this);
            ground = new online.mythoscraft.core.attraction.SeasonGroundService(this);
            skyDome = new online.mythoscraft.core.attraction.SkyDomeService(this);
            floorGames = new online.mythoscraft.core.attraction.FloorGamesService(this);
            upgrades = new online.mythoscraft.core.item.UpgradeService(this);
            shop = new ShopManager(this);
            auctions = new AuctionManager(this);
            orders = new OrderManager(this);

            loadAll();
            registerListeners();
            registerCommands();
            for (Player p : Bukkit.getOnlinePlayers()) {
                playerData.loadAsync(p);
            }
            if (!Bukkit.getOnlineMode()) {
                getLogger().warning("Server runs in offline mode: the MYTHOS rank owner (" + MythosOwner.NAME
                        + ") is identified by the name-based UUID, which is only safe behind a proxy with forwarding.");
            }
            getLogger().info("Plugin enabled in " + (System.currentTimeMillis() - start) + " ms"
                    + (configProblems.list().isEmpty() ? "" : " (" + configProblems.list().size() + " config warnings)"));
        } catch (Throwable t) {
            getLogger().log(Level.SEVERE, "MythosCraft could not start – disabling. Cause: " + t.getMessage(), t);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    /** Adds settings that are new in this plugin version to the server's existing files (keeps all changes). */
    private void updateFiles() {
        for (String name : new String[]{"config.yml", "messages.yml", "messages_en.yml", "crates.yml"}) {
            File file = new File(getDataFolder(), name);
            try (InputStream bundled = getResource(name)) {
                if (bundled == null) {
                    continue;
                }
                int added = ConfigUpdater.update(file, bundled);
                if (added >= 0) {
                    getLogger().info(name + " updated to the new plugin version: " + added
                            + " new settings added, your changes were kept (backup: " + name + ".v*.bak)");
                }
            } catch (IOException | InvalidConfigurationException e) {
                getLogger().warning("Could not update " + name + ": " + e.getMessage());
            }
        }
    }

    private ConnectionProvider createProvider(ConfigReader root) {
        ConfigReader db = root.sub("database");
        String type = db.string("type", "sqlite").toLowerCase();
        if (type.equals("mysql") || type.equals("mariadb")) {
            return new MysqlConnectionProvider(db.string("mysql.host", "localhost"), db.integer("mysql.port", 3306, 1, 65535),
                    db.string("mysql.database", "mythoscraft"), db.string("mysql.user", "mythoscraft"),
                    db.string("mysql.password", ""), db.bool("mysql.ssl", false));
        }
        if (!type.equals("sqlite")) {
            db.warn("type", "'" + type + "' unbekannt – verwende sqlite");
        }
        return new SqliteConnectionProvider(new File(getDataFolder(), db.string("file", "mythoscraft.db")));
    }

    public ConfigReader rootConfig() {
        return new ConfigReader(getConfig(), "config.yml", configProblems);
    }

    /** Reads every configuration and data file. Safe to call repeatedly. */
    private void loadAll() {
        ConfigReader root = rootConfig();
        ranks.reload(root);
        multipliers.reload(root);
        economy.reload(root);
        daily.reload(root);
        stats.reload(root);
        regions.reload(root);
        protection.reload(root);
        arenas.reload(root);
        spawn.reload(root);
        teleports.reload(root);
        combat.reload(root);
        homes.reload(root);
        tpa.reload(root);
        resourcePack.reload(root);
        chat.reload(root);
        display.reload(root);
        tools.reload(root);
        itemAbilities.reload(root);
        extraAbilities.reload(root);
        broom.reload(root);
        waveThree.reload(root);
        waveFour.reload(root);
        waveFive.reload(root);
        waveSix.reload(root);
        waveSeven.reload(root);
        waveEight.reload(root);
        relics.reload(root);
        companionFarms.reload(root);
        webDaily.reload(root);
        newbies.reload(root);
        bases.reload(root);
        rtp.reload(root);
        armorSets.start();
        seasons.reload(root);
        tournaments.reload(root);
        builds.reload(root);
        events.reload(root);
        crates.reload(configProblems);
        shop.reload(root);
        auctions.reload(root);
        orders.reload(root);
        holograms.reload();
        buttons.reload();
        jobs.reload(root);
        achievements.reload(root);
        weekly.reload(root);
        parkour.reload(root);
        clans.reload(root);
        clanWar.reload(root);
        crateCarousel.reload(root);
        boosters.reload(root);
        rankPerks.reload(root);
        giveaways.reload(root);
        webPanel.reload(root);
        magic.reload(root);
        creatures.reload(root);
        ground.reload(root);
        skyDome.reload(root);
        floorGames.reload(root);
        upgrades.reload(root);
        quests.reload(root);
        bets.reload(root);
        attractions.reload(root);
        playerData.reload(root);
        leaderboards.invalidate();
    }

    /** /mythos reload: config, messages, regions, arenas, events, crates – without duplicating listeners or tasks. */
    public void reloadAll() {
        configProblems.clear();
        updateFiles();
        reloadConfig();
        messages.load(getConfig().getString("messages.prefix"));
        messages.languageSettings(getConfig().getBoolean("language.per-player", true), getConfig().getString("language.default", "en"));
        online.mythoscraft.core.config.ConfigText.load(this);
        loadAll();
        for (Player p : Bukkit.getOnlinePlayers()) {
            ranks.applyPermissions(p);
        }
        getLogger().info("Reloaded" + (configProblems.list().isEmpty() ? "" : " with " + configProblems.list().size() + " config warnings"));
    }

    private void registerListeners() {
        PluginManager pm = getServer().getPluginManager();
        Listener[] listeners = {
                new PlayerDataListener(this, playerData),
                new BlockProtectionListener(protection),
                new EntityProtectionListener(protection),
                new EnvironmentProtectionListener(protection, regions),
                spawn, teleports, tpa, combat,
                new ArenaListener(this, arenas),
                resourcePack, display, chat, stats, tools,
                new ItemGuardListener(this),
                new CrateListener(this, crates),
                new MenuListener(),
                itemAbilities, extraAbilities, waveThree, waveFour, waveFive, waveSix, waveSeven, waveEight, silvester, relics, broom, vouchers, buttons, jobs, parkour, seasons,
                new TournamentListener(this, tournaments),
                new SessionListener(),
                clans, upgrades, floorGames, boosters, magic, creatures, rankPerks, giveaways,
                new online.mythoscraft.core.item.HelmetUpgradeListener(this),
                new online.mythoscraft.core.teleport.SpawnItemListener(this),
                companions, companionFarms, webDaily, newbies, bases, playerSite,
                trophies,
                new online.mythoscraft.core.display.ServerLinksService(this),
                new online.mythoscraft.core.config.LocaleContextListener(messages)
        };
        for (Listener l : listeners) {
            pm.registerEvents(l, this);
        }
        for (Listener l : attractions.listeners()) {
            pm.registerEvents(l, this);
        }
    }

    private void registerCommands() {
        command("mythos", new MythosCommand(this));
        BaseCommand spawnCmd = new SpawnCommand(this);
        command("spawn", spawnCmd);
        command("setspawn", spawnCmd);
        BaseCommand home = new HomeCommand(this);
        for (String c : new String[]{"home", "sethome", "delhome", "homes"}) {
            command(c, home);
        }
        BaseCommand tpaCmd = new TpaCommand(this);
        for (String c : new String[]{"tpa", "tpahere", "tpaccept", "tpdeny", "tpacancel"}) {
            command(c, tpaCmd);
        }
        BaseCommand balance = new BalanceCommand(this);
        for (String c : new String[]{"balance", "coins", "gems"}) {
            command(c, balance);
        }
        PayCommand payCommand = new PayCommand(this, cooldowns);
        command("pay", payCommand);
        command("gempay", payCommand);
        BaseCommand social = new SocialCommands(this, cooldowns);
        for (String c : new String[]{"helpop", "daily", "stats", "top", "event"}) {
            command(c, social);
        }
        command("rank", new RankCommand(this));
        command("arena", new ArenaCommand(this));
        command("tournament", new TournamentCommand(this));
        command("rtp", new RtpCommand(this));
        BaseCommand clanCmd = new online.mythoscraft.core.clan.ClanCommand(this, clans);
        command("clan", clanCmd);
        command("cc", clanCmd);
        command("wette", bets);
        command("verlosung", giveaways);
        command("website", new online.mythoscraft.core.web.WebsiteCommand(this));
        command("schmied", new online.mythoscraft.core.item.UpgradeCommand(this));
        BaseCommand attractionCmd = new online.mythoscraft.core.attraction.AttractionCommands(this);
        for (String c : new String[]{"ringe", "musik", "stream", "season", "haendler", "dropper", "labyrinth", "aufgaben", "snowman", "kisten", "blockparty", "spleef", "zielschiessen", "booster", "magie"}) {
            command(c, attractionCmd);
        }
        BaseCommand invsee = new online.mythoscraft.core.command.InvseeCommand(this);
        command("invsee", invsee);
        command("endersee", invsee);
        BaseCommand progressCmd = new ProgressCommands(this);
        for (String c : new String[]{"jobs", "erfolge", "parkour"}) {
            command(c, progressCmd);
        }
        BaseCommand starter = new online.mythoscraft.core.command.StarterKitCommand(this);
        command("new", starter);
        command("pronounce", new online.mythoscraft.core.command.AnnounceCommand(this));
        online.mythoscraft.core.command.CreatorModeService creatorMode = new online.mythoscraft.core.command.CreatorModeService(this);
        getServer().getPluginManager().registerEvents(creatorMode, this);
        command("creatormode", creatorMode);
        command("gm", creatorMode);
        intro = new online.mythoscraft.core.attraction.IntroService(this);
        getServer().getPluginManager().registerEvents(intro, this);
        command("intro", intro);
        guide = new online.mythoscraft.core.attraction.GuideService(this);
        getServer().getPluginManager().registerEvents(guide, this);
        command("guide", guide);
        command("thank", newbies);
        command("applications", playerSite);
        command("discord", new online.mythoscraft.core.display.DiscordCommand(this));
        command("base", bases);
        command("plot", new online.mythoscraft.core.teleport.PlotTransferCommand(this));
        online.mythoscraft.core.command.ModerationService moderation = new online.mythoscraft.core.command.ModerationService(this);
        getServer().getPluginManager().registerEvents(moderation, this);
        for (String c : new String[]{"mute", "unmute", "kick", "tempban", "ban", "unban", "warn", "warns", "goto", "modlog", "testkey"}) {
            command(c, moderation);
        }

        wiki();
        new online.mythoscraft.core.teleport.HomeMarkerService(this);
        tasks().later(60, "orphan crate/hologram sweep", () -> {
            int n = 0;
            for (org.bukkit.World w : getServer().getWorlds()) {
                org.bukkit.entity.Entity[] all = w.getEntities().toArray(new org.bukkit.entity.Entity[0]);
                n += crates().sweepOrphans(all) + holograms().sweepOrphans(all);
            }
            if (n > 0) {
                getLogger().info("Removed " + n + " leftover crate/hologram displays");
            }
        });
        BaseCommand market = new MarketCommands(this);
        for (String c : new String[]{"shop", "ah", "auftrag"}) {
            command(c, market);
        }
    }

    private void command(String name, BaseCommand executor) {
        PluginCommand cmd = getCommand(name);
        if (cmd == null) {
            getLogger().severe("Command /" + name + " is missing in plugin.yml");
            return;
        }
        cmd.setExecutor(executor);
        cmd.setTabCompleter(executor);
        org.bukkit.command.Command active = getServer().getCommandMap().getCommand(name);
        if (active instanceof PluginCommand pc && pc.getPlugin() != this) {
            getLogger().warning("/" + name + " belongs to " + pc.getPlugin().getName() + " – MythosCraft's version is /mythoscraft:" + name);
        }
    }

    /** Hook from the player data manager once a joining player's data is available. */
    public void onPlayerDataReady(Player player, PlayerData data, boolean firstJoin) {
        ranks.handleJoin(player, data);
        if (firstJoin && economy.startCoins() > 0) {
            economy.deposit(data, Currency.COINS, economy.startCoins());
        }
        display.handleJoin(player, data, firstJoin);
        spawn.handleJoin(player, firstJoin);
        if (firstJoin) {
            online.mythoscraft.core.command.StarterKitCommand.hint(this, player);
            if (intro != null) {
                intro.firstJoin(player);
            }
        }
        arenas.recover(player);
        seasons.greet(player);
        progress.load(player, () -> {
            achievements.check(player);
            jobs.backPay(player);
            if (guide != null) {
                guide.resume(player);
            }
            display.refresh(player);
        });
        tasks.later(80, "market reminders", () -> {
            if (player.isOnline()) {
                auctions.greet(player);
                orders.greet(player);
            }
        });
        if (daily.canClaim(data)) {
            tasks.later(60, "daily reminder", () -> {
                if (player.isOnline() && daily.canClaim(playerData.get(player))) {
                    messages.send(player, "daily.reminder");
                }
            });
        }
    }

    /** Called by the multiplier service when the calendar day changes (configured time zone). */
    public void onDayChange(LocalDate day) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (daily.canClaim(playerData.get(p))) {
                messages.send(p, "daily.reminder");
            }
        }
    }

    private final class SessionListener implements Listener {
        @EventHandler(priority = EventPriority.MONITOR)
        public void onQuit(PlayerQuitEvent event) {
            Player p = event.getPlayer();
            ranks.handleQuit(p);
            protection.forget(p.getUniqueId());
            selections.clear(p.getUniqueId());
            armorSets.forget(p.getUniqueId());
            rtp.forget(p.getUniqueId());
            progress.forget(p.getUniqueId());
        }
    }

    @Override
    public void onDisable() {
        if (guide != null) {
            guide.shutdown();
        }
        if (intro != null) {
            intro.shutdown();
        }
        safe("broom", () -> broom.shutdown());
        safe("item sizes", () -> waveFour.shutdown());
        safe("tournaments", () -> tournaments.shutdown());
        safe("events", () -> events.shutdown());
        safe("floor games", () -> floorGames.shutdown());
        safe("crate carousel", () -> crateCarousel.removeSigns());
        safe("spawn creatures", () -> creatures.despawnAll());
        safe("sky dome", () -> skyDome.clear());
        safe("rank perks", () -> rankPerks.shutdown());
        safe("web panel", () -> webPanel.stop());
        safe("crates", () -> crates.shutdown());
        safe("arenas", () -> arenas.shutdown());
        safe("teleports", () -> teleports.shutdown());
        safe("holograms", () -> holograms.shutdown());
        safe("buttons", () -> buttons.shutdown());
        safe("attractions", () -> attractions.shutdown());
        safe("planets", () -> waveEight.shutdown());
        safe("relic items", () -> relics.shutdown());
        safe("companion farms", () -> companionFarms.shutdown());
        safe("web daily", () -> webDaily.shutdown());
        safe("bases", () -> bases.shutdown());
        safe("companions", () -> companions.shutdown());
        safe("clans", () -> clans.shutdown());
        safe("bets", () -> bets.shutdown());
        safe("display", () -> display.shutdown());
        safe("ranks", () -> ranks.shutdown());
        safe("resource pack", () -> resourcePack.shutdown());
        safe("player data", () -> playerData.saveAllOnShutdown());
        safe("tasks", () -> tasks.shutdown());
        safe("database", () -> database.shutdown());
        getLogger().info("Plugin disabled");
    }

    private void safe(String what, Runnable action) {
        try {
            action.run();
        } catch (NullPointerException e) {
            // system was never started (enable failed early)
        } catch (Throwable t) {
            getLogger().log(Level.SEVERE, "Error while shutting down " + what, t);
        }
    }

    // ---- accessors ----------------------------------------------------------------------------------------

    public ConfigProblems configProblems() {
        return configProblems;
    }

    public Messages messages() {
        return messages;
    }

    public Tasks tasks() {
        return tasks;
    }

    public DatabaseManager database() {
        return database;
    }

    public PlayerDataManager playerData() {
        return playerData;
    }

    public RankManager ranks() {
        return ranks;
    }

    public MultiplierService multipliers() {
        return multipliers;
    }

    public EconomyManager economy() {
        return economy;
    }

    public DailyRewardService daily() {
        return daily;
    }

    public RegionManager regions() {
        return regions;
    }

    public SelectionManager selections() {
        return selections;
    }

    public ProtectionService protection() {
        return protection;
    }

    public TeleportService teleports() {
        return teleports;
    }

    public CombatTracker combat() {
        return combat;
    }

    public SpawnManager spawn() {
        return spawn;
    }

    public HomeManager homes() {
        return homes;
    }

    public TpaManager tpa() {
        return tpa;
    }

    public ArenaManager arenas() {
        return arenas;
    }

    public ResourcePackService resourcePack() {
        return resourcePack;
    }

    public DisplayService display() {
        return display;
    }

    public Placeholders placeholders() {
        return placeholders;
    }

    public CustomItemManager items() {
        return items;
    }

    public EventManager events() {
        return events;
    }

    public CrateManager crates() {
        return crates;
    }

    public LeaderboardService leaderboards() {
        return leaderboards;
    }

    public HologramManager holograms() {
        return holograms;
    }

    public ButtonManager buttons() {
        return buttons;
    }

    public ProgressService progress() {
        return progress;
    }

    public JobsService jobs() {
        return jobs;
    }

    public AchievementService achievements() {
        return achievements;
    }

    public WeeklyRewardService weekly() {
        return weekly;
    }

    public online.mythoscraft.core.attraction.AttractionStore attractionStore() {
        return attractionStore;
    }

    public online.mythoscraft.core.attraction.Attractions attractions() {
        return attractions;
    }

    public online.mythoscraft.core.attraction.BetService bets() {
        return bets;
    }

    public online.mythoscraft.core.web.WebPanelService webPanel() {
        return webPanel;
    }

    public online.mythoscraft.core.attraction.GiveawayService giveaways() {
        return giveaways;
    }

    public online.mythoscraft.core.rank.RankPerksService rankPerks() {
        return rankPerks;
    }

    public online.mythoscraft.core.item.BoosterService boosters() {
        return boosters;
    }

    public online.mythoscraft.core.item.MagicService magic() {
        return magic;
    }

    public online.mythoscraft.core.attraction.SpawnCreatureService creatures() {
        return creatures;
    }

    public online.mythoscraft.core.attraction.SeasonGroundService ground() {
        return ground;
    }

    public online.mythoscraft.core.crate.CrateCarouselService crateCarousel() {
        return crateCarousel;
    }

    public online.mythoscraft.core.attraction.FloorGamesService floorGames() {
        return floorGames;
    }

    public online.mythoscraft.core.clan.ClanWarService clanWar() {
        return clanWar;
    }

    public online.mythoscraft.core.item.UpgradeService upgrades() {
        return upgrades;
    }

    public online.mythoscraft.core.progress.QuestService quests() {
        return quests;
    }

    public online.mythoscraft.core.clan.ClanService clans() {
        return clans;
    }

    public ParkourService parkour() {
        return parkour;
    }

    private online.mythoscraft.core.web.PublicWikiData wiki;
    private online.mythoscraft.core.attraction.IntroService intro;
    private online.mythoscraft.core.attraction.GuideService guide;

    public online.mythoscraft.core.attraction.GuideService guide() {
        return guide;
    }

    public online.mythoscraft.core.attraction.IntroService intro() {
        return intro;
    }


    public online.mythoscraft.core.teleport.StarterBaseService bases() {
        return bases;
    }

    public online.mythoscraft.core.progress.NewbieService newbies() {
        return newbies;
    }

    public online.mythoscraft.core.web.PlayerSite playerSite() {
        return playerSite;
    }

    public online.mythoscraft.core.web.WebDailyRewards webDaily() {
        return webDaily;
    }

    /** Public data for the wiki website (created on first use, refreshes leaderboards every minute). */
    public online.mythoscraft.core.web.PublicWikiData wiki() {
        if (wiki == null) {
            wiki = new online.mythoscraft.core.web.PublicWikiData(this);
        }
        return wiki;
    }

    public online.mythoscraft.core.tournament.TrophyService trophies() {
        return trophies;
    }

    public TournamentManager tournaments() {
        return tournaments;
    }

    public SeasonService seasons() {
        return seasons;
    }

    public ArmorSetService armorSets() {
        return armorSets;
    }

    public BuildService builds() {
        return builds;
    }

    public ShopManager shop() {
        return shop;
    }

    public AuctionManager auctions() {
        return auctions;
    }

    public OrderManager orders() {
        return orders;
    }

    public RtpService rtp() {
        return rtp;
    }

    public RankVouchers vouchers() {
        return vouchers;
    }

    public BroomRide broom() {
        return broom;
    }
}
