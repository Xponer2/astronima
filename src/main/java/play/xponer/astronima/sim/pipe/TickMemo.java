package play.xponer.astronima.sim.pipe;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Shares one computed answer across every caller that asks for the same key in the same
 * tick, and forgets it the moment the tick moves on.
 *
 * <p>Exists for exactly one problem: a run of pipe with several gas ports on it has every
 * one of those ports ask, on the same interval, "what does this run look like" — because
 * every port ticks itself, and there is no elected owner for the run they share until
 * they have each walked it far enough to compare notes. Without sharing the answer, a run
 * with P ports pays the full walk of the run P times per interval merely to agree on
 * which one leads, even though the walk is the same walk every time. {@code PipeNetworks}
 * is the one real consumer (see its own doc, and {@code GasPortBlockEntity}'s).
 *
 * <p><strong>Not the persistent, event-invalidated network cache a fuller fix would be.</strong>
 * That would hold a network's topology until a real change to it — a pipe placed or
 * broken, a valve toggled, a pump added — and only re-walk then. This holds an answer for
 * exactly one tick and always re-walks on the next one, so nothing has to invalidate it
 * when the world changes: a stale entry cannot outlive the tick it was computed in. It
 * only removes the multiplier a shared run pays on top of the walk it already has to do.
 *
 * @param <K> what distinguishes one answer from another (a starting position, in the real
 *            use — kept generic here so this stays provably free of Minecraft, per rule 1,
 *            and testable without booting the game)
 * @param <V> the answer itself
 */
public final class TickMemo<K, V> {
    private long tick = Long.MIN_VALUE;
    private final Map<K, V> byKey = new HashMap<>();

    /**
     * The answer for {@code key} at {@code currentTick}. Computed by {@code compute} at
     * most once per (tick, key) no matter how many callers ask — including when the
     * computed answer is itself {@code null}, so a key that resolves to "nothing here" is
     * not re-walked by every caller who also finds nothing.
     */
    public V get(long currentTick, K key, Function<K, V> compute) {
        freshen(currentTick);
        if (byKey.containsKey(key)) {
            return byKey.get(key);
        }
        V value = compute.apply(key);
        byKey.put(key, value);
        return value;
    }

    /**
     * Answers {@code key} for the rest of {@code currentTick} without computing it — for
     * a caller who already paid for the answer under a different key and knows this key
     * shares it (every other port the one real walk actually found on the same run).
     */
    public void supply(long currentTick, K key, V value) {
        freshen(currentTick);
        byKey.put(key, value);
    }

    private void freshen(long currentTick) {
        if (currentTick != tick) {
            tick = currentTick;
            byKey.clear();
        }
    }
}
