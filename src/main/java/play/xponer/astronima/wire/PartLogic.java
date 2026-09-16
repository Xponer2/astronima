package play.xponer.astronima.wire;

import net.minecraft.server.level.ServerLevel;
import play.xponer.astronima.sim.logic.LogicSim;
import play.xponer.astronima.sim.logic.PartType;

import java.util.List;

/**
 * What a wire-layer part is putting out, right now.
 *
 * <p>The seam between the world and {@code sim/logic}: {@link LogicSim}, truth tables and the
 * plate's netlist are MC-free and exhaustively tested, and this is the only thing that knows how
 * to hand them what is actually on the pads.
 *
 * <h2>Read live, not from the stored bits</h2>
 * A part carries its outputs as a field so the <em>client</em> can draw it (see {@link WirePart}),
 * and it would be tempting to answer from that field here too. It would also be wrong: the field
 * is refreshed on a timer, so a chain of gates answering from it would propagate one stage per
 * refresh — a visible lag whose length depends on which order the parts happened to be placed in.
 * The gate blocks this replaces had exactly this split for exactly this reason, and it is worth
 * keeping.
 *
 * <p>The cost is bounded by {@link WireSignal#MAX_DEPTH}: gates read gates, so a ring of them is
 * mutual recursion, and past sixteen stages the honest answer is <em>"the signal has not arrived
 * yet"</em>, which is also what a real chain does.
 */
public final class PartLogic {

    /**
     * Whether that driving pad is closed — read from the part's <em>cached output</em>, not by
     * recursively re-evaluating it.
     *
     * <p><strong>Why cached, not live.</strong> {@link WireSignal} calls this while walking the
     * circuit to discover what is driving a pad, and that walk already sits inside a depth guard
     * ({@link WireSignal#MAX_DEPTH}) to prevent a ring of gates from recursing forever. If this
     * method called {@link #evaluate} again, a feedback loop — two NOR gates cross-wired as an RS
     * latch — would hit the limit and return {@code false} on both sides rather than reaching a
     * stable state. Reading the last-written {@link WirePart#outputs()} avoids the recursion
     * entirely: one gate reads what its neighbour reported at the previous refresh, which is
     * exactly what a clocked latch does.
     *
     * <p>The cost is real and worth stating: a straight chain of gates now propagates one stage
     * per refresh tick (every {@link WireTicker#REFRESH_TICKS} game ticks, so about 250 ms per
     * stage) rather than settling in one pass. A player building a long combinational chain will
     * see it "fill in" stage by stage — realistic for clocked logic, and the documented behaviour
     * for circuits with real propagation delay. For any circuit with a loop, correct behaviour
     * is not possible without it.
     */
    public static boolean isDriving(ServerLevel level, WirePart part, PartType.Pad pad) {
        return part.driving(pad);
    }

    /**
     * Everything the part is putting out, as one word — the same value that is stored for drawing.
     *
     * <p>One method for both, so what the renderer shows and what the wire carries cannot come
     * apart. Two computations of "is this gate on" would be two chances to disagree, and the
     * symptom would be a gate that is drawn lit and drives nothing, which is the single most
     * confusing thing a logic system can do.
     */
    public static int evaluate(ServerLevel level, WirePart part) {
        return part.type().evaluate(readInputs(level, part), part.held(), part.circuit(), part.memory());
    }

    /**
     * What is on each reading pad.
     *
     * <p>Pads with nothing landed on them are skipped without walking a circuit. A plate has four
     * input pads and is very often wired to one; walking three circuits that do not exist, on
     * every plate, five times a second, is the cost the machine strip already had to have removed.
     */
    public static boolean[] readInputs(ServerLevel level, WirePart part) {
        List<PartType.Pad> reading = part.type().inputs();
        boolean[] values = new boolean[reading.size()];
        for (int i = 0; i < values.length; i++) {
            PartType.Pad pad = reading.get(i);
            values[i] = WireSignal.wiredAtPad(level, part, pad)
                    && WireSignal.liveAtPad(level, part, pad);
        }
        return values;
    }

    private PartLogic() {}
}
