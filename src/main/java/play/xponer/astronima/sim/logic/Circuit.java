package play.xponer.astronima.sim.logic;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;

/**
 * A whole schematic, small enough to fit in a box you can hold.
 *
 * <p><strong>This is the point of the plate.</strong> Anything here can already be built out of
 * separate gates and wire, and for the first circuit a player writes they should — that is how
 * they learn what a gate does. What this adds is the thing an engineer does <em>next</em>: having
 * worked a piece of logic out once, stop rebuilding it. An airlock interlock is four gates and
 * eight runs the first time and one component every time after.
 *
 * <p>That is what integration actually is, and it is why real electronics has chips: not because
 * discrete logic stopped working, but because nobody wants to re-derive a known answer.
 *
 * <h2>Feed-forward and looping circuits</h2>
 * A node may read the plate's inputs or any other gate's output — including gates that come after
 * it in declaration order. Acyclic circuits are evaluated in a single pass; circuits with feedback
 * loops ({@link #hasLoops()} returns {@code true}) use {@link #evaluateStateful(boolean[], int)},
 * an iterative settling algorithm that converges to a stable state for RS latches, flip-flops and
 * any other feedback circuit that has one. An oscillator that has no stable state runs to
 * {@link #MAX_SETTLE_ITERATIONS} and stops wherever it is.
 *
 * <p>Minecraft-free (rule 1).
 *
 * <h2>Why a class, not a record</h2>
 * It was one, and the fields never changed — every edit still returns a new {@code Circuit}, the
 * same as before. What changed is {@link #flattened()}: measured on this machine, a plate with
 * nested chips pays for the <em>entire</em> expanded node graph fresh every five ticks
 * ({@code WireTicker.refresh}), and at real machine scale that garbage was 95%+ of the per-refresh
 * cost — 4.7 ms of a 50 ms tick budget at 32 768 gates, for work whose answer cannot have changed
 * since the last time it was asked. A record has nowhere to hold a lazily-computed field that must
 * not participate in equality; a class does. {@link #equals(Object)}/{@link #hashCode()} are
 * written by hand to cover exactly {@code nodes} and {@code outputs} — the cache is derived from
 * them, never a third thing two otherwise-identical circuits could differ on.
 */
public final class Circuit {

    private final List<Node> nodes;
    private final List<Source> outputs;

    /**
     * This circuit's own expanded form, computed once and kept. {@code null} until first asked.
     * Safe as a plain field, not {@code volatile}: {@link #flattened()} is a pure function of
     * {@code nodes}/{@code outputs}, which never change after construction, so two threads racing
     * on a first call compute the identical answer and one write simply loses to the other — never
     * a wrong value, only an occasional redundant recomputation. See {@code design/computer.md}
     * §1.1 for why this exists: {@code WireTicker.refresh} used to pay for a mounted plate's entire
     * expanded node graph fresh every five ticks, and at real machine scale that was measured at
     * over 95% of the per-refresh cost.
     */
    private Circuit flattenedCache;

    /**
     * This circuit's netlist, unpacked into primitive arrays — see {@link CompiledCircuit}. Only
     * ever populated on the flat circuit itself, never on a nested one: {@link #compiled()}
     * delegates to {@code flattened().compiled()} whenever {@code this} isn't already flat, so a
     * plate compiled through two different nesting paths still shares one cache, the same as
     * {@link #flattenedCache} already does for the flat form itself.
     */
    private CompiledCircuit compiledCache;

    /** How many external inputs a plate has (A, B, C, D, E). */
    public static final int INPUTS = 5;

    /** How many external outputs it has (V, W, X, Y, Z). */
    public static final int OUTPUTS = 5;

    /** How many gates fit inside one. Eight is a real circuit and still a small box. */
    public static final int MAX_NODES = 8;

    /** An empty plate: no gates, every output dead. */
    public static Circuit empty() {
        List<Source> dead = new ArrayList<>(OUTPUTS);
        for (int i = 0; i < OUTPUTS; i++) {
            dead.add(Source.off());
        }
        return new Circuit(List.of(), dead);
    }

    /**
     * Where one input of a gate — or one of the plate's outputs — gets its value.
     *
     * @param node   when non-negative, the index of an earlier node; otherwise unused
     * @param pin    when non-negative, the index of one of the plate's external inputs
     * @param output which output of {@code node} this reads. Always {@code 0} for a gate — a gate
     *               has exactly one output, so the field is inert there — and {@code 0..4} for a
     *               nested plate, which may have up to five. {@link #fromNode(int)} always means
     *               "output 0", so every call site written before this field existed keeps meaning
     *               exactly what it always meant.
     */
    public record Source(int node, int pin, int output) {

        /** Always false — an unconnected input, which is a real and common state. */
        public static Source off() {
            return new Source(-1, -1, 0);
        }

        public static Source fromInput(int pin) {
            return new Source(-1, pin, 0);
        }

        public static Source fromNode(int node) {
            return new Source(node, -1, 0);
        }

        /** A specific output of a multi-output node — a nested plate's second, third pin, and so on. */
        public static Source fromNodeOutput(int node, int output) {
            return new Source(node, -1, output);
        }

