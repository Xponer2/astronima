package play.xponer.astronima.sim.logic;

import java.util.ArrayList;
import java.util.List;

/**
 * A flattened circuit's netlist, unpacked into primitive arrays — same answers as evaluating
 * {@link Circuit.Node} objects directly, computed by walking a {@code long[]} bitset instead of a
 * graph of boxed {@link Circuit.Source} records.
 *
 * <h2>Why this exists, in one number</h2>
 * Measured on this machine (see {@code design/computer.md} §1.1): once {@link Circuit#flattened()}
 * itself is cached, the object-graph evaluator is already close to optimal — a 32 768-gate circuit
 * settles in 0.194 ms — but that number still carries the cost of chasing a {@code Node} object,
 * its boxed {@code List<Source>}, and two more {@code Source} objects for every gate, every
 * settling pass. This class removes that chasing: one {@code Gate} and two {@code int} source
 * codes per gate, laid out contiguously, so evaluating a gate touches three arrays at a known index
 * rather than following pointers through however many objects the JVM happened to place wherever.
 *
 * <h2>The source encoding</h2>
 * A gate's input is exactly one of three things once a circuit is flat — the plate's own external
 * pin, an earlier gate's single output, or unconnected — and each is folded into a single
 * {@code int}: {@code code >= 0} is a node index (read that gate's settled bit); {@code code == OFF}
 * is unconnected (always false); anything else is an input pin, recovered as {@code -code - 1}.
 * There is no fourth case to handle because a flattened circuit has no subcircuit nodes and no gate
 * has more than one output — see {@link Circuit#flattened()}'s own contract.
 *
 * <p>Built only through {@link Circuit#compiled()}, which guarantees the circuit hasn't been
 * flattened wrong (or at all) before this reads {@link Circuit.Node#gate()} — a subcircuit node's
 * {@code gate()} is {@code null}, which is exactly what {@link Circuit#flattened()} exists to
 * remove.
 */
public final class CompiledCircuit {

    private static final int OFF = Integer.MIN_VALUE;

    private final Gate[] ops;
    private final int[] sourceA;
    private final int[] sourceB;
    private final int[] outputSources;
    private final boolean hasLoops;

    /**
     * Which gates read gate {@code i}'s output — {@code readers[i]}, built once at compile time.
     * The settling loop uses this to know who needs re-checking after a change, instead of
     * re-checking every gate every pass regardless of whether anything it reads moved.
     */
    private final int[][] readers;

    private CompiledCircuit(Gate[] ops, int[] sourceA, int[] sourceB, int[] outputSources,
                            boolean hasLoops, int[][] readers) {
        this.ops = ops;
        this.sourceA = sourceA;
        this.sourceB = sourceB;
        this.outputSources = outputSources;
        this.hasLoops = hasLoops;
        this.readers = readers;
    }

    /**
     * Compiles an already-flat circuit. Package-private: {@link Circuit#compiled()} is the one
     * caller, and it is the one place that both flattens first and caches the result, the same
     * pairing {@link Circuit#flattened()} itself already established.
     */
    static CompiledCircuit of(Circuit flat) {
        List<Circuit.Node> nodes = flat.nodes();
        int n = nodes.size();
        Gate[] ops = new Gate[n];
        int[] sourceA = new int[n];
        int[] sourceB = new int[n];
        boolean loops = false;
        for (int i = 0; i < n; i++) {
            Circuit.Node node = nodes.get(i);
            ops[i] = node.gate();
            sourceA[i] = encode(node.a());
            sourceB[i] = encode(node.b());
            if (sourceA[i] >= i || sourceB[i] >= i) {
                loops = true;
            }
        }
        int[] outputSources = new int[Circuit.OUTPUTS];
        for (int i = 0; i < Circuit.OUTPUTS; i++) {
            outputSources[i] = encode(flat.outputs().get(i));
        }

        List<List<Integer>> readerLists = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            readerLists.add(new ArrayList<>());
        }
        for (int j = 0; j < n; j++) {
            if (sourceA[j] >= 0) {
                readerLists.get(sourceA[j]).add(j);
            }
            if (sourceB[j] >= 0) {
                readerLists.get(sourceB[j]).add(j);
            }
        }
        int[][] readers = new int[n][];
        for (int i = 0; i < n; i++) {
            List<Integer> list = readerLists.get(i);
            int[] array = new int[list.size()];
            for (int k = 0; k < array.length; k++) {
                array[k] = list.get(k);
            }
            readers[i] = array;
        }

