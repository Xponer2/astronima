package play.xponer.astronima.telescope;

import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import play.xponer.astronima.sim.optics.LineOfSight;

/**
 * The real raycast a telescope's occlusion check needs — an angled probe through the world, not
 * {@code SkyExposure}'s straight-up scan (that answers a different, narrower question: sunlight on
 * a fixed block, never an arbitrary look direction). Works identically for a {@code ServerLevel}
 * (the server's authoritative capture check) and a {@code ClientLevel} (the eyepiece's own live
 * readout), since {@code Level#clip} is declared once on {@code BlockGetter} for both — one probe,
 * read from two places, per rule 46.
 *
 * <p>This is the one honest render/world risk this feature carries (rule 50): the decision itself
 * ({@link LineOfSight#isClear}) is proven MC-free; only the act of casting the ray needs a real
 * {@code Level} and is exercised by playing rather than by a unit test.
 */
public final class TelescopeOcclusion {

    /** Far enough that no realistic build blocks a true view of open sky before the probe gives
     * up and calls it clear. */
    private static final double PROBE_DISTANCE = 320.0;

    /**
     * Whether the sky is genuinely visible looking from {@code from} along {@code direction} — a
     * real raycast for "anything solid in the way," ANDed with the level's own current weather
     * (a storm hides the sky exactly as an unrelated roof does).
     */
    public static boolean isClear(Level level, Vec3 from, Vec3 direction) {
        Vec3 to = from.add(direction.normalize().scale(PROBE_DISTANCE));
        BlockHitResult result = level.clip(new ClipContext(
                from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        boolean hitSomething = result.getType() != HitResult.Type.MISS;
        return LineOfSight.isClear(hitSomething, !level.isRaining());
    }

    private TelescopeOcclusion() {}
}
