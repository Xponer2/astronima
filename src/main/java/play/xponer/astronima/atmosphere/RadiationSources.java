package play.xponer.astronima.atmosphere;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import play.xponer.astronima.block.entity.RtgBlockEntity;
import play.xponer.astronima.sim.rad.RadiationDose;
import play.xponer.astronima.sim.sky.SkyEventOverride;

/**
 * Every real gamma source that can reach a position right now (design/radiation.md §4): the
 * sun during a flare, and every RTG within reach. One authority, so the Geiger counter's
 * chat/HUD readout and the physiology tick that actually harms the player can never disagree
 * (rule 16) — both call this, neither computes its own copy.
 */
public final class RadiationSources {

    /** Same seed every flare-reading call site in this mod already duplicates — see
     * {@code AtmosphereEvents.FLARE_SEED}'s own doc for why there is no shared constant to
     * import instead (established, if imperfect, convention). Must match exactly. */
    private static final long FLARE_SEED = 20260810L;

    /** How far to look for an RTG, blocks. Bounded on purpose: beyond this, the real
     * inverse-square falloff already puts a source's dose rate below what a full day of
     * continuous, unbroken exposure would need to reach even the mildest clinical threshold —
     * the cutoff costs no real gameplay case, only the computation of checking further. */
    private static final int RTG_SEARCH_RADIUS = 8;

    /**
     * Total gamma dose rate at {@code observer}, Sv/h — every flare and every RTG within reach,
     * combined. {@code observerBlock} is only used for the flare's own sky check; the RTG
     * distance and line-of-sight use the precise {@code observer} position.
     */
    public static double totalGammaSvPerH(ServerLevel level, BlockPos observerBlock, Vec3 observer) {
        double total = RadiationDose.fromFlare(
                SkyEventOverride.resolveFlareIntensity(level.getGameTime(), FLARE_SEED),
                SkyExposure.hasClearSky(level, observerBlock));

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -RTG_SEARCH_RADIUS; dx <= RTG_SEARCH_RADIUS; dx++) {
            for (int dy = -RTG_SEARCH_RADIUS; dy <= RTG_SEARCH_RADIUS; dy++) {
                for (int dz = -RTG_SEARCH_RADIUS; dz <= RTG_SEARCH_RADIUS; dz++) {
                    cursor.setWithOffset(observerBlock, dx, dy, dz);
                    if (!(level.getBlockEntity(cursor) instanceof RtgBlockEntity)) {
                        continue;
                    }
                    Vec3 source = Vec3.atCenterOf(cursor);
                    double distance = observer.distanceTo(source);
                    boolean clear = Shielding.lineOfSightClear(level, observer, source);
                    total += RadiationDose.fromPointSource(
                            RadiationDose.RTG_REFERENCE_SV_PER_H_AT_1_BLOCK, distance, clear);
                }
            }
        }
        return total;
    }

    private RadiationSources() {}
}
