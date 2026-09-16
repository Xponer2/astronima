package play.xponer.astronima.wire;

import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import play.xponer.astronima.sim.circuit.Delivery;
import play.xponer.astronima.sim.wire.WirePixel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What each run is <strong>actually</strong> carrying this tick — all of it, added up.
 *
 * <h2>The trunk could not see its own branches</h2>
 * {@code design/electrical.md} §4.2b describes the mistake this tier is supposed to teach:
 * <em>"the trunk now carries both branch currents, so an undersized trunk overheats while both
 * branches look fine. That is a real mistake with a real symptom."</em> It was not true. Every
 * machine asked its run for power on its own, so a run feeding six of them saw six separate
 * five-amp draws and never once saw thirty.
 *
 * <p>That is not a rounding error. Loss goes as the <strong>square</strong> of the current, so six
 * machines waste thirty-six times what one does — six times more than six separate calculations
 * report. The trunk was the one place the tier's central decision lived, and it was invisible.
 *
 * <h2>And the wire's clock ran fast</h2>
 * A second fault fell out of the same shape: every consumer stepped the run's temperature
 * separately, so a trace on a busy run advanced its own thermal clock once per consumer per tick.
 * Six machines meant six times the elapsed time. Heating now happens <strong>once per tick</strong>,
 * after everything has registered, which is both correct and the only way a total can exist at all.
 *
 * <h2>No identity, by construction</h2>
 * A run has no id and must not have one (rule 16 — the {@code RoomState} lesson). So the ledger is
 * keyed by something <em>derived</em>: the lowest pixel of the network in a fixed order, plus the
 * colour. Two lookups of the same circuit produce the same key because they produce the same walk,
 * and a circuit cut in two produces two keys the next tick without anything having to notice.
 */
public final class WireLoad {

    /** One circuit this tick, named by its own lowest pixel rather than by an id it does not have. */
    private record Key(WirePixel canonical, DyeColor colour) { }

    private static final Map<ResourceKey<Level>, Map<Key, Double>> WATTS = new HashMap<>();

    /**
     * A total order on pixels, so the same circuit always names itself the same way.
     *
     * <p>Never iteration order over a set (rule 19): the key has to be a property of the circuit,
     * not of the order a walk happened to visit it in.
     */
    private static final Comparator<WirePixel> LOWEST = Comparator
            .comparingInt(WirePixel::x).thenComparingInt(WirePixel::y)
            .thenComparingInt(WirePixel::z)
            .thenComparingInt(pixel -> pixel.face().ordinal())
            .thenComparingInt(WirePixel::u).thenComparingInt(WirePixel::v);

    /**
     * Notes that something is pushing or pulling this many watts through this run.
     *
     * <p>Watts rather than joules, because the callers tick at different rates — a machine every
     * twentieth of a second, a generator every twentieth of a <em>second</em> times the atmosphere
     * interval — and a sum of joules over mismatched intervals is not a current.
     */
    public static void register(ServerLevel level, WirePower.Run run, double watts) {
        if (run.isEmpty() || watts <= 0) {
            return;
        }
        run.pixels().stream().min(LOWEST).ifPresent(canonical ->
                WATTS.computeIfAbsent(level.dimension(), key -> new HashMap<>())
                        .merge(new Key(canonical, run.colour()), watts, Double::sum));
    }

    /** One surface of one circuit, and how many amps went through it. */
    public record Loaded(java.util.Map<Surface, Double> amps) {

        public double at(WireTrace trace) {
            return amps.getOrDefault(new Surface(trace.cell(), trace.face(), trace.colour()), 0.0);
        }
    }

    /** A trace's address: exactly what a trace is. */
    public record Surface(net.minecraft.core.BlockPos cell, Direction face, DyeColor colour) { }

    /**
     * Closes the books for this tick: works out what every run carried, and lets the fuses go.
     *
     * <p>Returns the current each trace saw, so the caller can step every one of them <em>once</em>
     * with the right number — which is the whole point, and the thing that could not be done while
     * each consumer heated the wire on its own behalf.
     *
     * @param seconds how much game time this settlement covers, so a test can drive a minute
     *                without waiting one
     */
    public static Loaded settle(ServerLevel level, double seconds) {
        Map<Key, Double> booked = WATTS.remove(level.dimension());
        Map<Surface, Double> amps = new HashMap<>();
        if (booked == null || booked.isEmpty()) {
            return new Loaded(amps);
        }
        List<Map.Entry<Key, Double>> entries = new ArrayList<>(booked.entrySet());
        // Sorted, so two circuits that both want to blow a shared fuse resolve the same way for
        // everybody rather than by hash order (rule 19).
        entries.sort(Comparator.comparing(entry -> entry.getKey().canonical(), LOWEST));

        for (var entry : entries) {
            Key key = entry.getKey();
            WirePower.Run run = WirePower.resolve(level, key.canonical(), key.colour());
            if (run.isEmpty()) {
                continue;   // cut, burned or unloaded since it was booked
            }
            double carried = Delivery.amps(entry.getValue(), Delivery.BUS_VOLTS);
            if (WireHeat.protect(level, run, carried)) {
                continue;   // the fuse interrupted it; the wire is not also cooked by it
            }
            // Each trace gets what IT carries, not what the run carries. On a mesh those are
            // different numbers, and marking every trace with the total meant both halves of a
            // doubled-up run read as fully loaded — four times the heat each, and an instrument
            // telling the player their second wire had not helped.
            for (WirePixel pixel : run.pixels()) {
                var face = new WirePower.Face(Wires.cellOf(pixel), Faces.of(pixel.face()));
                double share = run.load().isEmpty() ? 1.0 : run.load().getOrDefault(face, 0.0);
                amps.merge(new Surface(face.cell(), face.face(), run.colour()),
                        carried * share, Math::max);
            }
        }
        return new Loaded(amps);
    }

    /** A world going away takes its books with it. */
    public static void forget(ServerLevel level) {
        WATTS.remove(level.dimension());
    }

    private WireLoad() {}
}
