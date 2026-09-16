package play.xponer.astronima.sim.tool;

/**
 * How a tool goes blunt, and why it never goes away.
 *
 * <p>Tools do not fail by running out of a hidden number. They wear, and abrasive wear
 * is described by Archard's equation:
 *
 * <pre>
 *   V = K · W · L / H
 * </pre>
 *
 * <p>wear volume {@code V} from a normal load {@code W} over sliding distance {@code L}
 * against the hardness {@code H} of the softer material. The part worth keeping is that
 * <strong>wear rate is inversely proportional to hardness</strong>, which is what makes
 * the forging in {@code sim/metal/ColdWorking} govern a tool's whole working life rather
 * than just its starting quality.
 *
 * <p>The other real mechanism is chipping, which is what metal worked past its ductility
 * limit does: it holds an edge beautifully and then loses a piece of it. Sudden, partial,
 * and not fatal.
 *
 * <p>Nothing here can reach zero. A blunt pick still moves rock — slowly, badly, but it
 * moves it — and that is what guarantees the softlock invariant structurally instead of
 * hoping the player kept a spare.
 */
public final class ToolWear {
    /** A fresh edge. */
    public static final double SHARP = 1.0;

    /**
     * Speed of a completely spent edge, as a fraction of sharp.
     *
     * <p>Deliberately not zero. This single number is the softlock invariant: no
     * sequence of events can leave a player unable to obtain metal, because the worst
     * tool in the game still cuts.
     */
    public static final double SPENT_SPEED = 0.15;

    /** Edge condition below which the tool reads as each band. */
    public static final double DULLED_BELOW = 0.70;
    public static final double BLUNT_BELOW = 0.35;
    public static final double SPENT_BELOW = 0.10;

    /**
     * Archard coefficient, folded into "edge lost per block mined by a tool of unit
     * hardness". Set so a well-forged pickaxe stays usable across a serious dig rather
     * than fitted to a real material pair, which would need a wear coefficient this mod
     * has no way to measure.
     */
    private static final double BASE_WEAR_PER_BLOCK = 0.0012;

    /** How much edge a cracked piece loses when it chips. */
    public static final double CHIP_LOSS = 0.12;

    /** Hardness below which a piece is soft enough that wear runs away. */
    private static final double MIN_EFFECTIVE_HARDNESS = 0.15;

    /** What the tool looks like to someone holding it. */
    public enum Edge {
        SHARP,
        DULLED,
        BLUNT,
        SPENT
    }

    public static Edge classify(double edge) {
        if (edge < SPENT_BELOW) {
            return Edge.SPENT;
        }
        if (edge < BLUNT_BELOW) {
            return Edge.BLUNT;
        }
        return edge < DULLED_BELOW ? Edge.DULLED : Edge.SHARP;
    }

    /**
     * Edge lost mining one block, from Archard: proportional to load, inverse to
     * hardness.
     *
     * @param hardness the forged hardness of the head, 0..1 from {@code ColdWorking}
     * @param load     relative resistance of what is being mined, 1 for ordinary rock
     */
    public static double wearPerBlock(double hardness, double load) {
        double effective = Math.max(MIN_EFFECTIVE_HARDNESS, hardness);
        return BASE_WEAR_PER_BLOCK * Math.max(0, load) / effective;
    }

    /**
     * Advances the edge by one block's worth of work.
     *
     * <p>Clamped at zero rather than allowed to go negative: past spent there is nothing
     * further to lose, and the tool still exists.
     */
    public static double mine(double edge, double hardness, double load) {
        return Math.clamp(edge - wearPerBlock(hardness, load), 0.0, SHARP);
    }

    /**
     * A brittle head losing a piece of its edge.
     *
     * <p>Only cracked pieces chip. This is the cost of over-working metal, and it is
     * paid across the tool's life rather than at the moment of forging — which is what
     * makes over-working a mistake you live with rather than one you notice at once.
     */
    public static double chip(double edge) {
        return Math.clamp(edge - CHIP_LOSS, 0.0, SHARP);
    }

    /**
     * Mining speed multiplier for an edge.
     *
     * <p>Interpolates from full speed at a sharp edge down to {@link #SPENT_SPEED},
     * never below. A blunt tool is slow, not useless.
     */
    public static double speedMultiplier(double edge) {
        double clamped = Math.clamp(edge, 0.0, SHARP);
        return SPENT_SPEED + (1.0 - SPENT_SPEED) * clamped;
    }

    /**
     * Blocks of ordinary rock a fresh edge will cut before it is spent.
     *
     * <p>Exists so tool life can be asserted directly in a test rather than simulated
     * and eyeballed, and so the forging trade-off can be stated in blocks — a unit a
     * player actually feels.
     */
    public static double lifeInBlocks(double hardness) {
        return (SHARP - SPENT_BELOW) / wearPerBlock(hardness, 1.0);
    }

    /**
     * Grinding the edge back.
     *
     * <p>Restores it completely, because that is what sharpening does. The cost is
     * material: {@link ToolHead} tracks how many resharpenings are left, and when they
     * run out the tool stays at whatever edge it has rather than becoming unusable.
     */
    public static double resharpen() {
        return SHARP;
    }

    private ToolWear() {}
}
