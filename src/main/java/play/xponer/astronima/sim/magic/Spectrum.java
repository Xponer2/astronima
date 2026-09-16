package play.xponer.astronima.sim.magic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * What a spectrograph actually captures (design/astra-incognita.md §6, §8.1): a continuum peak
 * from the target's temperature (Wien's law — the same physics the demolished {@code
 * PrismaticCollector} carried and nothing ever called; this is its honest first consumer), plus
 * whichever of the target's real spectral lines the conditions allow through.
 *
 * <p>Deliberately does not know what a "target" is — the Sun, an RTG's glow, an ice lens — that
 * mapping is world content and belongs to whatever reads this (today, the debug command; later,
 * the spectrograph item). This class is the physics alone, so it can be tested without any of
 * that existing yet (rule 1).
 */
public final class Spectrum {
    /** Wien's displacement constant, b = 2.8977719e-3 m·K. */
    public static final double WIEN_DISPLACEMENT_M_K = 2.8977719e-3;

    /**
     * [O III] is a forbidden line: any collision de-excites the metastable state before it can
     * radiate, so it is only ever seen below this pressure — tighter than a laboratory vacuum,
     * which is the entire reason it is the tier's endgame line rather than an early one.
     */
    public static final double FORBIDDEN_LINE_MAX_KPA = 1e-3;

    /**
     * The continuum's peak wavelength, nanometres, from Wien's displacement law. A target with
     * no temperature has no continuum at all — infinity is "there is nothing to peak".
     */
    public static double peakWavelengthNm(double temperatureK) {
        if (temperatureK <= 0.0) {
            return Double.POSITIVE_INFINITY;
        }
        return WIEN_DISPLACEMENT_M_K / temperatureK * 1e9;
    }

    /**
     * Which lines a spectrograph would actually capture: none of them without a light source at
     * all, and the forbidden line only under {@link #FORBIDDEN_LINE_MAX_KPA}.
     *
     * @param temperatureK the target's blackbody temperature — the light source. Zero or below
     *                     means nothing to see, whatever lines the target could show
     * @param presentLines the lines the target's own composition could show, given light
     * @param vacuumKPa    the pressure around the target — governs the forbidden line alone
     */
    public static SortedSet<SpectralLine> capture(
            double temperatureK, Set<SpectralLine> presentLines, double vacuumKPa) {
        SortedSet<SpectralLine> observed =
                new TreeSet<>(Comparator.comparingDouble(SpectralLine::wavelengthNm));
        if (temperatureK <= 0.0) {
            return observed; // cold and dark: no photons, nothing to disperse
        }
        for (SpectralLine line : presentLines) {
            if (line.forbidden() && vacuumKPa > FORBIDDEN_LINE_MAX_KPA) {
                continue;
            }
            observed.add(line);
        }
        return Collections.unmodifiableSortedSet(observed);
    }

    /** The visible band a spectrum strip is drawn across (design/astra-research-m4b.md §3) — the
     *  same range {@code SpectrographItem}'s own dispersion-fan visual already uses. */
    public static final double VISIBLE_MIN_NM = 380.0;
    public static final double VISIBLE_MAX_NM = 780.0;

    /**
     * How close a candidate wavelength has to land to a real (shifted) line before it counts as
     * "found" it — design/astra-research-m4b.md §3's first-pass playtest number, not a balance
     * pass: wide margin against {@link ObservationTarget#ORION_NEBULA}/{@link
     * ObservationTarget#HELIX_NEBULA}'s own smallest real-line gap (68.7 nm), but tighter than
     * half the gap in {@link ObservationTarget#SUN}/{@link ObservationTarget#ANDROMEDA_GALAXY}'s
     * sodium doublet (0.6 nm) would need to stay unambiguous — a real, named, deliberately
     * uncovered hazard for whichever future leaf identifies those two.
     */
    public static final double DOPPLER_MATCH_TOLERANCE_NM = 1.5;

    /** A real line's on-strip width, once drawn — narrow, the "tall and narrow" half of §4a.2
     *  step 2's shape lesson. */
    private static final double REAL_PEAK_WIDTH_NM = 2.0;

    private static final int DECOY_MIN_COUNT = 3;
    private static final int DECOY_MAX_EXTRA = 3; // 3..5 inclusive
    private static final double DECOY_MIN_WIDTH_NM = 15.0;
    private static final double DECOY_WIDTH_SPAN_NM = 15.0; // 15..30 nm
    private static final double DECOY_MIN_HEIGHT = 0.3;
    private static final double DECOY_HEIGHT_SPAN = 0.3; // 0.3..0.6 of a real peak
    private static final double NOISE_FLOOR = 0.05;

    /** A broad, low decoy bump on a spectrum strip — real instrumental/sky noise, never a real
     *  emission (design/astra-research-m4b.md §3's {@code Spectrum.decoys}). */
    public record NoiseBump(double centerNm, double widthNm, double heightFrac) {}

