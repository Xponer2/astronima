package play.xponer.astronima.sim.circuit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A mesh of conductor, and how much of it stands between two points.
 *
 * <h2>The run was being counted, not solved</h2>
 * {@code design/electrical.md} §5.1 says the tier is <em>"solved, not simulated by handwave"</em>.
 * It was not. A run's resistance was its <strong>pixel count</strong> times ρ/A — every pixel in the
 * network treated as one series chain — and that gets two ordinary things exactly backwards:
 *
 * <ul>
 *   <li>a <strong>dead-end spur</strong> carries no current and must cost nothing, and it was
 *       charged for like conductor in the path;</li>
 *   <li>a <strong>doubled-up run</strong> is two conductors in parallel and must be <em>half</em>
 *       the resistance — counting made it <em>twice</em>, so the one obvious fix for a run that is
 *       running hot made it worse.</li>
 * </ul>
 *
 * <p>That second one is the whole reason this exists. A player who adds a second wire alongside the
 * first is doing the correct thing, and the game was punishing them for it.
 *
 * <h2>Reduce first, then solve (§5.3)</h2>
 * A forty-block run is six hundred and forty pixels, and six hundred and forty nodes is a matrix
 * nobody should build twenty times a second. So the graph is reduced first, and the reduction is
 * <strong>exact</strong> rather than an approximation:
 *
 * <ol>
 *   <li><strong>Prune</strong> every dead end — a node of degree one that is neither endpoint has
 *       nowhere for current to go, so it and its edge leave. Repeatedly, because pruning a leaf
 *       makes a new one.</li>
 *   <li><strong>Collapse</strong> series chains — a node of degree two that is neither endpoint is
 *       two resistors end to end, which is one resistor.</li>
 *   <li><strong>Merge</strong> parallel edges by conductance.</li>
 * </ol>
 *
 * <p>A plain run reduces to a single resistor and never reaches the solver at all. What survives is
 * a mesh, and a mesh gets <strong>Modified Nodal Analysis</strong>: ground one end, inject an amp at
 * the other, solve {@code G·v = i}, and the voltage that appears <em>is</em> the resistance. That is
 * what SPICE does and what a circuit is.
 *
 * <p>Series and parallel alone would not be enough, and that is not a theoretical worry: a
 * <strong>bridge</strong> — the shape you get the moment two runs are cross-connected in the middle
 * — is not series-parallel reducible at all. A reducer would either refuse it or quietly return
 * something wrong.
 *
 * <p>Minecraft-free (rule 1).
 */
public final class ResistorNetwork {

    /** An unreachable point is not zero ohms away and not an error — it is not connected. */
    public static final double DISCONNECTED = Double.POSITIVE_INFINITY;

    /** Conductances below this are a break, not a conductor: 10 GΩ. */
    private static final double NEGLIGIBLE = 1e-10;

    /** Every link in the order it was added, so a caller can map an answer back to its wire. */
    private final List<int[]> order = new ArrayList<>();
    private final List<Double> siemens = new ArrayList<>();

    /**
     * Joins two points with a resistor.
     *
     * <p>Two links between the same pair are two conductors in parallel, so they add by
     * conductance rather than replacing each other — which is what a caller laying a mesh out of
     * pixel adjacency will hand over without meaning to.
     */
    public void link(int a, int b, double ohms) {
        if (a == b || !(ohms > 0)) {
            return;      // a loop back to the same node carries nothing; nor does a short
        }
        double added = 1.0 / ohms;
        order.add(new int[] {a, b});
        siemens.add(added);
    }