        public boolean isOff() {
            return node < 0 && pin < 0;
        }

        public boolean isInput() {
            return pin >= 0;
        }

        public boolean isNode() {
            return node >= 0;
        }

        /** What the player sees on the editor's button. */
        public String label() {
            if (isInput()) {
                return String.valueOf((char) ('A' + pin));
            }
            if (!isNode()) {
                return "-";
            }
            return output == 0 ? "#" + (node + 1) : "#" + (node + 1) + (char) ('V' + output);
        }
    }

    /**
     * One gate inside the plate, and where it sits on the board.
     *
     * <p><strong>The position is data the model never reads and the player cannot do
     * without.</strong> A layout is how a person understands their own circuit three days later,
     * and an editor that re-laid it every time it opened would throw away the only documentation a
     * plate has. So it travels with the circuit: saved, sent, and preserved across every edit.
     *
     * <p>It is also what makes the topological renumbering below <em>deterministic</em> (rule 19):
     * ties are broken by where the gate is on the board, never by hash order or by the order an
     * edit happened to arrive in, so opening the same plate twice draws the same numbers on the
     * same gates.
     *
     * @param x column on the board, or {@link #UNPLACED} for a gate the editor has not laid out yet
     * @param y row on the board, or {@link #UNPLACED}
     * @param pins how many real input/output pins a subcircuit node has (four for a nested
     *             {@code PLATE}, five for a nested {@code MACRO_PLATE}) — meaningless for a gate,
     *             which always has one or two of each, derived from the gate itself. This is
     *             provenance the model cannot derive from the nested {@link Circuit} alone: every
     *             circuit always declares the same fixed {@link #INPUTS}/{@link #OUTPUTS} slots
     *             internally, so nothing about its own shape says which physical part it came from.
     * @param name the nested plate's own name at the moment it was nested — "" for a gate, and ""
     *             for a nested plate that was never named. A name lives on the item stack, not on
     *             a {@link Circuit} value, so nesting has to copy it in here explicitly or it is
     *             simply gone the instant a chip is armed — the same reason {@code pins} is a
     *             field here and not something read back off the nested circuit.
     */
    public record Node(@Nullable Gate gate, List<Source> inputs, int x, int y, @Nullable Circuit subcircuit,
                       int pins, String name) {

        /** A gate with no board position yet — the editor assigns one when it opens. */
        public static final int UNPLACED = -1;

        public Node(Gate gate, Source a, Source b) {
            this(gate, List.of(a, b), UNPLACED, UNPLACED, null, 0, "");
        }

        public Node(Gate gate, Source a, Source b, int x, int y) {
            this(gate, List.of(a, b), x, y, null, 0, "");
        }

        /** A nested plate, with {@code pins} real input pads (four or five — see {@link #pins}). */
        public Node(Circuit subcircuit, List<Source> inputs, int x, int y, int pins) {
            this(null, inputs, x, y, subcircuit, pins, "");
        }

        /** A nested plate that carries the name it had on the item stack it was armed from. */
        public Node(Circuit subcircuit, List<Source> inputs, int x, int y, int pins, String name) {
            this(null, inputs, x, y, subcircuit, pins, name);
        }

        public Node {
            inputs = List.copyOf(inputs);
            name = subcircuit != null && name != null ? name : "";
            if (subcircuit != null) {
                int want = Math.max(1, pins);
                if (inputs.size() != want) {
                    List<Source> padded = new ArrayList<>(want);
                    for (int i = 0; i < want; i++) {
                        padded.add(i < inputs.size() ? inputs.get(i) : Source.off());
                    }
                    inputs = List.copyOf(padded);
                }
                pins = want;
            } else {
                pins = 0;
                if (inputs.size() != 2) {
                    Source first = inputs.isEmpty() ? Source.off() : inputs.get(0);
                    Source second = inputs.size() > 1 ? inputs.get(1) : Source.off();
                    inputs = List.of(first, second);
                }
            }
            if (x < 0 || x >= BOARD_COLUMNS || y < 0 || y >= BOARD_ROWS) {
                x = UNPLACED;
                y = UNPLACED;
            }
        }

        public boolean isSubcircuit() {
            return subcircuit != null;
        }

        public boolean isPlaced() {
            return x != UNPLACED && y != UNPLACED;
        }

        /** How many inputs a player can actually wire into this node. */
        public int inputCount() {
            return isSubcircuit() ? inputs.size() : (gate.isSingleInput() ? 1 : 2);
        }

        /** How many outputs this node actually has — always one for a gate. */
        public int outputCount() {
            return isSubcircuit() ? pins : 1;
        }

        /** The node's source on that input. */
        public Source input(int index) {
            return index >= 0 && index < inputs.size() ? inputs.get(index) : Source.off();
        }

        /** The first input — for a gate, its A pin. */
        public Source a() {
            return input(0);
        }

        /** The second input — for a gate, its B pin. */
        public Source b() {
            return input(1);
        }

        /** The same node with one input re-pointed. */
        public Node withInput(int index, Source source) {
            List<Source> next = new ArrayList<>(inputs);
            while (next.size() <= index) {
                next.add(Source.off());
            }
            next.set(index, source);
            return withSources(next);
        }

        /**
         * The same node with its inputs replaced, at the same board position — keeping whichever
         * of {@code gate}/{@code subcircuit} it actually is.
         *
         * <p>Every place that rebuilds a node while renumbering or repointing its inputs
         * ({@link Circuit#removed}, {@link Circuit#normalised}, this record's own
         * {@link #withInput}) used to reach for {@code new Node(gate, ...)} directly, which
         * silently dropped a subcircuit node's actual circuit and left it with neither a gate nor
         * a subcircuit — reachable, and reached, the first time a real nested plate was linked in
         * the editor (found by {@code scenario_a_nested_subcircuit_survives_being_saved}).
         */
        public Node withSources(List<Source> newInputs) {
            return isSubcircuit() ? new Node(subcircuit, newInputs, x, y, pins, name)
                    : new Node(gate, newInputs.get(0), newInputs.get(1), x, y);
        }

        /** The same node moved to a new board position, keeping whichever of gate/subcircuit it is. */
        public Node withPosition(int newX, int newY) {
            return isSubcircuit() ? new Node(subcircuit, inputs, newX, newY, pins, name)
                    : new Node(gate, inputs.get(0), inputs.get(1), newX, newY);
        }
    }

