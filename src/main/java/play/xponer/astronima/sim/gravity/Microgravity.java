package play.xponer.astronima.sim.gravity;

import play.xponer.astronima.sim.world.AsteroidBody;

/**
 * Moving mass is easy on a small body. Stopping it is not.
 *
 * <p>The whole content of low gravity is that <strong>you cannot stop</strong> — not that you
 * can jump higher. A jump is a commitment the moment you make it, there is no air to slow you,
 * and kinetic energy goes as the square of speed. So the hazard of this phase is the player's
 * own momentum, and the only thing that turns a velocity into a consequence is this class.
 *
 * <h2>The one number that is not physics</h2>
 * Surface gravity here is {@value #GRAVITY_MULTIPLIER} of vanilla, and the honest figure for
 * the body in the lore is about <strong>1/70th</strong>. At the honest figure a Minecraft jump
 * is 87 blocks high with a two-minute hang, during which the player can do nothing whatever —
 * a cutscene triggered by pressing space, not a movement system. The compromise and its
 * arithmetic are written down in {@code design/microgravity.md} §2 rather than left looking
 * derived.
 *
 * <p>Everything else here <em>is</em> physics: energy goes as v², a suited body absorbs a
 * bounded amount of it, and there is no terminal velocity in vacuum.
 *
 * <p>Minecraft-free (rule 1): speeds in, damage out.
 */
public final class Microgravity {

    /** Vanilla's downward acceleration, blocks/tick². */
    public static final double VANILLA_GRAVITY = 0.08;

    /**
     * Surface gravity as a fraction of vanilla's 0.08 blocks/tick².
     *
     * <p>Chosen for hang time, not for realism — see the class note. At this value a jump goes
     * five blocks and takes two and a half seconds to come down, which is long enough that
     * committing to it feels like committing and short enough to aim.
     */
    public static final double GRAVITY_MULTIPLIER = 0.22;

    /**
     * Gravity's floor at and past the rim, as a fraction of {@link #GRAVITY_MULTIPLIER} —
     * design/asteroid-body.md §4.7's "at the rim you cannot stay down without boots" bar.
     *
     * <p>Rule 41's kind of number: stated as that experience, not derived, and it wants a
     * playtest before it is treated as final. It is never zero on purpose — a gravity of
     * nothing is unstandable rather than hard, and the rim is meant to be a place worked with
     * equipment, not one that ejects anyone who walks there without it.
     */
    public static final double RIM_GRAVITY_FLOOR = 0.15;

    /**
     * Surface gravity's multiplier at horizontal distance {@code r} from the spin axis: full
     * strength at the centre, falling toward {@link #RIM_GRAVITY_FLOOR} of that strength at and
     * past the rim.
     *
     * <p>Reuses {@link AsteroidBody#halfHeight}'s own falloff rather than inventing a second
     * curve: the same "the body thins toward the rim" fraction that fades surface relief in
     * {@code AsteroidChunkGenerator} does the same job for gravity here — see
     * {@code design/asteroid-body.md} §4.7's spin argument for why a thinner spun-up body and
     * weaker effective gravity there are one phenomenon, not two.
     *
     * <p><strong>Whoever else reads this value must read it from here, at the same position, in
     * the same tick</strong> — see {@code design/asteroid-body-b2.md} §1.2. The magnetic boots'
     * grip modifier cancels exactly whatever this function returns; if the two are ever
     * evaluated from different positions, or one of them goes back to reading
     * {@link #GRAVITY_MULTIPLIER} directly, the cancellation stops being exact everywhere except
     * the one point where the two coincidentally still agree.
     */
    public static double gravityMultiplierAt(double r) {
        double shapeFraction = AsteroidBody.halfHeight(r) / AsteroidBody.POLAR_RADIUS;
        return GRAVITY_MULTIPLIER * (RIM_GRAVITY_FLOOR + (1.0 - RIM_GRAVITY_FLOOR) * shapeFraction);
    }

    /**
     * Whether the player's own input can add velocity right now — §1's "nothing to push
     * against once you leave the ground", made a fact instead of a sentence.
     *
     * <p>Real physics, not a design flourish: a body's own effort applies zero net force to
     * itself with nothing solid to react against (Newton's third law). A jump or a shove sets
     * a trajectory once, at the instant of leaving something solid; nothing the player does
     * afterward can change it — only gravity, a caught grab rail (M4b), or a collision can.
     * §7 of design/microgravity.md records that nothing ever built this: {@code AsteroidGravity}
     * scaled {@code Attributes.GRAVITY} and stopped there, so vanilla's full air steering
     * survived untouched and a player in zero gravity could simply walk on air.
     *
     * <p>Deliberately just {@code onGround} — not a function of gripping or surface (candidate
     * direction #2 in §7.3, "traction as a real surface property"). Magnetic boots' existing
     * speed cost ({@code LocomotionAids}) already applies entirely within the grounded case;
     * there is nothing left for this fact to add once the body is touching something.
     */
    public static boolean hasAirControl(boolean onGround) {
        return onGround;
    }

    /** Vanilla's forgiven fall, in blocks — {@code Attributes.SAFE_FALL_DISTANCE}'s default. */
    public static final double VANILLA_SAFE_FALL_BLOCKS = 3.0;