    /**
     * How much of the current each link carries, as a fraction of what flows in at one point and
     * out at another.
     *
     * <h2>The other half of §5.1, and the half the instrument was lying about</h2>
     * Once a run's resistance halves for a doubled-up conductor, every trace on it was still being
     * marked as carrying the <strong>whole</strong> current — so both wires of a parallel pair read
     * as fully loaded, each was heated as if it were (I²R, so four times over), and the panel told
     * the player their fix had not worked.
     *
     * <p>Fractions rather than amps, because the answer is linear in the current and the current is
     * not known until the tick settles. Solve the shape once; multiply later.
     *
     * <p>Recovered from the <em>reduced</em> mesh and unwound, which is §5.3 step four. A dead end
     * carries nothing. Every link in a collapsed chain carries what the chain carries. Links merged
     * in parallel divide <strong>by conductance</strong> — which is the whole point, and the reason
     * an unequal pair does not share evenly.
     *
     * @return one fraction per link, in the order the links were added; all zero when the two
     *         points are not connected
     */
    public double[] shareOfCurrent(int from, int to) {
        double[] share = new double[order.size()];
        if (from == to || order.isEmpty()) {
            return share;
        }
        Map<Integer, Map<Integer, Working>> graph = working();
        reduce(graph, from, to);
        Map<Integer, Double> volts = potentials(graph, from, to);
        if (volts == null) {
            return share;
        }
        for (var entry : graph.entrySet()) {
            int a = entry.getKey();
            for (var link : entry.getValue().entrySet()) {
                int b = link.getKey();
                if (b < a) {
                    continue;                       // each link once
                }
                Working edge = link.getValue();
                double through = Math.abs(volts.getOrDefault(a, 0.0)
                        - volts.getOrDefault(b, 0.0)) * edge.conductance;
                edge.spread(through, share);
            }
        }
        return share;
    }

    /**
     * Node potentials with one amp entering and leaving, so a difference times a conductance is a
     * fraction of that amp.
     */
    private static Map<Integer, Double> potentials(Map<Integer, Map<Integer, Working>> graph,
                                                   int from, int to) {
        List<Integer> unknowns = new ArrayList<>(graph.keySet());
        unknowns.remove(Integer.valueOf(to));
        java.util.Collections.sort(unknowns);
        if (!graph.containsKey(from) || unknowns.isEmpty()) {
            return null;
        }
        Map<Integer, Integer> row = new HashMap<>();
        for (int i = 0; i < unknowns.size(); i++) {
            row.put(unknowns.get(i), i);
        }
        int n = unknowns.size();
        double[][] matrix = new double[n][n + 1];
        for (int i = 0; i < n; i++) {
            for (var link : graph.getOrDefault(unknowns.get(i), Map.<Integer, Working>of())
                    .entrySet()) {
                matrix[i][i] += link.getValue().conductance;
                Integer other = row.get(link.getKey());
                if (other != null) {
                    matrix[i][other] -= link.getValue().conductance;
                }
            }
        }
        matrix[row.get(from)][n] = 1.0;
        if (!eliminate(matrix, n)) {
            return null;
        }
        Map<Integer, Double> volts = new HashMap<>();
        volts.put(to, 0.0);
        for (int i = 0; i < n; i++) {
            volts.put(unknowns.get(i), matrix[i][n] / matrix[i][i]);
        }
        return volts;
    }

    /**
     * A link in the working mesh, and which real links it stands for.
     *
     * <p>The reduction throws away the wire it collapsed, and the wire is exactly what the caller
     * needs back — so each working link remembers what it was made of and how to hand a current
     * back down to it.
     */
    private static final class Working {

        private final double conductance;
        private final int original;                 // -1 when this is a composite
        private final List<Working> parts;
        private final boolean inParallel;

        Working(int original, double conductance) {
            this.original = original;
            this.conductance = conductance;
            this.parts = List.of();
            this.inParallel = false;
        }

        Working(List<Working> parts, double conductance, boolean inParallel) {
            this.original = -1;
            this.conductance = conductance;
            this.parts = parts;
            this.inParallel = inParallel;
        }

        /** Hands a current down: the same to each link in series, divided by conductance in parallel. */
        void spread(double amps, double[] into) {
            if (original >= 0) {
                into[original] += amps;
                return;
            }
            if (!inParallel) {
                parts.forEach(part -> part.spread(amps, into));
                return;
            }
            double total = 0;
            for (Working part : parts) {
                total += part.conductance;
            }
            for (Working part : parts) {
                part.spread(total > 0 ? amps * part.conductance / total : 0, into);
            }
        }

        static Working series(Working first, Working second) {
            double ohms = 1.0 / first.conductance + 1.0 / second.conductance;
            return new Working(List.of(first, second), 1.0 / ohms, false);
        }

        static Working parallel(Working first, Working second) {
            return new Working(List.of(first, second),
                    first.conductance + second.conductance, true);
        }
    }

