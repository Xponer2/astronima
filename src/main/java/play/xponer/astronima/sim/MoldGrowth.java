package play.xponer.astronima.sim;

/**
 * The stage a mold colony is at, and how it moves between stages.
 *
 * <p>Real fungal colonies pass through a vegetative growth phase — radial extension outward from
 * the point of establishment — before entering a sporulation phase, where they are mature enough
 * to actually disperse spores that found a new colony elsewhere. {@link #canSpread} is that gate,
 * not an arbitrary "must be big first" rule: {@code design/mold-growth.md} §2. Minecraft-free
 * (rule 1) — {@code MoldBlock} owns the random rolls and the world calls; this owns only the state
 * machine those rolls drive.
 */
public final class MoldGrowth {
    public static final int MAX_AGE = 3;

    /** True once a colony is mature enough to seed a neighbouring cell. */
    public static boolean canSpread(int age) {
        return age >= MAX_AGE;
    }

    /** Age after one favourable tick that rolled a growth hit — one stage, clamped at mature. */
    public static int grown(int age) {
        return Math.min(MAX_AGE, age + 1);
    }

    /**
     * Age after one unfavourable tick that rolled a die-back hit — one stage thinner, drying
     * outer growth back before the inner colony. Below zero means the colony is gone entirely;
     * the caller removes the block rather than writing a negative age.
     */
    public static int diedBack(int age) {
        return age - 1;
    }

    private MoldGrowth() {}
}
