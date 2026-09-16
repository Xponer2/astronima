package play.xponer.astronima.sim.pipe;

import play.xponer.astronima.sim.Gas;
import play.xponer.astronima.sim.RoomState;

import java.util.ArrayList;
import java.util.List;

/**
 * A set of volumes joined by pipe runs, settled one tick at a time.
 *
 * <p>The network does not need its own flow solver. Each run collapses to a single
 * conductance via {@link Conduit}, and {@code GasFlow}-style exponential relaxation
 * against that conductance is already unconditionally stable and mass-conserving. The
 * network's job is to compute the right numbers and apply them fairly.
 *
 * <p><strong>Fairly</strong> is the hard part. Two pipes into one tank must not give
 * different answers depending on which was placed first: block placement order is
 * invisible to the player and depending on it would be maddening to debug. So every
 * edge is evaluated against the node states as they were at the <em>start</em> of the
 * tick, and the resulting transfers are applied together afterwards. That is a Jacobi
 * step rather than Gauss-Seidel, and it costs one snapshot per tick to make the
 * network's behaviour a property of its topology alone.
 */
public final class GasNetwork {

    /** A pipe run between two volumes. */
    public record Edge(int from, int to, double conductance) {}

    private final List<RoomState> nodes = new ArrayList<>();
    private final List<Edge> edges = new ArrayList<>();

    /** Adds a volume to the network, returning its index. */
    public int addNode(RoomState node) {
        nodes.add(node);
        return nodes.size() - 1;
    }

    /** Joins two volumes by a run of pipe of the given conductance. */
    public void connect(int from, int to, double conductance) {
        if (from == to || conductance <= 0) {
            return;
        }
        edges.add(new Edge(from, to, conductance));
    }

    /** Joins two volumes by a run of {@code blocks} standard pipes. */
    public void connectBlocks(int from, int to, int blocks) {
        connect(from, to, Conduit.ofBlocks(blocks));
    }

    public List<RoomState> nodes() {
        return List.copyOf(nodes);
    }

    public List<Edge> edges() {
        return List.copyOf(edges);
    }

    /**
     * Settles the network for one step.
     *
     * <p>Every edge moves each gas species toward equality between its two nodes, at a
     * rate set by the run's conductance and the smaller of the two volumes — the small
     * end is what limits how fast the pair can equilibrate.
     *
     * <p>All transfers are computed from the snapshot and applied at the end, so the
     * result does not depend on the order edges were added.
     */
    public void tick(double dtSeconds) {
        if (dtSeconds <= 0 || edges.isEmpty()) {
            return;
        }
        Gas[] species = Gas.values();
        // Snapshot: what every node held at the start of the tick.
        double[][] before = new double[nodes.size()][species.length];
        for (int n = 0; n < nodes.size(); n++) {
            for (int g = 0; g < species.length; g++) {
                before[n][g] = nodes.get(n).gases().get(species[g]);
            }
        }

        // Per-edge, per-species moves, kept rather than accumulated straight into the
        // nodes so competing withdrawals can be reconciled before anything is applied.
        double[][] moves = new double[edges.size()][species.length];

        for (int e = 0; e < edges.size(); e++) {
            Edge edge = edges.get(e);
            RoomState from = nodes.get(edge.from());
            RoomState to = nodes.get(edge.to());
            double limitingVolume = Math.min(from.volumeM3(), to.volumeM3());
            double rate = Conduit.relaxationPerSecond(edge.conductance(), limitingVolume);
            if (rate <= 0) {
                continue;
            }
            // Exponential approach to equilibrium: stable at any dt, and it cannot
            // overshoot however long the step is.
            double closed = 1.0 - Math.exp(-rate * dtSeconds);

            double fromVolume = from.volumeM3();
            double totalVolume = fromVolume + to.volumeM3();
            if (totalVolume <= 0) {
                continue;
            }

            for (int g = 0; g < species.length; g++) {
                double total = before[edge.from()][g] + before[edge.to()][g];
                if (total <= 0) {
                    continue;
                }
                // Equilibrium is equal concentration, not equal moles: a big tank ends
                // up holding proportionally more of the gas than a small one.
                double fromTarget = total * fromVolume / totalVolume;
                moves[e][g] = (before[edge.from()][g] - fromTarget) * closed;
            }
        }

        rationOverdrawnNodes(before, moves, species.length);

        double[][] delta = new double[nodes.size()][species.length];
        for (int e = 0; e < edges.size(); e++) {
            Edge edge = edges.get(e);
            for (int g = 0; g < species.length; g++) {
                delta[edge.from()][g] -= moves[e][g];
                delta[edge.to()][g] += moves[e][g];
            }
        }

        for (int n = 0; n < nodes.size(); n++) {
            RoomState node = nodes.get(n);
            for (int g = 0; g < species.length; g++) {
                double change = delta[n][g];
                if (change > 0) {
                    node.addGasAt(species[g], change, node.temperatureK());
                } else if (change < 0) {
                    node.removeGas(species[g], -change);
                }
            }
        }
    }

    /**
     * Shares out a node's gas when more than one pipe wants to draw it in the same tick.
     *
     * <p>Because every edge is computed from the same start-of-tick snapshot, several
     * wide pipes leaving one small volume can each independently decide to take most of
     * what is there — together asking for more than exists. Clamping each withdrawal on
     * its own would prevent the node going negative and <em>destroy the excess</em>:
     * mass lost silently, visible only as a habitat that slowly empties for no reason.
     *
     * <p>So the withdrawals are scaled down proportionally instead. Everyone drawing on
     * a node gets the same fraction of what they asked for, which conserves mass, keeps
     * every node non-negative, and is independent of the order the edges were placed —
     * the same three properties the network is built to guarantee.
     */
    private void rationOverdrawnNodes(double[][] before, double[][] moves, int speciesCount) {
        for (int n = 0; n < nodes.size(); n++) {
            for (int g = 0; g < speciesCount; g++) {
                double requested = 0;
                for (int e = 0; e < edges.size(); e++) {
                    requested += withdrawalFrom(n, moves[e][g], edges.get(e));
                }
                double available = before[n][g];
                if (requested <= available || requested <= 0) {
                    continue;
                }
                double share = available / requested;
                for (int e = 0; e < edges.size(); e++) {
                    if (withdrawalFrom(n, moves[e][g], edges.get(e)) > 0) {
                        moves[e][g] *= share;
                    }
                }
            }
        }
    }

    /** How much this move takes out of node {@code n}, or zero if it puts gas in. */
    private static double withdrawalFrom(int n, double move, Edge edge) {
        if (edge.from() == n && move > 0) {
            return move;
        }
        if (edge.to() == n && move < 0) {
            return -move;
        }
        return 0;
    }

    /** Total moles held across the whole network, for conservation checks. */
    public double totalMoles() {
        double total = 0;
        for (RoomState node : nodes) {
            for (Gas gas : Gas.values()) {
                total += node.gases().get(gas);
            }
        }
        return total;
    }
}
