package online.mythoscraft.core.market;

import org.bukkit.Material;

import java.util.Set;

/** Pure calculations of the auction house and farm orders (unit tested, no Bukkit server needed). */
public final class MarketMath {

    private MarketMath() {
    }

    /** Sales tax taken from the seller, rounded down. */
    public static long tax(long price, double percent) {
        if (price <= 0 || percent <= 0) {
            return 0;
        }
        return (long) Math.floor(price * Math.min(100, percent) / 100.0);
    }

    public static long payout(long price, double percent) {
        return price - tax(price, percent);
    }

    /** amount × price per item; -1 on overflow. */
    public static long total(long amount, long priceEach) {
        try {
            return Math.multiplyExact(amount, priceEach);
        } catch (ArithmeticException e) {
            return -1;
        }
    }

    /** How many items are actually delivered: limited by what the player has and what the order still needs. */
    public static int deliverable(int has, int amount, int delivered) {
        return Math.max(0, Math.min(has, amount - delivered));
    }

    /** Text progress bar like ■■■■□□□□□□. */
    public static String bar(long done, long total, int width) {
        int filled = total <= 0 ? 0 : (int) Math.min(width, Math.round(width * (double) done / total));
        return "■".repeat(filled) + "□".repeat(width - filled);
    }

    /** Materials that can never be ordered or sold (admin blocks, spawn eggs …) besides the configured list. */
    public static boolean alwaysBlocked(Material material) {
        return material == null || material.isAir() || !material.isItem() || material.name().endsWith("_SPAWN_EGG")
                || ALWAYS.contains(material.name());
    }

    private static final Set<String> ALWAYS = Set.of("BEDROCK", "BARRIER", "COMMAND_BLOCK", "CHAIN_COMMAND_BLOCK",
            "REPEATING_COMMAND_BLOCK", "COMMAND_BLOCK_MINECART", "STRUCTURE_BLOCK", "STRUCTURE_VOID", "JIGSAW", "LIGHT",
            "DEBUG_STICK", "KNOWLEDGE_BOOK", "SPAWNER", "TRIAL_SPAWNER", "VAULT", "END_PORTAL_FRAME", "REINFORCED_DEEPSLATE",
            "PETRIFIED_OAK_SLAB", "TEST_BLOCK", "TEST_INSTANCE_BLOCK");
}
