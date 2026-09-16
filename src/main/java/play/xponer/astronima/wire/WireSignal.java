package play.xponer.astronima.wire;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import play.xponer.astronima.sim.logic.PartType;
import play.xponer.astronima.sim.wire.WirePixel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Whether a control line is live, asked one terminal at a time.
 *
 * <p>This is what turns wire from plumbing for watts into something worth <strong>building
 * with</strong>. Powering a machine is one decision made once; a line that carries a
 * <em>condition</em> — somebody is in the airlock, this switch is closed, both of those at once —
 * is a thing the player composes.
 *
 * <h2>One rule, and it is the physical one</h2>
 * <strong>A line is live when any output driving it is closed.</strong> That is what parallel
 * contacts do, so two sensors on one wire are an OR with no part needed. What is <em>not</em>
 * free is everything else — an AND, an inversion, a latch — and those are components, because in
 * a real circuit they are components too.
 *
 * <p><strong>Asked per terminal, not per block.</strong> A gate has two inputs and an output and
 * has to tell them apart; a block-level "is anything near me live" cannot express that, and a
 * system that cannot express it has no logic in it — only wires that happen to reach things.
 *
 * <h2>Two kinds of thing drive a line, and both must be asked</h2>
 * A <strong>block</strong> — a sensor, a mat, a machine's status stud — through
 * {@link SignalSource}, which is implemented by the block and not by its block entity (the first
 * power draft asked the block entity, found nothing, and did nothing at all with no error
 * anywhere). And a <strong>part</strong> in the wire layer itself — a gate, a switch, a plate —
 * through {@link PartLogic}. A walk that asked only one of them would be a circuit where half the
 * components are invisible, and the symptom would be a gate that is visibly wired and does
 * nothing.
 */
public final class WireSignal {

    /** How far a signal walk follows a line before giving up. */
    public static final int LIMIT = 8_192;

    /**
     * How many gates deep one question may go.
     *
     * <p><strong>Gates read gates.</strong> Asking a gate for its output makes it read its own
     * inputs, which may be driven by other gates — so a ring of them is mutual recursion, and a
     * player who wires a loop would otherwise crash the server rather than build an oscillator.
     * Stopping at a depth and answering "not driven" is what a real gate chain does anyway: past
     * some number of stages the signal has not arrived yet.
     */
    public static final int MAX_DEPTH = 16;

    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    /**
     * Whatever is asking, so a source can never hear itself.
     *
     * <p>Without it the first thing that happens is a gate reading its own output and latching on
     * forever. One type for both kinds of asker because both walk the same circuits: a block
     * excludes its own studs, a part excludes its own pads.
     */
    public record Asker(@Nullable BlockPos block, @Nullable WirePart part) {

        public static Asker block(BlockPos block) {
            return new Asker(block, null);
        }

        public static Asker part(WirePart part) {
            return new Asker(null, part);
        }

        /** Nobody in particular — a meter reading a line it is not part of. */
        public static Asker nobody() {
            return new Asker(null, null);
        }

        boolean isSelf(BlockPos support) {
            return block != null && block.equals(support);
        }

        boolean isSelf(WirePart other) {
            return part != null && part.sameSlotAs(other);
        }
    }

    /**
     * True when something is driving the line landed on this terminal.
     *
     * <p>The asking block's own outputs are ignored, so a gate cannot hear itself and latch on
     * forever — which is the first thing that happens without it.
     */
    public static boolean liveAt(ServerLevel level, BlockPos block, Terminal terminal) {
        WirePixel at = terminal.pixel(block);
        return guarded(() -> liveAtPixel(level, at, Asker.block(block), true));
    }

    /**
     * The same question for a part's own pad.
     *
     * <p>A pad is a pixel on the face the part is lying on, so a trace reaches it by ordinary
     * pixel adjacency — which is why this needed no new connection rule, only a second thing to
     * ask about the pixels a run passes through.
     */
    public static boolean liveAtPad(ServerLevel level, WirePart part, PartType.Pad pad) {
        WirePixel at = part.padPixel(pad);
        return guarded(() -> liveAtPixel(level, at, Asker.part(part), false));
    }

    /** The depth cap, applied once, around whichever question is being asked. */
    private static boolean guarded(BooleanSupplier question) {
        int depth = DEPTH.get();
        if (depth >= MAX_DEPTH) {
            return false;
        }
        DEPTH.set(depth + 1);
        try {
            return question.getAsBoolean();
        } finally {
            DEPTH.set(depth);
        }
    }

