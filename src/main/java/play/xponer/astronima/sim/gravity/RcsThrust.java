package play.xponer.astronima.sim.gravity;

/**
 * design/eva-mobility.md §3.5, promoted from named-future-work to built, then corrected once
 * actually flown: this is real, primary flight control, not a small correction on top of otherwise
 * uncontrolled drift. {@code AirControlMixin} correctly zeroes ordinary (yaw-only, ground-relative)
 * air control — nothing to push against, so unaided vanilla movement adds nothing to velocity — but
 * a first pass at this class read that as license for only a tiny nudge, and was reported directly
 * in play as still not what was wanted: pointing the camera anywhere (down at the surface below,
 * say) and holding forward should actually fly there, the same way real spacecraft RCS gives full
 * directional control, just never <em>instant</em> control the way vanilla's own air-control speed
 * would. {@link Microgravity}'s whole point — "you cannot stop," momentum is a real commitment —
 * still stays true: this only ever adds to whatever velocity already exists, never resets or caps
 * it, so stopping still means burning the opposite direction, not letting go of a key.
 *
 * <p>Minecraft-free (rule 1): a real {@link Orientation} and three raw input axes in, an
 * acceleration vector out.
 */
public final class RcsThrust {

    /**
     * Acceleration per tick, per unit of held input, blocks/tick² — a rule-41 number, open to
     * retuning once actually flown. Held for one full second (20 ticks) this adds about 0.6
     * blocks/tick of velocity, comfortably above {@link Microgravity#FREE_SPEED} — a real,
     * responsive cruise speed reached in about a second, not vanilla's instant top speed and not a
     * multi-second correction either.
     */
    public static final float THRUST_PER_TICK = 0.03f;

    /**
     * The world-space acceleration this tick's held input produces, given this player's own real
     * {@code orientation}. {@code strafe}/{@code vertical}/{@code forward} are the same raw
     * {@code xxa}/{@code yya}/{@code zza} axes vanilla's own movement already reads, rotated by the
     * <em>full</em> orientation — pitch and roll included, not yaw alone the way vanilla's own
     * ground-relative {@code moveRelative} would — so "forward" always means wherever this player
     * is actually looking, the whole point of a free 6DOF camera. Diagonal input is normalised the
     * same way vanilla's own is, so pressing two axes at once is not free extra thrust.
     */
    public static double[] accelerationFor(Orientation orientation, float strafe, float vertical,
                                            float forward) {
        float lengthSq = strafe * strafe + vertical * vertical + forward * forward;
        if (lengthSq < 1.0e-6f) {
            return new double[] {0, 0, 0};
        }
        float scale = THRUST_PER_TICK / Math.max(1.0f, (float) Math.sqrt(lengthSq));
        // Local -Z is this orientation's own forward (Orientation.IDENTITY's own doc), so the
        // forward input axis lands on the Z argument negated.
        float[] rotated = orientation.rotate(strafe * scale, vertical * scale, -forward * scale);
        return new double[] {rotated[0], rotated[1], rotated[2]};
    }

    private RcsThrust() {}
}