    /**
     * The speed below which arriving costs nothing, in blocks/tick.
     *
     * <p><strong>Derived, not chosen: it is the arrival speed vanilla already forgives.</strong>
     * Minecraft absolves a three-block fall at 0.08 blocks/tick², which lands at
     * {@code sqrt(2 · 0.08 · 3)} ≈ 0.69. Expressing the threshold as a <em>speed</em> rather
     * than a distance is what makes it gravity-independent — the same impact energy is
     * forgiven here as on Earth, and the fact that it now corresponds to a thirteen-block drop
     * is a consequence of the weaker gravity rather than a gift.
     *
     * <p>The first draft typed 0.4 instead, and it was wrong in the way that matters:
     * <strong>a jump here goes five blocks and lands at 0.42</strong>, so a player was hurt by
     * coming down from their own jump. A phase whose entire reward is movement cannot punish
     * the movement it just handed out, and deriving from the jump's own energy makes that
     * impossible to get wrong again.
     */
    public static final double FREE_SPEED =
            Math.sqrt(2 * VANILLA_GRAVITY * VANILLA_SAFE_FALL_BLOCKS);

    /**
     * The speed at which an unprotected arrival is certainly fatal, in blocks/tick.
     *
     * <p>About 30 m/s — the order of a bad car crash, and reached by falling roughly seventy
     * blocks here. Used to scale the instrument rather than as a cliff in the damage curve.
     */
    public static final double FATAL_SPEED = 1.5;

    /**
     * Extra arrival speed a sealed suit's rigid shell and internal padding absorb before the
     * same v² curve an unprotected body follows takes over — M1's other promise
     * (design/microgravity.md §8): <em>"a suited body absorbs a bounded amount of it."</em>
     *
     * <p><strong>Bounded, not scaled.</strong> A fixed amount of extra headroom on the free
     * threshold, not a percentage taken off the whole curve: a rigid shell has a fixed amount
     * of give and then behaves exactly like no suit at all, so the suit's edge shrinks as a
     * fraction of the hit the harder the arrival is, rather than saving the wearer at any speed.
     *
     * <p>Sized against {@link #FREE_SPEED} rather than picked from nothing: a sealed arrival is
     * forgiven again what an unprotected body's own give already forgives it. A playability
     * number in the rule-41 style — open to retuning like {@link #GRAVITY_MULTIPLIER} itself,
     * not derived from a physical constant, because a fictional suit's cushioning has none to
     * derive from.
     */
    public static final double SUIT_ABSORPTION_SPEED = FREE_SPEED / 2.0;

    /**
     * Damage from arriving at this speed, in half-hearts, with no suit protection.
     *
     * <p><strong>Quadratic, because kinetic energy is.</strong> That is not a detail: a linear
     * curve would make the deep shaft merely twice as bad as the shallow one, when in truth it
     * is four times, and the whole lesson of the phase — that speed is the thing to respect —
     * lives in that exponent.
     *
     * <p>Measured from the free threshold rather than from zero, so ordinary movement sits
     * flat at nothing instead of accruing a trickle that would make walking about mildly
     * harmful.
     *
     * <p>Equivalent to {@link #impactDamage(double, boolean)} with {@code suitedProtection}
     * false — kept as its own overload because most callers (the HUD gauge, the existing test
     * suite) have no suit to ask about and should not have to say so.
     */
    public static double impactDamage(double speed) {
        return impactDamage(speed, false);
    }

    /**
     * As {@link #impactDamage(double)}, but with a sealed suit's bounded protection applied —
     * see {@link #SUIT_ABSORPTION_SPEED}. The curve's shape and its {@link #FATAL_SPEED} anchor
     * are untouched; only the point where it starts climbing moves.
     *
     * @param suitedProtection whether the wearer's suit is sealed right now — see
     *                         {@code SuitCondition#isCurrentlySealed}. A suit that is worn but
     *                         not sealed (open visor, or incapable of holding pressure) does
     *                         none of this, exactly as rule 88 already distinguishes for
     *                         barotrauma.
     */
    public static double impactDamage(double speed, boolean suitedProtection) {
        double threshold = FREE_SPEED + (suitedProtection ? SUIT_ABSORPTION_SPEED : 0.0);
        double over = speed - threshold;
        if (over <= 0) {
            return 0;
        }
        // Scaled so that an unprotected FATAL_SPEED arrival lands on twenty half-hearts: a
        // full bar, from a fall of about seventy blocks. Derived from the two thresholds
        // rather than typed, so moving either one cannot silently leave the curve pointing
        // somewhere else. Fixed regardless of suit state, deliberately: only the threshold
        // moves for a sealed suit, not the slope, so the protection is bounded rather than
        // scaling with the hit.
        double span = FATAL_SPEED - FREE_SPEED;
        return 20.0 * (over * over) / (span * span);
    }

    /** True when arriving at this speed costs nothing at all. */
    public static boolean isSurvivableArrival(double speed) {
        return impactDamage(speed) <= 0;
    }

    /**
     * How fast something is going after falling this far from rest, in blocks/tick.
     *
     * <p><strong>There is no terminal velocity term, and its absence is the point.</strong>
     * In vacuum nothing slows a falling body, so a shaft keeps accelerating you for as long as
     * it is deep — which is exactly backwards from the intuition low gravity produces. People
     * expect falls to be safe here. Short ones are; the mineshaft is not.
     */
    public static double speedAfterFalling(double blocks) {
        if (blocks <= 0) {
            return 0;
        }
        return Math.sqrt(2 * VANILLA_GRAVITY * GRAVITY_MULTIPLIER * blocks);
    }

    /** The drop that first costs anything, in blocks — what a player should know by feel. */
    public static double freeFallDistance() {
        return FREE_SPEED * FREE_SPEED / (2 * VANILLA_GRAVITY * GRAVITY_MULTIPLIER);
    }

    /**
     * Where this speed sits on the instrument, 0..1.
     *
     * <p>Scaled across the band the player can act in — from a standstill to certainly fatal —
     * rather than against some maximum nothing ever reaches. A needle that never leaves the
     * bottom of its travel is a needle nobody learns to read.
     */
    public static double gauge(double speed) {
        return Math.clamp(speed / FATAL_SPEED, 0.0, 1.0);
    }

    private Microgravity() {}
}
