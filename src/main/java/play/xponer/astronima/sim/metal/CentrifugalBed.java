package play.xponer.astronima.sim.metal;

/**
 * A fluidized bed you spin, because you cannot fluidize one any other way out here.
 *
 * <p>A classic fluidized bed is a balance — gas drag lifting the powder up against its weight
 * pulling it down, and the bed hangs boiling in between. On an asteroid there is no weight worth
 * balancing: at the body's honest ~1/70 g the whole thing is a dust cloud the faintest draught
 * carries off. So this machine makes its own gravity. Spin the drum and the particles are flung
 * against its perforated wall by the centripetal acceleration {@code a = ω²r}, exactly as weight
 * would hold them to a floor; blow the reducing hydrogen inward through that wall and it
 * fluidizes them against the artificial gravity the player dialled up.
 *
 * <h2>The one dial is the spin, and it brackets a good band with two opposite failures</h2>
 * The gas flow is fixed — the machine runs the hydrogen at the rate the chemistry needs. What
 * the player trims is the drum speed:
 * <ul>
 *   <li><strong>Too slow</strong> → artificial gravity too weak to hold the bed against the
 *       fixed flow → the powder is carried up and <em>out of the exhaust</em>. Feed and fresh
 *       iron leave the vent unrecovered. ({@code Ugas ≥ terminal velocity}.)</li>
 *   <li><strong>In the band</strong> → the bed boils: vigorous contact, water swept, iron
 *       reduced. ({@code Umf ≤ Ugas < terminal}.)</li>
 *   <li><strong>Too fast</strong> → artificial gravity so strong the flow can no longer lift the
 *       bed → it packs to the wall, the gas channels, and reduction stalls.
 *       ({@code Ugas < Umf}.)</li>
 * </ul>
 *
 * <h2>The window is the crusher's, and its lesson is the winnowing table's inverted</h2>
 * Both velocities scale with particle size, so the whole usable spin window slides with how
 * finely the ilmenite was ground: a fine dust must be spun fast to be pinned; a coarse grind
 * packs solid if spun fast. There is no "better" grind here as there is at the winnowing table —
 * only a <em>match</em> between how fine you ground and how fast you spin.
 *
 * <p>Minecraft-free (rule 1): a grain size and a drum speed in, a regime out.
 */
public final class CentrifugalBed {

    /** Drum wall radius, m — about one block's half-width. The lever {@code a = ω²r} turns on. */
    public static final double DRUM_RADIUS_M = 0.40;

    /**
     * Superficial hydrogen velocity the machine holds, m/s.
     *
     * <p><strong>Fixed, not a dial.</strong> It is set by the chemistry — fast enough to reduce
     * the ilmenite and, as importantly, to keep sweeping the product water out so the reaction
     * does not stall at equilibrium. The player never touches it; they trim the spin to sit this
     * flow inside the fluidization window.
     */
    public static final double GAS_VELOCITY_MPS = 0.15;

    /** The reduction temperature the heater holds, K — about 1000 °C, a documented ilmenite figure. */
    public static final double REDUCTION_TEMPERATURE_K = 1273.15;

    /**
     * Hydrogen density at {@link #REDUCTION_TEMPERATURE_K} and 1 atm, kg/m³.
     *
     * <p>Ideal gas: {@code PM/RT = 101325 · 0.002016 / (8.314 · 1273.15)}. Hardcoded rather than
     * derived from a gas model, which is out of this class's scope, but the arithmetic is here so
     * it is not a magic number.
     */
    public static final double GAS_DENSITY_KGM3 = 0.01930;

    /** Hydrogen viscosity at {@link #REDUCTION_TEMPERATURE_K}, Pa·s — ~8.8e-6 at 293 K scaled ~T^0.7. */
    public static final double GAS_VISCOSITY_PAS = 2.46e-5;

    /** Ilmenite grain density, kg/m³. */
    public static final double SOLID_DENSITY_KGM3 = 4790.0;

    /**
     * Fluidization number ({@code Ugas/Umf}) at which gas–solid contact is fully developed.
     *
     * <p>Right at minimum fluidization the bed is barely lifting and contact is poor; a little
     * above it the bed is vigorously bubbling and well mixed. Two Umf is where a real bed is
     * comfortably boiling, so contact ramps from nothing at Umf to full here.
     */
    public static final double FULL_CONTACT_RATIO = 2.0;

    /** Which of the three states the bed is in. */
    public enum Regime {
        /** Spun too fast: pinned to the wall, the fixed flow cannot lift it, gas channels through. */
        PACKED,
        /** The good band: boiling, well contacted, water swept. */
        BOILING,
        /** Spun too slow: the fixed flow exceeds terminal velocity and carries the bed out the exhaust. */
        BLOWING_OUT
    }

