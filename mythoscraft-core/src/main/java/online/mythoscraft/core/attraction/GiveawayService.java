package online.mythoscraft.core.attraction;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.command.BaseCommand;
import online.mythoscraft.core.command.CommandFail;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.reward.Reward;
import online.mythoscraft.core.util.SoundSpec;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Giveaways: a prize is announced, everybody online can join with one click (/verlosung) for a few minutes, then
 * a random winner (who is still online) gets it. Automatic giveaways run on fixed days/times (e.g. a 7-day VIP rank
 * every Sunday evening), admins start their own with /verlosung start &lt;minutes&gt; &lt;reward&gt;.
 */
public final class GiveawayService extends BaseCommand implements org.bukkit.event.Listener {

    private record Planned(Set<DayOfWeek> days, LocalTime time, String reward, int minutes, int winners) {
    }

    private final List<Planned> planned = new ArrayList<>();
    private boolean enabled = true;
    private int minPlayers = 2;
    private Reward prize;
    /** Item giveaway of a creator: the item itself, the host (gets it back if nobody joins). */
    private org.bukkit.inventory.ItemStack prizeItem;
    private UUID host;
    /** Coins a player put into his own coin giveaway (back to him if nobody joins). */
    private long hostCoins;
    private long itemFee = 1500;
    private int itemMaxMinutes = 10;
    private int itemCooldownMinutes = 20;
    private final java.util.Map<UUID, Long> lastItemGiveaway = new java.util.HashMap<>();
    private final java.io.File returnsFile;
    private int winners = 1;
    private long endsAt;
    private final Set<UUID> entries = new LinkedHashSet<>();
    private String lastPlannedKey = "";

    public GiveawayService(MythosCraft plugin) {
        super(plugin);
        this.returnsFile = new java.io.File(plugin.getDataFolder(), "data/giveaway-returns.yml");
    }

