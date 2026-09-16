package play.xponer.astronima.sim.gravity;

/**
 * The two ways to answer a body that will not stay put, and what each of them costs.
 *
 * <p>Low gravity has two separate problems and they want different answers. You cannot stay on
 * the deck — that is a <em>standing</em> problem, and the answer is boots. You pushed off and
 * cannot stop — that is a <em>travelling</em> problem, and the answer is something to catch
 * hold of. Neither aid does the other's job, which is the whole reason there are two of them
 * rather than one with a bigger number (rule 8).
 *
 * <p>Minecraft-free (rule 1): a surface and a speed in, a decision out.
 */
public final class LocomotionAids {

    /**
     * How much of normal walking speed magnetic boots leave you, while gripping.
     *
     * <p><strong>The cost, and it is the point.</strong> Magnetic soles are laborious in
     * reality — every step is peeling a magnet off steel — and an aid that were strictly
     * better than not wearing it would not be a decision. This phase's reward is movement, so
     * boots that were free would quietly delete the thing the phase just handed out.
     */
    public static final double BOOT_SPEED_FACTOR = 0.55;

    /**
     * Whether magnetic soles have anything to hold on to.
     *
     * <p>Ferromagnetic or nothing. It is worth saying why this works here and not in reality:
     * <strong>magnetic boots do not work on the ISS</strong>, because spacecraft are built of
     * aluminium. They work on this asteroid because this mod's entire metallurgy is
     * iron-nickel — the hull plate is the ore chain's output — so the aid falls out of a
     * decision made three tiers earlier rather than being granted.
     *
     * @param surfaceIsFerrous whether the block underfoot is worked iron-nickel
     */
    public static boolean bootsGrip(boolean bootsWorn, boolean surfaceIsFerrous) {
        return bootsWorn && surfaceIsFerrous;
    }

    /**
     * The walking-speed multiplier for someone in this state.
     *
     * <p>Only charged while the boots are actually holding. Fitted boots on rock are dead
     * weight and cost nothing, which is both physically right and the fairer reading — being
     * slowed by an aid that is not helping would be a trap rather than a trade.
     */
    public static double speedFactor(boolean gripping) {
        return gripping ? BOOT_SPEED_FACTOR : 1.0;
    }

    /**
     * What arriving costs when the arrival was <em>caught</em> rather than hit.
     *
     * <p>Zero, and not "reduced". An astronaut who catches a handrail does not take a
     * fractional injury — they have hold of it. Grading this would make rails a probability
     * rather than a decision, and the decision (line the shaft before you use it) is the
     * mechanic.
     *
     * <p>The cost of rails is paid entirely in <strong>foresight and iron</strong>: they only
     * help on a route you prepared. The shaft you did not line is a planning failure and
     * should read as one.
     */
    public static double impactAfterCatching(double speed, boolean caughtHold) {
        return caughtHold ? 0 : Microgravity.impactDamage(speed);
    }

    /**
     * True when this aid is doing anything for the player right now.
     *
     * <p>Exists so the grip indicator and the movement penalty cannot disagree about whether
     * the boots are working — two answers to one question is how a player ends up slowed by
     * boots the HUD says are idle.
     */
    public static boolean isHelping(boolean bootsWorn, boolean surfaceIsFerrous) {
        return bootsGrip(bootsWorn, surfaceIsFerrous);
    }

    /**
     * Speed of a push-off impulse, blocks/tick — design/eva-mobility.md §2, candidate #3 of
     * microgravity.md §7.3, decided.
     *
     * <p><strong>Capped, deliberately, as this leaf's own mitigation for the tether (§3) not
     * shipping in the same pass.</strong> Sized so a full-strength push from any point inside a
     * normal build still leaves reaching some solid surface possible before drift becomes
     * unrecoverable — not risk-free, but not a one-press ticket to permanent open-vacuum drift
     * either. A player-felt safety margin (rule 41), not a physics constant, and exactly the
     * number a playtest should retune once the tether exists and the cap can be relaxed.
     */
    public static final double PUSH_IMPULSE_SPEED = 0.5;

    /**
     * The velocity a push-off adds, given the outward normal of the face that was touched.
     *
     * <p>Away from the surface, never toward the player and never toward wherever they happened
     * to be looking — design/eva-mobility.md §2's whole point: "you aim before you push, not
     * during," a literal fact about which face was touched, not a slogan.
     *
     * @param normalX/Y/Z a unit vector — the targeted face's own outward normal
     */
    public static double[] pushOffImpulse(double normalX, double normalY, double normalZ) {
        return new double[] {
                normalX * PUSH_IMPULSE_SPEED,
                normalY * PUSH_IMPULSE_SPEED,
                normalZ * PUSH_IMPULSE_SPEED
        };
    }

    /**
     * {@code (x, y, z)} rescaled to unit length, or exactly zero if the input is too small to
     * have a real direction at all.
     *
     * <p>design/eva-mobility.md §1.7's own network/anti-cheat question, closed for push-off:
     * {@link #pushOffImpulse} trusts its own inputs completely and has no cap of its own — a
     * legitimate client only ever sends a real face normal (already unit length), but nothing
     * about the network payload itself enforces that, so the server has to normalize whatever it
     * receives before treating it as a direction. A degenerate near-zero vector returns zero
     * rather than dividing by it, the same "no penalty, nothing happens" shape a miss already has.
     */
    public static double[] normalizedOrZero(double x, double y, double z) {
        double lengthSq = x * x + y * y + z * z;
        if (lengthSq < 1.0e-6) {
            return new double[] {0, 0, 0};
        }
        double invLength = 1.0 / Math.sqrt(lengthSq);
        return new double[] {x * invLength, y * invLength, z * invLength};
    }

    private LocomotionAids() {}
}