    /**
     * How wide and how tall the editor's board is, in cells.
     *
     * <p>Twenty-four cells for eight gates, which is loose enough that a circuit can be laid out
     * to read left-to-right and tight enough that <strong>the whole board is always on screen</strong>.
     * A viewport that can scroll is a viewport that can hide the part that is wrong.
     */
    public static final int BOARD_COLUMNS = 6;

    /** @see #BOARD_COLUMNS */
    public static final int BOARD_ROWS = 4;

    public Circuit(List<Node> nodes, List<Source> outputs) {
        nodes = List.copyOf(nodes);
        outputs = List.copyOf(outputs);
        if (outputs.size() != OUTPUTS) {
            throw new IllegalArgumentException("a plate has exactly " + OUTPUTS + " outputs");
        }
        for (Node node : nodes) {
            for (Source input : node.inputs()) {
                check(input, nodes.size());
            }
        }
        for (Source output : outputs) {
            check(output, nodes.size());
        }
        this.nodes = nodes;
        this.outputs = outputs;
    }

    public List<Node> nodes() {
        return nodes;
    }

    public List<Source> outputs() {
        return outputs;
    }

    /**
     * Exactly {@code nodes} and {@code outputs} — never {@link #flattenedCache}, which is derived
     * from them and would make a circuit unequal to itself the instant something happened to ask
     * for its flat form first. The round-trip gametests (NBT and network) and {@code
     * BoardEditor#apply}'s "did anything actually change" both depend on this equality meaning
     * exactly "same nodes, same outputs" and nothing about what has been computed since.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Circuit other)) {
            return false;
        }
        return nodes.equals(other.nodes) && outputs.equals(other.outputs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodes, outputs);
    }

    @Override
    public String toString() {
        return "Circuit[nodes=" + nodes + ", outputs=" + outputs + "]";
    }

    /**
     * Validates that a source references an existing pin, node and output — not that it points
     * backwards.
     *
     * <p>Ordering is no longer enforced here because feedback loops are legal: a circuit with an
     * RS latch has at least one node that reads a later node, and that is the design.
     *
     * @param nodeCount total number of nodes in the circuit being validated
     */
    private static void check(Source source, int nodeCount) {
        if (source.pin() >= INPUTS || (source.pin() < -1)) {
            throw new IllegalArgumentException("no such input pin: " + source.pin());
        }
        if (source.node() >= nodeCount || (source.node() < -1)) {
            throw new IllegalArgumentException("no such node: " + source.node());
        }
        if (source.output() < 0 || source.output() >= OUTPUTS) {
            throw new IllegalArgumentException("no such output: " + source.output());
        }
    }