    /**
     * Whether anything is driving any circuit that reaches this exact pixel.
     *
     * @param landed whether the trace must <em>end</em> here rather than pass over — true for a
     *               block's terminal, which is a stud in a row of studs, and false for a part's
     *               pad, which a run may legitimately continue across
     */
    private static boolean liveAtPixel(ServerLevel level, WirePixel at, Asker asker,
                                       boolean landed) {
        BlockPos cell = Wires.cellOf(at);
        for (WireTrace trace : Wires.bundleOn(level, cell, Faces.of(at.face()))) {
            if (landed ? !trace.endsAt(at.u(), at.v()) : !trace.has(at.u(), at.v())) {
                continue;
            }
            if (driven(level, at, trace, asker)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether any signal input of this block is live — for machines with a single enable.
     *
     * <p>Only walks circuits for inputs that actually have wire on them. A machine has four
     * input studs and almost always one wire; checking all four would walk three circuits that
     * do not exist, every tick, on every machine — and the cheap check that skips them is the
     * same bundle lookup {@link #hasSignalWiring} already does.
     */
    public static boolean anyInputLive(ServerLevel level, BlockPos block) {
        return anyInputLive(level, block, null);
    }

    /**
     * Whether any signal input <em>of this name</em> is live.
     *
     * <p>Asking about every input at once was sound while a machine had exactly one kind of them.
     * It stops being sound the moment there are two: a machine's enable and its servo are both
     * listeners, and an unscoped question would let a wire landed on the servo stud switch on a
     * machine whose enable line is dead — the machine ignoring its own controls, with nothing
     * anywhere reporting a fault (design/calibration.md §6.4).
     *
     * @param label the terminal's published name, or null for any input at all
     */
    public static boolean anyInputLive(ServerLevel level, BlockPos block,
                                       @org.jspecify.annotations.Nullable String label) {
        BlockState state = level.getBlockState(block);
        if (!(state.getBlock() instanceof Terminated terminated)) {
            return false;
        }
        for (Terminal terminal : terminated.terminals(state)) {
            if (terminal.kind() == Terminal.Kind.SIGNAL_IN
                    && (label == null || label.equals(terminal.label()))
                    && wiredAt(level, block, terminal)
                    && liveAt(level, block, terminal)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether anything is landed on that terminal at all — no circuit walk.
     *
     * <p>Landed, not passing: see {@link WireTrace#endsAt}. A machine reading a run that crosses
     * its strip as a wire on every stud it crosses is how the enable came to be satisfied by the
     * machine's own power cable.
     */
    public static boolean wiredAt(ServerLevel level, BlockPos block, Terminal terminal) {
        WirePixel at = terminal.pixel(block);
        for (WireTrace trace : Wires.bundleOn(level, terminal.cell(block), terminal.wireFace())) {
            if (trace.endsAt(at.u(), at.v())) {
                return true;
            }
        }
        return false;
    }

    /** Whether anything is landed on that pad at all — no circuit walk. */
    public static boolean wiredAtPad(ServerLevel level, WirePart part, PartType.Pad pad) {
        int u = part.padU(pad);
        int v = part.padV(pad);
        for (WireTrace trace : Wires.bundleOn(level, part.cell(), part.face())) {
            if (trace.has(u, v)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a {@code PartType.CLOCK} is landed anywhere on the network reaching this pad.
     *
     * <p>{@code design/computer.md} Phase 2's fast path: a plate wired to a real clock chip does
     * not wait for that chip's own slow, human-visible blink (see {@code WireTicker.clocked}) — it
     * detects the wiring here and synthesizes its own fast internal toggle on that one pin instead,
     * running many settle passes in the time the real clock part would have shown one edge. Landed,
     * not <em>currently high</em> — the clock's own phase is irrelevant to this question, only
     * whether one is present at all.
     */
    public static boolean clockDrivesPad(ServerLevel level, WirePart part, PartType.Pad pad) {
        WirePixel at = part.padPixel(pad);
        BlockPos cell = Wires.cellOf(at);
        for (WireTrace trace : Wires.bundleOn(level, cell, Faces.of(at.face()))) {
            if (trace.has(at.u(), at.v()) && clockOnNetwork(level, at, trace, Asker.part(part))) {
                return true;
            }
        }
        return false;
    }

    private static boolean clockOnNetwork(ServerLevel level, WirePixel start, WireTrace trace,
                                          Asker asker) {
        Wires.Network network = Wires.network(level, start, trace.colour(), LIMIT);
        Map<Surface, List<WirePixel>> bySurface = new HashMap<>();
        for (WirePixel pixel : network.pixels()) {
            BlockPos cell = Wires.cellOf(pixel);
            bySurface.computeIfAbsent(new Surface(cell, Faces.of(pixel.face())),
                    key -> new ArrayList<>()).add(pixel);
        }
        for (var entry : bySurface.entrySet()) {
            for (WirePart candidate : Wires.partsOn(level, entry.getKey().cell(),
                    entry.getKey().face())) {
                if (candidate.type() != PartType.CLOCK || asker.isSelf(candidate)) {
                    continue;
                }
                for (WirePixel pixel : entry.getValue()) {
                    PartType.Pad candidatePad = candidate.padAt(pixel.u(), pixel.v());
                    if (candidatePad != null && candidatePad.drives()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** True when this block has a wire landed on any signal input at all. */
    public static boolean hasSignalWiring(ServerLevel level, BlockPos block) {
        return hasSignalWiring(level, block, null);
    }

    /**
     * True when this block has a wire landed on a signal input of this name.
     *
     * <p>The companion to the scoped {@link #anyInputLive}: a machine decides whether it has been
     * taken charge of by looking for wire on <em>the stud that does that job</em>. Wiring a servo
     * must not make a machine believe somebody has wired its enable.
     */
    public static boolean hasSignalWiring(ServerLevel level, BlockPos block,
                                          @org.jspecify.annotations.Nullable String label) {
        BlockState state = level.getBlockState(block);
        if (!(state.getBlock() instanceof Terminated terminated)) {
            return false;
        }
        for (Terminal terminal : terminated.terminals(state)) {
            if (terminal.kind() == Terminal.Kind.SIGNAL_IN
                    && (label == null || label.equals(terminal.label()))
                    && wiredAt(level, block, terminal)) {
                return true;
            }
        }
        return false;
    }

    /** Walks one circuit and asks every output landed on it whether it is closed. */
    private static boolean driven(ServerLevel level, WirePixel start, WireTrace trace,
                                  Asker asker) {
        Wires.Network network = Wires.network(level, start, trace.colour(), LIMIT);

        // Group the run's pixels by the block they hang on before touching the world.
        //
        // A three-hundred-pixel run along one wall hangs on ONE block, and the first version
        // asked that block three hundred times — a blockstate lookup and a freshly allocated
        // terminal list apiece. Times two inputs per gate, times every gate, five times a
        // second. Grouping first turns the whole thing into one lookup.
        Map<BlockPos, List<WirePixel>> bySupport = new HashMap<>();
        // And by the surface they lie on, which is how a part is found: a part's pads are pixels
        // of the very face the run is on, not of a neighbouring block.
        Map<Surface, List<WirePixel>> bySurface = new HashMap<>();
        for (WirePixel pixel : network.pixels()) {
            BlockPos cell = Wires.cellOf(pixel);
            bySurface.computeIfAbsent(new Surface(cell, Faces.of(pixel.face())),
                    key -> new ArrayList<>()).add(pixel);
            BlockPos support = cell.relative(Faces.of(pixel.face()));
            if (asker.isSelf(support)) {
                continue;
            }
            bySupport.computeIfAbsent(support, key -> new ArrayList<>()).add(pixel);
        }

        return drivenByPart(level, bySurface, asker) || drivenByBlock(level, bySupport);
    }

    /** One surface, so a face's parts are looked up once however much wire crosses it. */
    private record Surface(BlockPos cell, net.minecraft.core.Direction face) { }

    private static boolean drivenByPart(ServerLevel level, Map<Surface, List<WirePixel>> bySurface,
                                        Asker asker) {
        for (var entry : bySurface.entrySet()) {
            List<WirePart> parts = Wires.partsOn(level, entry.getKey().cell(),
                    entry.getKey().face());
            if (parts.isEmpty()) {
                continue;
            }
            for (WirePart part : parts) {
                if (asker.isSelf(part)) {
                    continue;
                }
                for (WirePixel pixel : entry.getValue()) {
                    PartType.Pad pad = part.padAt(pixel.u(), pixel.v());
                    if (pad != null && pad.drives() && PartLogic.isDriving(level, part, pad)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean drivenByBlock(ServerLevel level,
                                         Map<BlockPos, List<WirePixel>> bySupport) {
        for (var entry : bySupport.entrySet()) {
            BlockPos support = entry.getKey();
            BlockState state = level.getBlockState(support);
            if (!(state.getBlock() instanceof Terminated terminated)
                    || !(state.getBlock() instanceof SignalSource source)) {
                continue;
            }
            for (Terminal terminal : terminated.terminals(state)) {
                if (terminal.kind() != Terminal.Kind.SIGNAL_OUT) {
                    continue;
                }
                for (WirePixel pixel : entry.getValue()) {
                    if (terminal.isAt(support, pixel)
                            && source.isDriving(level, support, terminal)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private WireSignal() {}
}
