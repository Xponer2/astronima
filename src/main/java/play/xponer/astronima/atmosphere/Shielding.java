package play.xponer.astronima.atmosphere;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * Whether anything solid stands between a gamma source and an observer (design/radiation.md
 * §1.1) — the one Minecraft-touching piece of the radiation model, since it is the only part
 * that has to walk real blocks. Everything downstream of the yes/no answer this produces is
 * pure and lives in {@link play.xponer.astronima.sim.rad.RadiationDose}.
 *
 * <p>Same physical question {@link SkyExposure#hasClearSky} already asks straight up, asked
 * instead along an arbitrary line — a single ordinary block is already dozens of half-value-
 * layers at this model's reference gamma energy, so "is there anything solid on the line" is
 * the real answer here, not a simplification of a richer one (see the design doc for the
 * arithmetic that justifies this rather than a continuous thickness model).
 */
public final class Shielding {

    /** How finely the line between source and observer is sampled, blocks. Fine enough that a
     * 1-block-thick wall is never stepped over. */
    private static final double SAMPLE_STEP = 0.4;

    /**
     * True if nothing solid stands between {@code from} and {@code to}.
     *
     * <p>Samples the segment at {@link #SAMPLE_STEP} intervals rather than a proper voxel
     * traversal — cheap, and a missed corner at worst reports one sample late, which the next
     * step along the same wall catches. This runs at most once per player per radiation tick
     * (design/radiation.md §4), not per frame, so the cost is not worth a more exact algorithm.
     */
    public static boolean lineOfSightClear(ServerLevel level, Vec3 from, Vec3 to) {
        double distance = from.distanceTo(to);
        if (distance <= 0) {
            return true;
        }
        // A source cannot shield itself: for a short enough distance a sample near the `to`
        // end can land inside the source's own block volume (its centre is `to`, but the block
        // itself occupies a whole cube around that point), which would make an emitter falsely
        // block its own line of sight to anything closer than about one block away. Its own
        // occupied block is excluded from the walk for exactly that reason.
        BlockPos sourceBlock = BlockPos.containing(to);
        int steps = Math.max(1, (int) Math.ceil(distance / SAMPLE_STEP));
        for (int i = 1; i < steps; i++) {
            double t = i / (double) steps;
            Vec3 sample = from.lerp(to, t);
            BlockPos pos = BlockPos.containing(sample);
            if (pos.equals(sourceBlock)) {
                continue;
            }
            if (!level.getBlockState(pos).propagatesSkylightDown()) {
                return false;
            }
        }
        return true;
    }

    private Shielding() {}
}
