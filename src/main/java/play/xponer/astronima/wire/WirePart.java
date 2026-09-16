package play.xponer.astronima.wire;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import play.xponer.astronima.item.CircuitPlate;
import play.xponer.astronima.sim.logic.Circuit;
import play.xponer.astronima.sim.logic.PartType;
import play.xponer.astronima.sim.wire.FaceBasis;
import play.xponer.astronima.sim.wire.PartFootprint;
import play.xponer.astronima.sim.wire.WirePixel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One logic part mounted on a block face, in the same layer as the wire.
 *
 * <p>Stored beside the traces in {@link WireChunk} — one storage, one save, one sync, one unload —
 * which is the reason {@code design/electrical.md} §11 chose this form in the first place and then
 * did not build it.
 *
 * <h2>{@code outputs} is the whole client-visible state, and that is not an optimisation</h2>
 * The client has no {@code ServerLevel}, so it cannot ask a part what it is doing; every signal
 * source in this mod has had to publish its own answer since the charge animation shipped, and a
 * source whose output cannot be read off its own record <strong>cannot be drawn, and if it cannot
 * be drawn it should not exist</strong>. So the server computes the bitfield and writes it here,
 * and the renderer reads exactly what the server wrote. Nothing is recomputed on the client.
 *
 * @param cell      the cell the part lies in, pressed against {@code face}
 * @param face      which way it is pressed — so it hangs on {@code cell.relative(face)}
 * @param u         the part's corner on that face
 * @param v         the part's corner on that face
 * @param rotation  quarter-turns clockwise in the face's own basis
 * @param outputs   one bit per driving pad, in {@link PartType#outputs()} order
 * @param held      the switch is closed, or the button is down
 * @param releaseAt the game tick a button springs back at; zero for everything else
 * @param circuit   what a plate computes; empty for everything else
 * @param gateState a plate's internal gate memory, for a circuit with feedback loops — one bit
 *                  per gate, packed sixty-four to a {@code long}; empty for everything else and
 *                  for a plate with none yet. A list rather than a single number because a
 *                  flattened, nested circuit can hold far more than sixty-four gates (rule 61's
 *                  own lesson, one level further out): a fixed-width number would silently wrap
 *                  a large plate's memory onto itself.
 * @param name      a plate's own custom name, empty for everything else and for an unnamed plate.
 *                  Carried here rather than left to ride the item stack, because the stack is
 *                  consumed on placement — anything not copied into the part is simply gone.
 * @param memory    a {@link PartType#RAM} chip's sixteen 4-bit cells, packed low cell first into
 *                  one {@code long}; zero (all cells empty) for everything else and for a chip
 *                  never written to. {@code held} doubles as that chip's edge-detection bit — was
 *                  {@code clk} read high as of the last refresh — the same reinterpretation
 *                  {@link PartType#CLOCK} already gives the field for its own phase, rather than a
 *                  second field for one more bit.
 * @param program   a {@link PartType#PROCESSOR} chip's assembly source, exactly as typed in its
 *                  code editor; empty for everything else and for a chip never programmed. Source
 *                  rather than assembled bytes, so reopening the editor shows what was written —
 *                  {@code design/processor.md} §5.3.
 * @param machine   a {@link PartType#PROCESSOR} chip's whole running state — its 256 bytes of
 *                  memory plus {@code A}, {@code PC}, both flags, the halt cause and both latched
 *                  output ports — packed by {@code Machine.pack()} into thirty-three longs (§5.3);
 *                  empty for everything else and for a chip with no program loaded. {@code held}
 *                  doubles once more here, the same reuse {@code memory} and {@code CLOCK} already
 *                  established: whether the {@code run} pad read high as of the last refresh, the
 *                  edge-detection bit the rising-edge restart in {@code Machine#restarted} needs.
 */
public record WirePart(BlockPos cell, Direction face, int u, int v, int rotation,
                       PartType type, int outputs, boolean held, long releaseAt,
                       Circuit circuit, List<Long> gateState, String name, long memory,
                       String program, List<Long> machine) {

    /** By name, so a save stays readable and survives the enum being reordered. */
    public static final Codec<PartType> TYPE_CODEC = Codec.STRING.xmap(
            name -> PartType.valueOf(name.toUpperCase(Locale.ROOT)),
            type -> type.name().toLowerCase(Locale.ROOT));

    /**
     * Rule 60's own lesson, applied here before a stale save could repeat it: every plate saved
     * before this fix has {@code gate_state} as a single number, not a list — reading it as a list
     * outright would refuse every one of them the moment they next ticked. Tried as a list first
     * (what every plate saved after this fix has); a bare number that fails that read falls back
     * and becomes the list's one low word, which is exactly what the number always meant.
     */
    private static final Codec<List<Long>> GATE_STATE_CODEC = Codec.either(Codec.LONG.listOf(), Codec.LONG)
            .xmap(either -> either.map(list -> list, single -> single == 0L ? List.of() : List.of(single)),
                    com.mojang.datafixers.util.Either::left);

    public static final Codec<WirePart> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    BlockPos.CODEC.fieldOf("cell").forGetter(WirePart::cell),
                    Direction.CODEC.fieldOf("face").forGetter(WirePart::face),
                    Codec.INT.fieldOf("u").forGetter(WirePart::u),
                    Codec.INT.fieldOf("v").forGetter(WirePart::v),
                    Codec.INT.fieldOf("rotation").forGetter(WirePart::rotation),
                    TYPE_CODEC.fieldOf("type").forGetter(WirePart::type),
                    Codec.INT.optionalFieldOf("outputs", 0).forGetter(WirePart::outputs),
                    Codec.BOOL.optionalFieldOf("held", false).forGetter(WirePart::held),
                    Codec.LONG.optionalFieldOf("release_at", 0L).forGetter(WirePart::releaseAt),
                    CircuitPlate.CODEC.optionalFieldOf("circuit", Circuit.empty())
                            .forGetter(WirePart::circuit),
                    GATE_STATE_CODEC.optionalFieldOf("gate_state", List.of()).forGetter(WirePart::gateState),
                    Codec.STRING.optionalFieldOf("name", "").forGetter(WirePart::name),
                    Codec.LONG.optionalFieldOf("memory", 0L).forGetter(WirePart::memory),
                    Codec.STRING.optionalFieldOf("program", "").forGetter(WirePart::program),
                    Codec.LONG.listOf().optionalFieldOf("machine", List.of()).forGetter(WirePart::machine)
            ).apply(instance, WirePart::new));

    /** A part as it is first placed: nothing driven, nothing held, no stored gate state. */
    public static WirePart placed(BlockPos cell, Direction face, int u, int v, int rotation,
                                  PartType type, Circuit circuit, String name) {
        return new WirePart(cell, face, u, v, Math.floorMod(rotation, PartFootprint.ROTATIONS),
                type, 0, false, 0L, circuit, List.of(), name, 0L, "", List.of());
    }

    /**
     * A part with an out-of-range rotation is turned back into range rather than refused.
     *
     * <p>A save file and a packet are untrusted input, and the record's own contract — {@code
     * WireTrace} takes the same view of a malformed pixel mask — is that a bad value costs the
     * thing itself, never the chunk it arrived in.
     */
    public WirePart {
        rotation = Math.floorMod(rotation, PartFootprint.ROTATIONS);
        u = Math.clamp(u, 0, FaceBasis.GRID - 1);
        v = Math.clamp(v, 0, FaceBasis.GRID - 1);
    }

    /** The block this part is fastened to — it comes down when that does. */
    public BlockPos support() {
        return cell.relative(face);
    }

    public int spanU() {
        return PartFootprint.spanU(type.width(), type.height(), rotation);
    }

    public int spanV() {
        return PartFootprint.spanV(type.width(), type.height(), rotation);
    }

    /** Whether that pixel of this face is part of this component at all. */
    public boolean covers(int atU, int atV) {
        return atU >= u && atU < u + spanU() && atV >= v && atV < v + spanV();
    }

    public boolean isOn(BlockPos otherCell, Direction otherFace) {
        return cell.equals(otherCell) && face == otherFace;
    }

    /**
     * How far a pixel of this face is from this part, in pixels — zero when it is on it.
     *
     * <p><strong>Because pointing at a part is not pointing at a pixel.</strong> A switch is three
     * pixels square: a target five centimetres across on a face a metre wide, which at any real
     * distance is a fifth of a degree of aim. {@code WireAim} exists in the first place because
     * that is roughly ten times finer than this game asks anywhere else — and interaction went
     * straight back to demanding it, so the smallest part in the set felt like it simply did not
     * work.
     *
     * <p>Chebyshev rather than Manhattan, so the margin around a part is a square: aiming past a
     * corner is exactly as forgiving as aiming past an edge, which is what a player expects from
     * something drawn as a rectangle.
     */
    public int distanceTo(int atU, int atV) {
        int across = Math.max(0, Math.max(u - atU, atU - (u + spanU() - 1)));
        int down = Math.max(0, Math.max(v - atV, atV - (v + spanV() - 1)));
        return Math.max(across, down);
    }

    /** Where a pad of this part sits on the face, once the part has been turned. */
    public int padU(PartType.Pad pad) {
        return u + PartFootprint.offsetU(type.width(), type.height(), rotation, pad.u(), pad.v());
    }

    /** @see #padU */
    public int padV(PartType.Pad pad) {
        return v + PartFootprint.offsetV(type.width(), type.height(), rotation, pad.u(), pad.v());
    }

    /** The exact pixel a run has to occupy to land on that pad. */
    public WirePixel padPixel(PartType.Pad pad) {
        return new WirePixel(cell.getX(), cell.getY(), cell.getZ(), Faces.of(face),
                padU(pad), padV(pad));
    }

    /** The pad at that pixel of this face, or null — how a run finds what it has landed on. */
    public PartType.Pad padAt(int atU, int atV) {
        for (PartType.Pad pad : type.pads()) {
            if (padU(pad) == atU && padV(pad) == atV) {
                return pad;
            }
        }
        return null;
    }

    /**
     * Pixels a trace may <strong>not</strong> occupy: the housing, but not the pads.
     *
     * <p>You cannot route a wire under a component and you can very much route one onto its pad.
     * One predicate for both, so the router's ghost, the coil's click and the refusal all agree
     * (rule 20) — three separate answers to "is this pixel free" would be three chances to
     * disagree, and disagreement here is invisible until a run silently fails to connect.
     */
    public boolean blocks(int atU, int atV) {
        return covers(atU, atV) && padAt(atU, atV) == null;
    }

    /**
     * Whether this part is currently joining its two pads.
     *
     * <p>A fuse conducts until it has blown. Everything else joins nothing — a gate reads one side
     * and drives the other, which is a decision rather than a piece of conductor.
     */
    public boolean conducts() {
        return type.isBridge() && !held;
    }

    /**
     * The pad on the other side of a bridge, given one of them — or null.
     *
     * <p>This is what lets a circuit run <em>through</em> a component instead of stopping at it,
     * and it is why a blown fuse breaks a run in two rather than merely looking sad.
     */
    public WirePixel bridgePartner(int atU, int atV) {
        if (!conducts()) {
            return null;
        }
        PartType.Pad here = padAt(atU, atV);
        if (here == null) {
            return null;
        }
        for (PartType.Pad other : type.pads()) {
            if (!other.equals(here)) {
                return padPixel(other);
            }
        }
        return null;
    }

    /** Whether that driving pad is putting something out right now. */
    public boolean driving(PartType.Pad pad) {
        List<PartType.Pad> driving = type.outputs();
        int index = driving.indexOf(pad);
        return index >= 0 && (outputs & (1 << index)) != 0;
    }

    /** True when anything at all on this part is driving — what the renderer lights. */
    public boolean drivingAnything() {
        return outputs != 0;
    }

    /** Every pixel the housing occupies, for drawing and for collision. */
    public List<int[]> body() {
        List<int[]> pixels = new ArrayList<>(spanU() * spanV());
        for (int du = 0; du < spanU(); du++) {
            for (int dv = 0; dv < spanV(); dv++) {
                pixels.add(new int[] {u + du, v + dv});
            }
        }
        return pixels;
    }

    /** Whether this part's footprint touches another's — placement refuses when it does. */
    public boolean overlaps(WirePart other) {
        return isOn(other.cell, other.face)
                && u < other.u + other.spanU() && other.u < u + spanU()
                && v < other.v + other.spanV() && other.v < v + spanV();
    }

    /** Same component, same place — the identity a chunk edit replaces by. */
    public boolean sameSlotAs(WirePart other) {
        return isOn(other.cell, other.face) && u == other.u && v == other.v;
    }

    public WirePart withOutputs(int bits) {
        return new WirePart(cell, face, u, v, rotation, type, bits, held, releaseAt, circuit, gateState, name, memory,
                program, machine);
    }

    public WirePart withHeld(boolean nowHeld, long until) {
        return new WirePart(cell, face, u, v, rotation, type, outputs, nowHeld, until, circuit, gateState, name, memory,
                program, machine);
    }

    public WirePart withCircuit(Circuit next) {
        return new WirePart(cell, face, u, v, rotation, type, outputs, held, releaseAt, next, gateState, name, memory,
                program, machine);
    }

    /** Stores new internal gate state for a plate whose circuit has feedback loops. */
    public WirePart withGateState(List<Long> words) {
        return new WirePart(cell, face, u, v, rotation, type, outputs, held, releaseAt, circuit, words, name, memory,
                program, machine);
    }

    /** The same part with its custom name changed — a plate only, but harmless on anything else. */
    public WirePart withName(String next) {
        return new WirePart(cell, face, u, v, rotation, type, outputs, held, releaseAt, circuit, gateState, next, memory,
                program, machine);
    }

    /** Stores a {@link PartType#RAM} chip's sixteen cells after a write — harmless on anything else. */
    public WirePart withMemory(long next) {
        return new WirePart(cell, face, u, v, rotation, type, outputs, held, releaseAt, circuit, gateState, name, next,
                program, machine);
    }

    /**
     * A {@link PartType#PROCESSOR} chip freshly programmed: the new source, and a matching
     * freshly-reset {@code machine} — {@code design/processor.md} §5.3's "reset when program
     * changes." The two always change together, so there is one wither for both rather than two
     * that could be called out of step and leave a program's bytes paired with another program's
     * running state.
     */
    public WirePart withProgram(String nextProgram, List<Long> freshMachine) {
        return new WirePart(cell, face, u, v, rotation, type, outputs, held, releaseAt, circuit, gateState, name,
                memory, nextProgram, freshMachine);
    }

    /** Stores a {@link PartType#PROCESSOR} chip's running state after a refresh; harmless elsewhere. */
    public WirePart withMachine(List<Long> next) {
        return new WirePart(cell, face, u, v, rotation, type, outputs, held, releaseAt, circuit, gateState, name,
                memory, program, next);
    }

    /**
     * The same part turned one quarter clockwise, staying on the face.
     *
     * <p>Returns {@code this} when the turn would push it off the edge: a part half off a face is
     * not a part that wraps round the corner, and a rigid component does not bend. Refusing is the
     * only honest answer, and it is visible — the part simply does not move.
     */
    public WirePart turned() {
        int next = Math.floorMod(rotation + 1, PartFootprint.ROTATIONS);
        if (!PartFootprint.fits(u, v, type.width(), type.height(), next)) {
            return this;
        }
        return new WirePart(cell, face, u, v, next, type, outputs, held, releaseAt, circuit, gateState, name, memory,
                program, machine);
    }
}
