package play.xponer.astronima.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import play.xponer.astronima.sim.logic.Circuit;
import play.xponer.astronima.sim.logic.Gate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * A circuit as it rides on an item stack.
 *
 * <p>Separate from {@link Circuit} because the model has no business knowing about codecs, and
 * because the wire form has to survive being edited by a future version: gates and sources are
 * written by <em>name</em> and by index, never by ordinal, so reordering the {@link Gate} enum
 * cannot silently turn every saved AND into an OR.
 */
public final class CircuitPlate {

    private static final Codec<Gate> GATE_CODEC = Codec.STRING.xmap(
            name -> Gate.valueOf(name.toUpperCase(Locale.ROOT)),
            gate -> gate.name().toLowerCase(Locale.ROOT));

    private static final Codec<Circuit.Source> SOURCE_CODEC = RecordCodecBuilder.create(i ->
            i.group(Codec.INT.fieldOf("node").forGetter(Circuit.Source::node),
                    Codec.INT.fieldOf("pin").forGetter(Circuit.Source::pin),
                    // Optional, so a source saved before a node could have more than one output
                    // still loads — it always meant "output 0", the same thing the missing field
                    // now spells out.
                    Codec.INT.optionalFieldOf("output", 0).forGetter(Circuit.Source::output))
                    .apply(i, Circuit.Source::new));

    /**
     * The board position rides with the gate.
     *
     * <p>Optional with a defaulted {@link Circuit.Node#UNPLACED}, so every plate authored before
     * the board existed still loads — and the editor lays those out on open rather than refusing
     * them. A layout is the only documentation a plate carries, which is why it is saved at all.
     *
     * <p><strong>A node is a gate or a nested plate, never both</strong> — both fields are optional
     * and {@link Circuit.Node}'s full canonical constructor takes either as {@code null}. The
     * {@code subcircuit} field recurses into {@link #CODEC} itself, wrapped in
     * {@link Codec#lazyInitialized} because {@code CODEC} is not assigned yet at the point this
     * field is declared — the lambda only reads it once encoding actually runs, by which point
     * static initialisation has finished.
     *
     * <p>{@code inputs} is a list rather than fixed {@code a}/{@code b} fields, so a nested plate's
     * real pin count (four or five, {@code pins}) can be saved in full; a gate node's list is
     * always exactly two long. {@code pins} is optional and defaults to {@code 2} — a nested node
     * saved before this fix had exactly two reachable inputs, so an old save degrades to reading
     * as the same two-pin chip it always was, rather than refusing to load.
     *
     * <p><strong>{@code a}/{@code b} are read-only fallbacks, never written.</strong> Every plate
     * saved before this fix has those two fields and no {@code inputs} list at all — a real save
     * crashed on load the moment this only wrote and read {@code inputs}, because a node whose
     * decode fails is a node a list codec can drop silently, and everything that used to reference
     * it by index then points at nothing. Both are read here so an old node decodes into exactly
     * the two-input node it always was; their {@code forGetter} always answers {@link
     * Circuit.Source#off()}, so a plate saved after this fix never writes them — {@code inputs} is
     * the one true field going forward, and this pair exists only so the past keeps loading.
     *
     * <p>{@code name} is the nested plate's own name at the moment it was armed — optional and
     * defaulted to {@code ""}, the same shape as {@code pins}, so every plate saved before a
     * nested node could carry a name still loads exactly as it did before (a gate never has one;
     * an already-nested plate reads as unnamed rather than refusing to load).
     */
    private static final Codec<Circuit.Node> NODE_CODEC = RecordCodecBuilder.create(i ->
            i.group(GATE_CODEC.optionalFieldOf("gate")
                            .forGetter(node -> Optional.ofNullable(node.gate())),
                    SOURCE_CODEC.listOf().optionalFieldOf("inputs")
                            .forGetter(node -> Optional.of(node.inputs())),
                    SOURCE_CODEC.optionalFieldOf("a", Circuit.Source.off())
                            .forGetter(node -> Circuit.Source.off()),
                    SOURCE_CODEC.optionalFieldOf("b", Circuit.Source.off())
                            .forGetter(node -> Circuit.Source.off()),
                    Codec.INT.optionalFieldOf("x", Circuit.Node.UNPLACED)
                            .forGetter(Circuit.Node::x),
                    Codec.INT.optionalFieldOf("y", Circuit.Node.UNPLACED)
                            .forGetter(Circuit.Node::y),
                    Codec.lazyInitialized(() -> CircuitPlate.CODEC).optionalFieldOf("subcircuit")
                            .forGetter(node -> Optional.ofNullable(node.subcircuit())),
                    Codec.INT.optionalFieldOf("pins", 2).forGetter(Circuit.Node::pins),
                    Codec.STRING.optionalFieldOf("name", "").forGetter(Circuit.Node::name))
                    .apply(i, (gate, inputs, a, b, x, y, subcircuit, pins, name) -> {
                        List<Circuit.Source> resolved = inputs.orElseGet(() -> List.of(a, b));
                        return subcircuit.isPresent()
                                ? new Circuit.Node(subcircuit.get(), resolved, x, y, pins, name)
                                : new Circuit.Node(gate.orElse(null), at(resolved, 0), at(resolved, 1), x, y);
                    }));

    /** A malformed load with too few saved inputs degrades to "unconnected" rather than throwing. */
    private static Circuit.Source at(List<Circuit.Source> inputs, int index) {
        return index < inputs.size() ? inputs.get(index) : Circuit.Source.off();
    }

    public static final Codec<Circuit> CODEC = RecordCodecBuilder.create(i ->
            i.group(NODE_CODEC.listOf().fieldOf("gates").forGetter(Circuit::nodes),
                    SOURCE_CODEC.listOf().fieldOf("outputs").forGetter(Circuit::outputs))
                    .apply(i, CircuitPlate::safeCircuit));

