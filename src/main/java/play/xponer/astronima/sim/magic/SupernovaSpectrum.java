package play.xponer.astronima.sim.magic;

import play.xponer.astronima.sim.sky.SupernovaSchedule;

import java.util.EnumSet;
import java.util.Set;

/**
 * A supernova's own spectrum, changing over real time as the explosion itself evolves
 * (design/sky.md §3's "supernova" row, design weight "its spectrum changes over time, so the
 * instrument has something to say that a static target never could") — the one thing {@link
 * ObservationTarget}'s own fixed temperature-and-lines pairs cannot honestly represent, since every
 * one of its entries really is a fixed, permanent thing.
 *
 * <p>Real content, the three real phases of a Type II-P core-collapse supernova, reusing this
 * mod's own {@link SpectralLine} catalogue rather than inventing new ones:
 * <ul>
 *   <li><strong>Shock breakout ({@link SupernovaSchedule#RAMP_TICKS}):</strong> an extremely hot,
 *   nearly featureless blue-white continuum — real ejecta this soon after explosion is too hot and
 *   too fast-moving for any line to stand out against it.</li>
 *   <li><strong>Plateau ({@link SupernovaSchedule#HOLD_TICKS}):</strong> the recombining hydrogen
 *   envelope holds the photosphere near a real, much cooler recombination temperature, and the
 *   real diagnostic lines of this phase emerge — hydrogen Balmer-alpha, helium, and the calcium
 *   H&amp;K doublet.</li>
 *   <li><strong>Nebular decline ({@link SupernovaSchedule#DECAY_TICKS}):</strong> the ejecta has
 *   thinned enough for the same forbidden {@link SpectralLine#FORBIDDEN_OIII} line this mod's own
 *   planetary/emission nebulae already model to appear — the real nebular-phase signature of a
 *   supernova remnant in the making.</li>
 * </ul>
 *
 * <p>{@code SupernovaSchedule} decides <em>when</em> one is visible at all and reuses its own
 * {@code RAMP_TICKS}/{@code HOLD_TICKS}/{@code DECAY_TICKS} directly as this class's phase
 * boundaries, rather than a second, independently tuned timeline that could quietly drift out of
 * step with the visible brightness curve.
 */
public final class SupernovaSpectrum {

    /** Real order-of-magnitude shock-breakout temperature — tens of thousands of kelvin hotter
     * than the Sun's own photosphere. */
    public static final double BREAKOUT_TEMPERATURE_K = 50_000.0;

    /** Real order-of-magnitude Type II-P plateau photosphere temperature — cooler than the Sun,
     * where hydrogen recombines and the diagnostic lines below actually appear. */
    public static final double PLATEAU_TEMPERATURE_K = 6_000.0;

    /** Cooler still by the nebular phase — the photosphere concept itself is already a
     * simplification this late (real nebular-phase spectra are line-dominated, not a clean
     * blackbody), kept only so {@link Spectrum#peakWavelengthNm} still has a real number to work
     * from rather than reporting "nothing to see" for an object that plainly still is. */
    public static final double NEBULAR_TEMPERATURE_K = 4_500.0;

    /**
     * The blackbody continuum temperature a spectrograph would read right now, {@code elapsed}
     * ticks after the supernova's own start — smoothly falling from {@link #BREAKOUT_TEMPERATURE_K}
     * through {@link #PLATEAU_TEMPERATURE_K} to {@link #NEBULAR_TEMPERATURE_K}, real in direction
     * (a supernova's photosphere only ever cools, never reheats, across this span) though the exact
     * interpolation shape within each phase is a straight line, not a measured cooling curve.
     */
    public static double temperatureKAt(long elapsed) {
        if (elapsed < SupernovaSchedule.RAMP_TICKS) {
            double fraction = elapsed / (double) SupernovaSchedule.RAMP_TICKS;
            return lerp(BREAKOUT_TEMPERATURE_K, PLATEAU_TEMPERATURE_K, fraction);
        }
        long afterRamp = elapsed - SupernovaSchedule.RAMP_TICKS;
        if (afterRamp < SupernovaSchedule.HOLD_TICKS) {
            double fraction = afterRamp / (double) SupernovaSchedule.HOLD_TICKS;
            return lerp(PLATEAU_TEMPERATURE_K, NEBULAR_TEMPERATURE_K, fraction);
        }
        return NEBULAR_TEMPERATURE_K;
    }

    /**
     * Which of {@link SpectralLine}'s catalogue this supernova would actually show right now —
     * none during the hot, featureless breakout; the real plateau recombination lines once it
     * reaches the plateau; those plus the real nebular-phase forbidden line once it reaches
     * decline. Fed straight into {@link Spectrum#capture} exactly like every {@link
     * ObservationTarget}'s own {@code lines()} already is — this class only supplies a
     * time-varying answer to the same question.
     */
    public static Set<SpectralLine> linesAt(long elapsed) {
        if (elapsed < SupernovaSchedule.RAMP_TICKS) {
            return EnumSet.noneOf(SpectralLine.class);
        }
        long afterRamp = elapsed - SupernovaSchedule.RAMP_TICKS;
        Set<SpectralLine> plateauLines =
                EnumSet.of(SpectralLine.H_ALPHA, SpectralLine.HELIUM_I, SpectralLine.CALCIUM_H, SpectralLine.CALCIUM_K);
        if (afterRamp < SupernovaSchedule.HOLD_TICKS) {
            return plateauLines;
        }
        Set<SpectralLine> nebularLines = EnumSet.copyOf(plateauLines);
        nebularLines.add(SpectralLine.FORBIDDEN_OIII);
        return nebularLines;
    }

    private static double lerp(double a, double b, double fraction) {
        return a + (b - a) * Math.clamp(fraction, 0.0, 1.0);
    }

    private SupernovaSpectrum() {}
}