    /**
     * Runs the circuit from a stateless starting point.
     *
     * <p>For acyclic circuits this is a single pass in declaration order — the fast path.
     * For circuits with feedback ({@link #hasLoops()} is true), this delegates to
     * {@link #evaluateStateful(boolean[], int)} with an all-false initial gate state, which
     * settles to one of the circuit's stable states.
     *
     * @param inputs one boolean per external input pin; shorter arrays read as false
     * @return one boolean per external output
     */
    public boolean[] evaluate(boolean[] inputs) {
        if (hasLoops()) {
            int outputBits = evaluateStateful(inputs, List.of()).outputs();
            boolean[] out = new boolean[OUTPUTS];
            for (int i = 0; i < OUTPUTS; i++) {
                out[i] = (outputBits >> i & 1) != 0;
            }
            return out;
        }
        // Fast path: acyclic, single pass in topological order.
        boolean[] computed = new boolean[nodes.size()];
        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            computed[i] = node.gate().apply(read(node.a(), inputs, computed),
                    read(node.b(), inputs, computed));
        }
        boolean[] result = new boolean[OUTPUTS];
        for (int i = 0; i < OUTPUTS; i++) {
            result[i] = read(outputs.get(i), inputs, computed);
        }
        return result;
    }

    private static boolean read(Source source, boolean[] inputs, boolean[] computed) {
        if (source.isInput()) {
            return source.pin() < inputs.length && inputs[source.pin()];
        }
        return source.isNode() && computed[source.node()];
    }

    /** True when nothing has been authored — used to tell a blank plate from a working one. */
    public boolean isBlank() {
        if (!nodes.isEmpty()) {
            return false;
        }
        for (Source output : outputs) {
            if (!output.isOff()) {
                return false;
            }
        }
        return true;
    }

    /**
     * The same circuit with one gate added at the end.
     *
     * <p><strong>{@code MAX_NODES} is an authoring limit, checked here, not a property of every
     * {@link Circuit} value.</strong> It used to live in the record's own compact constructor,
     * which meant it silently bounded {@link #flattened()}'s result too — and a flattened circuit
     * is not authored, it is <em>expanded</em>: nesting is the one feature whose whole point is
     * that a board of eight gates, each themselves up to eight gates, computes as sixty-four. The
     * first real nested plate a player actually built past that size crashed the server every tick
     * (`WireTicker.refresh` → `flattened` → this constructor), because the editor's own eight-gate
     * board limit was, by accident, also a hard cap on how deep the game itself was allowed to run.
     */
    public Circuit withNode(Node node) {
        if (nodes.size() >= MAX_NODES) {
            throw new IllegalArgumentException("a plate holds at most " + MAX_NODES + " gates");
        }
        List<Node> next = new ArrayList<>(nodes);
        next.add(node);
        return new Circuit(next, outputs);
    }

    /** The same circuit with one gate replaced. */
    public Circuit withNode(int index, Node node) {
        List<Node> next = new ArrayList<>(nodes);
        next.set(index, node);
        return new Circuit(next, outputs);
    }

    /**
     * The same circuit with the last gate removed, and anything that read it left dangling.
     *
     * <p>Sources pointing at the removed gate become off rather than shifting to another one:
     * silently re-pointing a wire at whatever moved into that slot is how an edit turns into a
     * different circuit without the player touching it.
     */
    public Circuit withoutLastNode() {
        if (nodes.isEmpty()) {
            return this;
        }
        int removed = nodes.size() - 1;
        List<Node> next = new ArrayList<>(nodes.subList(0, removed));
        List<Source> outs = new ArrayList<>(OUTPUTS);
        for (Source output : outputs) {
            outs.add(output.node() == removed ? Source.off() : output);
        }
        return new Circuit(next, outs);
    }

    public Circuit withOutput(int index, Source source) {
        List<Source> next = new ArrayList<>(outputs);
        next.set(index, source);
        return new Circuit(nodes, next);
    }

    // ---- the board: what the editor edits ---------------------------------------
    //
    // The operations below exist because the editor is a drawing rather than a table. A player
    // working on a board says "this pin feeds that pin" in whatever order the circuit occurs to
    // them — including backwards, dropping the last gate first — and the model has to keep its own
    // language, in which a node may only read an earlier node. The seam is `linked`.

    /** The same circuit with one gate added at a board position. */
    public Circuit added(Gate gate, int x, int y) {
        return withNode(new Node(gate, Source.off(), Source.off(), x, y));
    }

    /**
     * The same circuit with a nested subcircuit added at a board position.
     *
     * @param pins how many real pins the nested part has — four for a {@code PLATE}, five for a
     *             {@code MACRO_PLATE} (see {@link Node#pins}). The caller states it explicitly
     *             rather than the model guessing, because nothing about a {@link Circuit}'s own
     *             shape says which physical part it came from.
     */
    public Circuit addedSubcircuit(Circuit subcircuit, int x, int y, int pins) {
        return addedSubcircuit(subcircuit, x, y, pins, "");
    }

    /** The same circuit with a nested subcircuit added, carrying the name it was armed under. */
    public Circuit addedSubcircuit(Circuit subcircuit, int x, int y, int pins, String name) {
        List<Source> inputs = new ArrayList<>(pins);
        for (int i = 0; i < pins; i++) {
            inputs.add(Source.off());
        }
        return withNode(new Node(subcircuit, inputs, x, y, pins, name));
    }

    /** Whether any gate already occupies that cell — the editor refuses to stack them. */
    public boolean occupied(int x, int y) {
        for (Node node : nodes) {
            if (node.x() == x && node.y() == y) {
                return true;
            }
        }
        return false;
    }

    /** The gate at a board cell, or -1. */
    public int nodeAt(int x, int y) {
        for (int i = 0; i < nodes.size(); i++) {
            if (nodes.get(i).x() == x && nodes.get(i).y() == y) {
                return i;
            }
        }
        return -1;
    }

    /** The same circuit with one gate moved, or unchanged if the destination is taken. */
    public Circuit moved(int index, int x, int y) {
        if (index < 0 || index >= nodes.size() || occupied(x, y)) {
            return this;
        }
        Node node = nodes.get(index);
        return withNode(index, node.withPosition(x, y));
    }

    /**
     * The same circuit with one gate removed, wherever it sits, and its readers left dangling.
     *
     * <p>The generalisation of {@link #withoutLastNode()}: an editor that could only delete the
     * most recent gate would make every mistake a matter of undoing everything after it. The rule
     * it inherits is the important half — <strong>anything that read the removed gate becomes
     * unconnected</strong> rather than silently re-pointed at whatever slid into its index, which
     * is how an edit turns into a different circuit with nothing to see.
     */
    public Circuit removed(int index) {
        if (index < 0 || index >= nodes.size()) {
            return this;
        }
        List<Node> next = new ArrayList<>(nodes.size() - 1);
        for (int i = 0; i < nodes.size(); i++) {
            if (i == index) {
                continue;
            }
            Node node = nodes.get(i);
            List<Source> shifted = new ArrayList<>(node.inputs().size());
            for (Source input : node.inputs()) {
                shifted.add(shift(input, index));
            }
            next.add(node.withSources(shifted));
        }
        List<Source> outs = new ArrayList<>(OUTPUTS);
        for (Source output : outputs) {
            outs.add(shift(output, index));
        }
        return new Circuit(next, outs);
    }

    /** A reference across a removal: the removed node becomes nothing, later ones slide down. */
    private static Source shift(Source source, int removed) {
        if (!source.isNode()) {
            return source;
        }
        if (source.node() == removed) {
            return Source.off();
        }
        return source.node() > removed ? new Source(source.node() - 1, -1, source.output()) : source;
    }

    /**
     * Whether a source may legally feed that gate.
     *
     * <p><strong>This is the one method the editor asks twice</strong> — once to decide whether to
     * highlight a pin as a landing place, and once when the drop happens. One method for both, so
     * a pin cannot be drawn as legal and then refuse (rule 20's shape, and rule 23's: a control
     * has to read as operable, and *only* when it is).
     *
     * <p>Feedback loops are now legal: a circuit may contain RS latches, flip-flops and any other
     * topology. The only illegal connections are those that reference a gate or pin that does not
     * exist.
     */
    public boolean canLink(int target, Source source) {
        if (target < 0 || target >= nodes.size()) {
            return false;
        }
        if (!source.isNode()) {
            return source.pin() < INPUTS;
        }
        if (source.node() < 0 || source.node() >= nodes.size()) {
            return false;
        }
        return source.output() < nodes.get(source.node()).outputCount();
    }

    /**
     * The same circuit with one gate input re-pointed — or {@code this}, unchanged, if that link
     * would close a loop.
     *
     * <p><strong>Refused before anything is built, never rolled back.</strong> A "mutate, then
     * validate, then undo" version leaves a half-applied edit behind on the failing path, which is
     * worse than either outcome and is invisible to the player.
     *
     * <p>When the link is legal the nodes are renumbered so that every one still reads only
     * earlier ones — see {@link #normalised(List, List)}. That keeps the record's own guarantee
     * (evaluation is one pass, always defined) while letting the player wire in whatever order the
     * circuit occurs to them.
     */
    public Circuit linked(int target, int input, Source source) {
        if (!canLink(target, source)) {
            return this;
        }
        List<Node> next = new ArrayList<>(nodes);
        next.set(target, next.get(target).withInput(input, source));
        return normalised(next, outputs);
    }

    /**
     * Sorts the nodes so every one reads only earlier ones, and rewrites every index to match.
     *
     * <p>Kahn's algorithm with a <strong>deterministic</strong> tie-break: among the gates whose
     * inputs are all resolved, the next one taken is the highest on the board, then the leftmost,
     * then the one that was already earliest. Never iteration order over a set — rule 19's third
     * lesson is that an arbitrary order is not made safe by being repeatable within one JVM.
     *
     * @param proposed nodes whose sources are indices into {@code proposed} itself
     */
    private static Circuit normalised(List<Node> proposed, List<Source> outputs) {
        int count = proposed.size();
        // Almost every edit already satisfies the rule — a player wiring left to right never
        // breaks it — and renumbering anyway would shuffle the gate numbers under the player's
        // hand for no reason. The sort fires only when a link genuinely points backwards.
        if (alreadyOrdered(proposed)) {
            return new Circuit(proposed, outputs);
        }
        int[] remaining = new int[count];
        List<List<Integer>> readers = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            readers.add(new ArrayList<>());
        }
        for (int i = 0; i < count; i++) {
            for (Source source : proposed.get(i).inputs()) {
                if (source.isNode()) {
                    remaining[i]++;
                    readers.get(source.node()).add(i);
                }
            }
        }

        Comparator<Integer> order = Comparator
                .<Integer>comparingInt(i -> proposed.get(i).y() == Node.UNPLACED
                        ? Integer.MAX_VALUE : proposed.get(i).y())
                .thenComparingInt(i -> proposed.get(i).x() == Node.UNPLACED
                        ? Integer.MAX_VALUE : proposed.get(i).x())
                .thenComparingInt(i -> i);
        PriorityQueue<Integer> ready = new PriorityQueue<>(Math.max(1, count), order);
        for (int i = 0; i < count; i++) {
            if (remaining[i] == 0) {
                ready.add(i);
            }
        }

        int[] rank = new int[count];
        List<Integer> sorted = new ArrayList<>(count);
        while (!ready.isEmpty()) {
            int taken = ready.poll();
            rank[taken] = sorted.size();
            sorted.add(taken);
            for (int reader : readers.get(taken)) {
                if (--remaining[reader] == 0) {
                    ready.add(reader);
                }
            }
        }
        if (sorted.size() != count) {
            // A feedback loop exists: some nodes depend on each other and were not scheduled by
            // Kahn's algorithm. Append them in board order so they get deterministic indices.
            // evaluateStateful() handles the iterative settling they require.
            List<Integer> cyclic = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                if (remaining[i] > 0) {
                    cyclic.add(i);
                }
            }
            cyclic.sort(order);
            for (int node : cyclic) {
                rank[node] = sorted.size();
                sorted.add(node);
            }
        }

        List<Node> laid = new ArrayList<>(count);
        for (int old : sorted) {
            Node node = proposed.get(old);
            List<Source> remapped = new ArrayList<>(node.inputs().size());
            for (Source input : node.inputs()) {
                remapped.add(remap(input, rank));
            }
            laid.add(node.withSources(remapped));
        }
        List<Source> outs = new ArrayList<>(OUTPUTS);
        for (Source output : outputs) {
            outs.add(remap(output, rank));
        }
        return new Circuit(laid, outs);
    }

    /** Whether every node already reads only earlier ones. */
    private static boolean alreadyOrdered(List<Node> proposed) {
        for (int i = 0; i < proposed.size(); i++) {
            for (Source input : proposed.get(i).inputs()) {
                if (input.node() >= i) {
                    return false;
                }
            }
        }
        return true;
    }

    private static Source remap(Source source, int[] rank) {
        return source.isNode() ? new Source(rank[source.node()], -1, source.output()) : source;
    }

    // ---- loops and stateful evaluation ------------------------------------------

    /**
     * Maximum Gauss-Seidel iterations per {@link #evaluateStateful} call.
     *
     * <p>256 is enough for any practical feedback circuit (an RS latch settles in 2–4 steps),
     * and small enough that an oscillator does not delay a server tick noticeably.
     */
    public static final int MAX_SETTLE_ITERATIONS = 256;

    /**
     * True when any node reads a node with an equal or higher index — the canonical sign that
     * the circuit has a feedback path and needs {@link #evaluateStateful} to evaluate correctly.
     *
     * <p>For acyclic circuits (all references are backwards) this is always {@code false}, and
     * {@link #evaluate(boolean[])} uses the fast single-pass path. For circuits with loops
     * (RS latches, flip-flops, counters) this is {@code true}, and the stateful path is used.
     */
    public boolean hasLoops() {
        for (int i = 0; i < nodes.size(); i++) {
            for (Source input : nodes.get(i).inputs()) {
                if (input.node() >= i) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Iterative evaluation for circuits that contain feedback loops.
     *
     * <p>Uses <strong>Gauss-Seidel iteration</strong>: within each pass, every gate reads the
     * most recently computed values of all other gates rather than a snapshot of the previous
     * pass. This breaks the symmetry that Jacobi (simultaneous update) cannot, allowing an RS
     * latch starting from all-false to converge to a real stable state instead of oscillating
     * between the two forbidden states.
     *
     * <p>Iteration stops when no gate's output changes in a full pass, or when
     * {@link #MAX_SETTLE_ITERATIONS} passes have run (oscillating circuit — the state is left
     * wherever it settled, which is always a coherent value each gate could legitimately compute).
     *
     * @param inputs    one boolean per external input pin; shorter arrays read as false
     * @param gateState the gate outputs from the previous evaluation, packed one bit per gate,
     *                  sixty-four gates per {@code long}, low bit first within each word; pass
     *                  {@link List#of()} to start from all-false
     * @return the output bitfield and the gate state to persist for the next call
     */
    public Settled evaluateStateful(boolean[] inputs, List<Long> gateState) {
        int n = nodes.size();
        boolean[] state = new boolean[n];
        for (int i = 0; i < n; i++) {
            int word = i >> 6;
            state[i] = word < gateState.size() && (gateState.get(word) >> (i & 63) & 1) != 0;
        }
        for (int iter = 0; iter < MAX_SETTLE_ITERATIONS; iter++) {
            boolean changed = false;
            for (int i = 0; i < n; i++) {
                Node node = nodes.get(i);
                boolean next = node.gate().apply(read(node.a(), inputs, state),
                        read(node.b(), inputs, state));
                if (next != state[i]) {
                    state[i] = next;
                    changed = true;
                }
            }
            if (!changed) {
                break;
            }
        }
        long[] newWords = new long[(n + 63) / 64];
        for (int i = 0; i < n; i++) {
            if (state[i]) {
                newWords[i >> 6] |= 1L << (i & 63);
            }
        }
        int outputBits = 0;
        for (int i = 0; i < OUTPUTS; i++) {
            if (read(outputs.get(i), inputs, state)) {
                outputBits |= 1 << i;
            }
        }
        List<Long> newGateState = new ArrayList<>(newWords.length);
        for (long word : newWords) {
            newGateState.add(word);
        }
        return new Settled(outputBits, List.copyOf(newGateState));
    }

    /**
     * What a settling pass produced: the output bitfield, and the gate state to hand back on the
     * next call.
     *
     * <p>Gate state is a list of {@code long} words rather than a single number, deliberately —
     * see {@link #evaluateStateful}'s own note. A single {@code int} (thirty-two gates) was the
     * original shape, and it was wrong the moment nesting made a genuinely large flattened circuit
     * reachable: bits past the thirty-second silently wrapped onto the first, which is a latch
     * quietly reading and writing the wrong gate's memory rather than a crash — the kind of fault
     * nobody notices until the circuit it happens to (rule 61's own lesson, one level further out).
     */
    public record Settled(int outputs, List<Long> gateState) { }

    /** Whether this circuit contains any nested subcircuit nodes. */
    public boolean hasSubcircuits() {
        for (Node node : nodes) {
            if (node.isSubcircuit()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Expands all nested subcircuits in this circuit recursively into a flat circuit
     * consisting solely of primitive gate nodes for high-speed evaluation.
     *
     * <h2>Three passes, not one — a node here may be read by an earlier one</h2>
     * A feedback loop (rule 58's own shape, and the entire reason a plate can latch at all) is a
     * node reading one that comes <em>after</em> it. A single forward pass that both reserves flat
     * indices and resolves sources as it goes cannot answer a reference to a block it has not
     * reserved yet — and it does not fail loudly: it fell back to {@link Source#off()}, the same
     * defensive default used for a genuinely out-of-range reference, so a nested latch's own
     * cross-coupling silently became "unconnected." The player-visible shape of that was exact:
     * a nested D-Latch's {@code Q} gate could still go high, since it read the other half of the
     * pair on the way <em>up</em>, but could never depend on it again — so it could never be
     * clocked back down. <em>"Постоянно горят"</em> is precisely what a latch reads like once its
     * own feedback path has been cut.
     *
     * <ol>
     *   <li><strong>Reserve.</strong> Walk this level's own nodes once, recursively flattening
     *       every subcircuit (which resolves that subcircuit's own internal forward references,
     *       at whatever depth) and reserving each node a contiguous block of flat indices. No
     *       source is touched yet — only counted.</li>
     *   <li><strong>Settle.</strong> Build this level's own node-to-flat-sources map. A gate's
     *       entry is immediate (its block is exactly one slot). A subcircuit's own outward-facing
     *       outputs may depend on this level's wiring into it, which may itself reference another
     *       node at this level defined <em>later</em> — two nested chips wired into a loop between
     *       each other, which is exactly what feeding a register's output back through an adder
     *       into its own input is. Settled the same way {@link #evaluateStateful} settles a loop:
     *       repeat until nothing changes, rather than assume one pass resolves it.</li>
     *   <li><strong>Emit.</strong> With every node's identity fully known regardless of which
     *       order they were defined in, write out every real gate — this level's own, and every
     *       nested level's — with its inputs resolved against the now-settled map.</li>
     * </ol>
     */
    public Circuit flattened() {
        if (!hasSubcircuits()) {
            return this;
        }
        if (flattenedCache != null) {
            return flattenedCache;
        }
        int count = nodes.size();

        // Pass 1 — reserve. Every node's own flat block, and every subcircuit's own recursively-
        // flattened form, decided before any source is resolved.
        Circuit[] flattenedSubs = new Circuit[count];
        int[] blockStart = new int[count];
        int total = 0;
        for (int i = 0; i < count; i++) {
            Node node = nodes.get(i);
            blockStart[i] = total;
            if (node.isSubcircuit()) {
                flattenedSubs[i] = node.subcircuit().flattened();
                total += flattenedSubs[i].nodes().size();
            } else {
                total += 1;
            }
        }

        // Pass 2 — settle. A gate's map entry never changes; a subcircuit's does, until every
        // reference it makes into this level (however it is ordered) has resolved.
        List<List<Source>> nodeMap = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            nodeMap.add(nodes.get(i).isSubcircuit() ? List.of() : List.of(Source.fromNode(blockStart[i])));
        }
        for (int round = 0; round <= count; round++) {
            boolean changed = false;
            for (int i = 0; i < count; i++) {
                Node node = nodes.get(i);
                if (!node.isSubcircuit()) {
                    continue;
                }
                List<Source> subInputs = subInputsOf(node, nodeMap);
                Circuit sub = flattenedSubs[i];
                List<Source> outs = new ArrayList<>(node.outputCount());
                for (int pin = 0; pin < node.outputCount(); pin++) {
                    outs.add(pin < sub.outputs().size()
                            ? offset(sub.outputs().get(pin), subInputs, blockStart[i])
                            : Source.off());
                }
                if (!outs.equals(nodeMap.get(i))) {
                    nodeMap.set(i, outs);
                    changed = true;
                }
            }
            if (!changed) {
                break;
            }
        }

        // Pass 3 — emit. Every node's identity is final; every source, at any depth, resolves.
        List<Node> flatNodes = new ArrayList<>(total);
        for (int i = 0; i < count; i++) {
            Node node = nodes.get(i);
            if (!node.isSubcircuit()) {
                flatNodes.add(new Node(node.gate(), mapSource(node.a(), nodeMap),
                        mapSource(node.b(), nodeMap), node.x(), node.y()));
                continue;
            }
            List<Source> subInputs = subInputsOf(node, nodeMap);
            for (Node subNode : flattenedSubs[i].nodes()) {
                // A flattened sub is already pure gates, so a() / b() are exactly its two
                // inputs — never a wider list to walk.
                flatNodes.add(new Node(subNode.gate(), offset(subNode.a(), subInputs, blockStart[i]),
                        offset(subNode.b(), subInputs, blockStart[i]), subNode.x(), subNode.y()));
            }
        }

        List<Source> flatOutputs = new ArrayList<>(outputs.size());
        for (Source out : outputs) {
            flatOutputs.add(mapSource(out, nodeMap));
        }
        Circuit flat = new Circuit(flatNodes, flatOutputs);
        flattenedCache = flat;
        return flat;
    }

    /**
     * This circuit's netlist as flat primitive arrays — see {@link CompiledCircuit}'s own javadoc
     * for why. Flattens first if needed, and caches on <em>that</em> object rather than on
     * {@code this}: two different unflattened circuits that flatten to the same cached instance
     * (nesting the same chip twice, or re-flattening after an edit that came back to an equal
     * form) then also share the one compiled netlist, rather than each compiling their own copy of
     * an identical answer.
     */
    public CompiledCircuit compiled() {
        Circuit flat = flattened();
        if (flat != this) {
            return flat.compiled();
        }
        if (compiledCache == null) {
            compiledCache = CompiledCircuit.of(this);
        }
        return compiledCache;
    }

    private static List<Source> subInputsOf(Node subcircuitNode, List<List<Source>> nodeMap) {
        List<Source> subInputs = new ArrayList<>(subcircuitNode.inputs().size());
        for (Source input : subcircuitNode.inputs()) {
            subInputs.add(mapSource(input, nodeMap));
        }
        return subInputs;
    }

    /** An outer source, translated into the flattened circuit's terms. */
    private static Source mapSource(Source source, List<List<Source>> nodeMap) {
        if (!source.isNode() || source.node() < 0 || source.node() >= nodeMap.size()) {
            return source;
        }
        List<Source> outs = nodeMap.get(source.node());
        return source.output() >= 0 && source.output() < outs.size()
                ? outs.get(source.output()) : Source.off();
    }

    /**
     * One of an already-flattened subcircuit's own sources, translated into this level's flat
     * terms. {@code subSource} is trusted here — {@code sub} validated it against its own node
     * count at construction, so a node reference is always in range; only an input pin beyond
     * what this level actually wired resolves to {@link Source#off()} rather than falling through
     * unchanged, which used to hand back a raw {@code Source.fromInput(2)} that the <em>outer</em>
     * circuit then read as its own input pin 2 — a different, wrong signal silently substituted
     * for "unreachable."
     */
    private static Source offset(Source subSource, List<Source> subInputs, int blockStart) {
        if (subSource.isInput()) {
            return subSource.pin() >= 0 && subSource.pin() < subInputs.size()
                    ? subInputs.get(subSource.pin()) : Source.off();
        }
        if (subSource.isNode()) {
            return new Source(blockStart + subSource.node(), -1, subSource.output());
        }
        return subSource;
    }

    /**
     * Every gate given a board position, without moving any that already has one.
     *
     * <p>For circuits authored before the board existed, and for anything that arrives off a save
     * or a packet with its positions missing. Laid left to right in evaluation order, which is the
     * reading a schematic wants anyway.
     */
    public Circuit laidOut() {
        List<Node> next = new ArrayList<>(nodes.size());
        boolean changed = false;
        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            if (node.isPlaced()) {
                next.add(node);
                continue;
            }
            int x = 0;
            int y = 0;
            while (taken(next, x, y) || occupiedFrom(nodes, i + 1, x, y)) {
                if (++y >= BOARD_ROWS) {
                    y = 0;
                    x++;
                }
                if (x >= BOARD_COLUMNS) {
                    break;
                }
            }
            next.add(node.withPosition(x, y));
            changed = true;
        }
        return changed ? new Circuit(next, outputs) : this;
    }

    private static boolean taken(List<Node> placed, int x, int y) {
        for (Node node : placed) {
            if (node.x() == x && node.y() == y) {
                return true;
            }
        }
        return false;
    }

    private static boolean occupiedFrom(List<Node> nodes, int from, int x, int y) {
        for (int i = from; i < nodes.size(); i++) {
            if (nodes.get(i).x() == x && nodes.get(i).y() == y) {
                return true;
            }
        }
        return false;
    }
}