    /**
     * Artificial gravity at the drum wall, m/s². {@code a = ω²r}, with {@code ω = 2π · rpm / 60}.
     *
     * <p>This is the whole trick: it stands in for {@code g} in every velocity below, so a machine
     * spun faster behaves like a bed under higher gravity.
     */
    public static double artificialGravity(double rpm) {
        double omega = 2 * Math.PI * Math.max(rpm, 0) / 60.0;
        return omega * omega * DRUM_RADIUS_M;
    }

    /**
     * Archimedes number for a grain of this size under this acceleration — the dimensionless
     * group both fluidization correlations turn on.
     */
    public static double archimedes(double particleSizeMicrons, double accelerationMps2) {
        double d = Math.max(particleSizeMicrons, 1e-6) * 1e-6;
        return GAS_DENSITY_KGM3 * (SOLID_DENSITY_KGM3 - GAS_DENSITY_KGM3)
                * accelerationMps2 * d * d * d / (GAS_VISCOSITY_PAS * GAS_VISCOSITY_PAS);
    }

    /**
     * Minimum fluidization velocity, m/s — below this the bed sits packed. Wen &amp; Yu, with the
     * artificial gravity substituted for {@code g}.
     */
    public static double minFluidizationVelocity(double particleSizeMicrons, double accelerationMps2) {
        double d = Math.max(particleSizeMicrons, 1e-6) * 1e-6;
        double ar = archimedes(particleSizeMicrons, accelerationMps2);
        double reMf = Math.sqrt(33.7 * 33.7 + 0.0408 * ar) - 33.7;
        return reMf * GAS_VISCOSITY_PAS / (GAS_DENSITY_KGM3 * d);
    }

    /**
     * Terminal (entrainment) velocity, m/s — above this the gas carries the grain out. Haider &amp;
     * Levenspiel's closed-form correlation for a sphere, again with the artificial gravity in
     * place of {@code g}.
     */
    public static double terminalVelocity(double particleSizeMicrons, double accelerationMps2) {
        double ar = archimedes(particleSizeMicrons, accelerationMps2);
        double dStar = Math.cbrt(ar);
        double uStar = 1.0 / (18.0 / (dStar * dStar) + 0.5909 / Math.sqrt(dStar));
        double scale = Math.cbrt(GAS_VISCOSITY_PAS * (SOLID_DENSITY_KGM3 - GAS_DENSITY_KGM3)
                * accelerationMps2 / (GAS_DENSITY_KGM3 * GAS_DENSITY_KGM3));
        return uStar * scale;
    }

    /**
     * Which regime a bed of this grind is in at this drum speed.
     *
     * <p>The fixed gas velocity is compared against the two grind-and-spin-dependent velocities:
     * below minimum fluidization it is {@link Regime#PACKED}, at or above terminal it is
     * {@link Regime#BLOWING_OUT}, and between them it {@link Regime#BOILING}.
     */
    public static Regime regimeAt(double particleSizeMicrons, double rpm) {
        double a = artificialGravity(rpm);
        if (GAS_VELOCITY_MPS < minFluidizationVelocity(particleSizeMicrons, a)) {
            return Regime.PACKED;
        }
        if (GAS_VELOCITY_MPS >= terminalVelocity(particleSizeMicrons, a)) {
            return Regime.BLOWING_OUT;
        }
        return Regime.BOILING;
    }

    /**
     * How well the gas contacts the solids, 0..1 — what drives the reduction rate.
     *
     * <p>Zero when packed (the gas channels past a fixed bed and reaches almost none of it),
     * ramping from the minimum-fluidization threshold to full contact once the flow is
     * {@link #FULL_CONTACT_RATIO} times minimum fluidization. It stays high while blowing out —
     * a dilute entrained bed is beautifully contacted; the trouble there is that it is leaving,
     * which is {@link #entrainmentSeverity} rather than this.
     */
    public static double contactQuality(double particleSizeMicrons, double rpm) {
        double a = artificialGravity(rpm);
        double umf = minFluidizationVelocity(particleSizeMicrons, a);
        if (umf <= 0) {
            return 1.0;
        }
        double ratio = GAS_VELOCITY_MPS / umf;
        return Math.clamp((ratio - 1.0) / (FULL_CONTACT_RATIO - 1.0), 0.0, 1.0);
    }

    /**
     * How hard the bed is blowing out, 0..1 — the fraction-driving term for solids lost out the
     * exhaust each step.
     *
     * <p>Zero until the fixed flow reaches terminal velocity, then ramping: just past terminal
     * only the finest tail leaves slowly, and by twice terminal the bed is emptying fast. A bed
     * spun far too slow loses its charge before it can reduce.
     */
    public static double entrainmentSeverity(double particleSizeMicrons, double rpm) {
        double a = artificialGravity(rpm);
        double ut = terminalVelocity(particleSizeMicrons, a);
        if (ut <= 0) {
            return 1.0;
        }
        return Math.clamp(GAS_VELOCITY_MPS / ut - 1.0, 0.0, 1.0);
    }

    private CentrifugalBed() {}
}
