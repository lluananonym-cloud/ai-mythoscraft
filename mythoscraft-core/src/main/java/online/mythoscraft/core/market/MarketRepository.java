package online.mythoscraft.core.market;

import online.mythoscraft.core.database.SqlDialect;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/** SQL for the auction house and farm orders. Runs on the database thread; items travel as Paper byte arrays. */
public final class MarketRepository {

    /** Raw auction row – the item is deserialised on the main thread. */
    public record AuctionRow(String id, String seller, String sellerName, byte[] item, long price, String currency, long created,
                             long expires, String state, String buyer, long finished) {
    }

    public record OrderRow(String id, String owner, String ownerName, String material, int amount, int delivered, int collected,
                           long priceEach, long created, long expires, String state) {
    }

    private static final List<String> AUCTION_COLUMNS = List.of("id", "seller", "seller_name", "item", "price", "currency",
            "created", "expires", "state", "buyer", "finished");
    private static final List<String> ORDER_COLUMNS = List.of("id", "owner", "owner_name", "material", "amount", "delivered",
            "collected", "price_each", "created", "expires", "state");

    private final String auctionUpsert;
    private final String orderUpsert;

    public MarketRepository(SqlDialect dialect) {
        this.auctionUpsert = dialect.upsert("mc_auctions", List.of("id"), AUCTION_COLUMNS);
        this.orderUpsert = dialect.upsert("mc_orders", List.of("id"), ORDER_COLUMNS);
    }

    public List<AuctionRow> openAuctions(Connection c) throws SQLException {
        List<AuctionRow> rows = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT id, seller, seller_name, item, price, currency, created, expires, "
                + "state, buyer, finished FROM mc_auctions WHERE state IN ('ACTIVE', 'EXPIRED', 'CANCELLED') ORDER BY created");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rows.add(new AuctionRow(rs.getString(1), rs.getString(2), rs.getString(3), rs.getBytes(4), rs.getLong(5),
                        rs.getString(6), rs.getLong(7), rs.getLong(8), rs.getString(9), rs.getString(10), rs.getLong(11)));
            }
        }
        return rows;
    }

    public void saveAuction(Connection c, AuctionRow r) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(auctionUpsert)) {
            ps.setString(1, r.id());
            ps.setString(2, r.seller());
            ps.setString(3, r.sellerName());
            ps.setBytes(4, r.item());
            ps.setLong(5, r.price());
            ps.setString(6, r.currency());
            ps.setLong(7, r.created());
            ps.setLong(8, r.expires());
            ps.setString(9, r.state());
            if (r.buyer() == null) {
                ps.setNull(10, Types.CHAR);
            } else {
                ps.setString(10, r.buyer());
            }
            ps.setLong(11, r.finished());
            ps.executeUpdate();
        }
    }

    public List<OrderRow> openOrders(Connection c) throws SQLException {
        List<OrderRow> rows = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT id, owner, owner_name, material, amount, delivered, collected, "
                + "price_each, created, expires, state FROM mc_orders WHERE state = 'OPEN' OR collected < delivered ORDER BY created");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rows.add(new OrderRow(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getInt(5), rs.getInt(6),
                        rs.getInt(7), rs.getLong(8), rs.getLong(9), rs.getLong(10), rs.getString(11)));
            }
        }
        return rows;
    }

    public void saveOrder(Connection c, OrderRow r) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(orderUpsert)) {
            ps.setString(1, r.id());
            ps.setString(2, r.owner());
            ps.setString(3, r.ownerName());
            ps.setString(4, r.material());
            ps.setInt(5, r.amount());
            ps.setInt(6, r.delivered());
            ps.setInt(7, r.collected());
            ps.setLong(8, r.priceEach());
            ps.setLong(9, r.created());
            ps.setLong(10, r.expires());
            ps.setString(11, r.state());
            ps.executeUpdate();
        }
    }
}