    private Map<Integer, Map<Integer, Working>> working() {
        Map<Integer, Map<Integer, Working>> graph = new HashMap<>();
        for (int i = 0; i < order.size(); i++) {
            joinWorking(graph, order.get(i)[0], order.get(i)[1],
                    new Working(i, siemens.get(i)));
        }
        return graph;
    }

    private static void joinWorking(Map<Integer, Map<Integer, Working>> graph, int a, int b,
                                    Working edge) {
        if (a == b) {
            return;
        }
        Working existing = graph.getOrDefault(a, Map.<Integer, Working>of()).get(b);
        Working merged = existing == null ? edge : Working.parallel(existing, edge);
        graph.computeIfAbsent(a, key -> new HashMap<>()).put(b, merged);
        graph.computeIfAbsent(b, key -> new HashMap<>()).put(a, merged);
    }

    /** The same reduction as the one above, over links that remember what they were. */
    private static void reduce(Map<Integer, Map<Integer, Working>> graph, int from, int to) {
        boolean changed = true;
        while (changed) {
            changed = false;
            List<Integer> nodes = new ArrayList<>(graph.keySet());
            java.util.Collections.sort(nodes);
            for (int node : nodes) {
                if (node == from || node == to) {
                    continue;
                }
                Map<Integer, Working> links = graph.get(node);
                if (links == null) {
                    continue;
                }
                if (links.size() <= 1) {
                    detachWorking(graph, node);     // a dead end carries nothing at all
                    changed = true;
                } else if (links.size() == 2) {
                    var ends = new ArrayList<>(links.entrySet());
                    int left = ends.get(0).getKey();
                    int right = ends.get(1).getKey();
                    Working chain = Working.series(ends.get(0).getValue(), ends.get(1).getValue());
                    detachWorking(graph, node);
                    joinWorking(graph, left, right, chain);
                    changed = true;
                }
            }
        }
    }

    private static void detachWorking(Map<Integer, Map<Integer, Working>> graph, int node) {
        Map<Integer, Working> links = graph.remove(node);
        if (links != null) {
            links.keySet().forEach(other -> {
                Map<Integer, Working> back = graph.get(other);
                if (back != null) {
                    back.remove(node);
                }
            });
        }
    }

    /** A zero-resistance join: a bridging part, whose element is nothing beside metres of wire. */
    public void bond(int a, int b) {
        link(a, b, 1e-6);
    }

    /**
     * How much conductor stands between two points, in ohms.
     *
     * <p>One reduction and one solve, shared with {@link #shareOfCurrent} — two copies of this
     * would be two answers about one circuit, and they would disagree the first time either was
     * tuned.
     *
     * @return {@link #DISCONNECTED} when no path joins them
     */
    public double resistanceBetween(int from, int to) {
        if (from == to) {
            return 0;
        }
        Map<Integer, Map<Integer, Working>> graph = working();
        reduce(graph, from, to);
        Map<Integer, Working> direct = graph.get(from);
        if (direct == null || direct.isEmpty()) {
            return DISCONNECTED;
        }
        if (graph.size() == 2 && direct.size() == 1 && direct.containsKey(to)) {
            return 1.0 / direct.get(to).conductance;   // one resistor left; no matrix needed
        }
        // One amp in and one out, so the potential that appears at `from` IS the resistance.
        Map<Integer, Double> volts = potentials(graph, from, to);
        return volts == null ? DISCONNECTED : volts.getOrDefault(from, DISCONNECTED);
    }

    /** Gauss-Jordan with partial pivoting; false when the block is singular. */
    private static boolean eliminate(double[][] matrix, int n) {
        for (int column = 0; column < n; column++) {
            int pivot = column;
            for (int candidate = column + 1; candidate < n; candidate++) {
                if (Math.abs(matrix[candidate][column]) > Math.abs(matrix[pivot][column])) {
                    pivot = candidate;
                }
            }
            if (Math.abs(matrix[pivot][column]) < NEGLIGIBLE) {
                return false;
            }
            double[] swap = matrix[column];
            matrix[column] = matrix[pivot];
            matrix[pivot] = swap;
            for (int other = 0; other < n; other++) {
                if (other == column) {
                    continue;
                }
                double factor = matrix[other][column] / matrix[column][column];
                for (int cell = column; cell <= n; cell++) {
                    matrix[other][cell] -= factor * matrix[column][cell];
                }
            }
        }
        return true;
    }
}
