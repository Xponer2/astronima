package play.xponer.astronima.sim.magic;

import java.util.EnumSet;
import java.util.Set;

/**
 * Named things worth pointing a spectrograph at, until a real instrument reads them off the
 * world instead (design/astra-incognita.md §8.1). Each is a temperature and the real lines its
 * composition would show if lit — the composite solar spectrum below is the textbook one:
 * Fraunhofer's own sodium and calcium lines, and helium, found here first in 1868.
 */
public enum ObservationTarget {
    SUN(5778.0, EnumSet.of(
            SpectralLine.H_ALPHA, SpectralLine.SODIUM_D1, SpectralLine.SODIUM_D2,
            SpectralLine.CALCIUM_H, SpectralLine.CALCIUM_K, SpectralLine.HELIUM_I,
            SpectralLine.IRON_I), 0.0),
    /**
     * M42, the first deep-sky target this mod ships (design/sky.md L4): an HII emission region
     * lit by the Trapezium cluster's hottest member, theta&#185; Orionis C — an O-type star at
     * roughly this temperature, used the same way {@link #SUN}'s own does, as the continuum's
     * source. What is diagnostic about a real emission nebula is not its continuum but its
     * lines: Balmer-alpha, neutral helium excited by the same hot star, and [O III] — the
     * textbook forbidden line, meaning this is also the first target whose own line set actually
     * reaches {@link Spectrum#FORBIDDEN_LINE_MAX_KPA}, a threshold the mod modelled long before
     * anything existed that could show it.
     */
    ORION_NEBULA(39000.0, EnumSet.of(
            SpectralLine.H_ALPHA, SpectralLine.HELIUM_I, SpectralLine.FORBIDDEN_OIII), 4.0),
    /**
     * M45, the Pleiades: real content, a genuinely different kind from {@link #ORION_NEBULA} —
     * a reflection nebula, not an emission one. There is no ionised gas here to produce emission
     * lines; the nebulosity is starlight scattered off dust, so its own real spectrum is close to
     * the reflected continuum of the embedded B-type stars (real: Alcyone and its neighbours,
     * roughly this temperature) rather than a line-rich signature. {@link SpectralLine#HELIUM_I}
     * stands in for that hot-star signature — the one real line this catalogue already carries
     * that a B-type star's own spectrum would actually show.
     */
    PLEIADES(12000.0, EnumSet.of(SpectralLine.HELIUM_I), 1.5),
    /**
     * NGC 7293, the Helix Nebula: a planetary nebula, the same emission-line family as {@link
     * #ORION_NEBULA} (recombination plus the [O III] forbidden line) but from a wholly different
     * kind of source — not a young star's own HII region, but the exposed, superheated core of a
     * star that has already died. Real central white dwarf temperatures in planetary nebulae
     * reach this range, hot enough to fully ionise helium as well as hydrogen and oxygen.
     */
    HELIX_NEBULA(120000.0, EnumSet.of(
            SpectralLine.H_ALPHA, SpectralLine.HELIUM_I, SpectralLine.FORBIDDEN_OIII), -3.0),
    /**
     * M31, the Andromeda Galaxy: real content is a whole galaxy, not a nebula — the nearest
     * major galaxy to our own. Its integrated light is dominated by its bulge, an old population
     * of cool G-K giant stars, which is why this reuses {@link #SUN}'s own real absorption lines
     * (sodium, calcium, iron) rather than inventing a new set: a galaxy's bulge is, spectrally, a
     * great many Sun-like and cooler stars superimposed. {@link SpectralLine#H_ALPHA} is added
     * for the disc's own real star-forming regions, visible in every deep photograph of it —
     * unlike the Sun, which shows none. No forbidden line: nothing here is a single, nearby,
     * tightly-ionised region the way a planetary or HII nebula is.
     */
    ANDROMEDA_GALAXY(4500.0, EnumSet.of(
            SpectralLine.H_ALPHA, SpectralLine.SODIUM_D1, SpectralLine.SODIUM_D2,
            SpectralLine.CALCIUM_H, SpectralLine.CALCIUM_K, SpectralLine.IRON_I), -6.0),
    /**
     * M32, Andromeda's own compact elliptical companion — real, and genuinely the sharpest
     * possible contrast with {@link #ANDROMEDA_GALAXY} despite carrying almost the identical
     * line set. Both are old stellar populations of cool G-K giants (the same real Fe I/Na D/Ca
     * H&amp;K absorption suite), which is the whole point of pairing them for a claim: matching
     * {@link SpectralLine#IRON_I} between a spiral's bulge and a wholly different galaxy's
     * integrated light is the same non-destructive-assay principle a heated lab sample would
     * demonstrate, real content this catalogue did not have a pair for until now
     * (design/astra-research.md §3.3.1). What real astronomy actually tells apart the two: a
     * compact elliptical has essentially no cold gas left to form stars from, so unlike
     * Andromeda's own disc it shows no {@link SpectralLine#H_ALPHA} at all — pure old-star
     * absorption, nothing young. Slightly hotter than Andromeda's own bulge value here (a
     * compact elliptical's stellar population skews a touch older/hotter on average), sharing
     * Andromeda's real approach velocity in sign (M32 is real, gravitationally bound to it) at a
     * distinguishable exaggerated magnitude.
     */
    M32(4700.0, EnumSet.of(
            SpectralLine.SODIUM_D1, SpectralLine.SODIUM_D2,
            SpectralLine.CALCIUM_H, SpectralLine.CALCIUM_K, SpectralLine.IRON_I), -7.5),
    /** A radioisotope's waste heat: a dull glow with a continuum and no lines of its own. */
    RTG_GLOW(600.0, EnumSet.noneOf(SpectralLine.class), 0.0),
    /** Four billion years undisturbed and stone cold — nothing to see without lighting it. */
    ICE_LENS(0.0, EnumSet.noneOf(SpectralLine.class), 0.0);

    private final double temperatureK;
    private final Set<SpectralLine> lines;
    private final double dopplerShiftNm;

    ObservationTarget(double temperatureK, Set<SpectralLine> lines, double dopplerShiftNm) {
        this.temperatureK = temperatureK;
        this.lines = lines;
        this.dopplerShiftNm = dopplerShiftNm;
    }

    public double temperatureK() {
        return temperatureK;
    }

    public Set<SpectralLine> lines() {
        return lines;
    }

    /**
     * How far this object's own real lines sit from their catalogue (rest) wavelength, in
     * nanometres — real radial-motion Doppler shift, exaggerated in magnitude for a spectrum
     * strip a player reads by eye (design/astra-research-m4b.md §1/§3): a real shift at these
     * objects' actual velocities would be a small fraction of a nanometre, far below what a GUI
     * curve can show. The mechanism stays real; only the number is stylised for legibility, the
     * same "invent the magnitude, never the mechanism" precedent this tier's other exaggerations
     * already follow.
     *
     * <p>{@link #ORION_NEBULA} and {@link #HELIX_NEBULA} are given opposite signs on purpose —
     * both carry {@link SpectralLine#H_ALPHA}, and the same line sitting at two visibly different
     * (and oppositely displaced) positions is what makes §4a.6's shared-line proof a real reading
     * rather than a coincidence to shrug at. {@link #ANDROMEDA_GALAXY}'s negative sign is not
     * arbitrary either: the real Andromeda Galaxy is genuinely blueshifted (one of the few
     * galaxies approaching rather than receding from us), so even the exaggerated number here
     * keeps a real fact honest.
     */
    public double dopplerShiftNm() {
        return dopplerShiftNm;
    }
}
