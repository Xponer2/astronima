package play.xponer.astronima.sim.ore;

import play.xponer.astronima.sim.optics.BlackBody;

/**
 * How much the retort's vessel should visibly incandesce.
 *
 * <p>Real, not decoration: {@link Dehydroxylation}'s own onset (673 K) already sits past
 * {@link BlackBody#DRAPER_POINT_K} (798 K) partway through its working range, and
 * {@link ChlorateDecomposition}'s spoil point (873 K) crosses it too — a solar furnace hot
 * enough to bake water out of rock is, in reality, hot enough to glow. This is the one place
 * in the mod a black-body glow is honest (contrast {@code sim/circuit/Scorch}'s own note that a
 * wire is not, because its insulation fails at half this temperature).
 *
 * <p>Declares the emitter's own rate ceiling and falloff (design/presentation.md §3.1), kept
 * here rather than in the block entity so a unit test can hold the budget to account without a
 * running game.
 */
public final class RetortGlow {

    /** Particles per second once the vessel is fully white-hot. Never exceeded, whatever ticks. */
    public static final double MAX_PARTICLES_PER_SECOND = 6.0;

    /**
     * Motes in the one bright flare a completed charge fires (design/vfx-craft.md §2's accent
     * role) — an event, not a rate, but declared here so both the real call site and the debug
     * preview draw from the one number rather than two literals that can drift apart.
     */
    public static final double ACCENT_BURST_PARTICLES = 10.0;

    /** How many embers to spawn this tick, at the mod's own tick rate. */
    public static double particlesPerTick(double temperatureK) {
        return MAX_PARTICLES_PER_SECOND * BlackBody.intensity01(temperatureK) / 20.0;
    }

    private RetortGlow() {}
}
