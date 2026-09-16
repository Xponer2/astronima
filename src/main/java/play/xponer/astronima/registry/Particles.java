package play.xponer.astronima.registry;

import play.xponer.astronima.sim.ore.RetortGlow;

import java.util.List;

/**
 * design/presentation.md §3.1's published set: every particle emitter the mod ships, in one
 * place, so a guard can check a registered {@code ModParticles} type actually has an emitter
 * and every emitter admits its own rate ceiling — rather than that living only inside whichever
 * block entity happens to call {@code sendParticles}.
 *
 * <p>Deliberately Minecraft-free (only {@link RetortGlow}, itself Minecraft-free), so this class
 * — unlike {@link ModParticles} — can be read directly by a JUnit test rather than needing the
 * text-scan trick {@code SoundCoverageTest} uses for classes the test sourceset cannot import.
 */
public final class Particles {

    /**
     * No single emitter may claim more than this at distance zero, whatever it is doing — the
     * static half of §3.1's "the sum across every emitter in view is bounded"; the dynamic half
     * (the actual in-view sum) is a runtime concern this cannot check from source alone.
     */
    public static final double MAX_PER_EMITTER_PER_SECOND = 30.0;

    /**
     * @param name a role's own name — distinct from {@code particleType}, since two roles can
     *             (and here do) share one registered particle type: a role is a spawn-time
     *             choice ({@code IncandescenceOptions.accent}), not a second registration
     * @param particleType the id {@code ModParticles} actually registered this role's type under
     * @param maxPerSecondAtZero the role's own declared ceiling, at distance zero
     * @param reads what real reading drives the rate, as {@code Class.member} — for a human
     *              today, and for an {@code EffectSourceTest} to hold against the real accessor
     *              once one exists
     */
    public record Emitter(String name, String particleType, double maxPerSecondAtZero, String reads) {}

    /** Grains thrown by one impact — must match {@code ImpactFlashEvents}' own burst count. */
    public static final double IMPACT_PLUME_GRAINS = 22.0;

    public static final List<Emitter> ALL = List.of(
            new Emitter("incandescence_core", "incandescence", RetortGlow.MAX_PARTICLES_PER_SECOND,
                    "SolarRetortBlockEntity.temperatureK via RetortGlow.particlesPerTick"),
            new Emitter("incandescence_accent", "incandescence", RetortGlow.ACCENT_BURST_PARTICLES,
                    "SolarRetortBlockEntity.finishBatch via spawnCompletionFlare"),
            // One burst per impact, and an impact is a one-tick pulse the schedule fires at most
            // once per its own period - so the rate ceiling is the burst itself, not a sustained
            // stream. Each grain then flies a real parabola under Microgravity's own gravity.
            new Emitter("regolith_plume", "regolith", IMPACT_PLUME_GRAINS,
                    "ImpactSchedule.firesAt via ImpactFlashEvents.flashNear; "
                            + "arc from RegolithBallistics under Microgravity.GRAVITY_MULTIPLIER"));

    private Particles() {}
}
