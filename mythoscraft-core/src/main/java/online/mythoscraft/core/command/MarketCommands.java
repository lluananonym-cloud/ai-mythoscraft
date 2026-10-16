package online.mythoscraft.core.command;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.economy.Currency;
import online.mythoscraft.core.market.AuctionManager;
import online.mythoscraft.core.market.AuctionMenu;
import online.mythoscraft.core.market.OrderMenu;
import online.mythoscraft.core.shop.ShopMenu;
import online.mythoscraft.core.util.Numbers;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;

/**
 * /shop, /ah (auction house) and /auftrag (farm orders).
 * <pre>
 * /ah                          browse         /auftrag                                 all open orders
 * /ah sell &lt;Preis&gt; [coins|gems] sell the stack in hand  /auftrag erstellen &lt;Item|hand&gt; &lt;Menge&gt; &lt;Preis/Stück&gt;
 * /ah meine | abholen | suche &lt;Text&gt;   /auftrag meine
 * </pre>
 */
public final class MarketCommands extends BaseCommand {

    public MarketCommands(MythosCraft plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandSender sender, String rawLabel, String[] args) throws CommandFail {
        Player player = player(sender);
        String label = rawLabel.substring(rawLabel.indexOf(':') + 1);
        switch (label) {
            case "shop", "gemshop", "laden" -> {
                permission(sender, "mythos.shop");
                if (!plugin.shop().enabled()) {
                    throw new CommandFail("shop.disabled");
                }
                if (plugin.auctions().blockedHere(player)) {
                    throw new CommandFail("shop.blocked-here");
                }
                new ShopMenu(plugin, player).open(player);
            }
            case "auftrag", "auftraege", "farmauftrag", "orders" -> orders(player, args);
            default -> auction(player, args);
        }
    }

    private void auction(Player player, String[] args) throws CommandFail {
        permission(player, "mythos.auction");
        AuctionManager auctions = plugin.auctions();
        if (!auctions.ready()) {
            throw new CommandFail(auctionsEnabled() ? "auction.loading" : "auction.disabled");
        }
        if (auctions.blockedHere(player)) {
            throw new CommandFail("auction.blocked-here");
        }
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "" -> open(player, AuctionMenu.Mode.BROWSE, null);
            case "sell", "verkaufen" -> {
                if (args.length < 2) {
                    throw CommandFail.usage("/ah sell <Preis> [coins|gems]");
                }
                OptionalLong price = Numbers.parseAmount(args[1], Long.MAX_VALUE / 4);
                if (price.isEmpty()) {
                    throw CommandFail.usage("/ah sell <Preis> [coins|gems]");
                }
                Currency currency = args.length > 2 ? Currency.byId(args[2]) : Currency.COINS;
                if (currency == null) {
                    throw CommandFail.usage("/ah sell <Preis> [coins|gems]");
                }
                auctions.sell(player, price.getAsLong(), currency);
            }
            case "meine", "my", "mine" -> open(player, AuctionMenu.Mode.MINE, null);
            case "abholen", "claim", "pickup" -> {
                int n = auctions.claimAll(player);
                if (n == 0 && auctions.claimable(player.getUniqueId()).isEmpty()) {
                    plugin.messages().send(player, "auction.nothing-to-claim");
                }
            }
            case "suche", "search" -> {
                if (args.length < 2) {
                    throw CommandFail.usage("/ah suche <Text>");
                }
                open(player, AuctionMenu.Mode.BROWSE, join(args, 1));
            }
            default -> plugin.messages().sendRaw(player, "auction.help");
        }
    }

    private boolean auctionsEnabled() {
        return plugin.getConfig().getBoolean("auction.enabled", true);
    }

    private void open(Player player, AuctionMenu.Mode mode, String filter) {
        new AuctionMenu(plugin, player, mode, 0, AuctionManager.Sort.NEWEST, filter).open(player);
    }

    private void orders(Player player, String[] args) throws CommandFail {
        permission(player, "mythos.orders");
        if (!plugin.orders().ready()) {
            throw new CommandFail(plugin.getConfig().getBoolean("farm-orders.enabled", true) ? "orders.loading" : "orders.disabled");
        }
        if (plugin.auctions().blockedHere(player)) {
            throw new CommandFail("orders.blocked-here");
        }
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "" -> new OrderMenu(plugin, player, OrderMenu.Mode.BROWSE, 0).open(player);
            case "meine", "my", "mine", "abholen" -> new OrderMenu(plugin, player, OrderMenu.Mode.MINE, 0).open(player);
            case "erstellen", "create", "neu" -> {
                if (args.length < 4) {
                    throw CommandFail.usage("/auftrag erstellen <Item|hand> <Menge> <Preis pro Stück>");
                }
                Material material;
                if (args[1].equalsIgnoreCase("hand")) {
                    material = player.getInventory().getItemInMainHand().getType();
                    if (material.isAir()) {
                        throw new CommandFail("orders.empty-hand");
                    }
                } else {
                    material = Material.matchMaterial(args[1]);
                    if (material == null || !material.isItem()) {
                        throw new CommandFail("orders.unknown-item", "item", args[1]);
                    }
                }
                OptionalLong amount = Numbers.parseAmount(args[2], plugin.orders().maxAmount());
                OptionalLong price = Numbers.parseAmount(args[3], 1_000_000_000L);
                if (amount.isEmpty() || price.isEmpty()) {
                    throw CommandFail.usage("/auftrag erstellen <Item|hand> <Menge> <Preis pro Stück>");
                }
                plugin.orders().create(player, material, (int) amount.getAsLong(), price.getAsLong());
            }
            default -> plugin.messages().sendRaw(player, "orders.help");
        }
    }

    @Override
    protected List<String> complete(CommandSender sender, String rawLabel, String[] args) {
        String label = rawLabel.substring(rawLabel.indexOf(':') + 1);
        if (label.equals("shop") || label.equals("gemshop") || label.equals("laden")) {
            return List.of();
        }
        if (label.startsWith("auftrag") || label.equals("farmauftrag") || label.equals("orders")) {
            if (args.length == 1) {
                return List.of("erstellen", "meine");
            }
            if (args[0].equalsIgnoreCase("erstellen") || args[0].equalsIgnoreCase("create")) {
                return switch (args.length) {
                    case 2 -> materials(args[1]);
                    case 3 -> List.of("64", "256", "1000");
                    case 4 -> List.of("1", "5", "10");
                    default -> List.of();
                };
            }
            return List.of();
        }
        if (args.length == 1) {
            return List.of("sell", "meine", "abholen", "suche");
        }
        if (args[0].equalsIgnoreCase("sell") || args[0].equalsIgnoreCase("verkaufen")) {
            return switch (args.length) {
                case 2 -> List.of("100", "1000", "10k");
                case 3 -> plugin.auctions().gemsAllowed() ? List.of("coins", "gems") : List.of("coins");
                default -> List.of();
            };
        }
        return List.of();
    }

    private List<String> materials(String prefix) {
        List<String> out = new ArrayList<>();
        out.add("hand");
        String p = prefix.toLowerCase(Locale.ROOT);
        if (p.length() < 2) {
            return out;
        }
        for (Material m : Material.values()) {
            if (!m.isLegacy() && m.isItem() && !m.isAir() && m.name().toLowerCase(Locale.ROOT).startsWith(p)) {
                out.add(m.name().toLowerCase(Locale.ROOT));
                if (out.size() > 60) {
                    break;
                }
            }
        }
        return out;
    }
}
