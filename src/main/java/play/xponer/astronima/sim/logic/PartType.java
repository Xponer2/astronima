package play.xponer.astronima.sim.logic;

import java.util.ArrayList;
import java.util.List;

/**
 * The kinds of logic that mount on a wall in the wire layer, and where their pads are.
 *
 * <p><strong>These were blocks, and a block was the wrong scale.</strong> The conductor in this mod
 * is one pixel across; a gate that occupied a cubic metre said the scale was decoration. Worse, it
 * had three concrete costs a player meets — two gates set side by side leave nowhere for the wire
 * between them, a circuit cannot be buried in a wall the way the whole tier promises, and a NOT
 * gate costs the same sealed floor area as a scrubber. See {@code design/wire-parts.md}.
 *
 * <p>So a part is a <strong>surface-mount component</strong>: it lies on a block face in the plane
 * of the traces and its pads are pixels on that same face. A trace reaches a pad by ordinary pixel
 * adjacency, which means <em>there is no new connection rule at all</em> — the geometry already
 * proved in {@code sim/wire} answers every question about how a wire meets a part.
 *
 * <h2>Published once, as a set (rule 20)</h2>
 * Registration, the creative tab, datagen, JEI and the language file all walk {@link #values()}.
 * Three times now, adding one thing has meant editing half a dozen files and the one that got
 * missed was always the one no gate looked at.
 *
 * <p>Minecraft-free (rule 1).
 */
public enum PartType {

    GATE_AND("gate_and", Gate.AND),
    GATE_OR("gate_or", Gate.OR),
    GATE_XOR("gate_xor", Gate.XOR),
    GATE_NAND("gate_nand", Gate.NAND),
    GATE_NOR("gate_nor", Gate.NOR),
    GATE_NOT("gate_not", Gate.NOT),

    /** A hand switch: closed, and the line it is on is live. */
    SWITCH("signal_switch", 3, 3, List.of(new Pad(2, 1, true, "out"))),

    /** A press-and-release button — the part you <em>test</em> a circuit with. */
    BUTTON("signal_button", 3, 3, List.of(new Pad(2, 1, true, "out"))),

    /**
     * A free-running oscillator: no input pads, toggles its own output on a fixed interval, no
     * hand needed. {@code design/computer.md} Phase 2's own answer to a machine that cannot outrun
     * one clock a second no matter how cheap {@link Circuit#evaluateStateful} gets — a switch or a
     * button is a person's decision, a clock is nobody's, which is why it gets a fourth verb
     * instead of being a switch with a timer bolted on (rule 8).
     *
     * <p>{@code held} is not set by a click here — {@code WireTicker} derives it fresh every
     * refresh from the world's own game time ({@code WireTicker.clocked}), so two clocks placed a
     * minute apart still tick in lockstep and a relog cannot leave one stuck.
     */
    CLOCK("signal_clock", 3, 3, List.of(new Pad(2, 1, true, "out"))),

    /** A whole authored schematic in one component: four in, four out. */
    PLATE("circuit_plate", 8, 8, platePads()),

    /** A high-density macro assembly plate: 10x10 footprint for nested subcircuits. */
    MACRO_PLATE("macro_plate", 10, 10, macroPlatePads()),

    /**
     * The one component whose job is to be destroyed.
     *
     * <p>{@code design/electrical.md} §4.2b: <em>"the fuse is the answer, and this is why a fuse is
     * not optional."</em> A run that can cook needs something cheaper than itself to fail first —
     * otherwise the only answer to an overload is a lesson learned twice, once per length of
     * conductor.
     *
     * <p><strong>A different verb from everything else here</strong> (rule 8). A gate computes, a
     * switch is a state a person sets, a plate is a circuit in a box. A fuse conducts until it
     * cannot, and then it sacrifices itself — it drives nothing, it decides nothing, and it is the
     * only part in the set that is <em>meant</em> to be consumed.
     */
    FUSE("power_fuse", 3, 3, fusePads()),

