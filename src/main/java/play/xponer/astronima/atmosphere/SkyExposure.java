package play.xponer.astronima.atmosphere;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LightLayer;
import play.xponer.astronima.registry.ModDimensions;
import play.xponer.astronima.sim.sky.SkyEventOverride;

/**
 * How much sun is falling on a block, 0 to 1.
 *
 * <p>One authority, because two machines now depend on it and they must never disagree. The
 * retort's mirror and the solar array are asking exactly the same physical question — <em>can
 * this thing see the sun right now</em> — and a player who roofs both over expects both to
 * stop. Two copies of this logic would drift the first time one of them was tuned, and the
 * symptom would be one machine working under a ceiling that stopped the other.
 */
public final class SkyExposure {

    /** How far up to look for something in the way before trusting the light engine. */
    private static final int COVER_SCAN = 64;

    /** Same seed {@code AsteroidSkyRenderer} draws the transiting body with — one schedule, read
     * from two places, so what a player sees crossing the Sun and what the retort's own gauge
     * does can never disagree. */
    private static final long OCCULTATION_SEED = 20260811L;

    /**
     * Sun on the block above {@code pos}, 0 to 1.
     *
     * <p>Sky access and daylight, asked of the level rather than assumed, because both are
     * things the player changes: roofing a machine over is a mistake they can make, and night
     * is a condition they have to plan around.
     */
    public static double sunlightAt(ServerLevel level, BlockPos pos) {
        if (!hasClearSky(level, pos)) {
            return 0;
        }
        // Brightness of the sky itself, so the asteroid's day drives the machine rather than
        // a separate clock nobody can see.
        double raw = Math.clamp(level.getSkyDarken() >= 4 ? 0.0
                : level.getBrightness(LightLayer.SKY, pos.above()) / 15.0, 0.0, 1.0);

        // design/sky.md §3's occultation event: the one sky event this design calls "mechanical"
        // — the machine has to genuinely dim, not just the picture. Gated to the asteroid
        // dimension specifically (never guessed): every other dimension, including a vanilla
        // overworld the retort or array is somehow placed in, must read exactly as it did before
        // this event existed. Resolved through SkyEventOverride, not OccultationSchedule
        // directly, so a `/astronima sky occultation <depth>` test override dims this exactly
        // the same way it dims what the player sees on screen.
        if (level.dimension() == ModDimensions.ASTEROID_LEVEL) {
            double depth = SkyEventOverride.resolveOccultationDepth(level.getGameTime(), OCCULTATION_SEED);
            raw *= 1.0 - depth;
        }
        return raw;
    }

    /**
     * Whether anything is between this block and the sky.
     *
     * <p>Scanned directly rather than asked of {@code canSeeSky} alone, because the heightmap
     * behind that call does not update in the same tick a block is placed: laying a plate
     * straight onto a mirror left the retort reporting full sun and happily running, which is
     * exactly the mistake a player makes and exactly the moment the machine has to notice. The
     * scan answers immediately; {@code canSeeSky} still backs it up for anything buried deeper
     * than the scan reaches.
     */
    public static boolean hasClearSky(ServerLevel level, BlockPos pos) {
        int top = Math.min(level.getMaxY(), pos.getY() + COVER_SCAN);
        for (int y = pos.getY() + 1; y <= top; y++) {
            BlockPos above = new BlockPos(pos.getX(), y, pos.getZ());
            if (!level.getBlockState(above).propagatesSkylightDown()) {
                return false;
            }
        }
        return level.canSeeSky(pos.above());
    }

    private SkyExposure() {}
}
