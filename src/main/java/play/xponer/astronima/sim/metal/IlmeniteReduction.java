package play.xponer.astronima.sim.metal;

/**
 * Hydrogen reduction of ilmenite — the lunar/ISRU reaction, run in a bed you have to keep boiling.
 *
 * <pre>
 *   FeTiO₃  +  H₂   →   Fe  +  TiO₂  +  H₂O
 * </pre>
 *
 * <p>The iron oxide half of the ilmenite gives up its oxygen to the hydrogen and comes out as
 * metal; the titanium half is left as titania for v0.62 albedo paint and, vacuum-arc reduced,
 * titanium; the oxygen leaves as water for the loop. One mole of everything — the equation is
 * already balanced.
 *
 * <h2>The hydrogen is spent, and that is the point beside the carbonyl refiner</h2>
 * The Mond process one bench over uses carbon monoxide as a <em>carrier</em> that comes back.
 * Here the hydrogen is a <em>reagent</em>: it takes the oxygen and leaves as water, so it is gone.
 * You hunt CO once and keep it; you must keep supplying H₂. If a completed reduction handed the
 * hydrogen back this would be the refiner with different constants (rule 8), and the contrast the
 * whole phase turns on — carrier versus reagent — would be lost.
 *
 * <h2>What a bed blowing out costs</h2>
 * The bed's contact quality (from {@link CentrifugalBed}) sets how fast it reduces; its
 * entrainment severity blows a fraction of the <em>whole</em> bed — unreacted ilmenite and
 * freshly-made iron alike — out the exhaust each step. Spin too slow and the feed leaves the vent
 * before it can reduce, so the iron recovered is a fraction of the charge. That, not a stall, is
 * the expensive mistake this machine can make.
 *
 * <p>Minecraft-free (rule 1): a bed and a hydrogen supply in, moles moved out.
 */
public final class IlmeniteReduction {

    /** Ilmenite, g/mol: Fe 55.845 + Ti 47.867 + O₃ 47.997. */
    public static final double M_ILMENITE = 151.709;
    /** Hydrogen, g/mol. */
    public static final double M_H2 = 2.016;
    /** Iron, g/mol. */
    public static final double M_IRON = 55.845;
    /** Titania, g/mol: Ti 47.867 + O₂ 31.999. */
    public static final double M_TITANIA = 79.866;
    /** Water, g/mol. */
    public static final double M_WATER = 18.015;

    /** The solids sitting in the drum: what has not reacted, and what has. */
    public record Charge(double ilmeniteMol, double ironMol, double titaniaMol) {

        /** An empty drum. */
        public static Charge empty() {
            return new Charge(0, 0, 0);
        }

        /** A fresh charge of pure ilmenite. */
        public static Charge ofIlmenite(double moles) {
            return new Charge(Math.max(moles, 0), 0, 0);
        }

        /** Total solid mass in the bed, g — for balance checks. */
        public double solidMassGrams() {
            return ilmeniteMol * M_ILMENITE + ironMol * M_IRON + titaniaMol * M_TITANIA;
        }

        /** True once there is nothing left to reduce. */
        public boolean isReduced() {
            return ilmeniteMol <= 1e-9;
        }
    }

    /**
     * One step of a bed sitting at a given contact quality and entrainment severity.
     *
     * @param charge         the solids in the drum
     * @param availableH2Mol hydrogen free to react this step — the hard limit, because it is spent
     * @param contactQuality gas–solid contact, 0..1, from {@link CentrifugalBed#contactQuality}
     * @param entrainedFrac  how hard the bed is blowing out, 0..1, from
     *                       {@link CentrifugalBed#entrainmentSeverity}
     * @param rate           fraction of the possible movement this step performs
     */
    public static Step step(Charge charge, double availableH2Mol,
                            double contactQuality, double entrainedFrac, double rate) {
        double r = Math.clamp(rate, 0, 1);

        // Reduce first: limited by how much ilmenite is present and how well it is contacted, and
        // then hard-capped by the hydrogen, which is consumed one-for-one.
        double couldReduce = Math.max(charge.ilmeniteMol, 0)
                * Math.clamp(contactQuality, 0, 1) * r;
        double reduced = Math.min(couldReduce, Math.max(availableH2Mol, 0));

        double ilmenite = charge.ilmeniteMol - reduced;
        double iron = charge.ironMol + reduced;
        double titania = charge.titaniaMol + reduced;

        // Then entrain: a fraction of the whole bed as it now stands is carried out the exhaust,
        // reacted and unreacted alike. This is where a slow-spun bed loses its charge.
        double lost = Math.clamp(entrainedFrac, 0, 1) * r;
        double lostIlmenite = Math.max(ilmenite, 0) * lost;
        double lostIron = Math.max(iron, 0) * lost;
        double lostTitania = Math.max(titania, 0) * lost;

        Charge next = new Charge(ilmenite - lostIlmenite,
                iron - lostIron, titania - lostTitania);

        return new Step(reduced, reduced, reduced,
                lostIlmenite, lostIron, lostTitania, next);
    }

    /**
     * What one step moved.
     *
     * @param reducedMol      ilmenite turned to metal this step (also the iron and titania gained)
     * @param h2ConsumedMol   hydrogen spent — equal to {@code reducedMol}, and gone, not returned
     * @param waterProducedMol water swept out this step — equal to {@code reducedMol}
     * @param lostIlmeniteMol unreacted ilmenite blown out the exhaust
     * @param lostIronMol     freshly-made iron blown out the exhaust — the painful loss
     * @param lostTitaniaMol  titania blown out the exhaust
     * @param charge          the bed after this step
     */
    public record Step(double reducedMol, double h2ConsumedMol, double waterProducedMol,
                       double lostIlmeniteMol, double lostIronMol, double lostTitaniaMol,
                       Charge charge) {

        /** Total solids that left the drum out the exhaust this step, in moles. */
        public double lostMol() {
            return lostIlmeniteMol + lostIronMol + lostTitaniaMol;
        }
    }

    private IlmeniteReduction() {}
}
