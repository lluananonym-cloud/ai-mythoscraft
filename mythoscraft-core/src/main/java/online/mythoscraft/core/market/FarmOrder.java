package online.mythoscraft.core.market;

import org.bukkit.Material;

import java.util.UUID;

/**
 * A farm order: a player pays coins in advance (escrow) for {@code amount} items of one material, other players
 * deliver them and are paid per item, the owner collects the delivered items. Only touched on the main thread.
 */
public final class FarmOrder {

    public enum State { OPEN, COMPLETED, CANCELLED, EXPIRED }

    private final String id;
    private final UUID owner;
    private final String ownerName;
    private final Material material;
    private final int amount;
    private final long priceEach;
    private final long created;
    private final long expires;
    private int delivered;
    private int collected;
    private State state;

    public FarmOrder(String id, UUID owner, String ownerName, Material material, int amount, int delivered, int collected,
                     long priceEach, long created, long expires, State state) {
        this.id = id;
        this.owner = owner;
        this.ownerName = ownerName;
        this.material = material;
        this.amount = amount;
        this.delivered = delivered;
        this.collected = collected;
        this.priceEach = priceEach;
        this.created = created;
        this.expires = expires;
        this.state = state;
    }

    public String id() {
        return id;
    }

    public UUID owner() {
        return owner;
    }

    public String ownerName() {
        return ownerName;
    }

    public Material material() {
        return material;
    }

    public int amount() {
        return amount;
    }

    public int delivered() {
        return delivered;
    }

    public int collected() {
        return collected;
    }

    public long priceEach() {
        return priceEach;
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

    public int remaining() {
        return amount - delivered;
    }

    public int collectable() {
        return delivered - collected;
    }

    /** Coins still held in escrow for undelivered items. */
    public long escrow() {
        return state == State.OPEN ? remaining() * priceEach : 0;
    }

    /** Nothing left to do: closed and every delivered item collected. */
    public boolean done() {
        return state != State.OPEN && collectable() <= 0;
    }

    void addDelivered(int n) {
        delivered += n;
        if (delivered >= amount) {
            state = State.COMPLETED;
        }
    }

    void addCollected(int n) {
        collected += n;
    }

    void close(State newState) {
        state = newState;
    }
}
