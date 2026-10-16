package online.mythoscraft.core.reward;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.Component;
import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.economy.Currency;
import online.mythoscraft.core.item.CustomItem;
import online.mythoscraft.core.item.CustomItemManager;
import online.mythoscraft.core.player.PlayerData;
import online.mythoscraft.core.rank.Rank;
import online.mythoscraft.core.util.Numbers;
import online.mythoscraft.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

import java.util.Locale;

/**
 * A reward written as one config line:
 * <pre>
 * coins 500 | gems 5 | xp 200 | key vip 1 | tool mythos_hammer | item DIAMOND 5 | voucher VIP 30d | book sharpness 5
 * command lp user {player} …
 * </pre>
 */
public record Reward(Type type, long amount, String id, String raw) {

    public enum Type { COINS, GEMS, XP, KEY, TOOL, ITEM, VOUCHER, BOOK, COMMAND, SPAWNER }

    public static Reward parse(String line) {
        String s = line.trim();
        String[] p = s.split("\\s+");
        if (p.length == 0 || p[0].isEmpty()) {
            throw new IllegalArgumentException("leere Belohnung");
        }
        String kind = p[0].toLowerCase(Locale.ROOT);
        switch (kind) {
            case "coins", "gems", "xp" -> {
                if (p.length < 2) {
                    throw new IllegalArgumentException("Menge fehlt: '" + s + "'");
                }
                long amount = parseAmount(p[1], s);
                Type t = kind.equals("coins") ? Type.COINS : kind.equals("gems") ? Type.GEMS : Type.XP;
                return new Reward(t, amount, null, s);
            }
            case "key" -> {
                if (p.length < 2) {
                    throw new IllegalArgumentException("Kisten-ID fehlt: '" + s + "'");
                }
                return new Reward(Type.KEY, p.length > 2 ? parseAmount(p[2], s) : 1, p[1].toLowerCase(Locale.ROOT), s);
            }
            case "tool" -> {
                if (p.length < 2 || CustomItem.byId(p[1]) == null || !CustomItem.byId(p[1]).giveable()) {
                    throw new IllegalArgumentException("unbekanntes Tool: '" + s + "'");
                }
                return new Reward(Type.TOOL, p.length > 2 ? parseAmount(p[2], s) : 1, p[1].toLowerCase(Locale.ROOT), s);
            }
            case "item" -> {
                Material m = p.length > 1 ? Material.matchMaterial(p[1]) : null;
                if (m == null || !m.isItem()) {
                    throw new IllegalArgumentException("unbekanntes Item: '" + s + "'");
                }
                return new Reward(Type.ITEM, p.length > 2 ? parseAmount(p[2], s) : 1, m.name(), s);
            }
            case "voucher" -> {
                if (p.length < 2) {
                    throw new IllegalArgumentException("Rang fehlt: '" + s + "'");
                }
                String duration = p.length > 2 ? p[2].toLowerCase(Locale.ROOT) : "perm";
                return new Reward(Type.VOUCHER, p.length > 3 ? parseAmount(p[3], s) : 1, p[1].toUpperCase(Locale.ROOT) + "|" + duration, s);
            }
            case "book" -> {
                if (p.length < 2 || !p[1].matches("[a-z0-9_]+")) {
                    throw new IllegalArgumentException("Verzauberung fehlt: '" + s + "'");
                }
                int level = p.length > 2 ? (int) parseAmount(p[2], s) : 1;
                return new Reward(Type.BOOK, p.length > 3 ? parseAmount(p[3], s) : 1, p[1].toLowerCase(Locale.ROOT) + "|" + level, s);
            }
            case "spawner" -> {
                if (p.length < 2 || online.mythoscraft.core.item.SpawnerItems.parse(p[1]) == null) {
                    throw new IllegalArgumentException("unbekannter Spawner-Mob: '" + s + "'");
                }
                return new Reward(Type.SPAWNER, p.length > 2 ? parseAmount(p[2], s) : 1, p[1].toUpperCase(Locale.ROOT), s);
            }
            case "command" -> {
                if (p.length < 2) {
                    throw new IllegalArgumentException("Befehl fehlt: '" + s + "'");
                }
                return new Reward(Type.COMMAND, 1, s.substring(s.indexOf(' ') + 1).trim(), s);
            }
            default -> throw new IllegalArgumentException("unbekannter Belohnungstyp '" + kind + "'");
        }
    }

    private static long parseAmount(String value, String line) {
        return Numbers.parseAmount(value, 1_000_000_000_000L)
                .orElseThrow(() -> new IllegalArgumentException("ungültige Menge in '" + line + "'"));
    }