    /**
     * A circuit decoded from saved or networked data degrades to blank rather than throwing — the
     * same rule {@link #STREAM_CODEC} already followed, extended to the NBT side after it proved
     * it needed to: a genuinely inconsistent node list (a stale save, a list codec that silently
     * dropped one element that failed to decode under an older schema) took the whole player-load
     * down with it via {@link Circuit}'s own strict validation, rather than just that one item
     * failing to read. {@link Circuit}'s constructor itself stays strict — refusing a genuinely
     * illegal in-game edit is still exactly right — this is the deserialisation boundary's job.
     */
    private static Circuit safeCircuit(List<Circuit.Node> nodes, List<Circuit.Source> outputs) {
        try {
            return new Circuit(nodes, outputs);
        } catch (RuntimeException malformed) {
            return Circuit.empty();
        }
    }

    /**
     * Hand-written, so every field's wire form is visible at both ends.
     *
     * <p>A malformed circuit off the network reads as an <strong>empty</strong> plate rather than
     * throwing: a packet is untrusted input, and taking a connection down over one bad plate
     * would be a denial of service wearing a validation check.
     *
     * <p>A node writes one leading boolean — is it a gate or a nested plate — then either a gate
     * ordinal or a whole recursive {@code STREAM_CODEC} frame for the subcircuit. The recursion is
     * legal the same way {@link #NODE_CODEC}'s is: {@code STREAM_CODEC} is read by name inside its
     * own initialiser lambdas, which do not run until encoding actually happens, well after the
     * field itself is assigned.
     *
     * <p>{@code inputs} writes as a count followed by that many sources — two for a gate, {@code
     * pins} for a nested plate — rather than a fixed {@code a}/{@code b} pair, so a chip's real
     * pin count survives the wire the same way it survives NBT.
     */
    public static final StreamCodec<ByteBuf, Circuit> STREAM_CODEC = StreamCodec.of(
            (buffer, circuit) -> {
                ByteBufCodecs.VAR_INT.encode(buffer, circuit.nodes().size());
                for (Circuit.Node node : circuit.nodes()) {
                    boolean subcircuit = node.isSubcircuit();
                    ByteBufCodecs.BOOL.encode(buffer, subcircuit);
                    if (subcircuit) {
                        CircuitPlate.STREAM_CODEC.encode(buffer, node.subcircuit());
                    } else {
                        ByteBufCodecs.VAR_INT.encode(buffer, node.gate().ordinal());
                    }
                    ByteBufCodecs.VAR_INT.encode(buffer, node.inputs().size());
                    for (Circuit.Source input : node.inputs()) {
                        writeSource(buffer, input);
                    }
                    ByteBufCodecs.VAR_INT.encode(buffer, node.x() + 1);
                    ByteBufCodecs.VAR_INT.encode(buffer, node.y() + 1);
                    ByteBufCodecs.STRING_UTF8.encode(buffer, node.name());
                }
                for (Circuit.Source output : circuit.outputs()) {
                    writeSource(buffer, output);
                }
            },
            buffer -> {
                try {
                    int count = Math.clamp(ByteBufCodecs.VAR_INT.decode(buffer), 0, Circuit.MAX_NODES);
                    List<Circuit.Node> nodes = new ArrayList<>(count);
                    Gate[] gates = Gate.values();
                    for (int i = 0; i < count; i++) {
                        boolean subcircuit = ByteBufCodecs.BOOL.decode(buffer);
                        Gate gate = null;
                        Circuit sub = null;
                        if (subcircuit) {
                            sub = CircuitPlate.STREAM_CODEC.decode(buffer);
                        } else {
                            gate = gates[Math.floorMod(ByteBufCodecs.VAR_INT.decode(buffer), gates.length)];
                        }
                        int pins = Math.clamp(ByteBufCodecs.VAR_INT.decode(buffer), 0, Circuit.INPUTS);
                        List<Circuit.Source> inputs = new ArrayList<>(pins);
                        for (int p = 0; p < pins; p++) {
                            inputs.add(readSource(buffer));
                        }
                        int x = ByteBufCodecs.VAR_INT.decode(buffer) - 1;
                        int y = ByteBufCodecs.VAR_INT.decode(buffer) - 1;
                        String name = ByteBufCodecs.STRING_UTF8.decode(buffer);
                        nodes.add(subcircuit
                                ? new Circuit.Node(sub, inputs, x, y, pins, name)
                                : new Circuit.Node(gate, at(inputs, 0), at(inputs, 1), x, y));
                    }
                    List<Circuit.Source> outputs = new ArrayList<>(Circuit.OUTPUTS);
                    for (int i = 0; i < Circuit.OUTPUTS; i++) {
                        outputs.add(readSource(buffer));
                    }
                    return new Circuit(nodes, outputs);
                } catch (RuntimeException malformed) {
                    return Circuit.empty();
                }
            });

    private static void writeSource(ByteBuf buffer, Circuit.Source source) {
        ByteBufCodecs.VAR_INT.encode(buffer, source.node() + 1);
        ByteBufCodecs.VAR_INT.encode(buffer, source.pin() + 1);
        ByteBufCodecs.VAR_INT.encode(buffer, source.output());
    }

    private static Circuit.Source readSource(ByteBuf buffer) {
        int node = ByteBufCodecs.VAR_INT.decode(buffer) - 1;
        int pin = ByteBufCodecs.VAR_INT.decode(buffer) - 1;
        int output = ByteBufCodecs.VAR_INT.decode(buffer);
        return new Circuit.Source(node, pin, output);
    }

    private CircuitPlate() {}
}
