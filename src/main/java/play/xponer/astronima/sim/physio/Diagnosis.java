package play.xponer.astronima.sim.physio;

/**
 * How much the biomonitor is allowed to say about a condition, decided by instrumentation rather
 * than always telling the whole truth (design/biomonitor-chips.md).
 *
 * <p>Split at {@link Ailment#isUnidentified()}, not at "hazard vs. disease": every ailment except
 * {@code UNKNOWN_INFECTION} already names a real, directly-measurable physical cause (blood gas,
 * pressure, temperature), so a basic chip that can read those numbers has nothing left to hide.
 * The one ailment that is never directly measurable — an organism, not a quantity — is the one a
 * chip alone cannot fully resolve.
 */
public final class Diagnosis {

    /** What the biomonitor draws for one ailment. */
    public enum Detail {
        /** No chip at all: a vague, pre-diagnosis physical sensation, {@link Ailment#rawSymptom()}. */
        RAW_SYMPTOM,
        /** Chip present, but this ailment is not directly measurable: system and region only. */
        SYSTEM_REGION,
        /** Chip present and this ailment names a real, measurable cause: full name and remedy. */
        FULL
    }

    public static Detail detailFor(Ailment ailment, boolean hasBiomonitorChip) {
        if (!hasBiomonitorChip) {
            return Detail.RAW_SYMPTOM;
        }
        return ailment.isUnidentified() ? Detail.SYSTEM_REGION : Detail.FULL;
    }

    private Diagnosis() {}
}
