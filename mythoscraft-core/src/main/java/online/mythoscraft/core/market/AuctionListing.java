package online.mythoscraft.core.market;

import online.mythoscraft.core.economy.Currency;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/** One offer in the auction house. Only touched on the main thread. */
public final class AuctionListing {

    /** ACTIVE = buyable; EXPIRED/CANCELLED = item waits for the seller (/ah abholen); SOLD/RETURNED = done. */
    public enum State { ACTIVE, EXPIRED, CANCELLED, SOLD, RETURNED }

    private final String id;
    private final UUID seller;
    private final String sellerName;
    private final ItemStack item;
    private final long price;
    private final Currency currency;
    private final long created;
    private final long expires;
    private State state;
    private UUID buyer;
    private long finished;

    public AuctionListing(String id, UUID seller, String sellerName, ItemStack item, long price, Currency currency, long created,
                          long expires, State state, UUID buyer, long finished) {
        this.id = id;
        this.seller = seller;
        this.sellerName = sellerName;
        this.item = item;
        this.price = price;
        this.currency = currency;
        this.created = created;
        this.expires = expires;
        this.state = state;
        this.buyer = buyer;
        this.finished = finished;
    }

    public String id() {
        return id;
    }

    public UUID seller() {
        return seller;
    }

    public String sellerName() {
        return sellerName;
    }

    /** The stored item (mutable: a partially claimed stack shrinks). */
    public ItemStack item() {
        return item;
    }

    public long price() {
        return price;
    }

    public Currency currency() {
        return currency;
    }

    public long created() {
        return created;
    }

    public long expires() {
        return expires;
    }

    public State state() {
        return state;
    }

    public UUID buyer() {
        return buyer;
    }

    public long finished() {
        return finished;
    }

    public boolean claimable() {
        return state == State.EXPIRED || state == State.CANCELLED;
    }

    void finish(State newState, UUID newBuyer, long time) {
        this.state = newState;
        this.buyer = newBuyer;
        this.finished = time;
    }
}
