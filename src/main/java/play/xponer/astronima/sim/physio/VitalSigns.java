package play.xponer.astronima.sim.physio;

import java.util.EnumMap;
import java.util.Map;

/**
 * A person's current set of {@link Ailment}s and how bad each one is, packed into a
 * single int so it can ride along as one synced value.
 *
 * <p>Two bits per ailment, in declaration order. Eight ailments therefore need
 * sixteen bits, leaving room to grow before this has to become anything larger.
 */
public final class VitalSigns {
    private static final int BITS_PER_AILMENT = 2;
    private static final int SEVERITY_MASK = 0b11;

    static {
        int required = Ailment.values().length * BITS_PER_AILMENT;
        if (required > Integer.SIZE) {
            throw new IllegalStateException("Too many ailments to pack into an int: " + required + " bits");
        }
    }

    public static int pack(Map<Ailment, Ailment.Severity> severities) {
        int packed = 0;
        for (Map.Entry<Ailment, Ailment.Severity> entry : severities.entrySet()) {
            packed |= (entry.getValue().ordinal() & SEVERITY_MASK)
                    << (entry.getKey().ordinal() * BITS_PER_AILMENT);
        }
        return packed;
    }

    public static Ailment.Severity severityOf(int packed, Ailment ailment) {
        int value = (packed >> (ailment.ordinal() * BITS_PER_AILMENT)) & SEVERITY_MASK;
        return Ailment.Severity.values()[value];
    }

    /** Every ailment currently registering, in declaration order. */
    public static Map<Ailment, Ailment.Severity> unpack(int packed) {
        Map<Ailment, Ailment.Severity> active = new EnumMap<>(Ailment.class);
        for (Ailment ailment : Ailment.values()) {
            Ailment.Severity severity = severityOf(packed, ailment);
            if (severity != Ailment.Severity.NONE) {
                active.put(ailment, severity);
            }
        }
        return active;
    }

    public static boolean isHealthy(int packed) {
        return packed == 0;
    }

    /** The worst thing currently happening, for a single-line summary. */
    public static Ailment.Severity worst(int packed) {
        Ailment.Severity worst = Ailment.Severity.NONE;
        for (Ailment ailment : Ailment.values()) {
            Ailment.Severity severity = severityOf(packed, ailment);
            if (severity.ordinal() > worst.ordinal()) {
                worst = severity;
            }
        }
        return worst;
    }

    private VitalSigns() {}
}
