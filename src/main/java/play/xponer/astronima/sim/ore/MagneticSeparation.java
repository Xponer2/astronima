package play.xponer.astronima.sim.ore;

import java.util.EnumMap;
import java.util.Map;

/**
 * Sorting crushed ore with a magnet — the whole of tier 1, and the first real decision
 * the game offers.
 *
 * <p>A magnet costs nothing to run. No oxygen, no heat, no reagents; only motion. On
 * an asteroid where fire is impossible that makes it the only extraction method
 * available at all, and it happens to work because chondrites are full of magnetite
 * and native iron-nickel.
 *
 * <h2>The trade</h2>
 * Two things can go wrong, and fixing either makes the other worse:
 * <ul>
 *   <li><strong>Locked particles.</strong> Crush too coarse and valuable grains are
 *       still bonded to silicate. A composite particle follows the magnet only if
 *       enough of it is magnetic, so values are lost to the tailings —
 *       <em>poor recovery</em>.</li>
 *   <li><strong>Entrainment.</strong> Crush too fine and non-magnetic dust is dragged
 *       along mechanically, riding with the concentrate regardless of what it is —
 *       <em>poor grade</em>.</li>
 * </ul>
 *
 * <p>There is no setting that avoids both, because they are caused by opposite things.
 * That is the genuine recovery-versus-grade curve every mineral processing plant lives
 * on, and it is the reason this tier has skill in it rather than a correct answer.
 */
public final class MagneticSeparation {
    /**
     * Susceptibility above which a mineral is worth calling magnetic in prose.
     *
     * <p>Descriptive only. Capture is a continuous function of susceptibility and
     * field ({@link #captureFraction}) — treating it as a hard threshold was the bug
     * that made field strength a free upgrade instead of a trade.
     */
    public static final double MAGNETIC_THRESHOLD = 0.3;

    /**
     * How strongly fine particles are dragged along regardless of magnetism.
     *
     * <p>Entrainment is mechanical, not magnetic: fine powder simply travels with
     * whatever is moving. It is why grinding to dust ruins a concentrate.
     */
    public static final double ENTRAINMENT_STRENGTH = 0.55;

    /** Work for one batch at the widest field, before the drum's own drag adds to it. Moved here
     *  from {@code MagneticSeparatorBlockEntity} (rule 46) so the codex calculator can reference
     *  the real constant for its work-input step instead of a bare literal of its own. */
    public static final int BASE_WORK = 70;

    /** A hand-wound drum, not an industrial one; this caps what the tier can reach. */
    public static final double MAX_FIELD = 0.9;
    public static final double MIN_FIELD = 0.25;

    /** Field strength for a dial position, 0..1 in, {@link #MIN_FIELD}..{@link #MAX_FIELD} out. */
    public static double fieldFor(double dial) {
        return MIN_FIELD + Math.clamp(dial, 0.0, 1.0) * (MAX_FIELD - MIN_FIELD);
    }

    /** And back — used to place the mark on the panel and to read an older save. */
    public static double dialForField(double field) {
        return Math.clamp((field - MIN_FIELD) / (MAX_FIELD - MIN_FIELD), 0.0, 1.0);
    }

    /** The two streams a separator produces. Together they hold the whole feed. */
    public record Result(OreBody concentrate, OreBody tailings) {
        /** Fraction of the feed's contained metal that ended up in the concentrate. */
        public double recovery() {
            double total = concentrate.containedMetalGrams() + tailings.containedMetalGrams();
            return total <= 0 ? 0 : concentrate.containedMetalGrams() / total;
        }

        /** Metal fraction of the concentrate — how good the product actually is. */
        public double grade() {
            return concentrate.isEmpty() ? 0
                    : concentrate.containedMetalGrams() / concentrate.totalMass();
        }
    }

