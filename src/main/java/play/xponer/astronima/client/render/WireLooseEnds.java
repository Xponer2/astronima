package play.xponer.astronima.client.render;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import play.xponer.astronima.registry.ModAttachments;
import play.xponer.astronima.sim.wire.PixelGeometry;
import play.xponer.astronima.sim.wire.WirePixel;
import play.xponer.astronima.wire.Faces;
import play.xponer.astronima.wire.WireTrace;

import java.util.ArrayList;
import java.util.List;

/**
 * Where a run stops, marked so it can be picked up again.
 *
 * <p><strong>Written for a specific complaint.</strong> Break the block a run was fastened to and
 * the run comes down with it — correct, and reported as a dead end: <em>"цепочка исчезает, и как
 * обратно присоединить?"</em>. The wire that survives is still there, but its new end is one
 * pixel wide somewhere in a wall, and finding it by eye is not a reasonable thing to ask.
 *
 * <p>So while the coil is in hand, every <em>end</em> of a matching run nearby is marked. An end
 * is a pixel with fewer than two neighbours in its own circuit: the tip of a run, or the stump
 * left where one was cut. Junctions and the middles of runs are not marked, because they are not
 * where you would join.
 *
 * <p>Only the coil's current colour, and only nearby — marking every stump in a base would be
 * decoration rather than an answer.
 */
public final class WireLooseEnds {

    /** How far around the player to look, in blocks. */
    private static final int RANGE = 8;

    /** A pixel with at most this many neighbours is an end rather than a middle. */
    private static final int END_DEGREE = 1;

    /**
     * Every joinable end of that colour within reach.
     *
     * <p>Scanned rather than cached: the set changes whenever anything is laid or broken, and a
     * cache that had to notice both would cost more to keep honest than the scan costs.
     */
    public static List<WirePixel> near(Level level, BlockPos around, DyeColor colour) {
        List<WirePixel> ends = new ArrayList<>();
        int minChunkX = SectionPos.blockToSectionCoord(around.getX() - RANGE);
        int maxChunkX = SectionPos.blockToSectionCoord(around.getX() + RANGE);
        int minChunkZ = SectionPos.blockToSectionCoord(around.getZ() - RANGE);
        int maxChunkZ = SectionPos.blockToSectionCoord(around.getZ() + RANGE);

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                ChunkAccess chunk = level.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
                if (chunk == null) {
                    continue;
                }
                for (WireTrace trace : chunk.getData(ModAttachments.WIRES.get()).traces()) {
                    if (trace.colour() != colour || !isNear(trace.cell(), around)) {
                        continue;
                    }
                    for (WirePixel point : trace.pixels()) {
                        if (degree(level, point, colour) <= END_DEGREE) {
                            ends.add(point);
                        }
                    }
                }
            }
        }
        return ends;
    }

    private static boolean isNear(BlockPos cell, BlockPos around) {
        return Math.abs(cell.getX() - around.getX()) <= RANGE
                && Math.abs(cell.getY() - around.getY()) <= RANGE
                && Math.abs(cell.getZ() - around.getZ()) <= RANGE;
    }

    /** How many pixels of the same circuit this one touches. */
    private static int degree(Level level, WirePixel pixel, DyeColor colour) {
        int found = 0;
        for (WirePixel candidate : PixelGeometry.neighbours(pixel)) {
            if (has(level, candidate, colour)) {
                found++;
                if (found > END_DEGREE) {
                    return found; // enough to know it is not an end
                }
            }
        }
        return found;
    }

    private static boolean has(Level level, WirePixel pixel, DyeColor colour) {
        BlockPos cell = new BlockPos(pixel.x(), pixel.y(), pixel.z());
        ChunkAccess chunk = level.getChunk(SectionPos.blockToSectionCoord(cell.getX()),
                SectionPos.blockToSectionCoord(cell.getZ()), ChunkStatus.FULL, false);
        return chunk != null && chunk.getData(ModAttachments.WIRES.get())
                .trace(cell, Faces.of(pixel.face()), colour)
                .filter(trace -> trace.has(pixel.u(), pixel.v()))
                .isPresent();
    }

    private WireLooseEnds() {}
}
