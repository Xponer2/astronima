package play.xponer.astronima.astra;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import play.xponer.astronima.Astronima;
import play.xponer.astronima.sim.astra.AstraField;
import play.xponer.astronima.sim.astra.AstraGrid;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-dimension registry of real astra depletion — design/astra-extraction-loop.md §1: the field
 * remembers what was taken, per real grid cell ({@link AstraGrid}), and refills it lazily on read
 * rather than ticking every cell every game tick. Mirrors {@code Atmosphere}'s own established
 * shape (a {@link SavedData} keyed registry, materialised/queried on demand) at a smaller size,
 * because {@link AstraField#refilled} is already an exact closed-form function of elapsed time —
 * there is no per-tick simulation step to run here at all, only a lookup and an exponential.
 *
 * <p>Deliberately holds no {@link ServerLevel} reference (unlike {@code Atmosphere}, which needs
 * one for its own per-tick room queries): every method here takes the caller's own {@code
 * nowGameTime}, so the class itself never needs Minecraft's clock and its codec never needs a
 * level either (rule 46 — one fact, the field's own baseline, is never stored here at all; it is
 * {@code AstraFieldGeometry.baselineDensity}, supplied fresh by the caller every time).
 */
public final class AstraFieldStorage extends SavedData {

    private static final int SAVE_VERSION = 1;

    public static final SavedDataType<AstraFieldStorage> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(Astronima.MODID, "astra_field"),
            level -> new AstraFieldStorage(),
            level -> codec());

    private final Long2ObjectMap<Cell> cells = new Long2ObjectOpenHashMap<>();

    private AstraFieldStorage() {}

    public static AstraFieldStorage get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    /**
     * The real density at {@code (x, y, z)} right now: exactly {@code baselineDensity} if this
     * cell has never been swept, or {@code AstraField.refilled} from whatever it was last left at
     * otherwise. Never mutates — a plain read.
     */
    public double densityAt(int x, int y, int z, double baselineDensity, long nowGameTime) {
        Cell cell = cells.get(AstraGrid.cellKey(x, y, z));
        if (cell == null) {
            return baselineDensity;
        }
        double elapsedSeconds = Math.max(0.0, nowGameTime - cell.lastUpdateGameTime) / 20.0;
        return AstraField.refilled(cell.density, baselineDensity, elapsedSeconds);
    }

    /**
     * Sweeps the real cell at {@code (x, y, z)}: reads its current density exactly like {@link
     * #densityAt}, applies {@link AstraField#densityAfterSweep}, persists the result, and returns
     * what {@link AstraField#swept} says was actually collected — the same amount the ground lost,
     * so a caller never has to keep the two numbers in sync by hand.
     */
    public double sweep(int x, int y, int z, double baselineDensity, long nowGameTime) {
        double current = densityAt(x, y, z, baselineDensity, nowGameTime);
        double collected = AstraField.swept(current);
        double after = AstraField.densityAfterSweep(current);
        cells.put(AstraGrid.cellKey(x, y, z), new Cell(after, nowGameTime));
        setDirty();
        return collected;
    }

    /**
     * Draws up to {@code amountRequested} from the real cell at {@code (x, y, z)} — design/
     * astra-ritual-grammar.md's own live draw, a genuinely different verb from {@link #sweep}:
     * a ritual asks for a specific amount per tick rather than a fixed fraction of whatever is
     * there. Never draws more than is actually present (clamped, never negative), persists the
     * result, and returns exactly what was taken — the same "return what actually left the
     * ground" contract {@link #sweep} already keeps.
     */
    public double drawAt(int x, int y, int z, double baselineDensity, double amountRequested, long nowGameTime) {
        double current = densityAt(x, y, z, baselineDensity, nowGameTime);
        double drawn = Math.min(Math.max(0.0, amountRequested), current);
        cells.put(AstraGrid.cellKey(x, y, z), new Cell(current - drawn, nowGameTime));
        setDirty();
        return drawn;
    }

    /** One real cell's own persisted state — the density it was left at, and when. Nothing about
     *  the cell's own baseline is stored (see the class doc's rule-46 note). */
    private record Cell(double density, long lastUpdateGameTime) {}

    /** A cell plus the key identifying it, for a flat list-shaped save format — the same
     *  key-carrying-record shape {@code Atmosphere}'s own {@code SavedRoom} uses. */
    private record SavedCell(long key, double density, long lastUpdateGameTime) {
        static final Codec<SavedCell> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.fieldOf("key").forGetter(SavedCell::key),
                Codec.DOUBLE.fieldOf("density").forGetter(SavedCell::density),
                Codec.LONG.fieldOf("last_update_game_time").forGetter(SavedCell::lastUpdateGameTime)
        ).apply(instance, SavedCell::new));
    }

    private List<SavedCell> snapshot() {
        List<SavedCell> out = new ArrayList<>(cells.size());
        for (var entry : cells.long2ObjectEntrySet()) {
            Cell cell = entry.getValue();
            out.add(new SavedCell(entry.getLongKey(), cell.density, cell.lastUpdateGameTime));
        }
        return out;
    }

    private static AstraFieldStorage restore(List<SavedCell> saved) {
        AstraFieldStorage storage = new AstraFieldStorage();
        for (SavedCell savedCell : saved) {
            storage.cells.put(savedCell.key, new Cell(savedCell.density, savedCell.lastUpdateGameTime));
        }
        return storage;
    }

    /** Exposed (not merely used by {@link #TYPE}) so a reload proof can do the same manual
     *  encode-then-decode round trip {@code storedAirSurvivesAReload} already does for a block
     *  entity — design/astra-extraction-loop.md §4's own headline claim, applied to a
     *  {@link SavedData} instead of a block entity's NBT. */
    public static Codec<AstraFieldStorage> codec() {
        return RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.optionalFieldOf("version", SAVE_VERSION).forGetter(storage -> SAVE_VERSION),
                SavedCell.CODEC.listOf().fieldOf("cells").forGetter(AstraFieldStorage::snapshot)
        ).apply(instance, (version, saved) -> restore(saved)));
    }
}
