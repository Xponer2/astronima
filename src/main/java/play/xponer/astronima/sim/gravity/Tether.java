package play.xponer.astronima.sim.gravity;

/**
 * design/eva-mobility.md §3: a real safety line for the case push-off's own cap does not solve — a
 * player working deliberately at range from any surface. A real damped spring, never a reel or a
 * teleport: force proportional to how far the line has stretched past its own rest length, applied
 * every tick while taut, plus a damping term opposing the radial component of velocity so the line
 * arrests an overshoot instead of oscillating past its own rest length forever.
 *
 * <p><strong>Clamped so the tether can never push, only ever pull less hard.</strong> A taut real
 * line has no mechanism to shove a body away from its own anchor, however fast that body is
 * swinging back toward it — without the clamp, a large enough damping term at a large enough
 * inward speed would do exactly that, a real physical impossibility this class must never produce.
 *
 * <p>Minecraft-free (rule 1): a displacement and a velocity in, an acceleration out.
 */
public final class Tether {

    /** Blocks/tick² per block of stretch past rest length — a rule-41 number, open to retuning
     *  once actually flown. */
    public static final double SPRING_CONSTANT = 0.05;

    /** Blocks/tick² per block/tick of outward radial speed — picked close to this spring
     *  constant's own critical-damping ratio ({@code 2·√k ≈ 0.45}) so an overshoot is arrested
     *  without visibly bouncing, not derived from anything more fundamental. */
    public static final double DAMPING = 0.35;

    /** How far a tether can be fired, in blocks — "a harpoon, not a fishing rod": it holds
     *  rather than reels one bite at a time. */
    public static final double RANGE = 25.0;

    /** How far a raycast hit point gets nudged inward along its own face normal before being
     *  stored as an anchor — see {@link #nudgeIntoBlock}'s own doc for why this exists at all. */
    public static final double ANCHOR_NUDGE = 0.01;

    /**
     * A raycast hit point sits exactly <em>on</em> the target block's own surface — an integer
     * coordinate on whichever axis {@code faceNormal} points along — and deriving a block position
     * from that point by flooring it (as {@code BlockPos.containing} does) lands inside the solid
     * block for some faces but in the air block just past the surface for others (any face on the
     * block's own upper boundary: the top, and the two "positive" side faces). Found live, PLAN.md
     * architecture rule 105: the tether attached, then released itself within a tick, because
     * {@code TetherMixin}'s own "is the anchor still solid" check read exactly that wrong, adjacent
     * air block. Nudging the point a hair inward along the face's own inward normal, once, before
     * anything ever derives a block position from it, makes the stored point unambiguous regardless
     * of which face was hit.
     *
     * @param faceNormalX/Y/Z the hit face's own outward normal — a unit vector (each component
     *                        -1, 0, or 1, matching a real block face), not an arbitrary direction
     */
    public static double[] nudgeIntoBlock(double hitX, double hitY, double hitZ,
                                           double faceNormalX, double faceNormalY,
                                           double faceNormalZ) {
        return new double[] {
                hitX - faceNormalX * ANCHOR_NUDGE,
                hitY - faceNormalY * ANCHOR_NUDGE,
                hitZ - faceNormalZ * ANCHOR_NUDGE
        };
    }

    /**
     * The restoring acceleration the tether applies this tick, given the vector from the player
     * to the anchor ({@code toAnchorX/Y/Z}), the line's own rest length, and the player's current
     * velocity. Zero whenever the line is not taut (distance at or under rest length) — slack rope
     * does nothing, exactly like a real one.
     */
    public static double[] restoringAcceleration(double toAnchorX, double toAnchorY, double toAnchorZ,
                                                  double restLength,
                                                  double velX, double velY, double velZ) {
        double distance = Math.sqrt(
                toAnchorX * toAnchorX + toAnchorY * toAnchorY + toAnchorZ * toAnchorZ);
        double stretch = distance - restLength;
        if (stretch <= 0.0 || distance < 1.0e-6) {
            return new double[] {0, 0, 0};
        }
        double towardAnchorX = toAnchorX / distance;
        double towardAnchorY = toAnchorY / distance;
        double towardAnchorZ = toAnchorZ / distance;
        double towardAnchorSpeed =
                velX * towardAnchorX + velY * towardAnchorY + velZ * towardAnchorZ;
        double outwardSpeed = -towardAnchorSpeed;
        double magnitude = Math.max(0.0, SPRING_CONSTANT * stretch + DAMPING * outwardSpeed);
        return new double[] {
                towardAnchorX * magnitude,
                towardAnchorY * magnitude,
                towardAnchorZ * magnitude
        };
    }

    private Tether() {}
}