    /**
     * A protection you reset instead of replacing — and an isolator, because that is one lever.
     *
     * <p><strong>Not a fuse with different numbers</strong> (rule 8). It trips at the same current,
     * deliberately: the difference is not a constant, it is what a player does about it, and two
     * things separate them.
     *
     * <p><strong>It has to stay reachable.</strong> A fuse can be buried in a wall and dug out once;
     * a breaker is only worth having where a hand can get to it. That puts it in direct tension with
     * this tier's flagship promise — plaster the whole circuit over — and a tension is what turns a
     * placement into a decision. A fuse says <em>put me anywhere</em>; a breaker says <em>put me
     * where you will be standing when this goes wrong</em>.
     *
     * <p><strong>And it is a lever a person may throw.</strong> Real panels are full of breakers
     * precisely because the thing that protects a circuit is the thing you switch off to work on
     * one. So it isolates as well as protects, which no fuse does.
     *
     * <p>One consequence is worth the whole part: <strong>it cannot be reset into a live fault.</strong>
     * Throw it back on with three machines still drawing and it trips again within the tick. Nothing
     * in the code checks — the ordinary overload check runs and finds the overload still there —
     * which is why it teaches the order of operations rather than announcing it.
     */
    BREAKER("power_breaker", 3, 3, fusePads()),

    /**
     * Sixteen 4-bit cells: addressed and read combinationally, written on the clock's rising edge
     * — the real shape of a synchronous SRAM chip, sized to the 4-bit machines this mod already
     * builds rather than to a byte a general-purpose computer would want. {@code design/
     * memory-chips.md} is the design this part follows; the honest limits it names (why sixteen
     * cells, why edge-triggered, why this does not run at a clocked plate's internal speed) live
     * there rather than repeated here.
     *
     * <p>Fourteen pads — more than any part before it (`MACRO_PLATE`'s ten was the previous high)
     * — spread across all four edges of a 12×12 housing rather than piled onto one: address on the
     * left, data in across the top, data out on the right, the two control lines along the bottom.
     * A real chip's pins run around the whole package; a RAM chip earns being the biggest thing on
     * the board the same way {@code MACRO_PLATE} already does.
     */
    RAM("memory_ram", 12, 12, ramPads()),

    /**
     * A {@link #RAM} chip that also draws its sixty-four bits on its own face, eight pixels by
     * eight — memory-mapped video, which is what a real framebuffer card actually is rather than a
     * device you send drawing commands to. {@code design/display.md} is the design this follows;
     * the honest limits it names (why a full redraw takes sixteen refreshes, why it cannot scan an
     * external chip's memory, why it is monochrome) live there rather than repeated here.
     *
     * <p>{@link #ramPads()} reused rather than copied — one pad order for both memory-backed
     * parts, so {@link play.xponer.astronima.wire.MemoryLogic}'s index convention cannot drift
     * between the type it was written for and the one that reuses it whole.
     */
    FRAMEBUFFER("video_framebuffer", 12, 12, ramPads()),

    /**
     * A whole microcontroller: 256 bytes of unified program-and-data memory, {@code A}, {@code PC},
     * {@code Z}/{@code C}, run from an assembled program typed into its own code editor.
     * {@code design/processor.md} is the design this follows — most load-bearingly §1.1's own
     * arithmetic for why this is a chip at all: a {@code MACRO_PLATE} has five outputs and reaching
     * {@link #RAM} needs ten, so a plate-built CPU cannot address memory, full stop, before a single
     * gate of one is even placed.
     *
     * <p>Fourteen pads, {@link #ramPads()}'s own footprint reused once more with different
     * directions: {@code i0..i3} where {@code a0..a3} sits (input port), {@code p0..p3} where
     * {@code d0..d3} sits (output port 0), {@code q0..q3} unchanged (output port 1), {@code run}
     * where {@code we} sits and {@code stb} where {@code clk} sits — {@code stb} driving rather
     * than listening, which is the one direction this layout actually changes.
     */
    PROCESSOR("processor", 12, 12, processorPads());

    /**
     * One connection point on a part, in the part's own grid.
     *
     * <p><strong>Pads are never adjacent to each other</strong>, and that is load-bearing rather
     * than tidy: two runs of the same colour landing on neighbouring pixels are touching bare
     * metal, which is a short. Two pixels apart is the closest a pair of pads may sit.
     *
     * @param drives true for an output — something the part puts out; false for something it
     *               listens to
     */
    public record Pad(int u, int v, boolean drives, String label) { }

    private final String id;
    private final Gate gate;
    private final int width;
    private final int height;
    private final List<Pad> pads;

    PartType(String id, Gate gate) {
        this(id, 5, 5, gatePads(gate), gate);
    }

    PartType(String id, int width, int height, List<Pad> pads) {
        this(id, width, height, pads, null);
    }

    PartType(String id, int width, int height, List<Pad> pads, Gate gate) {
        this.id = id;
        this.width = width;
        this.height = height;
        this.pads = List.copyOf(pads);
        this.gate = gate;
    }

