package play.xponer.astronima.wire;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import org.jspecify.annotations.Nullable;
import play.xponer.astronima.atmosphere.Atmosphere;
import play.xponer.astronima.power.PowerConnectable;
import play.xponer.astronima.sim.circuit.Conductor;
import play.xponer.astronima.sim.circuit.ConductorMaterial;
import play.xponer.astronima.sim.circuit.WireGauge;
import play.xponer.astronima.sim.circuit.Delivery;
import play.xponer.astronima.sim.wire.WirePixel;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Power, down a trace the player routed themselves.
 *
 * <p>This is where the tier stops being scenery. A run laid from an array to a machine now
 * <em>carries</em> — and what arrives depends on every decision made while laying it: which
 * metal, how long a way round, how many corners taken to keep it tidy. The numbers on the coil's
 * panel were always real; this is what makes them matter.
 *
 * <h2>How a wire meets a machine</h2>
 * <strong>A trace is connected to whatever it is fastened to.</strong> No connector part, no
 * facing to get right, no terminal to commission: run the wire onto the block and it is wired to
 * it, because a conductor bolted to a terminal <em>is</em> the connection. So the rule is one
 * line — a pixel's support block, if it is {@link PowerConnectable}, is on this circuit.
 *
 * <p>That also means a run passing <em>along</em> a machine's face powers it, which is correct
 * and occasionally surprising: wiring laid across the front of a crusher is wired to the crusher.
 * Real installations have exactly this property and real electricians route around it.
 *
 * <h2>What a run costs</h2>
 * Every pixel of conductor is {@code ρL/A} over a sixteenth of a metre, using the
 * <strong>worst</strong> metal anywhere on the run, because a chain is its weakest link and one
 * spliced pixel of iron makes an iron run. What those pixels add up to is <strong>solved</strong>
 * rather than counted — see {@link Run#resistanceOhms()}, which is the difference between a mesh
 * and a length of string. The loss is {@code I²R} at the bus voltage and it is
 * <strong>deposited along the wire</strong>, room by room, rather than at either end: a lossy run
 * warms the corridor it passes through, which in a game about fighting cold is a placement
 * decision rather than a tax.
 */
public final class WirePower {

    /** How far a power walk follows a run before it gives up and says so. */
    public static final int LIMIT = 8_192;

    /**
     * A resolved circuit: the conductor, and everything electrical hanging off it.
     *
     * @param terminals every {@link PowerConnectable} the run is fastened to, the source
     *                  included — callers filter to what they care about
     * @param truncated the walk stopped early, so this is a floor and not a total
     */
    /** One trace's address inside a run: a cell and the face the conductor is pressed against. */
    public record Face(BlockPos cell, Direction face) { }

    /**
     * @param load how much of the run's current each trace carries, 0..1 — because on a mesh they
     *             do not all carry the same thing, and marking them all with the total was telling
     *             a player that their second wire had not helped
     * @param heat what fraction of the resistive loss each cell makes, which is I²R and therefore
     *             emphatically not "the same everywhere the wire goes"
     */
    public record Run(List<WirePixel> pixels, Set<BlockPos> terminals,
                      ConductorMaterial metal, WireGauge gauge, DyeColor colour,
                      boolean truncated, double ohms,
                      java.util.Map<Face, Double> load, java.util.Map<BlockPos, Double> heat) {

        /** Sixteen pixels to the metre, because a block is a metre everywhere in this mod. */
        public double metres() {
            return pixels.size() / (double) play.xponer.astronima.sim.wire.FaceBasis.GRID;
        }

        /**
         * What stands between the two things this run joins, in ohms.
         *
         * <p><strong>Solved, not counted</strong> — {@code design/electrical.md} §5.1. This used to
         * be the pixel count times ρ/A, every pixel in the network treated as one series chain, and
         * that got two ordinary things exactly backwards. A <em>dead-end spur</em> carries no
         * current and was charged for anyway. A <em>doubled-up run</em> is two conductors in
         * parallel and came out at twice the resistance instead of half — so the one obvious fix
         * for a run that is overheating made it worse.
         *
         * <p>Computed once, in {@link #resolve}, because the mesh is already walked there and
         * solving it twice for one circuit would be two answers waiting to disagree.
         */
        public double resistanceOhms() {
            return ohms;
        }

        /** What this run may carry where it lies, before its insulation is at its limit. */
        public double ratingAmps(double pressureKPa, double ambientK) {
            return play.xponer.astronima.sim.circuit.Cooling
                    .of(gauge.metre(metal), 0, pressureKPa, ambientK).ratingAmps();
        }

        public boolean isEmpty() {
            return pixels.isEmpty();
        }
    }

    /**
     * The circuit fastened to this block, or null when nothing is wired to it.
     *
     * <p>Starts from the block rather than from a wire, because that is who asks: an array with
     * power to give and a machine with power to take both want the same question answered.
     */
    public static @Nullable Run runFrom(ServerLevel level, BlockPos block) {
        // Only from this block's own POWER terminals, and only where a run actually *ends* on
        // one: a trace that merely passes across a machine is passing across it, not wired to it.
        //
        // This comment was here before the code was. Under "any trace occupying the pixel", a
        // control line laid along the strip crossed the power stud on its way past, and this
        // method returned that circuit - which has no cells on it - so a correctly wired machine
        // with a signal wire near it silently got no power at all. Same bug as the enable's, on
        // the other half of the strip (PLAN rule 43).
        if (!(level.getBlockState(block).getBlock() instanceof Terminated terminated)) {
            return null;
        }
        for (Terminal terminal : terminated.terminals(level.getBlockState(block))) {
            if (terminal.kind() != Terminal.Kind.POWER) {
                continue;
            }
            WirePixel at = terminal.pixel(block);
            for (WireTrace trace : Wires.bundleOn(level, terminal.cell(block),
                    terminal.wireFace())) {
                if (trace.endsAt(at.u(), at.v())) {
                    return resolve(level, at, trace.colour());
                }
            }
        }
        return null;
    }

    /** Walks one circuit and collects everything on it. */
    public static Run resolve(ServerLevel level, WirePixel start, DyeColor colour) {
        Wires.Network network = Wires.network(level, start, colour, LIMIT);
        // Grouped by support block first, for the reason WireSignal.driven records: a long run
        // along one wall hangs on one block, and asking it once per pixel is a blockstate lookup
        // and a fresh terminal list for every sixteenth of a metre.
        java.util.Map<BlockPos, java.util.List<WirePixel>> bySupport = new java.util.HashMap<>();
        for (WirePixel point : network.pixels()) {
            bySupport.computeIfAbsent(
                    Wires.cellOf(point).relative(Faces.of(point.face())),
                    key -> new java.util.ArrayList<>()).add(point);
        }

        Set<BlockPos> terminals = new LinkedHashSet<>();
        for (var entry : bySupport.entrySet()) {
            BlockPos support = entry.getKey();
            var state = level.getBlockState(support);
            if (!(state.getBlock() instanceof Terminated terminated)) {
                continue;
            }
            // Landed on a POWER terminal, not merely resting against the block. This is the
            // difference between a wired machine and a wire that happens to run past one.
            outer:
            for (Terminal candidate : terminated.terminals(state)) {
                if (candidate.kind() != Terminal.Kind.POWER) {
                    continue;
                }
                for (WirePixel point : entry.getValue()) {
                    if (candidate.isAt(support, point)) {
                        terminals.add(support);
                        break outer;
                    }
                }
            }
        }
        ConductorMaterial metal = network.weakest().orElse(ConductorMaterial.IRON);
        Solved solved = solve(network, metal, terminalPixels(network, bySupport, terminals));
        return new Run(network.pixels(), terminals, metal, network.thinnest(), colour,
                network.truncated(), solved.ohms(), solved.load(), solved.heat());
    }

    /**
     * One pixel of each terminal, so the mesh can be measured between the things it joins.
     *
     * <p>The lowest-indexed pixel touching that block, which is a property of the run rather than
     * of the order a map happened to iterate in (rule 19). Which pixel of a terminal is chosen
     * barely matters electrically — they are a pixel apart — but it must be the <em>same</em> one
     * every time, or a run's resistance changes when nothing about the run did.
     */
    private static List<Integer> terminalPixels(Wires.Network network,
            java.util.Map<BlockPos, java.util.List<WirePixel>> bySupport, Set<BlockPos> terminals) {
        java.util.Map<WirePixel, Integer> index = new java.util.HashMap<>();
        for (int i = 0; i < network.pixels().size(); i++) {
            index.put(network.pixels().get(i), i);
        }
        List<Integer> found = new java.util.ArrayList<>();
        for (BlockPos terminal : terminals) {
            int lowest = Integer.MAX_VALUE;
            for (WirePixel point : bySupport.getOrDefault(terminal, List.of())) {
                Integer at = index.get(point);
                if (at != null) {
                    lowest = Math.min(lowest, at);
                }
            }
            if (lowest != Integer.MAX_VALUE) {
                found.add(lowest);
            }
        }
        java.util.Collections.sort(found);
        return found;
    }

    /**
     * What the mesh works out: the resistance, and who carries what.
     *
     * <p>One solve for both, because they are one circuit. Two would be two answers waiting to
     * disagree the first time either was tuned (rule 20).
     */
    private record Solved(double ohms, java.util.Map<Face, Double> load,
                          java.util.Map<BlockPos, Double> heat) { }

    /**
     * The conductor between the two things this run joins, solved as a mesh.
     *
     * <p><strong>The worst pair, when there are more than two.</strong> One scalar cannot describe
     * a circuit with a source and four machines on it — the real answer is a current per branch,
     * and {@code design/electrical.md} §5.1 says so. What one scalar <em>can</em> honestly be is the
     * longest electrical path between two things on this run: conservative, well defined, and it
     * moves the right way for every change a player makes — a parallel path lowers it, a dead end
     * does not touch it, a longer route raises it.
     *
     * <p>With fewer than two terminals the run joins nothing and there is no "between" to measure,
     * so it reports its conductor end to end, which is what the goggles showed before any of this.
     */
    private static Solved solve(Wires.Network network, ConductorMaterial metal,
                                List<Integer> terminals) {
        double perPixel = network.thinnest()
                .over(metal, 1.0 / play.xponer.astronima.sim.wire.FaceBasis.GRID)
                .resistance(ConductorMaterial.REFERENCE_K);
        if (terminals.size() < 2) {
            // Joined to nothing: there is no "between" to measure and no current to divide, so it
            // reports its conductor end to end and treats every trace alike — which is what the
            // goggles showed before any of this, for a run that is not carrying anything.
            return new Solved(network.thinnest().over(metal, network.metres())
                    .resistance(ConductorMaterial.REFERENCE_K),
                    java.util.Map.of(), java.util.Map.of());
        }
        var mesh = new play.xponer.astronima.sim.circuit.ResistorNetwork();
        for (int[] link : network.links()) {
            if (link.length > 2) {
                mesh.bond(link[0], link[1]);
            } else {
                mesh.link(link[0], link[1], perPixel);
            }
        }
        double worst = 0;
        int fromAt = terminals.get(0);
        int toAt = terminals.get(1);
        for (int i = 0; i < terminals.size(); i++) {
            for (int j = i + 1; j < terminals.size(); j++) {
                double between = mesh.resistanceBetween(terminals.get(i), terminals.get(j));
                if (Double.isFinite(between) && between > worst) {
                    worst = between;
                    fromAt = terminals.get(i);
                    toAt = terminals.get(j);
                }
            }
        }
        double[] share = mesh.shareOfCurrent(fromAt, toAt);
        return new Solved(worst, loadPerTrace(network, share), heatPerCell(network, share));
    }

    /**
     * How much of the run's current each trace is carrying.
     *
     * <p>The <strong>most</strong> of any link touching that trace, because a trace is a whole
     * cell's worth of conductor and what decides whether it survives is its busiest pixel. Taking
     * an average would let a hot spot hide behind the quiet end of the same metre.
     */
    private static java.util.Map<Face, Double> loadPerTrace(Wires.Network network, double[] share) {
        java.util.Map<Face, Double> load = new java.util.HashMap<>();
        List<int[]> links = network.links();
        for (int i = 0; i < links.size() && i < share.length; i++) {
            for (int end = 0; end < 2; end++) {
                WirePixel point = network.pixels().get(links.get(i)[end]);
                load.merge(new Face(Wires.cellOf(point), Faces.of(point.face())), share[i],
                        Math::max);
            }
        }
        return java.util.Map.copyOf(load);
    }

    /**
     * What fraction of the loss each cell makes.
     *
     * <p><strong>I²R, so a busy branch makes four times what a branch at half the current does</strong>
     * — and the loss used to be divided equally between every cell the wire passed through, dead-end
     * spurs included. A parallel pair warmed two corridors as hard as one wire warmed its own, which
     * is twice the heat physics allows and in the wrong rooms.
     *
     * <p>Every pixel of a run is the same length of the same conductor, so R is a constant and
     * drops out of the ratio: the weight is the square of the current, counted per link.
     */
    private static java.util.Map<BlockPos, Double> heatPerCell(Wires.Network network,
                                                               double[] share) {
        java.util.Map<BlockPos, Double> weight = new java.util.HashMap<>();
        List<int[]> links = network.links();
        double total = 0;
        for (int i = 0; i < links.size() && i < share.length; i++) {
            double made = share[i] * share[i];
            if (made <= 0) {
                continue;
            }
            total += made;
            for (int end = 0; end < 2; end++) {
                WirePixel point = network.pixels().get(links.get(i)[end]);
                weight.merge(Wires.cellOf(point), made / 2.0, Double::sum);
            }
        }
        if (total <= 0) {
            return java.util.Map.of();
        }
        java.util.Map<BlockPos, Double> fraction = new java.util.HashMap<>();
        for (var entry : weight.entrySet()) {
            fraction.put(entry.getKey(), entry.getValue() / total);
        }
        return java.util.Map.copyOf(fraction);
    }

    /**
     * Sends energy down a run and puts the loss where the wire is.
     *
     * @param seconds the interval this energy was made over — {@code I²R} is a rate, so a
     *                generator ticking slowly and a machine ticking fast must say which they mean
     * @return what actually arrives at the far end, in joules
     */
    public static double send(ServerLevel level, Run run, double joules, double seconds) {
        Delivery.Split split = Delivery.send(joules, seconds, run.resistanceOhms(),
                Delivery.BUS_VOLTS);
        shedAlong(level, run, split.heatJ());
        // The wire's own temperature is settled once a tick, for the whole run, after everything
        // using it has said so — see WireLoad. Heating it here instead meant a trunk feeding six
        // machines saw six separate five-amp draws and never thirty, which is the one number the
        // tier's central decision is about (design/electrical.md §4.2b). It also ran the wire's
        // thermal clock once per consumer, so a busy run aged six times too fast.
        WireLoad.register(level, run, joules / seconds);
        return split.deliveredJ();
    }

    /**
     * Spreads resistive loss over the cells the trace actually passes through.
     *
     * <p>Not at either end. The array is usually outdoors and the machine is usually in the
     * workshop, so depositing the loss at either would put the warmth somewhere the player did
     * not lay it — and <em>where the loss lands</em> is the decision that makes a cable a mechanic
     * instead of a tax.
     */
    public static void shedAlong(ServerLevel level, Run run, double joules) {
        if (joules <= 0 || run.pixels().isEmpty()) {
            return;
        }
        // Per cell rather than per pixel: sixteen pixels of one metre of trace are all in the
        // same room, and asking the atmosphere sixteen times would be the same answer sixteen
        // times over.
        List<BlockPos> cells = new ArrayList<>();
        Set<BlockPos> seen = new LinkedHashSet<>();
        for (WirePixel point : run.pixels()) {
            BlockPos cell = Wires.cellOf(point);
            if (seen.add(cell)) {
                cells.add(cell);
            }
        }
        // Weighted by what each cell actually made, which is I²R — a spur to nowhere warms
        // nothing, and the busier half of a parallel pair warms four times what a half-loaded one
        // does. Evenly, only when the run is joined to nothing and there is no current to divide.
        Atmosphere atmosphere = Atmosphere.get(level);
        java.util.Map<BlockPos, Double> made = run.heat();
        if (made.isEmpty()) {
            double each = joules / cells.size();
            for (BlockPos cell : cells) {
                atmosphere.addHeatJoules(cell, each);
            }
            return;
        }
        for (BlockPos cell : cells) {
            double fraction = made.getOrDefault(cell, 0.0);
            if (fraction > 0) {
                atmosphere.addHeatJoules(cell, joules * fraction);
            }
        }
    }

    private WirePower() {}
}
