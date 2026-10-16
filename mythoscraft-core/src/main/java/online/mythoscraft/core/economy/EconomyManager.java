package online.mythoscraft.core.economy;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.player.PlayerData;
import online.mythoscraft.core.util.Numbers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Coins and gems. All balance changes happen on the main thread against the player cache, which makes each
 * operation atomic; transfers and admin changes are additionally persisted at once in a single database
 * transaction together with an audit row in mc_transactions.
 */
public final class EconomyManager {

    public enum Result { SUCCESS, INSUFFICIENT_FUNDS, INVALID_AMOUNT, LIMIT_REACHED, DISABLED }

    public enum AdminOp { GIVE, TAKE, SET }

    private final MythosCraft plugin;
    private long maxBalance = 1_000_000_000_000L;
    private boolean coinsEnabled = true;
    private boolean gemsEnabled = true;
    private boolean gemsTransferable;
    private long maxPay = 1_000_000_000L;
    private int payCooldownSeconds = 3;
    private long startCoins;

    public EconomyManager(MythosCraft plugin) {
        this.plugin = plugin;
    }

    public void reload(ConfigReader root) {
        ConfigReader cfg = root.sub("economy");
        maxBalance = cfg.longValue("max-balance", 1_000_000_000_000L, 1_000, Long.MAX_VALUE / 4);
        coinsEnabled = cfg.bool("coins.enabled", true);
        gemsEnabled = cfg.bool("gems.enabled", true);
        gemsTransferable = cfg.bool("gems.transferable", false);
        maxPay = cfg.longValue("pay.max-amount", 1_000_000_000L, 1, maxBalance);
        payCooldownSeconds = cfg.integer("pay.cooldown-seconds", 3, 0, 3600);
        startCoins = cfg.longValue("coins.start-balance", 100, 0, maxBalance);
    }

    public boolean enabled(Currency currency) {
        return currency == Currency.COINS ? coinsEnabled : gemsEnabled;
    }

    public boolean transferable(Currency currency) {
        return enabled(currency) && (currency == Currency.COINS || gemsTransferable);
    }

    public long maxBalance() {
        return maxBalance;
    }

    public long maxPay() {
        return maxPay;
    }

    public int payCooldownSeconds() {
        return payCooldownSeconds;
    }

    public long startCoins() {
        return startCoins;
    }

    public long balance(PlayerData data, Currency currency) {
        return currency.get(data);
    }

    public boolean has(PlayerData data, Currency currency, long amount) {
        return currency.get(data) >= amount;
    }

    public Result deposit(PlayerData data, Currency currency, long amount) {
        if (!enabled(currency)) {
            return Result.DISABLED;
        }
        if (amount <= 0) {
            return Result.INVALID_AMOUNT;
        }
        long current = currency.get(data);
        if (current > maxBalance - amount) {
            return Result.LIMIT_REACHED;
        }
        currency.set(data, current + amount);
        plugin.display().markDirty(data.uuid());
        return Result.SUCCESS;
    }

    /** Credits a reward, applying daily, booster and rank multipliers. Returns the credited amount (clamped). */
    public long earn(PlayerData data, Currency currency, long base, boolean applyMultipliers) {
        if (!enabled(currency) || base <= 0 || data == null) {
            return 0;
        }
        double factor = applyMultipliers ? plugin.multipliers().forPlayer(currency.multiplier(), data) : 1.0;
        long amount = Math.max(1, Math.round(base * factor));
        long current = currency.get(data);
        long target = Numbers.clampedAdd(current, amount, maxBalance);
        currency.set(data, target);
        plugin.display().markDirty(data.uuid());
        return target - current;
    }

    public Result withdraw(PlayerData data, Currency currency, long amount) {
        if (!enabled(currency)) {
            return Result.DISABLED;
        }
        if (amount <= 0) {
            return Result.INVALID_AMOUNT;
        }
        long current = currency.get(data);
        if (current < amount) {
            return Result.INSUFFICIENT_FUNDS;
        }
        currency.set(data, current - amount);
        plugin.display().markDirty(data.uuid());
        return Result.SUCCESS;
    }

