package play.xponer.astronima.sim.sky;

import play.xponer.astronima.sim.gravity.Microgravity;

/**
 * Where a grain thrown up by an impact actually goes (design/sky.md §3's impact-flash row).
 *
 * <p><strong>This is the lesson the impact flash exists to teach, and the first cut got it
 * wrong.</strong> That cut spawned vanilla {@code CLOUD} particles and named the choice as an
 * honest simplification — but vanilla's own particle physics falls at Earth gravity through Earth
 * air, and this body has neither. A plume that drops like a dust cloud on Earth quietly contradicts
 * the one thing the whole event is for, and it contradicts a real model this mod already has and
 * already trusts for the player's own movement ({@link Microgravity}). Rule 46: one law for how
 * fast things fall here, not two.
 *
 * <p>Real content, all of it:
 * <ul>
 *   <li><strong>No air, so no drag.</strong> A grain's path is an exact parabola from the moment it
 *   leaves the ground until it lands. On Earth a dust plume billows and hangs because air resists
 *   it; here nothing does, so the plume is a spray of individual ballistic arcs and it looks like
 *   one.</li>
 *   <li><strong>Low gravity, so it goes far.</strong> Hang time is {@code 2v/g} and apex height is
 *   {@code v²/2g} — both scale as {@code 1/g}, so at {@link Microgravity#GRAVITY_MULTIPLIER} of
 *   vanilla's own pull the same throw hangs several times longer and climbs several times higher
 *   than the same throw in an ordinary world. That slow, high, unhurried arc <em>is</em> the read
 *   on this body's gravity, visible from a distance without an instrument.</li>
 * </ul>
 *
 * <p>Closed-form in age rather than accumulated per-tick velocity, the same construction {@code
 * EmberDrift} already established for the same reasons: a test can hold the exact height at any
 * tick without simulating every tick before it, and the arc cannot drift away from the model
 * through accumulated rounding.
 */
public final class RegolithBallistics {

    /** Downward acceleration at this body's own surface, blocks/tick² — {@link Microgravity}'s own
     * numbers, read rather than restated, so a change there moves the plume too. */
    public static final double GRAVITY_BLOCKS_PER_TICK2 =
            Microgravity.VANILLA_GRAVITY * Microgravity.GRAVITY_MULTIPLIER;

    /** Height above the launch point after {@code ageTicks}, for a grain thrown straight up at
     * {@code upSpeedBlocksPerTick} — the textbook {@code vt - gt²/2}, with no drag term because
     * there is no air to provide one. Goes negative once the grain is below its launch height,
     * which is a real answer (a grain thrown off a rise lands below where it started), not an
     * error state. */
    public static double heightAt(double upSpeedBlocksPerTick, double ageTicks) {
        return upSpeedBlocksPerTick * ageTicks
                - 0.5 * GRAVITY_BLOCKS_PER_TICK2 * ageTicks * ageTicks;
    }

    /** How long a grain thrown straight up at this speed spends above its launch height, in ticks:
     * {@code 2v/g}. */
    public static double hangTimeTicks(double upSpeedBlocksPerTick) {
        if (upSpeedBlocksPerTick <= 0.0) {
            return 0.0;
        }
        return 2.0 * upSpeedBlocksPerTick / GRAVITY_BLOCKS_PER_TICK2;
    }

    /** The top of that arc, in blocks above the launch point: {@code v²/2g}. */
    public static double apexHeightBlocks(double upSpeedBlocksPerTick) {
        if (upSpeedBlocksPerTick <= 0.0) {
            return 0.0;
        }
        return upSpeedBlocksPerTick * upSpeedBlocksPerTick / (2.0 * GRAVITY_BLOCKS_PER_TICK2);
    }

    /** The tick the arc peaks at: {@code v/g}, exactly half of {@link #hangTimeTicks}. */
    public static double apexTicks(double upSpeedBlocksPerTick) {
        if (upSpeedBlocksPerTick <= 0.0) {
            return 0.0;
        }
        return upSpeedBlocksPerTick / GRAVITY_BLOCKS_PER_TICK2;
    }

    /** How much higher and longer this body's own gravity makes an identical throw, against an
     * ordinary vanilla world — the single number the plume is really showing, exposed so the
     * codex and any future instrument quote the same one rather than each inventing it. Both
     * apex height and hang time scale the same way, so one ratio covers both. */
    public static double advantageOverVanilla() {
        return 1.0 / Microgravity.GRAVITY_MULTIPLIER;
    }

    /** Horizontal distance covered in {@code ageTicks} at a constant sideways speed. Constant
     * because there is no air: nothing slows a grain sideways once it is moving. */
    public static double horizontalAt(double sidewaysSpeedBlocksPerTick, double ageTicks) {
        return sidewaysSpeedBlocksPerTick * ageTicks;
    }

    private RegolithBallistics() {}
}