        return new CompiledCircuit(ops, sourceA, sourceB, outputSources, loops, readers);
    }

    private static int encode(Circuit.Source source) {
        if (source.isNode()) {
            return source.node();
        }
        if (source.isInput()) {
            return -(source.pin() + 1);
        }
        return OFF;
    }

    private static boolean read(int code, boolean[] inputs, long[] state) {
        if (code >= 0) {
            return (state[code >> 6] >>> (code & 63) & 1L) != 0;
        }
        if (code == OFF) {
            return false;
        }
        int pin = -code - 1;
        return pin < inputs.length && inputs[pin];
    }

    /**
     * Mirrors {@link Circuit#evaluate(boolean[])} exactly: a loop routes through
     * {@link #evaluateStateful} from a blank start, an acyclic netlist settles in one pass.
     */
    public boolean[] evaluate(boolean[] inputs) {
        if (hasLoops) {
            int bits = evaluateStateful(inputs, List.of()).outputs();
            boolean[] out = new boolean[Circuit.OUTPUTS];
            for (int i = 0; i < Circuit.OUTPUTS; i++) {
                out[i] = (bits >> i & 1) != 0;
            }
            return out;
        }
        int n = ops.length;
        long[] state = new long[(n + 63) / 64];
        for (int i = 0; i < n; i++) {
            if (ops[i].apply(read(sourceA[i], inputs, state), read(sourceB[i], inputs, state))) {
                state[i >> 6] |= 1L << (i & 63);
            }
        }
        boolean[] result = new boolean[Circuit.OUTPUTS];
        for (int i = 0; i < Circuit.OUTPUTS; i++) {
            result[i] = read(outputSources[i], inputs, state);
        }
        return result;
    }

    /**
     * Same Gauss-Seidel settling {@link Circuit#evaluateStateful} runs — every gate reads the most
     * recently written bits within a pass, not a snapshot of the previous one — over the primitive
     * netlist instead of the object graph. The gate-state wire format is identical: one bit per
     * gate, sixty-four gates per {@code long}, low bit first, so a value produced by one evaluator
     * is a legal input to the other. That compatibility is deliberate, not incidental — it's what
     * lets {@link Circuit#compiled()} replace the object-graph path without a save-format bump.
     *
     * <h2>Only re-checking what could have changed — and preserving the exact same schedule</h2>
     * The straightforward version of this loop re-evaluates every gate every pass, whether or not
     * anything it reads has moved — correct, and most of what a running machine's per-tick cost was
     * (design/computer.md §1). A gate whose sources are unchanged since its last evaluation always
     * recomputes the identical answer, so skipping it is always safe <em>on its own</em>. The part
     * that isn't automatically safe is scheduling: {@code design/computer.md}'s own history is
     * built on cross-coupled latches whose resolution — which of two equally valid stable states
     * they settle to from a blank start — depends on the exact order gates are visited in (rule 19,
     * rule 63). A plain dirty-queue that visits readers in whatever order they were enqueued can
     * silently pick a different, still-"correct", but <em>different</em> resolution than the
     * original always-ascending sweep did — which would be a real, observable behaviour change for
     * any latch already saved in a world.
     *
     * <p>So this keeps the original's exact schedule instead of replacing it: still one ascending
     * pass over every index, still updating {@code state} in place as it goes (so a later index
     * always sees an earlier one's fresh value within the same pass — Gauss-Seidel's whole point).
     * The only change is that a gate whose {@code dirty} bit is clear is skipped rather than
     * redundantly recomputed, and marking a reader dirty needs no branch on its index to get the
     * schedule right: a single {@code dirty[]} array, persisting across passes, already reproduces
     * it for free. If {@code reader > i}, the ascending sweep hasn't reached it yet this pass, so
     * marking it dirty now means it gets visited later in <em>this same</em> pass — exactly what
     * the original's redundant full sweep would have done anyway. If {@code reader <= i}, the sweep
     * already passed it (or {@code reader == i}, a gate reading its own output); marking it dirty
     * has no effect until the next pass's sweep reaches index {@code reader} again from the start —
     * again exactly matching the original, where such a gate is only re-visited on the next full
     * pass. No separate "next pass" array or branch is needed to get this: it falls out of
     * "dirty bits persist, the sweep is a fixed ascending order, and a bit already true stays true
     * until whoever owns that index next runs." Pass one starts with every gate dirty, which makes
     * it byte-for-byte identical to the original's own first pass — the pass where a symmetric
     * latch's race is actually decided.
     */
    public Circuit.Settled evaluateStateful(boolean[] inputs, List<Long> gateState) {
        int n = ops.length;
        long[] state = new long[(n + 63) / 64];
        for (int word = 0; word < state.length && word < gateState.size(); word++) {
            state[word] = gateState.get(word);
        }
        boolean[] dirty = new boolean[n];
        java.util.Arrays.fill(dirty, true);
        for (int iter = 0; iter < Circuit.MAX_SETTLE_ITERATIONS; iter++) {
            boolean changed = false;
            for (int i = 0; i < n; i++) {
                if (!dirty[i]) {
                    continue;
                }
                dirty[i] = false;
                boolean next = ops[i].apply(read(sourceA[i], inputs, state),
                        read(sourceB[i], inputs, state));
                boolean current = (state[i >> 6] >>> (i & 63) & 1L) != 0;
                if (next != current) {
                    if (next) {
                        state[i >> 6] |= 1L << (i & 63);
                    } else {
                        state[i >> 6] &= ~(1L << (i & 63));
                    }
                    changed = true;
                    for (int reader : readers[i]) {
                        dirty[reader] = true;
                    }
                }
            }
            if (!changed) {
                break;
            }
        }
        int outputBits = 0;
        for (int i = 0; i < Circuit.OUTPUTS; i++) {
            if (read(outputSources[i], inputs, state)) {
                outputBits |= 1 << i;
            }
        }
        List<Long> newGateState = new ArrayList<>(state.length);
        for (long word : state) {
            newGateState.add(word);
        }
        return new Circuit.Settled(outputBits, List.copyOf(newGateState));
    }
}
