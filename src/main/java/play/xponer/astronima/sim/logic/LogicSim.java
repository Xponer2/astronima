package play.xponer.astronima.sim.logic;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Simulator for a discrete-logic network of arbitrary topology, including feedback loops.
 *
 * <p><strong>Why this is not {@link Circuit}.</strong> {@code Circuit} is a pure function: given
 * the same inputs it always returns the same outputs, evaluation is one pass in order, and there
 * is no state to carry between calls. That is the right model for a plate — a reusable, authored
 * recipe a player drops in once. It is the wrong model for individual gates on a wall, where a
 * player wires them by hand and expects real electronics to follow: an RS latch that remembers
 * which button was pressed last, a counter that increments, a register that holds a byte. Those
 * circuits have state, and state needs a simulator that keeps it.
 *
 * <h2>How propagation works</h2>
 * Each node stores its last computed output. When a source changes (a button pressed, a switch
 * thrown), its readers are added to a work queue. Each reader is re-evaluated from the current
 * outputs of its inputs. If its output changes, its own readers join the queue. Iteration
 * continues until the queue is empty (the network has settled) or {@link #MAX_PROPAGATIONS} steps
 * have been consumed (the network is oscillating — the state is left wherever it settled, and
 * the cap keeps the server tick from hanging).
 *
 * <p>This is event-driven propagation — the same strategy used in Logisim and most gate-level
 * simulators. It converges for any circuit that has a stable state; it runs to the limit for
 * circuits that genuinely oscillate (a ring of an odd number of NOT gates, for example).
 *
 * <h2>What you can build</h2>
 * With the six gates in {@link Gate} and arbitrary wiring, a player can construct:
 * <ul>
 *   <li>RS latches (two NOR or NAND gates with cross-coupled feedback) — 1 bit of memory</li>
 *   <li>D latches, D flip-flops — clocked 1-bit memory</li>
 *   <li>Half-adders (XOR + AND) and full adders (two half-adders + OR)</li>
 *   <li>N-bit ripple-carry adders chained from full adders</li>
 *   <li>Multiplexers (data routing), demultiplexers, decoders</li>
 *   <li>N-bit registers (N flip-flops with a shared clock and enable line)</li>
 *   <li>An ALU combining adders with logic-gate planes selected by a mux</li>
 *   <li>A CPU: ALU + register file + instruction decoder + program counter</li>
 * </ul>
 * Every one of these is constructible from the parts the player already holds. None of them need
 * changes to this simulator — they are emergent from the wiring, exactly as in real electronics.
 *
 * <p>Minecraft-free (rule 1).
 */
public final class LogicSim {

    /**
     * Maximum propagation steps per event before declaring a circuit unstable and stopping.
     *
     * <p>256 is enough for a 64-bit ripple-carry adder (64 full adders × a handful of gate depths
     * each) to fully settle in a single propagation pass, and small enough that a tight oscillator
     * cannot noticeably delay a server tick.
     */
    public static final int MAX_PROPAGATIONS = 256;

    // ---- internal node representation -------------------------------------------

    private static final class Node {

        /** The gate this node computes, or {@code null} for an external source. */
        final Gate gate;

        /** Index of the node feeding input A, or -1 (treated as {@code false}). */
        int inputA = -1;

        /** Index of the node feeding input B, or -1 (treated as {@code false}). */
        int inputB = -1;

        /** The node's current output — what other nodes read when they compute. */
        boolean output;

        Node(Gate gate) {
            this.gate = gate;
        }
    }

    private final List<Node> nodes = new ArrayList<>();

    /**
     * For each node: the list of node indices that currently read its output.
     *
     * <p>Kept in sync with {@link Node#inputA} / {@link Node#inputB} by {@link #connect} so that
     * propagation never needs to scan the whole network to find who to wake up next.
     */
    private final List<List<Integer>> readers = new ArrayList<>();

    // ---- building the network ---------------------------------------------------

    /**
     * Adds an external source — a button, switch, or the output of a plate — and returns its
     * node index.
     *
     * <p>A source has no gate; its output is set directly via {@link #setSource}. It is the
     * boundary between this simulator and the game world.
     */
    public int addSource() {
        return addNode(null);
    }

    /**
     * Adds a gate node and returns its node index.
     *
     * <p>Both inputs start unconnected ({@code false}). Use {@link #connect} to wire them.
     * The gate is evaluated every time one of its inputs changes.
     */
    public int addGate(Gate gate) {
        return addNode(Objects.requireNonNull(gate, "gate"));
    }

    private int addNode(Gate gate) {
        int id = nodes.size();
        nodes.add(new Node(gate));
        readers.add(new ArrayList<>());
        return id;
    }

    /**
     * Wires the output of node {@code from} to input {@code inputIndex} (0 = A, 1 = B) of
     * node {@code to}.
     *
     * <p><strong>Feedback loops are explicitly allowed.</strong> An RS latch is two NOR gates
     * each reading the other's output — that is not a defect to catch but a feature to preserve.
     * The propagation algorithm handles it: the circuit settles when it reaches a stable state,
     * and is bounded when it does not.
     *
     * <p>Replacing an existing connection silently removes the old reader registration, so the
     * reverse map never accumulates stale edges.
     *
     * @param from       node whose output feeds the connection
     * @param to         node whose input receives the connection
     * @param inputIndex 0 for input A, 1 for input B
     * @throws IndexOutOfBoundsException if either index is not in range
     * @throws IllegalArgumentException  if {@code inputIndex} is not 0 or 1
     */
    public void connect(int from, int to, int inputIndex) {
        if (inputIndex != 0 && inputIndex != 1) {
            throw new IllegalArgumentException("inputIndex must be 0 or 1, was: " + inputIndex);
        }
        Node target = nodes.get(to);
        int old = inputIndex == 0 ? target.inputA : target.inputB;
        if (old >= 0) {
            readers.get(old).remove(Integer.valueOf(to));
        }
        if (inputIndex == 0) {
            target.inputA = from;
        } else {
            target.inputB = from;
        }
        readers.get(from).add(to);
    }

    // ---- driving the network ----------------------------------------------------

    /**
     * Sets a source node's output and propagates the change through the network.
     *
     * <p>If the value is the same as the current output nothing is propagated — calling this
     * repeatedly with the same value is cheap.
     *
     * @throws IllegalArgumentException if the node is a gate, not a source
     */
    public void setSource(int id, boolean value) {
        Node node = nodes.get(id);
        if (node.gate != null) {
            throw new IllegalArgumentException("node " + id + " is a gate, not a source — "
                    + "drive sources only");
        }
        if (node.output == value) {
            return;
        }
        node.output = value;
        propagate(readers.get(id));
    }

    // ---- reading results --------------------------------------------------------

    /**
     * The current output of any node — source or gate.
     *
     * <p>For gates this is the value computed at the last propagation step that reached them.
     * For sources it is whatever {@link #setSource} last wrote.
     */
    public boolean output(int id) {
        return nodes.get(id).output;
    }

    /** How many nodes are in this simulator — useful for iteration in tests and serialisation. */
    public int size() {
        return nodes.size();
    }

    // ---- initial settling -------------------------------------------------------

    /**
     * Propagates from all sources until the network reaches a stable state.
     *
     * <p>All nodes start with output {@code false}. Many circuits (an RS latch with both inputs
     * released, a chain of NOT gates) have a natural stable state that differs from all-false;
     * this resolves it. Call once after all nodes and connections have been added.
     *
     * <p>For circuits that are already running and only need incremental updates, normal
     * {@link #setSource} propagation is sufficient — this is not needed on every tick.
     */
    public void settle() {
        List<Integer> seed = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i++) {
            if (nodes.get(i).gate == null) {
                seed.addAll(readers.get(i));
            }
        }
        propagate(seed);
    }

    // ---- propagation internals --------------------------------------------------

    /**
     * Event-driven propagation from a set of initially-dirty gate indices.
     *
     * <p>Each gate in the queue is re-evaluated from its inputs' current outputs. If the result
     * differs from the gate's stored output, the new value is written and all readers of that gate
     * are added to the queue (if not already present). This repeats until the queue is empty or
     * {@link #MAX_PROPAGATIONS} steps have run.
     *
     * <p>The "only enqueue if not already present" rule (via {@code inQueue}) prevents the same
     * gate from being evaluated redundantly in one pass while still allowing it to be re-evaluated
     * after its inputs change again — which is exactly what a feedback loop needs.
     */
    private void propagate(List<Integer> initial) {
        Deque<Integer> queue = new ArrayDeque<>(initial);
        Set<Integer> inQueue = new HashSet<>(initial);

        int steps = 0;
        while (!queue.isEmpty() && steps < MAX_PROPAGATIONS) {
            int idx = queue.poll();
            inQueue.remove(idx);
            steps++;

            Node node = nodes.get(idx);
            if (node.gate == null) {
                continue; // sources are driven externally, not computed
            }

            boolean a = node.inputA >= 0 && nodes.get(node.inputA).output;
            boolean b = node.inputB >= 0 && nodes.get(node.inputB).output;
            boolean newOut = node.gate.apply(a, b);

            if (newOut != node.output) {
                node.output = newOut;
                for (int reader : readers.get(idx)) {
                    if (inQueue.add(reader)) {
                        queue.add(reader);
                    }
                }
            }
        }
        // Reaching MAX_PROPAGATIONS means the circuit is oscillating. The state is left as-is:
        // every stored value is one a gate could legitimately have computed from its inputs at
        // some point, and stopping here is always preferable to hanging the tick indefinitely.
    }
}
