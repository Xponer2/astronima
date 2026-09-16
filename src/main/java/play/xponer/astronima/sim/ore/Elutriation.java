package play.xponer.astronima.sim.ore;

import java.util.EnumMap;
import java.util.Map;

/**
 * Sorting crushed ore with a gas stream — density separation, and tier 1's second axis.
 *
 * <p>Blow gas up through a bed of powder and drag carries the light grains over the lip
 * while the dense ones stay put. Winnowing grain is the same physics, and it is still
 * industrial practice as air classification. On an asteroid it is the natural method for a
 * reason that is not decoration: there is no gravity worth settling in, so a gas stream is
 * the only way to make a particle's density express itself as motion.
 *
 * <h2>Why it exists beside the magnet</h2>
 * {@link MagneticSeparation} sorts on magnetism, and is deliberately unable to touch
 * pentlandite — the nickel ore — because pentlandite is not magnetic. That is honest and it
 * left tier 1 with one axis and one verb. Density is a second axis the same crude equipment
 * can genuinely exploit: pentlandite at 4.8 g/cm³ against olivine at 3.3 is a real and
 * workable difference, and it is the difference {@link Mineral#densityGPerCm3()} has been
 * carrying, unread, since the minerals were written.
 *
 * <h2>The trap, which is the whole lesson</h2>
 * <strong>Size confounds density.</strong> Drag depends on a particle's size as well as its
 * density, so a large light grain is carried exactly like a small heavy one — the
 * "equal-settling" problem every mineral-processing text opens with. Classification only
 * works on a <em>narrowly sized</em> feed, so a coarse, ragged grind cannot be separated by
 * density however good the machine is.
 *
 * <p>That is why this machine has no dial. Its performance is set by the crusher's, which
 * gives an existing control a second and different consequence rather than adding a third
 * slider to tune.
 */
public final class Elutriation {

    /**
     * Density that divides "carried away" from "left behind", g/cm³.
     *
     * <p>Set between the silicates (olivine 3.3, serpentine 2.6) and the sulfides and metals
     * (troilite 4.7, pentlandite 4.8, magnetite 5.2, kamacite 7.9). It is a property of the
     * gas stream a hand-cranked table can raise, not a tuning knob: a stronger blower would
     * move it, and there is no stronger blower in this tier.
     */
    public static final double CUT_DENSITY = 4.0;

    /**
     * Below this room pressure, in kPa, there is not enough gas to classify anything.
     *
     * <p>A fifth of an atmosphere. Drag scales with gas density, so a thin atmosphere lifts
     * nothing — and above this the gas has stopped being the limiting factor, which is why
     * this is a threshold rather than a curve. A version that made 101 kPa separate very
     * slightly better than 80 kPa would be dressing a number nobody could act on as a
     * decision.
     */
    public static final double MINIMUM_PRESSURE_KPA = 20.0;

    /**
     * How sharply the best possible grind separates.
     *
     * <p>Not 1: a real classifier has a spread, so even a perfect feed sends some heavies
     * over the lip and leaves some lights behind. A machine that sorted perfectly would make
     * the recovery-versus-grade tension this tier is built on disappear.
     */
    public static final double BEST_SHARPNESS = 0.85;

    /** The two streams. Together they hold the whole feed. */
    public record Result(OreBody heavies, OreBody lights) {

        /** Fraction of the feed's contained metal that reported to the heavy stream. */
        public double recovery() {
            double total = heavies.containedMetalGrams() + lights.containedMetalGrams();
            return total <= 0 ? 0 : heavies.containedMetalGrams() / total;
        }

        /** Metal fraction of the heavy stream — how good the product actually is. */
        public double grade() {
            return heavies.totalMass() <= 0
                    ? 0 : heavies.containedMetalGrams() / heavies.totalMass();
        }
    }

    /**
     * Splits a feed in a gas stream.
     *
     * @param feed        the crushed batch
     * @param fineness    the crusher setting it was ground at, 0 coarse .. 1 fine. Stands in
     *                    for how narrowly sized the particles are, which is what decides
     *                    whether density can express itself at all
     * @param pressureKPa the pressure of the room the table stands in
     */
    public static Result separate(OreBody feed, double fineness, double pressureKPa) {
        double sharpness = sharpness(fineness, pressureKPa);
        Map<Mineral, Double> toHeavies = new EnumMap<>(Mineral.class);
        Map<Mineral, Double> toLights = new EnumMap<>(Mineral.class);

        for (Map.Entry<Mineral, Double> entry : feed.masses().entrySet()) {
            Mineral mineral = entry.getKey();
            double mass = entry.getValue();
            double stays = stayFraction(mineral, sharpness);
            toHeavies.merge(mineral, mass * stays, Double::sum);
            toLights.merge(mineral, mass * (1.0 - stays), Double::sum);
        }
        return new Result(rebuild(toHeavies), rebuild(toLights));
    }

    /**
     * How well this feed, in this air, can be classified at all — 0 not at all, 1 as well as
     * the machine can manage.
     *
     * <p>Gas first and as a gate: with too little of it nothing is carried and the bed simply
     * sits there, whatever its grind. Then the grind, which is the part the player controls.
     */
    public static double sharpness(double fineness, double pressureKPa) {
        if (pressureKPa < MINIMUM_PRESSURE_KPA) {
            return 0;
        }
        return BEST_SHARPNESS * Math.clamp(fineness, 0.0, 1.0);
    }

    /**
     * The fraction of a mineral that stays in the bed rather than being carried over.
     *
     * <p>At zero sharpness nothing has been sorted, so every mineral splits evenly — the bed
     * has been stirred, not classified, which is exactly what an unsorted feed in a gas
     * stream does. As sharpness rises, each mineral is pushed toward the side of
     * {@link #CUT_DENSITY} it belongs on, and how far it is pushed depends on how far its
     * density sits from the cut: kamacite at 7.9 is unambiguous, troilite at 4.7 is marginal
     * and will always contaminate somewhat.
     */
    public static double stayFraction(Mineral mineral, double sharpness) {
        double clarity = Math.clamp((mineral.densityGPerCm3() - CUT_DENSITY) / CUT_DENSITY,
                -1.0, 1.0);
        return Math.clamp(0.5 + 0.5 * clarity * Math.clamp(sharpness, 0.0, 1.0), 0.0, 1.0);
    }

    private static OreBody rebuild(Map<Mineral, Double> masses) {
        OreBody body = OreBody.empty();
        for (Map.Entry<Mineral, Double> entry : masses.entrySet()) {
            if (entry.getValue() > 0) {
                body = body.with(entry.getKey(), entry.getValue());
            }
        }
        return body;
    }

    private Elutriation() {}
}