    /**
     * Inputs down the left edge, output on the right — the schematic convention, and the same
     * arrangement the gate <em>blocks</em> used, so a player who learned those has learned these.
     *
     * <p>A single-input gate gets <strong>one</strong> input, in the middle of the left edge. An
     * inverter with two input pads of which only one is read is a trap: both look connectable and
     * the wrong one fails silently — a scenario caught its own author doing exactly that with the
     * block version.
     */
    private static List<Pad> gatePads(Gate gate) {
        if (gate.isSingleInput()) {
            return List.of(new Pad(0, 2, false, "in"), new Pad(4, 2, true, "out"));
        }
        return List.of(new Pad(0, 0, false, "A"), new Pad(0, 4, false, "B"),
                new Pad(4, 2, true, "out"));
    }

    /**
     * A fuse is a bridge: one pad each side, and nothing else.
     *
     * <p>Neither of them drives — the fuse has no opinion about the circuit, it merely joins its
     * two ends while it is intact. Two pixels apart, like every other pair, so a run landing on
     * both does not short across the housing instead of going through it.
     */
    private static List<Pad> fusePads() {
        return List.of(new Pad(0, 1, false, "line"), new Pad(2, 1, false, "load"));
    }

    private static List<Pad> platePads() {
        List<Pad> pads = new ArrayList<>(8);
        for (int i = 0; i < 4; i++) {
            pads.add(new Pad(0, i * 2, false, String.valueOf((char) ('A' + i))));
        }
        for (int i = 0; i < 4; i++) {
            pads.add(new Pad(7, i * 2, true, String.valueOf((char) ('W' + i))));
        }
        return pads;
    }

    private static List<Pad> macroPlatePads() {
        List<Pad> pads = new ArrayList<>(10);
        for (int i = 0; i < 5; i++) {
            pads.add(new Pad(0, i * 2, false, String.valueOf((char) ('A' + i))));
        }
        for (int i = 0; i < 5; i++) {
            pads.add(new Pad(9, i * 2, true, String.valueOf((char) ('V' + i))));
        }
        return pads;
    }

    /**
     * Address on the left, data in across the top, data out on the right, controls along the
     * bottom — see {@link #RAM}'s own note for why all four edges rather than the usual two.
     * Declared in this exact order because {@link #inputs()} preserves declaration order among the
     * non-driving pads it filters to, which is what lets {@code MemoryLogic} read {@code a0..a3}
     * off indices {@code 0..3} and {@code d0..d3} off {@code 4..7} without naming pads by hand at
     * every call site — the same convention {@link #evaluate}'s own gate case already leans on for
     * its two inputs.
     */
    private static List<Pad> ramPads() {
        List<Pad> pads = new ArrayList<>(14);
        for (int i = 0; i < 4; i++) {
            pads.add(new Pad(0, 2 + i * 2, false, "a" + i));
        }
        for (int i = 0; i < 4; i++) {
            pads.add(new Pad(2 + i * 2, 0, false, "d" + i));
        }
        for (int i = 0; i < 4; i++) {
            pads.add(new Pad(11, 2 + i * 2, true, "q" + i));
        }
        pads.add(new Pad(3, 11, false, "we"));
        pads.add(new Pad(7, 11, false, "clk"));
        return pads;
    }

