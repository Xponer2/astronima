package play.xponer.astronima.sim.astra;

import play.xponer.astronima.sim.ore.Mineral;
import play.xponer.astronima.sim.ore.OreBody;

import java.util.EnumMap;
import java.util.Map;

/**
 * The eleven-band absorption alphabet — design/astra-crust-sounding.md §2, astra-systems.md §3.3's
 * "the best idea in this document": astra leaves the core with one spectrum, and different real
 * minerals absorb different parts of it on the way up, so a reading at a real position is a
 * readout of everything really beneath it. Minecraft-free (rule 1): a caller reduces a real column
 * of placed blocks into an {@link OreBody} and a real position into {@code shellCoordinate}
 * exactly once ({@code item.AstraSounderItem}'s own job), and this class turns those two real
 * inputs into a signature.
 *
 * <h2>Why nine minerals, not ten or eleven</h2>
 * {@link Mineral#PLATINUM_GROUP} is a trace constituent of metal-rich ore, never its own
 * detectable band (astra-content.md §4.1 names nine real minerals, not ten) — excluded here on
 * purpose. Ice (band 10) and unaccounted (band 11) are real fields on {@link Signature} that stay
 * honest about what nothing in this world can produce yet: see this class's own methods.
 */
public final class CrustSounding {

    /** Below this {@code shellCoordinate}, a reading starts finding something no mineral in the
     *  catalogue explains — astra-crust-sounding.md §2.4's own discovery-hook curve. Chosen, not
     *  measured (rule 41): far enough from the body's overwhelming majority that most of a normal
     *  playthrough never sees it move at all. */
    private static final double UNACCOUNTED_ONSET_SHELL_COORDINATE = 0.1;

    /** The nine real bands, in the alphabet's own order (astra-content.md §4.1) —
     *  {@link Mineral#PLATINUM_GROUP} deliberately excluded. */
    private static final Mineral[] NAMED_BANDS = {
            Mineral.KAMACITE, Mineral.MAGNETITE, Mineral.TROILITE, Mineral.PENTLANDITE,
            Mineral.SERPENTINE, Mineral.OLIVINE, Mineral.CARBONATE, Mineral.THOLIN, Mineral.ILMENITE,
    };

    private CrustSounding() {}

    /**
     * A real reading: each of the nine named minerals' own real mass fraction of {@code column}
     * (never an invented absorption coefficient laid on top of density — the fraction *is* the
     * signal), ice always silent (§2.3 — nothing in this world can produce a nonzero reading yet),
     * and unaccounted derived from {@code shellCoordinate} alone, never from the column (§2.4 —
     * every real block this leaf recognises already fully accounts for its own mass; there is no
     * real leftover fraction to call unaccounted without inventing one).
     */
    public record Signature(Map<Mineral, Double> mineralFractions, double iceFraction,
                            double unaccountedFraction) {
        public Signature {
            mineralFractions = Map.copyOf(mineralFractions);
        }

        /** The fraction reported for one named mineral band, {@code 0.0} if the column carried
         *  none of it at all. */
        public double fractionOf(Mineral mineral) {
            return mineralFractions.getOrDefault(mineral, 0.0);
        }
    }

    /** The nine real bands this leaf can ever report — for a caller building a display order or a
     *  test without duplicating the alphabet's own list (rule 46). */
    public static Mineral[] namedBands() {
        return NAMED_BANDS.clone();
    }

    public static Signature signatureOf(OreBody column, double shellCoordinate) {
        Map<Mineral, Double> fractions = new EnumMap<>(Mineral.class);
        for (Mineral mineral : NAMED_BANDS) {
            fractions.put(mineral, column.fractionOf(mineral));
        }
        return new Signature(fractions, 0.0, unaccountedFraction(shellCoordinate));
    }

    /**
     * How much of a reading nothing in the catalogue explains, astra-crust-sounding.md §2.4's own
     * curve: exactly zero at and past {@link #UNACCOUNTED_ONSET_SHELL_COORDINATE}, ramping
     * linearly to exactly {@code 1.0} at the core itself ({@code shellCoordinate == 0}).
     */
    public static double unaccountedFraction(double shellCoordinate) {
        double clamped = Math.max(0.0, Math.min(1.0, shellCoordinate));
        if (clamped >= UNACCOUNTED_ONSET_SHELL_COORDINATE) {
            return 0.0;
        }
        return 1.0 - clamped / UNACCOUNTED_ONSET_SHELL_COORDINATE;
    }
}