    /**
     * Runs a crushed feed past a magnet.
     *
     * @param feed          the crushed ore
     * @param liberation    fraction of grains freed by crushing, from {@link Comminution}
     * @param fineness      0 for coarse, 1 for finely ground; drives entrainment
     * @param fieldStrength 0..1, how strong the magnet is
     */
    public static Result separate(OreBody feed, double liberation, double fineness,
                                  double fieldStrength) {
        double freed = Math.clamp(liberation, 0.0, 1.0);
        double field = Math.clamp(fieldStrength, 0.0, 1.0);
        double entrained = ENTRAINMENT_STRENGTH * Math.clamp(fineness, 0.0, 1.0);

        Map<Mineral, Double> toConcentrate = new EnumMap<>(Mineral.class);
        Map<Mineral, Double> toTailings = new EnumMap<>(Mineral.class);

        for (Map.Entry<Mineral, Double> entry : feed.masses().entrySet()) {
            Mineral mineral = entry.getKey();
            double mass = entry.getValue();
            double pull = pullFraction(mineral, freed, field, entrained);
            toConcentrate.merge(mineral, mass * pull, Double::sum);
            toTailings.merge(mineral, mass * (1.0 - pull), Double::sum);
        }
        return new Result(rebuild(toConcentrate), rebuild(toTailings));
    }

    /**
     * The magnetic capture criterion, and the reason field strength is a decision.
     *
     * <p>A particle in a magnetic separator feels a force proportional to
     * {@code chi * B * grad(B)} — its susceptibility times the field times the field
     * gradient — and it is captured when that force beats the drag and gravity trying
     * to carry it past. In a drum separator the gradient scales with the field, so the
     * capture term goes as <strong>chi times B squared</strong>.
     *
     * <p>The consequence is the one that matters, and the first version missed it
     * entirely: raising the field does not simply catch <em>more</em> of the good
     * mineral, it <strong>lowers the susceptibility cut point</strong>. A strong field
     * starts capturing the weakly magnetic things too — troilite, pentlandite,
     * iron-bearing silicates — which are mass in the concentrate that carries little
     * or no recoverable metal. That is real, it is why industrial separators are run
     * at a chosen field rather than flat out, and it is what turns the dial from
     * "always maximum" into a choice.
     *
     * <p>The response is a logistic curve rather than a hard threshold because
     * particles vary in size, shape and liberation, so a real separator has a
     * <em>cut</em> with a soft edge rather than a clean line.
     */
    public static double captureFraction(double susceptibility, double fieldStrength) {
        double force = susceptibility * fieldStrength * fieldStrength;
        return 1.0 / (1.0 + Math.exp(-(force - CAPTURE_CUT) / CAPTURE_WIDTH));
    }

    /** Capture term at which half the particles of a mineral report to the magnet. */
    public static final double CAPTURE_CUT = 0.05;

    /** Softness of the cut, from the spread of particle size and liberation. */
    public static final double CAPTURE_WIDTH = 0.018;

    /**
     * What fraction of one mineral's mass follows the magnet.
     *
     * <p>Three effects, pulling in different directions:
     * <ol>
     *   <li>Liberated grains are captured according to their own susceptibility and
     *       the field, per {@link #captureFraction}.</li>
     *   <li>Locked grains behave like the composite particle they are part of, so a
     *       magnetic mineral in a coarse feed is partly lost and a non-magnetic one is
     *       partly dragged in with it.</li>
     *   <li>Fines are entrained mechanically, whatever they are made of.</li>
     * </ol>
     */
    private static double pullFraction(Mineral mineral, double liberation,
                                       double fieldStrength, double entrainment) {
        double free = captureFraction(mineral.magneticSusceptibility(), fieldStrength);
        // A composite particle responds to the average of what it contains, which for
        // chondrite is mostly silicate.
        double locked = captureFraction(MIDDLINGS_SUSCEPTIBILITY, fieldStrength);
        double sorted = liberation * free + (1.0 - liberation) * locked;

        // Entrainment adds indiscriminately and cannot push anything over 100 %.
        return Math.clamp(sorted + entrainment * (1.0 - sorted), 0.0, 1.0);
    }

    /**
     * Susceptibility of an average composite particle in a chondrite.
     *
     * <p>These are the <em>middlings</em>: particles that are part valuable mineral
     * and part silicate. Their susceptibility sits between the two, which is what
     * makes them the mechanism behind the field trade — turn the field up and you
     * start catching middlings, gaining a little metal and a lot of gangue.
     */
    public static final double MIDDLINGS_SUSCEPTIBILITY = 0.045;

    private static OreBody rebuild(Map<Mineral, Double> masses) {
        OreBody body = OreBody.empty();
        for (Map.Entry<Mineral, Double> entry : masses.entrySet()) {
            body = body.with(entry.getKey(), entry.getValue());
        }
        return body;
    }

    private MagneticSeparation() {}
}