    /** The three honest outcomes of applying a filter to a marked candidate position
     *  (design/astra-research.md §4a.2 step 4) — never collapsed into one generic failure. */
    public enum DecodeOutcome { NOT_ABOVE_NOISE, WRONG_FILTER, MATCH }

    /** {@code matchedLine} is only ever non-null alongside {@link DecodeOutcome#MATCH}. */
    public record DecodeResult(DecodeOutcome outcome, SpectralLine matchedLine) {}

    /**
     * The decoy bumps a given target's strip shows, deterministic per object — the same object
     * always shows the same noise, so a player can genuinely learn to read one rather than
     * fighting fresh randomness every time they open it (design/astra-research-m4b.md §3).
     *
     * <p>A candidate centre is rejected and re-rolled if it would land within twice the match
     * tolerance of one of the target's own real (shifted) lines — a decoy must never be able to
     * masquerade as a real peak, or read as an ambiguous fourth outcome nobody asked for.
     */
    public static List<NoiseBump> decoys(ObservationTarget target) {
        Random rng = new Random(target.ordinal());
        int count = DECOY_MIN_COUNT + rng.nextInt(DECOY_MAX_EXTRA);
        List<Double> realShiftedNm = new ArrayList<>();
        for (SpectralLine line : capture(target.temperatureK(), target.lines(), 0.0)) {
            realShiftedNm.add(line.wavelengthNm() + target.dopplerShiftNm());
        }

        List<NoiseBump> bumps = new ArrayList<>();
        int attempts = 0;
        int maxAttempts = count * 50;
        while (bumps.size() < count && attempts < maxAttempts) {
            attempts++;
            double centerNm = VISIBLE_MIN_NM + rng.nextDouble() * (VISIBLE_MAX_NM - VISIBLE_MIN_NM);
            boolean tooCloseToARealLine = realShiftedNm.stream()
                    .anyMatch(shifted -> Math.abs(shifted - centerNm) < DOPPLER_MATCH_TOLERANCE_NM * 2.0);
            if (tooCloseToARealLine) {
                continue;
            }
            double widthNm = DECOY_MIN_WIDTH_NM + rng.nextDouble() * DECOY_WIDTH_SPAN_NM;
            double heightFrac = DECOY_MIN_HEIGHT + rng.nextDouble() * DECOY_HEIGHT_SPAN;
            bumps.add(new NoiseBump(centerNm, widthNm, heightFrac));
        }
        return List.copyOf(bumps);
    }

    /**
     * The strip's own curve height at one wavelength, 0..1 — a low flat floor, each decoy's own
     * gaussian bump, and each real (shifted) line's own narrow gaussian spike, whichever is
     * tallest at that point. Pure and MC-free, so a strip renderer can sample it at as many
     * points as it likes without touching any of this tier's other state.
     */
    public static double intensity(ObservationTarget target, double wavelengthNm) {
        double value = NOISE_FLOOR;
        for (NoiseBump bump : decoys(target)) {
            double d = (wavelengthNm - bump.centerNm()) / bump.widthNm();
            value = Math.max(value, bump.heightFrac() * Math.exp(-d * d));
        }
        for (SpectralLine line : capture(target.temperatureK(), target.lines(), 0.0)) {
            double shiftedNm = line.wavelengthNm() + target.dopplerShiftNm();
            double d = (wavelengthNm - shiftedNm) / REAL_PEAK_WIDTH_NM;
            value = Math.max(value, Math.exp(-d * d));
        }
        return value;
    }

    /**
     * Reading a spectrum for real (design/astra-research.md §4a.2 step 4): does a candidate
     * position land on one of the target's own real (shifted) lines, and if so, is the applied
     * filter the right one? A candidate near a decoy, or near nothing at all, is indistinguishable
     * from "just noise" — a decoy's entire job is to look like something without being real, not
     * to earn its own separate outcome.
     */
    public static DecodeResult decode(
            ObservationTarget target, double candidateNm, SpectralLine appliedFilter) {
        SpectralLine nearest = null;
        double nearestDistanceNm = Double.POSITIVE_INFINITY;
        for (SpectralLine line : capture(target.temperatureK(), target.lines(), 0.0)) {
            double shiftedNm = line.wavelengthNm() + target.dopplerShiftNm();
            double distanceNm = Math.abs(candidateNm - shiftedNm);
            if (distanceNm < nearestDistanceNm) {
                nearestDistanceNm = distanceNm;
                nearest = line;
            }
        }
        if (nearest == null || nearestDistanceNm > DOPPLER_MATCH_TOLERANCE_NM) {
            return new DecodeResult(DecodeOutcome.NOT_ABOVE_NOISE, null);
        }
        if (nearest != appliedFilter) {
            return new DecodeResult(DecodeOutcome.WRONG_FILTER, null);
        }
        return new DecodeResult(DecodeOutcome.MATCH, nearest);
    }

    private Spectrum() {}
}
