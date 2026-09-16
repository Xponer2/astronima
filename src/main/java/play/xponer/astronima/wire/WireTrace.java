package play.xponer.astronima.wire;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import play.xponer.astronima.sim.circuit.ConductorMaterial;
import play.xponer.astronima.sim.circuit.WireGauge;
import play.xponer.astronima.sim.wire.FaceBasis;
import play.xponer.astronima.sim.wire.PixelMask;
import play.xponer.astronima.sim.wire.WirePixel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * One colour of trace on one face: which pixels it occupies, and what it is made of.
 *
 * <p>The storage unit of the wire layer. A face can hold several of these — one per colour —
 * which is what lets a wall carry a bundle and two runs cross without joining.
 *
 * <p>The pixels live in a {@link PixelMask}, so a face costs thirty-two bytes whether it carries
 * one pixel or all two hundred and fifty-six. Material is per trace rather than per pixel: a
 * length of cable is one cable, and letting each pixel be a different metal would model something
 * nobody builds while multiplying the storage by sixteen.
 */
public record WireTrace(BlockPos cell, Direction face, DyeColor colour,
                        ConductorMaterial material, WireGauge gauge, double temperatureK,
                        PixelMask mask) {

    /** By name, so the save file stays readable and survives the enum being reordered. */
    public static final Codec<ConductorMaterial> MATERIAL_CODEC = Codec.STRING.xmap(
            name -> ConductorMaterial.valueOf(name.toUpperCase(Locale.ROOT)),
            material -> material.name().toLowerCase(Locale.ROOT));

    /**
     * By name, and <strong>optional</strong>: every trace written before the gauge existed was
     * 4 mm2 because one line of code said so, and must keep being exactly that after the update.
     */
    public static final Codec<WireGauge> GAUGE_CODEC = Codec.STRING.xmap(
            WireGauge::byId, WireGauge::id);

/**
     * What a wire nobody is using sits at, K.
     *
     * <p>The asteroid's own background, the same figure {@code Cooling} quotes for a run with no
     * room around it. A trace in a warm habitat is relaxed toward the room instead; this is only
     * the value a brand-new or freshly-loaded trace starts from.
     */
    public static final double REST_K = play.xponer.astronima.sim.circuit.Cooling.VACUUM_AMBIENT_K;

    public static final Codec<WireTrace> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    BlockPos.CODEC.fieldOf("cell").forGetter(WireTrace::cell),
                    Direction.CODEC.fieldOf("face").forGetter(WireTrace::face),
                    DyeColor.CODEC.fieldOf("colour").forGetter(WireTrace::colour),
                    MATERIAL_CODEC.fieldOf("material").forGetter(WireTrace::material),
                    GAUGE_CODEC.optionalFieldOf("gauge", WireGauge.DEFAULT)
                            .forGetter(WireTrace::gauge),
                    // Optional and defaulted to cold: a trace saved before the wire had a
                    // temperature was, as far as anything could tell, at rest.
                    Codec.DOUBLE.optionalFieldOf("temperature_k", REST_K)
                            .forGetter(WireTrace::temperatureK),
                    Codec.LONG.listOf().fieldOf("pixels")
                            .forGetter(trace -> boxed(trace.mask.words()))
            ).apply(instance, (cell, face, colour, material, gauge, temperature, words) ->
                    new WireTrace(cell, face, colour, material, gauge, temperature,
                            unbox(words))));


    private static List<Long> boxed(long[] words) {
        List<Long> list = new ArrayList<>(words.length);
        for (long word : words) {
            list.add(word);
        }
        return list;
    }

    /**
     * Rebuilds a mask from stored words, tolerating a wrong length.
     *
     * <p>A save file is untrusted input by the time anyone has edited it or a version has
     * changed. Refusing loudly here would take a whole chunk down over one bad trace, so a
     * malformed mask reads as an empty one — the wire is gone, the world is not.
     */
    private static PixelMask unbox(List<Long> words) {
        if (words.size() != PixelMask.WORDS) {
            return PixelMask.empty();
        }
        long[] raw = new long[PixelMask.WORDS];
        for (int i = 0; i < PixelMask.WORDS; i++) {
            raw[i] = words.get(i);
        }
        return PixelMask.of(raw);
    }

    public static WireTrace empty(BlockPos cell, Direction face, DyeColor colour,
                                  ConductorMaterial material, WireGauge gauge) {
        return new WireTrace(cell, face, colour, material, gauge, REST_K, PixelMask.empty());
    }

    /**
     * The same trace with no conductor left on it.
     *
     * <p>{@link WireChunk#with} drops an empty trace rather than storing it, so this is how one
     * removes itself — there is no "delete" that could disagree with the emptiness rule.
     */
    public WireTrace cleared() {
        return new WireTrace(cell, face, colour, material, gauge, temperatureK,
                PixelMask.empty());
    }

    /** The same trace at a new temperature. */
    public WireTrace at(double nowK) {
        return new WireTrace(cell, face, colour, material, gauge, nowK, mask);
    }

    /**
     * How cooked its insulation looks, 0..1 — what the renderer paints and the meter warns on.
     *
     * <p>Against the run's own resting temperature rather than against absolute zero, so a wire
     * in a warm habitat is not permanently reported as slightly scorched.
     */
    public double scorch() {
        return play.xponer.astronima.sim.circuit.Scorch.of(temperatureK, REST_K);
    }

    /**
     * Whether this trace <em>ends</em> at that pixel rather than merely passing over it.
     *
     * <p>The difference between a wire landed on a screw terminal and a wire running past one, and
     * it is not a nicety. A machine's terminal strip is a row of studs, so a power run approaching
     * along that row crosses the enable stud on its way to the power stud — and under "any trace
     * occupying the pixel", every machine in the game was reading its own power cable as somebody
     * having wired up its enable. The feature had been dead since it shipped and no test could see
     * it, because both halves were doing exactly what they said.
     *
     * <p>Tapping a bus still works, and works the way real wiring does: the drop <em>ends</em> at
     * the stud while the bus goes past. Only a run driven straight through a terminal fails to
     * connect, which is the case that was never a connection.
     */
    public boolean endsAt(int u, int v) {
        if (!has(u, v)) {
            return false;
        }
        int neighbours = 0;
        if (has(u - 1, v)) {
            neighbours++;
        }
        if (has(u + 1, v)) {
            neighbours++;
        }
        if (has(u, v - 1)) {
            neighbours++;
        }
        if (has(u, v + 1)) {
            neighbours++;
        }
        return neighbours <= 1;
    }

    public boolean has(int u, int v) {
        return mask.has(u, v);
    }

    public WireTrace with(int u, int v) {
        return new WireTrace(cell, face, colour, material, gauge, temperatureK, mask.with(u, v));
    }

    public WireTrace without(int u, int v) {
        return new WireTrace(cell, face, colour, material, gauge, temperatureK,
                mask.without(u, v));
    }

    public boolean isEmpty() {
        return mask.isEmpty();
    }

    /** How much conductor is on this face — what it cost, and what pulling it up returns. */
    public int pixelCount() {
        return mask.count();
    }

    /** Whether this is the same colour on the same surface, whatever it is made of. */
    public boolean sameTraceAs(BlockPos otherCell, Direction otherFace, DyeColor otherColour) {
        return cell.equals(otherCell) && face == otherFace && colour == otherColour;
    }

    /** The block every pixel of this trace is fastened to. */
    public BlockPos support() {
        return cell.relative(face);
    }

    /** Each occupied pixel as an addressable point, in a stable order. */
    public List<WirePixel> pixels() {
        List<WirePixel> found = new ArrayList<>(mask.count());
        for (int packed : mask.occupied()) {
            found.add(new WirePixel(cell.getX(), cell.getY(), cell.getZ(),
                    Faces.of(face), PixelMask.uOf(packed), PixelMask.vOf(packed)));
        }
        return found;
    }

    /** Where a pixel sits inside its cell, 0..1 on each axis — what the renderer draws at. */
    public static double[] centreOf(WirePixel pixel, double standoffBlocks) {
        var face = pixel.face();
        var uAxis = FaceBasis.uAxis(face);
        var vAxis = FaceBasis.vAxis(face);
        double du = (pixel.u() + 0.5) / FaceBasis.GRID - 0.5;
        double dv = (pixel.v() + 0.5) / FaceBasis.GRID - 0.5;
        double out = 0.5 - standoffBlocks;
        return new double[] {
                0.5 + uAxis.dx() * du + vAxis.dx() * dv + face.dx() * out,
                0.5 + uAxis.dy() * du + vAxis.dy() * dv + face.dy() * out,
                0.5 + uAxis.dz() * du + vAxis.dz() * dv + face.dz() * out};
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof WireTrace trace
                && cell.equals(trace.cell) && face == trace.face && colour == trace.colour
                && material == trace.material && gauge == trace.gauge
                && Double.compare(temperatureK, trace.temperatureK) == 0
                && mask.equals(trace.mask);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(new Object[] {cell, face, colour, material, gauge, temperatureK,
                mask});
    }
}
