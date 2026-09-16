package play.xponer.astronima.client.gravity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Reported directly in play: "the head sometimes clips into textures." A real, understood
 * consequence of design/eva-mobility.md §1.7's own accepted boundary — the collision box stays
 * upright and axis-aligned on purpose (§0's own stated reason), which is exactly what leaves the
 * player's *box* safely clear of geometry it is only standing next to. The eye offset used to be
 * fixed straight up, always safely inside that same upright box; now that
 * {@code OrientationCameraPositionMixin}/{@code OrientationEyePositionMixin} rotate it with this
 * player's real facing, a heavy pitch or roll can point that offset sideways or down, out of the
 * box's own safe footprint and into whatever block happens to be next to it — the box itself never
 * moved, only where the eye looks out from did.
 *
 * <p>Fixed the same way vanilla's own third-person camera already solves the same shape of problem
 * (confirmed against this project's own decompiled {@code Camera#getMaxZoom}, rule 2): a real
 * raycast from a point already known safe, pulled back short of whatever it hits rather than
 * trusting the naive offset blindly.
 */
public final class EyeOffsetClip {

    /** Stop just short of a hit rather than exactly on the surface, so the eye never sits flush
     *  against a face (z-fighting, edge flicker). */
    private static final double PULLBACK_FRACTION = 0.95;

    /**
     * Clips a candidate eye position so it never lands inside solid geometry. {@code safeOrigin}
     * must already be known clear (the naive, unrotated eye position — straight up from the feet,
     * always inside the upright box); {@code candidate} is the real, orientation-rotated point
     * this system actually wants.
     */
    public static Vec3 clip(Level level, Entity except, Vec3 safeOrigin, Vec3 candidate) {
        HitResult hit = level.clip(new ClipContext(
                safeOrigin, candidate, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, except));
        if (hit.getType() == HitResult.Type.MISS) {
            return candidate;
        }
        Vec3 hitPoint = hit.getLocation();
        return safeOrigin.add(hitPoint.subtract(safeOrigin).scale(PULLBACK_FRACTION));
    }

    private EyeOffsetClip() {}
}