    /**
     * {@link #ramPads()}'s own layout, renamed and with the two control pads swapped in direction:
     * {@code design/processor.md} §2.2's three 4-bit ports and two control lines. Declared in this
     * exact order so {@link #inputs()} — which keeps only the non-driving pads, in declaration
     * order — lets {@code WireTicker} read {@code i0..i3} off indices {@code 0..3} and {@code run}
     * off index {@code 4}; {@code p0..p3}, {@code q0..q3} and {@code stb} all drive, so none of
     * them appear in {@link #inputs()} at all, the same filtering {@link #ramPads()} already
     * relies on for {@code MemoryLogic}.
     */
    private static List<Pad> processorPads() {
        List<Pad> pads = new ArrayList<>(14);
        for (int i = 0; i < 4; i++) {
            pads.add(new Pad(0, 2 + i * 2, false, "i" + i));
        }
        for (int i = 0; i < 4; i++) {
            pads.add(new Pad(2 + i * 2, 0, true, "p" + i));
        }
        for (int i = 0; i < 4; i++) {
            pads.add(new Pad(11, 2 + i * 2, true, "q" + i));
        }
        pads.add(new Pad(3, 11, false, "run"));
        pads.add(new Pad(7, 11, true, "stb"));
        return pads;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public List<Pad> pads() {
        return pads;
    }

    /** The gate this part is, or null for the switch, the button and the plate. */
    public Gate gate() {
        return gate;
    }

    public boolean isGate() {
        return gate != null;
    }

    /**
     * True for the parts a player operates by hand rather than by wiring.
     *
     * <p>The breaker is in here <em>and</em> in {@link #isBridge()}, and that overlap is the whole
     * component: the thing that protects a circuit is also the thing you throw to work on one.
     */
    public boolean isOperable() {
        return this == SWITCH || this == BUTTON || this == BREAKER;
    }

    /**
     * True for a part that <em>joins</em> its two pads rather than driving one of them.
     *
     * <p>The distinction matters to the network walk: a bridging part is a piece of conductor with
     * a condition attached, so a circuit runs straight through it while it holds and stops dead at
     * it when it does not.
     */
    public boolean isBridge() {
        return this == FUSE || this == BREAKER;
    }

    /**
     * The registry name, the texture name and the recipe id: one string, stated once.
     *
     * <p>Deliberately <strong>not</strong> derived from the enum name. These parts replace blocks
     * that already had ids, recipes, JEI pages and language entries, and a rename would have
     * silently orphaned every one of them — including anything a player already had in a chest.
     */
    public String id() {
        return id;
    }

    /** Reading pads, in the order the part publishes them. */
    public List<Pad> inputs() {
        return pads.stream().filter(pad -> !pad.drives()).toList();
    }

    /** Driving pads, in the order the part publishes them — the bit order of its output word. */
    public List<Pad> outputs() {
        return pads.stream().filter(Pad::drives).toList();
    }

    /**
     * What this part puts out, given what is on its input pads and whatever internal state it has.
     *
     * <p>One bit per driving pad, in {@link #outputs()} order. A bitfield rather than a list
     * because <strong>this is the whole of a part's client-visible state</strong>: it rides in the
     * chunk data the renderer already reads, which is how a part can be drawn lit without the
     * client ever calling server-side logic. That constraint is not new — every signal source in
     * this mod has had to answer it since the charge animation shipped.
     *
     * @param inputs one boolean per reading pad, in {@link #inputs()} order
     * @param held   the part's own state: a switch that is closed, a button that is down, or — for
     *               a {@link #RAM} chip — whether {@code clk} read high as of the last refresh,
     *               reused for edge detection the same way {@link #CLOCK} already reuses it for
     *               its own phase rather than adding a field for a single bit
     * @param memory a {@link #RAM} chip's sixteen 4-bit cells, packed low cell first; ignored by
     *               everything else. This method never writes to it — a live read only ever
     *               projects the addressed cell, the same as {@link #PLATE}'s own case here never
     *               mutates anything either; the one place a real write happens is {@code
     *               WireTicker}'s own refresh, where the rising edge this overload cannot see (it
     *               is handed one instant's inputs, not two) is actually detected.
     */
    public int evaluate(boolean[] inputs, boolean held, Circuit circuit, long memory) {
        return switch (this) {
            case SWITCH, BUTTON, CLOCK -> held ? 1 : 0;
            // A bridge drives nothing: it joins its two ends or it does not, and that is `held`
            // rather than an output. Spelled out rather than left to the default branch, which
            // reached for a gate this part does not have and took the server down with it.
            case FUSE, BREAKER -> 0;
            case PLATE, MACRO_PLATE -> {
                boolean[] out = circuit.flattened().evaluate(inputs);
                int bits = 0;
                for (int i = 0; i < out.length && i < Circuit.OUTPUTS; i++) {
                    bits |= out[i] ? 1 << i : 0;
                }
                yield bits;
            }
            case RAM, FRAMEBUFFER -> {
                int address = 0;
                for (int i = 0; i < 4; i++) {
                    if (read(inputs, i)) {
                        address |= 1 << i;
                    }
                }
                yield (int) ((memory >>> (address * 4)) & 0xF);
            }
            // A processor's real output — p0..p3, q0..q3, stb — is whatever its own Machine last
            // latched, and that state (256 bytes plus registers) does not fit through this method's
            // `long memory` parameter the way RAM's sixteen cells do. WireTicker computes the real
            // answer directly from the WirePart's own `machine` field and never calls this case;
            // it exists, spelled out rather than left to the gate-shaped default below, purely so a
            // stray call through PartLogic.evaluate (rule 20's coverage loops, or a future caller
            // nobody has written yet) reads a processor as driving nothing rather than crashing on
            // a null Gate. design/processor.md names this gap rather than hiding it.
            case PROCESSOR -> 0;
            default -> gate.apply(read(inputs, 0), read(inputs, 1)) ? 1 : 0;
        };
    }

    private static boolean read(boolean[] inputs, int index) {
        return index < inputs.length && inputs[index];
    }
}
