package play.xponer.astronima.physio;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import play.xponer.astronima.sim.pathogen.Contamination;
import play.xponer.astronima.sim.pathogen.Strain;
import play.xponer.astronima.sim.pathogen.SurfaceLoads;

/**
 * One chunk's worth of {@link SurfaceLoads}, plus the codec that saves and sends it.
 *
 * <p>Deliberately thin. The first version of this class held the map itself, keyed on
 * {@link BlockPos} — and was therefore untestable, because a unit test in this project cannot
 * touch Minecraft at all (rule 1). None of the behaviour worth guarding needs a block position:
 * it is sparse storage that forgets. So the bookkeeping moved to {@code sim/} where it can be
 * proven, and what is left here is an adapter and a serial format.
 *
 * <p>Chunk-scoped rather than world-scoped so it travels and unloads with the terrain it
 * describes, the same way the wire layer does.
 */
public final class SurfaceContamination {

    private final SurfaceLoads loads;

    public SurfaceContamination() {
        this(new SurfaceLoads());
    }

    public SurfaceContamination(SurfaceLoads loads) {
        this.loads = loads;
    }

    public SurfaceLoads loads() {
        return loads;
    }

    public static final MapCodec<SurfaceContamination> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(Entry.CODEC.listOf().optionalFieldOf("surfaces", List.of())
                            .forGetter(SurfaceContamination::entries))
                    .apply(instance, SurfaceContamination::of));

    private record Entry(BlockPos at, String source, double load) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(it -> it.group(
                BlockPos.CODEC.fieldOf("at").forGetter(Entry::at),
                Codec.STRING.fieldOf("source").forGetter(Entry::source),
                Codec.DOUBLE.fieldOf("load").forGetter(Entry::load)).apply(it, Entry::new));
    }

    private List<Entry> entries() {
        List<Entry> out = new ArrayList<>();
        loads.all().forEach((packed, load) ->
                out.add(new Entry(BlockPos.of(packed), load.source().name(), load.load())));
        return out;
    }

    private static SurfaceContamination of(List<Entry> entries) {
        Map<Long, Contamination> map = new HashMap<>();
        for (Entry entry : entries) {
            map.put(entry.at().asLong(),
                    new Contamination(source(entry.source()), entry.load()));
        }
        return new SurfaceContamination(new SurfaceLoads(map));
    }

    private static Strain.Source source(String name) {
        for (Strain.Source candidate : Strain.Source.values()) {
            if (candidate.name().equals(name)) {
                return candidate;
            }
        }
        return Strain.Source.COMMENSAL;
    }

    public Contamination at(BlockPos pos) {
        return loads.at(pos.asLong());
    }

    public void set(BlockPos pos, Contamination load) {
        loads.set(pos.asLong(), load);
    }

    public boolean isEmpty() {
        return loads.isEmpty();
    }

    public boolean decay(double seconds, double halfLife) {
        return loads.decay(seconds, halfLife);
    }
}