    public void reload(ConfigReader root) {
        ConfigReader cfg = root.sub("giveaways");
        enabled = cfg.bool("enabled", true);
        minPlayers = cfg.integer("min-players", 2, 1, 1000);
        itemFee = cfg.longValue("item.fee-coins", 1500, 0, 100_000_000);
        itemMaxMinutes = cfg.integer("item.max-minutes", 10, 1, 60);
        itemCooldownMinutes = cfg.integer("item.cooldown-minutes", 20, 0, 1440);
        planned.clear();
        for (String key : cfg.sub("auto").keys()) {
            ConfigReader g = cfg.sub("auto").sub(key);
            Set<DayOfWeek> days = java.util.EnumSet.noneOf(DayOfWeek.class);
            for (String d : g.strings("days")) {
                try {
                    days.add(DayOfWeek.valueOf(d.trim().toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException e) {
                    g.warn("days", "'" + d + "' ist kein Wochentag");
                }
            }
            if (days.isEmpty()) {
                days.addAll(java.util.EnumSet.allOf(DayOfWeek.class));
            }
            LocalTime t;
            try {
                t = LocalTime.parse(g.string("time", "18:00"));
            } catch (RuntimeException e) {
                g.warn("time", "keine Uhrzeit – 18:00");
                t = LocalTime.of(18, 0);
            }
            String reward = g.string("reward", "");
            try {
                Reward.parse(reward);
            } catch (IllegalArgumentException e) {
                g.warn("reward", e.getMessage());
                continue;
            }
            planned.add(new Planned(days, t, reward, g.integer("join-minutes", 5, 1, 120), g.integer("winners", 1, 1, 20)));
        }
        plugin.tasks().cancel(this);
        plugin.tasks().repeat(this, 100, 20, "giveaways", this::tick);
    }

    public boolean running() {
        return prize != null || prizeItem != null;
    }

    private net.kyori.adventure.text.Component prizeName() {
        if (prizeItem != null) {
            net.kyori.adventure.text.Component n;
            try {
                n = prizeItem.effectiveName();
            } catch (RuntimeException e) {
                n = net.kyori.adventure.text.Component.translatable(prizeItem.getType().translationKey());
            }
            return net.kyori.adventure.text.Component.text(prizeItem.getAmount() + "× ").append(n);
        }
        return prize == null ? net.kyori.adventure.text.Component.empty() : prize.describe(plugin, 1.0);
    }

    /** A creator raffles the item in the main hand (pays a small fee, the item is held until the draw). */
    private void startItem(Player p, int minutes) throws CommandFail {
        org.bukkit.inventory.ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            throw new CommandFail("giveaway.item-empty");
        }
        if (running()) {
            throw new CommandFail("giveaway.already");
        }
        long last = lastItemGiveaway.getOrDefault(p.getUniqueId(), 0L);
        long wait = last + itemCooldownMinutes * 60_000L - System.currentTimeMillis();
        if (wait > 0 && !p.hasPermission("mythos.giveaway")) {
            throw new CommandFail("giveaway.item-cooldown", "minutes", Math.max(1, wait / 60_000));
        }
        online.mythoscraft.core.player.PlayerData data = plugin.playerData().get(p);
        if (itemFee > 0 && (data == null || plugin.economy().withdraw(data, online.mythoscraft.core.economy.Currency.COINS, itemFee)
                != online.mythoscraft.core.economy.EconomyManager.Result.SUCCESS)) {
            throw new CommandFail("giveaway.item-fee", "fee", online.mythoscraft.core.util.Numbers.format(itemFee));
        }
        if (data != null) {
            plugin.playerData().save(data);
        }
        prizeItem = hand.clone();
        p.getInventory().setItemInMainHand(null);
        host = p.getUniqueId();
        prize = null;
        winners = 1;
        lastItemGiveaway.put(p.getUniqueId(), System.currentTimeMillis());
        endsAt = System.currentTimeMillis() + minutes * 60_000L;
        entries.clear();
        plugin.messages().broadcast("giveaway.item-started", "player", p.getName(), "prize", prizeName(), "minutes", minutes);
        for (Player o : plugin.getServer().getOnlinePlayers()) {
            SoundSpec.of("block.note_block.bell", 1f, 1.2f).play(o);
        }
    }

    /** A player raffles his own coins (taken from his balance now, back to him if nobody joins). */
    private void startCoins(Player p, long amount, int minutes) throws CommandFail {
        if (running()) {
            throw new CommandFail("giveaway.already");
        }
        long last = lastItemGiveaway.getOrDefault(p.getUniqueId(), 0L);
        long wait = last + itemCooldownMinutes * 60_000L - System.currentTimeMillis();
        if (wait > 0 && !p.hasPermission("mythos.giveaway")) {
            throw new CommandFail("giveaway.item-cooldown", "minutes", Math.max(1, wait / 60_000));
        }
        online.mythoscraft.core.player.PlayerData data = plugin.playerData().get(p);
        if (data == null || plugin.economy().withdraw(data, online.mythoscraft.core.economy.Currency.COINS, amount)
                != online.mythoscraft.core.economy.EconomyManager.Result.SUCCESS) {
            throw new CommandFail("giveaway.coins-not-enough", "amount", online.mythoscraft.core.util.Numbers.format(amount));
        }
        plugin.playerData().save(data);
        prize = Reward.parse("coins " + amount);
        prizeItem = null;
        host = p.getUniqueId();
        hostCoins = amount;
        winners = 1;
        lastItemGiveaway.put(p.getUniqueId(), System.currentTimeMillis());
        endsAt = System.currentTimeMillis() + minutes * 60_000L;
        entries.clear();
        plugin.messages().broadcast("giveaway.item-started", "player", p.getName(), "prize", prizeName(), "minutes", minutes);
        for (Player o : plugin.getServer().getOnlinePlayers()) {
            SoundSpec.of("block.note_block.bell", 1f, 1.2f).play(o);
        }
    }

    private void giveItem(UUID to, org.bukkit.inventory.ItemStack item) {
        Player p = plugin.getServer().getPlayer(to);
        if (p != null) {
            for (org.bukkit.inventory.ItemStack rest : p.getInventory().addItem(item).values()) {
                p.getWorld().dropItem(p.getLocation(), rest);
            }
            return;
        }
        // offline: keep it until the next join
        org.bukkit.configuration.file.YamlConfiguration y = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(returnsFile);
        List<String> list = new ArrayList<>(y.getStringList(to.toString()));
        list.add(java.util.Base64.getEncoder().encodeToString(item.serializeAsBytes()));
        y.set(to.toString(), list);
        try {
            returnsFile.getParentFile().mkdirs();
            y.save(returnsFile);
        } catch (java.io.IOException e) {
            plugin.getLogger().warning("giveaway-returns.yml: " + e.getMessage());
        }
    }

    @org.bukkit.event.EventHandler
    public void onJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        if (!returnsFile.exists()) {
            return;
        }
        Player p = event.getPlayer();
        org.bukkit.configuration.file.YamlConfiguration y = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(returnsFile);
        List<String> list = y.getStringList(p.getUniqueId().toString());
        if (list.isEmpty()) {
            return;
        }
        y.set(p.getUniqueId().toString(), null);
        try {
            y.save(returnsFile);
        } catch (java.io.IOException e) {
            plugin.getLogger().warning("giveaway-returns.yml: " + e.getMessage());
            return;
        }
        plugin.tasks().later(40, "giveaway return", () -> {
            for (String b64 : list) {
                org.bukkit.inventory.ItemStack item = org.bukkit.inventory.ItemStack.deserializeBytes(java.util.Base64.getDecoder().decode(b64));
                giveItem(p.getUniqueId(), item);
            }
            plugin.messages().send(p, "giveaway.item-returned");
        });
    }