    /**
     * Gives the reward. {@code multiplier} scales coins/gems/xp (e.g. the event multiplier). Returns the amount
     * that was actually credited (for messages).
     */
    public long give(MythosCraft plugin, Player player, double multiplier) {
        long scaled = Math.max(1, Math.round(amount * multiplier));
        PlayerData data = plugin.playerData().get(player);
        switch (type) {
            case COINS -> {
                return data == null ? 0 : plugin.economy().earn(data, Currency.COINS, scaled, false);
            }
            case GEMS -> {
                return data == null ? 0 : plugin.economy().earn(data, Currency.GEMS, scaled, false);
            }
            case XP -> {
                player.giveExp((int) Math.min(Integer.MAX_VALUE, scaled));
                return scaled;
            }
            case KEY -> {
                ItemStack key = plugin.crates().createKey(id, (int) Math.min(64, amount));
                if (key == null) {
                    plugin.getLogger().warning("Reward references unknown crate '" + id + "'");
                    return 0;
                }
                CustomItemManager.give(player, key);
                return amount;
            }
            case TOOL -> {
                CustomItem item = CustomItem.byId(id);
                for (int i = 0; i < amount && item != null; i++) {
                    CustomItemManager.give(player, plugin.items().create(item));
                }
                return amount;
            }
            case ITEM -> {
                Material m = Material.valueOf(id);
                long left = amount;
                while (left > 0) {
                    int n = (int) Math.min(left, m.getMaxStackSize());
                    CustomItemManager.give(player, new ItemStack(m, n));
                    left -= n;
                }
                return amount;
            }
            case VOUCHER -> {
                String[] v = id.split("\\|");
                ItemStack voucher = plugin.vouchers().create(v[0], v[1], (int) Math.min(16, amount));
                if (voucher == null) {
                    plugin.getLogger().warning("Reward references unknown or restricted rank '" + v[0] + "'");
                    return 0;
                }
                CustomItemManager.give(player, voucher);
                return amount;
            }
            case BOOK -> {
                ItemStack book = enchantedBook();
                if (book == null) {
                    plugin.getLogger().warning("Reward references unknown enchantment '" + id + "'");
                    return 0;
                }
                for (int i = 0; i < amount; i++) {
                    CustomItemManager.give(player, book.clone());
                }
                return amount;
            }
            case SPAWNER -> {
                CustomItemManager.give(player, online.mythoscraft.core.item.SpawnerItems.create(
                        online.mythoscraft.core.item.SpawnerItems.parse(id), (int) Math.min(64, amount)));
                return amount;
            }
            case COMMAND -> {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), id.replace("{player}", player.getName())
                        .replace("{uuid}", player.getUniqueId().toString()));
                return 1;
            }
            default -> {
                return 0;
            }
        }
    }

    /** Human readable description, e.g. "500 Coins" or "1× VIP-Schlüssel". */
    public Component describe(MythosCraft plugin, double multiplier) {
        long scaled = Math.max(1, Math.round(amount * multiplier));
        return switch (type) {
            case COINS -> plugin.messages().get("rewards.describe.coins", "amount", scaled);
            case GEMS -> plugin.messages().get("rewards.describe.gems", "amount", scaled);
            case XP -> plugin.messages().get("rewards.describe.xp", "amount", scaled);
            case KEY -> plugin.messages().get("rewards.describe.key", "amount", amount, "crate", plugin.crates().displayName(id));
            case TOOL -> plugin.messages().get("rewards.describe.tool", "amount", amount,
                    "item", plugin.messages().get("items." + id + ".name"));
            case ITEM -> plugin.messages().get("rewards.describe.item", "amount", amount,
                    "item", Component.translatable(Material.valueOf(id).translationKey()));
            case VOUCHER -> {
                String[] v = id.split("\\|");
                Rank rank = plugin.ranks().byId(v[0]);
                yield plugin.messages().get("rewards.describe.voucher", "amount", amount,
                        "rank", rank == null ? Component.text(v[0]) : Text.parse(rank.display()), "duration", plugin.vouchers().durationText(v[1]));
            }
            case BOOK -> {
                String[] b = id.split("\\|");
                Enchantment ench = enchantment(b[0]);
                yield plugin.messages().get("rewards.describe.book", "amount", amount,
                        "enchantment", ench == null ? Component.text(b[0]) : ench.displayName(Integer.parseInt(b[1])));
            }
            case COMMAND -> plugin.messages().get("rewards.describe.command");
            case SPAWNER -> plugin.messages().get("rewards.describe.spawner", "amount", amount,
                    "mob", Component.translatable(org.bukkit.entity.EntityType.valueOf(id).translationKey()));
        };
    }

    private static Enchantment enchantment(String key) {
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).get(NamespacedKey.minecraft(key));
    }

    /** The enchanted book of a BOOK reward (level capped at the enchantment's maximum + 5), or null. */
    public ItemStack enchantedBook() {
        String[] b = id.split("\\|");
        Enchantment ench = enchantment(b[0]);
        if (ench == null) {
            return null;
        }
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        int level = Math.max(1, Math.min(Integer.parseInt(b[1]), ench.getMaxLevel() + 5));
        book.editMeta(EnchantmentStorageMeta.class, meta -> meta.addStoredEnchant(ench, level, true));
        return book;
    }
}
