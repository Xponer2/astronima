package play.xponer.astronima.client.hud;

import play.xponer.astronima.sim.Co2Status;
import play.xponer.astronima.sim.Gas;
import play.xponer.astronima.sim.O2Status;

/**
 * Pure display mapping for the instrument panel: values to bar fills and colours.
 *
 * <p>Deliberately free of any Minecraft or rendering types so it can be unit-tested.
 * The point of the tests is that the colours a player reads must agree with the
 * thresholds that actually hurt them — a bar showing green while the sim is quietly
 * damaging you would be worse than no bar at all.
 */
public final class HudScale {
    // Monochrome phosphor palette (design/presentation.md, the CRT-terminal rework) — one hue
    // family, lit or dim, on near-black glass. Colour still carries meaning: green for a normal
    // reading, amber for a caution, red for an alarm — the way a real multi-colour terminal used
    // its few available hues, not a decorative accent scheme.
    public static final int COLOR_PANEL = 0xD0060C08;
    public static final int COLOR_FRAME = 0xFF1E3624;
    public static final int COLOR_TRACK = 0xFF12291A;
    public static final int COLOR_LABEL = 0xFF3C8F52;
    public static final int COLOR_VALUE = 0xFF3EFF6E;
    /** Secondary copy: advice and units, present but not competing with the reading. */
    public static final int COLOR_TEXT_DIM = 0xFF2F7A44;

    public static final int COLOR_GOOD = 0xFF3EFF6E;
    public static final int COLOR_WARN = 0xFFFFB000;
    public static final int COLOR_BAD = 0xFFFF4433;
    public static final int COLOR_INFO = 0xFF3EF0D8;

    /** Full-scale references, chosen so a healthy habitat sits near two thirds. */
    public static final double O2_FULL_SCALE_KPA = 32.0;
    public static final double CO2_FULL_SCALE_KPA = 5.0;
    public static final double PRESSURE_FULL_SCALE_KPA = 150.0;
    public static final double TEMP_MIN_C = -60.0;
    public static final double TEMP_MAX_C = 60.0;

    public static float fraction(double value, double fullScale) {
        if (fullScale <= 0) {
            return 0f;
        }
        return (float) Math.clamp(value / fullScale, 0.0, 1.0);
    }

    /** Temperature maps onto a bipolar scale, so 0 °C is not "empty". */
    public static float temperatureFraction(double celsius) {
        return (float) Math.clamp((celsius - TEMP_MIN_C) / (TEMP_MAX_C - TEMP_MIN_C), 0.0, 1.0);
    }

    public static int oxygenColor(double ppO2KPa) {
        return switch (O2Status.classify(ppO2KPa)) {
            case NORMAL -> COLOR_GOOD;
            case MILD_HYPOXIA, OXYGEN_TOXICITY -> COLOR_WARN;
            case SEVERE_HYPOXIA, SUFFOCATING -> COLOR_BAD;
        };
    }

    public static int carbonDioxideColor(double ppCO2KPa) {
        return switch (Co2Status.classify(ppCO2KPa)) {
            case NORMAL -> COLOR_GOOD;
            case ELEVATED -> COLOR_WARN;
            case HIGH, TOXIC -> COLOR_BAD;
        };
    }

    /**
     * Game-design audit #2, finding D: the suit's worst subsystem needs the same kind of
     * before-it-fails warning every other instrument here already gives — {@link
     * play.xponer.astronima.sim.suit.SuitWear#isWornOut} triggers only once life reaches zero, by
     * which point the player has already lost the capability, not been warned about losing it.
     *
     * @param fraction remaining life, 0..1 ({@link play.xponer.astronima.sim.suit.SuitWear#fraction})
     */
    public static int suitWearColor(float fraction) {
        if (fraction <= 0.15f) {
            return COLOR_BAD;
        }
        if (fraction <= 0.35f) {
            return COLOR_WARN;
        }
        return COLOR_GOOD;
    }

    public static int temperatureColor(double celsius) {
        if (celsius < 0 || celsius > 45) {
            return COLOR_BAD;
        }
        if (celsius < 10 || celsius > 30) {
            return COLOR_WARN;
        }
        return COLOR_GOOD;
    }

    public static int pressureColor(double kPa) {
        if (kPa < 30 || kPa > 130) {
            return COLOR_BAD;
        }
        if (kPa < 70) {
            return COLOR_WARN;
        }
        return COLOR_GOOD;
    }