    private void refundCoins(UUID owner, long amount) {
        // works online and offline, without multipliers
        plugin.economy().credit(owner, online.mythoscraft.core.economy.Currency.COINS, amount, null, "giveaway refund");
        Player p = plugin.getServer().getPlayer(owner);
        if (p != null) {
            plugin.messages().send(p, "giveaway.coins-returned", "amount", online.mythoscraft.core.util.Numbers.format(amount));
        }
    }

    public void start(Reward reward, int minutes, int winnerCount) {
        prize = reward;
        prizeItem = null;
        host = null;
        hostCoins = 0;
        winners = Math.max(1, winnerCount);
        endsAt = System.currentTimeMillis() + minutes * 60_000L;
        entries.clear();
        plugin.messages().broadcast("giveaway.started", "prize", reward.describe(plugin, 1.0), "minutes", minutes, "winners", winners);
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            SoundSpec.of("block.note_block.bell", 1f, 1.2f).play(p);
        }
    }

    private void tick() {
        if (!enabled) {
            return;
        }
        if (running()) {
            long left = (endsAt - System.currentTimeMillis()) / 1000;
            if (left == 60 || left == 30 || left == 10) {
                plugin.messages().broadcast("giveaway.reminder", "prize", prizeName(), "seconds", left, "entries", entries.size());
            }
            if (left <= 0) {
                draw();
            }
            return;
        }
        ZonedDateTime now = ZonedDateTime.now(plugin.multipliers().zone());
        for (Planned p : planned) {
            String key = now.toLocalDate() + "@" + p.time();
            if (p.days().contains(now.getDayOfWeek()) && now.getHour() == p.time().getHour() && now.getMinute() == p.time().getMinute()
                    && !key.equals(lastPlannedKey) && plugin.getServer().getOnlinePlayers().size() >= minPlayers) {
                lastPlannedKey = key;
                start(Reward.parse(p.reward()), p.minutes(), p.winners());
                return;
            }
        }
    }

    private void draw() {
        Reward r = prize;
        net.kyori.adventure.text.Component name = prizeName();
        org.bukkit.inventory.ItemStack item = prizeItem;
        UUID owner = host;
        long ownCoins = hostCoins;
        prize = null;
        prizeItem = null;
        host = null;
        hostCoins = 0;
        List<Player> pool = new ArrayList<>();
        for (UUID id : entries) {
            Player p = plugin.getServer().getPlayer(id);
            if (p != null) {
                pool.add(p);
            }
        }
        entries.clear();
        if (pool.isEmpty()) {
            plugin.messages().broadcast("giveaway.nobody", "prize", name);
            if (item != null && owner != null) {
                giveItem(owner, item);
            }
            if (ownCoins > 0 && owner != null) {
                refundCoins(owner, ownCoins);
            }
            return;
        }
        java.util.Collections.shuffle(pool, ThreadLocalRandom.current());
        for (int i = 0; i < Math.min(winners, pool.size()); i++) {
            Player w = pool.get(i);
            if (item != null) {
                giveItem(w.getUniqueId(), item);
            } else {
                r.give(plugin, w, 1.0);
            }
            plugin.messages().broadcast("giveaway.winner", "player", w.getName(), "prize", name, "entries", pool.size());
            try {
                online.mythoscraft.core.util.Effects.burst(w.getLocation().add(0, 2, 0), org.bukkit.Color.fromRGB(0xfde047),
                        org.bukkit.Color.fromRGB(0xf472b6), true);
            } catch (RuntimeException ignored) {
                // fireworks are only decoration
            }
            SoundSpec.of("ui.toast.challenge_complete", 1f, 1.1f).play(w);
        }
    }

    /** /verlosung – join; /verlosung start &lt;minutes&gt; [winners] &lt;reward …&gt;; /verlosung stop. */
    @Override
    protected void execute(CommandSender sender, String label, String[] args) throws CommandFail {
        String a = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        if (a.equals("item")) {
            Player p = player(sender);
            if (!p.hasPermission("mythos.giveaway.item") && !p.hasPermission("mythos.giveaway")) {
                throw new CommandFail("giveaway.item-no-permission");
            }
            int minutes = 5;
            if (args.length > 1) {
                try {
                    minutes = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    throw new CommandFail("general.not-a-number", "value", args[1]);
                }
            }
            startItem(p, Math.max(1, Math.min(itemMaxMinutes, minutes)));
            return;
        }
        if (a.equals("coins")) {
            Player p = player(sender);
            if (!p.hasPermission("mythos.giveaway.item") && !p.hasPermission("mythos.giveaway")) {
                throw new CommandFail("giveaway.item-no-permission");
            }
            if (args.length < 2) {
                throw CommandFail.usage("/verlosung coins <Betrag> [Minuten]");
            }
            long amount;
            int minutes = 5;
            try {
                amount = Long.parseLong(args[1].replace(".", "").replace("k", "000"));
                if (args.length > 2) {
                    minutes = Integer.parseInt(args[2]);
                }
            } catch (NumberFormatException e) {
                throw new CommandFail("general.not-a-number", "value", args.length > 2 ? args[2] : args[1]);
            }
            if (amount < 100) {
                throw new CommandFail("giveaway.coins-min");
            }
            startCoins(p, amount, Math.max(1, Math.min(itemMaxMinutes, minutes)));
            return;
        }
        if (a.equals("start")) {
            permission(sender, "mythos.giveaway");
            if (args.length < 3) {
                throw CommandFail.usage("/verlosung start <Minuten> [Gewinner] <Belohnung, z.B. voucher VIP 7d>");
            }
            int minutes;
            try {
                minutes = Math.max(1, Integer.parseInt(args[1]));
            } catch (NumberFormatException e) {
                throw new CommandFail("general.not-a-number", "value", args[1]);
            }
            int from = 2;
            int count = 1;
            if (args.length > 3 && args[2].matches("\\d+")) {
                count = Integer.parseInt(args[2]);
                from = 3;
            }
            Reward r;
            try {
                r = Reward.parse(String.join(" ", java.util.Arrays.copyOfRange(args, from, args.length)));
            } catch (IllegalArgumentException e) {
                throw new CommandFail("giveaway.bad-reward", "error", e.getMessage());
            }
            if (running()) {
                throw new CommandFail("giveaway.already");
            }
            start(r, minutes, count);
            return;
        }
        if (a.equals("stop")) {
            permission(sender, "mythos.giveaway");
            if (running()) {
                draw();
            }
            return;
        }
        Player p = player(sender);
        if (!running()) {
            plugin.messages().send(p, "giveaway.none");
            return;
        }
        if (p.getUniqueId().equals(host)) {
            plugin.messages().send(p, "giveaway.own");
            return;
        }
        if (!entries.add(p.getUniqueId())) {
            plugin.messages().send(p, "giveaway.already-in", "entries", entries.size());
            return;
        }
        plugin.messages().send(p, "giveaway.joined", "prize", prizeName(), "entries", entries.size(),
                "seconds", Math.max(0, (endsAt - System.currentTimeMillis()) / 1000));
        SoundSpec.of("entity.experience_orb.pickup", 1f, 1.4f).play(p);
    }

    @Override
    protected List<String> complete(CommandSender sender, String label, String[] args) {
        if (args.length == 1) {
            return sender.hasPermission("mythos.giveaway") ? List.of("start", "stop", "item", "coins")
                    : sender.hasPermission("mythos.giveaway.item") ? List.of("item", "coins") : List.of();
        }
        return List.of();
    }
}
