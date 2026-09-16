package play.xponer.astronima.client.render;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import play.xponer.astronima.registry.ModAttachments;
import play.xponer.astronima.wire.WirePart;

import java.util.ArrayList;
import java.util.List;

/**
 * The wire-layer parts near the camera, found in one place.
 *
 * <p>Two things want this list and they want it at different rates — the renderer needs it every
 * frame so a part appears the instant it is placed, and the charge animation needs it on its own
 * slower timer. One method, two cadences: sharing the <em>walk</em> is what matters, because two
 * walks would be two chances for the picture and the animation to disagree about what is on the
 * wall, which is the fault {@link TerminalScan} was written to end for blocks.
 *
 * <p>Never loads or generates a chunk: asking for terrain while drawing it is how a frame turns
 * into a world-generation stall.
 */
public final class PartScan {

    /** Every part within that many chunks of the given position. */
    public static List<WirePart> near(ClientLevel level, BlockPos around, int chunkRadius) {
        int centreX = SectionPos.blockToSectionCoord(around.getX());
        int centreZ = SectionPos.blockToSectionCoord(around.getZ());
        List<WirePart> found = new ArrayList<>();
        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                ChunkAccess chunk = level.getChunk(centreX + dx, centreZ + dz,
                        ChunkStatus.FULL, false);
                if (chunk == null) {
                    continue;
                }
                found.addAll(chunk.getData(ModAttachments.WIRES.get()).parts());
            }
        }
        return found;
    }

    private PartScan() {}
}
