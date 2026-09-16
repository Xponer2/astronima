package play.xponer.astronima.sim.optics;

/**
 * Pure math behind the Moon's own phase shading (design/sky.md): a synthetic light direction
 * derived from a discrete phase index, not from any real sun/moon geometry.
 *
 * <p>Extracted rather than left inline in {@code AsteroidSkyRenderer}, specifically so the
 * mapping itself carries a guard test against regressing back to the bugs this replaced: the
 * first version projected the Sun's own real direction into the Moon's local basis, which always
 * computed exact full-moon geometry, because this engine's own day timeline holds {@code
 * MOON_ANGLE} in constant 180-degree opposition to {@code SUN_ANGLE} at every tick of every day —
 * confirmed by reading {@code asteroid_day.json}, not guessed. The second version read the real
 * {@code MOON_PHASE} attribute, and that was also wrong: this dimension's own timeline carries no
 * {@code minecraft:visual/moon_phase} track, so the attribute sits at its registered default
 * ({@code FULL_MOON}) forever — confirmed by {@code /astronima sky}'s own printed log across
 * several {@code /time set} jumps, never once leaving {@code full_moon}.
 *
 * <p><strong>The third version paced the phase against real elapsed game ticks instead — also
 * wrong, for a usability reason rather than a data one.</strong> {@code Level#getGameTime()} is
 * deliberately *not* moved by {@code /time set} (confirmed against {@code TimeCommand}: that
 * command only mutates a separate {@code ServerClockManager}'s own tick count), which is exactly
 * why the asteroid's own axial rotation was tied to it — a body's physical spin should not jump
 * because a tester fast-forwarded the day. Reusing that same reasoning for the Moon's phase was a
 * mistake: reported back a second time as "тупо белая... даже когда время проматываю" (just
 * white, even scrubbing time), because scrubbing time is `/time set`, which never touches
 * `gameTime` at all — so from the tester's own chair this looked identical to the first two bugs.
 * {@link #phaseIndexForSunAngle} is the actual fix: paced against {@code SUN_ANGLE} itself, the
 * one value every round of testing in this file's own history has already shown responds
 * correctly and immediately to {@code /time set}. The trade against real astronomy (a lunar cycle
 * completing once per day rather than the ~29.5 real days, or even the sky's own already-invented
 * multi-day compression) is accepted deliberately: a mechanic that cannot be verified the way the
 * tester actually verifies things is worse than one that is paced unrealistically but works.
 */
public final class MoonPhaseLighting {

    /** Must match the real {@code net.minecraft.world.level.MoonPhase.COUNT} (rule 2) — not
     * duplicated as a guess, the mismatch is exactly the kind of thing a test below catches. */
    public static final int PHASE_COUNT = 8;

    /**
     * The synthetic light direction for {@code phaseIndex} (0-7): index 0 is full moon (light
     * toward the viewer), {@code PHASE_COUNT / 2} is new moon (light away from the viewer), the
     * rest sweep evenly around a full turn between them.
     */
    public static SkyRotation.Vec3 direction(int phaseIndex) {
        double angle = phaseIndex * (2.0 * Math.PI / PHASE_COUNT);
        return new SkyRotation.Vec3(Math.sin(angle), 0.0, Math.cos(angle));
    }

    /** Which phase index is current for a given {@code sunAngleDegrees} — one full 8-phase cycle
     * per day, immediately and correctly responsive to {@code /time set} since that is exactly
     * what moves {@code SUN_ANGLE}. Handles negative or over-360 input the same way a raw
     * environment-attribute read could hand it one. */
    public static int phaseIndexForSunAngle(float sunAngleDegrees) {
        float wrapped = sunAngleDegrees % 360.0F;
        if (wrapped < 0.0F) {
            wrapped += 360.0F;
        }
        int index = (int) (wrapped / 360.0F * PHASE_COUNT);
        return Math.min(index, PHASE_COUNT - 1);
    }

    private MoonPhaseLighting() {}
}
