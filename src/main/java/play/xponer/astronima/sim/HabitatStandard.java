package play.xponer.astronima.sim;

import play.xponer.astronima.sim.suit.SuitCondition;

/**
 * What counts as a habitat you have actually built, rather than a hole you are hiding
 * in.
 *
 * <p>The distinction matters because the habitat advancements are supposed to reward
 * <em>having made something that works</em>, not having crafted a hull plate. Rewarding
 * possession is easy and teaches nothing; rewarding a room that holds breathable air at
 * survivable pressure teaches the whole point of the atmosphere system.
 *
 * <p>MC-free so the thresholds can be argued with in a test rather than discovered in
 * play. They are the same physiology the rest of the mod uses — this is not a second
 * opinion about what is breathable, it is the same one.
 */
public final class HabitatStandard {
    /**
     * Oxygen partial pressure a room needs before a person can live in it.
     *
     * <p>Matches the bottom of {@link O2Status}'s normal band, and is sourced from
     * {@link SuitCondition#SEAL_BELOW_PPO2_KPA} rather than a second copy of the
     * number: both ask "is the ambient air still good enough to breathe unaided",
     * just for two different systems — a suit auto-seals at exactly the ppO2 a room
     * stops being a livable habitat. This used to be its own hard-coded {@code 16.0}
     * here, which is exactly the "a literal repeated in two places is how they stop
     * agreeing" shape PLAN.md warns about: retuning the suit's seal point would have
     * silently left the habitat advancement judging a different air quality than the
     * suit itself reacts to.
     */
    public static final double BREATHABLE_PPO2_KPA = SuitCondition.SEAL_BELOW_PPO2_KPA;

    /**
     * Total pressure below which water boils at body temperature — the Armstrong
     * limit, about 6.3 kPa. A room below it is not a habitat whatever it contains.
     */
    public static final double ARMSTRONG_LIMIT_KPA = 6.3;

    /** Carbon dioxide above which the room is quietly poisoning whoever is in it. */
    public static final double MAX_PPCO2_KPA = 1.0;

    /** A room big enough to be a room, rather than a one-block airlock cheat. */
    public static final int MIN_VOLUME_BLOCKS = 8;

    /**
     * True when a room is genuinely sealed — the first habitat milestone, and
     * deliberately separate from being breathable. Sealing something is an achievement
     * on its own, and it is the step most players will reach first.
     */
    public static boolean isSealedShelter(boolean sealed, int volumeBlocks) {
        return sealed && volumeBlocks >= MIN_VOLUME_BLOCKS;
    }

    /**
     * True when a room could keep a person alive with the helmet off.
     *
     * <p>All four conditions, because any one of them alone is a way to die in a room
     * that looks fine: enough oxygen, enough total pressure to keep your blood liquid,
     * not enough carbon dioxide to dull you, and sealed so it stays that way.
     */
    public static boolean isBreathableHabitat(boolean sealed, int volumeBlocks,
                                              double ppO2KPa, double pressureKPa,
                                              double ppCo2KPa) {
        return isSealedShelter(sealed, volumeBlocks)
                && ppO2KPa >= BREATHABLE_PPO2_KPA
                && pressureKPa >= ARMSTRONG_LIMIT_KPA
                && ppCo2KPa <= MAX_PPCO2_KPA;
    }

    private HabitatStandard() {}
}