    /** Atomic transfer between two cached players, persisted immediately in one transaction. */
    public Result transfer(PlayerData from, PlayerData to, Currency currency, long amount, String reason) {
        if (!enabled(currency)) {
            return Result.DISABLED;
        }
        if (amount <= 0 || amount > maxPay || from.uuid().equals(to.uuid())) {
            return Result.INVALID_AMOUNT;
        }
        long fromBalance = currency.get(from);
        long toBalance = currency.get(to);
        if (fromBalance < amount) {
            return Result.INSUFFICIENT_FUNDS;
        }
        if (toBalance > maxBalance - amount) {
            return Result.LIMIT_REACHED;
        }
        currency.set(from, fromBalance - amount);
        currency.set(to, toBalance + amount);
        plugin.display().markDirty(from.uuid());
        plugin.display().markDirty(to.uuid());
        long now = System.currentTimeMillis();
        plugin.playerData().saveTogether("transfer " + amount + " " + currency.id() + " " + from.name() + " -> " + to.name(),
                List.of(from, to), c -> insertLog(c, now, from.uuid(), to.uuid(), currency, amount, reason));
        return Result.SUCCESS;
    }

    /**
     * Admin change that works for online and offline players. Completes with false if the player is unknown.
     * The resulting balance is reported through {@code newBalance[0]}.
     */
    public CompletableFuture<Boolean> adminChange(UUID target, Currency currency, AdminOp op, long amount, UUID actor,
                                                  long[] newBalance) {
        long now = System.currentTimeMillis();
        return plugin.playerData().modify(target, data -> {
            long current = currency.get(data);
            long value = switch (op) {
                case GIVE -> Numbers.clampedAdd(current, amount, maxBalance);
                case TAKE -> Math.max(0, current - amount);
                case SET -> Math.max(0, Math.min(maxBalance, amount));
            };
            currency.set(data, value);
            newBalance[0] = value;
        }).thenApply(found -> {
            if (found) {
                plugin.tasks().sync(() -> {
                    plugin.display().markDirty(target);
                    PlayerData cached = plugin.playerData().get(target);
                    if (cached != null) {
                        plugin.playerData().save(cached);
                    }
                });
                long delta = op == AdminOp.TAKE ? -amount : amount;
                log(actor, target, currency, delta, "admin-" + op.name().toLowerCase());
            }
            return found;
        });
    }

    /**
     * Pays money to a player who may be offline (auction earnings, refunds). No multipliers, clamped to the
     * maximum balance, persisted at once and logged.
     */
    public void credit(UUID target, Currency currency, long amount, UUID sender, String reason) {
        if (amount <= 0) {
            return;
        }
        PlayerData online = plugin.playerData().get(target);
        if (online != null) {
            currency.set(online, Numbers.clampedAdd(currency.get(online), amount, maxBalance));
            plugin.display().markDirty(target);
            plugin.playerData().save(online);
        } else {
            plugin.playerData().modify(target, data -> currency.set(data, Numbers.clampedAdd(currency.get(data), amount, maxBalance)))
                    .exceptionally(e -> {
                        plugin.getLogger().severe("Could not credit " + amount + " " + currency.id() + " to " + target + " (" + reason + "): "
                                + e.getMessage());
                        return false;
                    });
        }
        log(sender, target, currency, amount, reason);
    }

    public void log(UUID sender, UUID receiver, Currency currency, long amount, String reason) {
        long now = System.currentTimeMillis();
        plugin.database().query("log transaction", c -> {
            insertLog(c, now, sender, receiver, currency, amount, reason);
            return null;
        });
    }

    private static void insertLog(Connection c, long time, UUID sender, UUID receiver, Currency currency, long amount,
                                  String reason) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO mc_transactions (created, sender, receiver, currency, amount, reason) VALUES (?, ?, ?, ?, ?, ?)")) {
            ps.setLong(1, time);
            if (sender == null) {
                ps.setNull(2, Types.CHAR);
            } else {
                ps.setString(2, sender.toString());
            }
            if (receiver == null) {
                ps.setNull(3, Types.CHAR);
            } else {
                ps.setString(3, receiver.toString());
            }
            ps.setString(4, currency.id());
            ps.setLong(5, amount);
            ps.setString(6, reason.length() > 64 ? reason.substring(0, 64) : reason);
            ps.executeUpdate();
        }
    }
}
