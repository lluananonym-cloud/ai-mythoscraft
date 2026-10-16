package online.mythoscraft.core.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.ToDoubleFunction;

/** Immutable weighted pick list. Entries with weight ≤ 0 are ignored. */
public final class WeightedRandom<T> {

    private final List<T> items;
    private final double[] cumulative;
    private final double total;

    public WeightedRandom(List<T> source, ToDoubleFunction<T> weight) {
        List<T> kept = new ArrayList<>();
        List<Double> sums = new ArrayList<>();
        double sum = 0;
        for (T t : source) {
            double w = weight.applyAsDouble(t);
            if (w > 0 && Double.isFinite(w)) {
                sum += w;
                kept.add(t);
                sums.add(sum);
            }
        }
        this.items = Collections.unmodifiableList(kept);
        this.cumulative = sums.stream().mapToDouble(Double::doubleValue).toArray();
        this.total = sum;
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public double total() {
        return total;
    }

    public List<T> items() {
        return items;
    }

    public T pick(Random random) {
        if (items.isEmpty()) {
            throw new IllegalStateException("No weighted entries");
        }
        double r = random.nextDouble() * total;
        int lo = 0;
        int hi = cumulative.length - 1;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (cumulative[mid] > r) {
                hi = mid;
            } else {
                lo = mid + 1;
            }
        }
        return items.get(lo);
    }

    /** Chance of an entry in percent (0–100). */
    public double chance(int index) {
        double prev = index == 0 ? 0 : cumulative[index - 1];
        return total <= 0 ? 0 : (cumulative[index] - prev) * 100.0 / total;
    }
}