    public static int humidityColor(double relativeHumidity) {
        if (relativeHumidity > 0.85) {
            return COLOR_BAD;
        }
        if (relativeHumidity > 0.70) {
            return COLOR_WARN;
        }
        return COLOR_GOOD;
    }

    public static int noiseColor(double db) {
        if (db > 75) {
            return COLOR_BAD;
        }
        if (db > 60) {
            return COLOR_WARN;
        }
        return COLOR_GOOD;
    }

    /** Distinct hue per species for the composition strip. */
    /**
     * Colour for a consumable running down. The thresholds are the points at which a
     * player should change plan, not even divisions: amber at a quarter means "start
     * heading back", red at a tenth means "you are already late".
     */
    /**
     * The value on each gauge where the reading stops being comfortable, so a bar can
     * show not just where you are but how close you are to the thing that matters.
     * Returning 0 means "no meaningful threshold" and the marker is omitted.
     */
    public static double dangerThreshold(String label) {
        return switch (label) {
            // Below roughly 16 kPa ppO2, judgement starts to go.
            case "O2" -> 16.0;
            // Above 2 kPa ppCO2 is where hypercapnia begins to bite.
            case "CO2" -> 2.0;
            // Below about 50 kPa total, water begins to boil at body temperature.
            case "PRES" -> 50.0;
            default -> 0.0;
        };
    }

    public static int supplyColor(int percent) {
        if (percent <= 10) {
            return COLOR_BAD;
        }
        return percent <= 25 ? COLOR_WARN : COLOR_GOOD;
    }

    /**
     * τ this good or better reads as a fully-lit bar — {@code Coherence}'s own javadoc names a
     * pumped-down cryostat's "whole seconds" as a real run.
     */
    public static final double COHERENCE_FULL_SCALE_S = 1.0;
    /** {@code Coherence.OBSERVER_PRESENT_S} — the model's own hard floor, so a bar at zero means
     *  exactly "as bad as someone standing in the room", not an arbitrary bottom of a scale. */
    public static final double COHERENCE_MIN_SCALE_S = 1.0e-6;

    /** τ spans microseconds to seconds, so the bar fills on a log scale — a linear one would sit
     *  pinned at either end for every reading except the last order of magnitude. */
    public static float coherenceFraction(double seconds) {
        if (Double.isInfinite(seconds) || seconds >= COHERENCE_FULL_SCALE_S) {
            return 1f;
        }
        if (seconds <= COHERENCE_MIN_SCALE_S) {
            return 0f;
        }
        double logMin = Math.log10(COHERENCE_MIN_SCALE_S);
        double logMax = Math.log10(COHERENCE_FULL_SCALE_S);
        return (float) Math.clamp((Math.log10(seconds) - logMin) / (logMax - logMin), 0.0, 1.0);
    }

    /**
     * design/astra-incognita.md §5: room temperature and cabin pressure with no isolation reads
     * "tens of microseconds (unusable)" — below a millisecond. A real short interferometer run
     * needs at least tens of milliseconds, so the bands are drawn at those two real reference
     * points rather than dividing the scale evenly.
     */
    public static int coherenceColor(double seconds) {
        if (Double.isInfinite(seconds) || seconds >= 0.1) {
            return COLOR_GOOD;
        }
        if (seconds >= 1.0e-3) {
            return COLOR_WARN;
        }
        return COLOR_BAD;
    }

    public static int gasColor(Gas gas) {
        return switch (gas) {
            case OXYGEN -> 0xFF4CD07A;
            case NITROGEN -> 0xFF4A6FA8;
            case CARBON_DIOXIDE -> 0xFFB0873C;
            case WATER_VAPOR -> 0xFF54B6C8;
            case METHANE -> 0xFFD05FD0;
            case HYDROGEN -> 0xFFE0E0E0;
            case CARBON_MONOXIDE -> 0xFFE2564A;
            case HYDROGEN_SULFIDE -> 0xFFD8D048;
            case SULFUR_DIOXIDE -> 0xFFC8A030;
            case AMMONIA -> 0xFF7ED0B0;
            // Chlorine really is yellow-green, and it is the one colour on this scale a
            // player should learn to dread rather than read.
            case CHLORINE -> 0xFFBFD048;
            // Sickly green-white: it should not look like any of the gases you can survive.
            case NICKEL_CARBONYL -> 0xFFD8E8C0;
            // Cracked-hydrocarbon orange — warm, distinct from CO's red and SO2's amber.
            case ETHYLENE -> 0xFFE0883C;
        };
    }

    private HudScale() {}
}
