package play.xponer.astronima.sim.physio;

import java.util.EnumMap;
import java.util.Map;

/**
 * What is left over after you survive.
 *
 * <h2>A chronic condition changes the shape of a system, it does not subtract from a bar</h2>
 * The obvious version is a permanent debuff: ten per cent worse forever because you were careless
 * once. That is a punishment. It gives the player nothing to do, nothing to learn, and a save file
 * they resent.
 *
 * <p>What these do instead is move a <em>line</em>. After scarring, every instrument the player has
 * learned to read still works and still tells the truth — and the safe mark on it is somewhere
 * else, because they need a richer atmosphere than the habitat standard to get the same oxygen
 * across a smaller membrane. The rules did not change; they did. That is a far stronger feeling
 * than a smaller bar, and it is manageable with machinery the mod already has: run the room
 * richer, carry more, pay for it.
 *
 * <p>Marrow damage does the same to {@link play.xponer.astronima.sim.pathogen.Immunity}, which is
 * already a <em>rate</em> rather than a shield. Nothing new is modelled: an infection a healthy
 * body threw off now establishes, so what is lost is the margin that let small problems be
 * ignored, and every system that was already there gets sharper.
 *
 * <p>Minecraft-free (rule 1). See {@code design/chronic.md}.
 */
public final class Chronic {

    /** The lasting things, and what each of them moves. */
    public enum Condition {
        /**
         * Scarred lungs. Less membrane to exchange across, so the same breath delivers less —
         * and the fix is a richer atmosphere rather than a bigger lung.
         */
        SCARRED_LUNGS("Scarred lungs", "Raises the oxygen pressure you need to breathe safely"),
        /**
         * Damaged marrow. Fewer white cells, and immunity here is a rate, so infections that used
         * to resolve on their own now win the race.
         */
        MARROW_DAMAGE("Marrow damage", "Your body fights infections more slowly, permanently");

        private final String displayName;
        private final String effect;

        Condition(String displayName, String effect) {
            this.displayName = displayName;
            this.effect = effect;
        }

        public String displayName() {
            return displayName;
        }

        public String effect() {
            return effect;
        }

        public String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** Insult at which the change becomes permanent. */
    public static final double SCARRING_THRESHOLD = 1.0;

    /**
     * How much insult a second of the relevant stress adds.
     *
     * <p>Sized so a single long exposure — ten unbroken minutes of it — reaches about a fifth of
     * the threshold. <strong>One bad day never scars anything.</strong> It takes a habit, and a
     * habit is something a player can be shown and can change.
     */
    public static final double INSULT_PER_SECOND = 0.00033;

    /**
     * How fast insult falls away while nothing is hurting you.
     *
     * <p>Slower than it accumulates, but not much: recovery has to be real or the threshold is
     * merely a delayed certainty for anybody who plays long enough.
     */
    public static final double RECOVERY_PER_SECOND = 0.00012;

    /**
     * How much higher a scarred player's oxygen floor sits, as a fraction of the base.
     *
     * <p>A fraction rather than a constant on purpose: the habitat standard is a number that could
     * move, and a scarred player's line has to move with it rather than sitting at a figure
     * somebody typed once.
     */
    public static final double SCARRED_FLOOR_RATIO = 1.20;

    /** What is left of a damaged immune response. */
    public static final double MARROW_IMMUNITY_FACTOR = 0.45;

    private final Map<Condition, Double> insult = new EnumMap<>(Condition.class);
    private final Map<Condition, Boolean> scarred = new EnumMap<>(Condition.class);

    public Chronic() {
    }

    public Chronic(Map<Condition, Double> insult, Map<Condition, Boolean> scarred) {
        this.insult.putAll(insult);
        this.scarred.putAll(scarred);
    }

    public double insultOf(Condition condition) {
        return insult.getOrDefault(condition, 0.0);
    }

    public boolean hasScarred(Condition condition) {
        return Boolean.TRUE.equals(scarred.get(condition));
    }

    public Map<Condition, Double> insults() {
        return Map.copyOf(insult);
    }

    public Map<Condition, Boolean> scars() {
        return Map.copyOf(scarred);
    }

    /**
     * Time spent under the stress that causes this condition.
     *
     * @return whether it crossed the threshold on this step, so a caller can announce it once
     */
    public boolean strain(Condition condition, double seconds) {
        if (hasScarred(condition) || seconds <= 0) {
            return false;
        }
        double now = insultOf(condition) + INSULT_PER_SECOND * seconds;
        if (now >= SCARRING_THRESHOLD) {
            insult.put(condition, SCARRING_THRESHOLD);
            scarred.put(condition, true);
            return true;
        }
        insult.put(condition, now);
        return false;
    }

    /**
     * Time spent with nothing hurting you.
     *
     * <p>Does nothing at all once a condition has scarred, which is what <em>permanent</em> means.
     * A version where resting slowly undid a scar would make the threshold a speed bump and the
     * whole system decoration.
     */
    public void rest(double seconds) {
        if (seconds <= 0) {
            return;
        }
        for (Condition condition : Condition.values()) {
            if (hasScarred(condition)) {
                continue;
            }
            double now = insultOf(condition) - RECOVERY_PER_SECOND * seconds;
            insult.put(condition, Math.max(0, now));
        }
    }

    /**
     * The oxygen partial pressure this body actually needs, given the habitat's standard.
     *
     * <p>Stated as a function of the base so a change to the standard moves this with it.
     */
    public double ppO2FloorKPa(double baseFloorKPa) {
        return hasScarred(Condition.SCARRED_LUNGS)
                ? baseFloorKPa * SCARRED_FLOOR_RATIO : baseFloorKPa;
    }

    /** What is left of the immune rate — a multiplier, because immunity is already a rate. */
    public double immunityFactor() {
        return hasScarred(Condition.MARROW_DAMAGE) ? MARROW_IMMUNITY_FACTOR : 1.0;
    }

    public boolean isHealthy() {
        for (Condition condition : Condition.values()) {
            if (hasScarred(condition) || insultOf(condition) > 0) {
                return false;
            }
        }
        return true;
    }
}
