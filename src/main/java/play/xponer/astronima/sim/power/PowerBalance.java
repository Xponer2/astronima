package play.xponer.astronima.sim.power;

/**
 * Electricity, in the units everything else in this mod is already in.
 *
 * <p>Watts and joules, not an invented currency — and that is not tidiness, it is what makes
 * the central rule of this tier expressible at all. <strong>Every watt a machine consumes is a
 * watt of heat in the room it is standing in.</strong> In SI that is one number and a law; with
 * an invented unit it would be two numbers and a fudge factor between them, and the fudge
 * factor is where the physics goes to die.
 *
 * <h2>Why that rule is the whole phase</h2>
 * On Earth waste heat is ignorable because air carries it away. In vacuum there is no
 * convection and the only way out is radiating, which is why the ISS's hard problem is cooling
 * rather than power. So v0.4 spent itself teaching the player to fight <em>for</em> heat — bury
 * it, insulate it, work in it — and this tier inverts that fight without changing a single
 * instrument. A well-insulated habitat is the worst possible place to run a machine.
 *
 * <p>Minecraft-free (rule 1): watts and seconds in, joules and watts out.
 */
public final class PowerBalance {

    /**
     * Sunlight at the asteroid, W/m².
     *
     * <p>1361 W/m² at Earth, falling as the inverse square of distance; the main belt is
     * 2.1–3.3 AU and this takes the middle of it. Not a balance figure — move the asteroid and
     * the number moves with it.
     */
    public static final double SOLAR_FLUX_W_PER_M2 = 1361.0 / (2.7 * 2.7);

    /**
     * What a panel actually converts, 0..1.
     *
     * <p>Twenty per cent is an ordinary silicon panel, and deliberately ordinary: this is the
     * first electrical tier on a rock, not spacecraft-grade triple-junction.
     */
    public static final double PANEL_EFFICIENCY = 0.20;

    /**
     * Output of one square metre of array in full sun, W.
     *
     * <p><strong>Thirty-seven watts, and that number is the phase.</strong> A hand-cranked
     * machine is 250 W, so it takes <em>seven panels to replace one person on a handle</em> —
     * which is not a nerf, it is the belt. It makes the first generator feel like what it is,
     * and it earns the rest of the chain (fuel cell, RTG) instead of announcing it.
     */
    public static final double PANEL_WATTS = SOLAR_FLUX_W_PER_M2 * PANEL_EFFICIENCY;

    /** What a panel makes right now, given how much sun is on it. */
    public static double panelWatts(double sunlight) {
        return PANEL_WATTS * Math.clamp(sunlight, 0.0, 1.0);
    }

    /**
     * Energy a source of this many watts delivers over this long, in joules.
     *
     * <p>A joule is a watt-second. There is no conversion here and there is not meant to be —
     * the absence of one is the point.
     */
    public static double joules(double watts, double seconds) {
        return Math.max(watts, 0) * Math.max(seconds, 0);
    }

    /**
     * How much a store can actually accept, given what it holds and how big it is.
     *
     * <p>Returned rather than applied, so the caller has to decide what happens to the
     * remainder — which for a solar panel is "it was never made" and for a battery being
     * charged from another is "it stays where it was". A method that silently swallowed the
     * excess would make the two indistinguishable.
     */
    public static double acceptable(double storedJ, double capacityJ, double offeredJ) {
        return Math.clamp(offeredJ, 0, Math.max(capacityJ - storedJ, 0));
    }

    /**
     * How much a draw actually gets: what it asked for, or what is there.
     *
     * <p>Partial supply is deliberate. A machine that refused to run at all below its full
     * draw would make the difference between six panels and seven a cliff, when the honest
     * answer — and the more legible one — is that it runs more slowly.
     */
    public static double drawn(double storedJ, double wantedJ) {
        return Math.clamp(wantedJ, 0, Math.max(storedJ, 0));
    }

    /**
     * The heat a consumer puts into its room, W.
     *
     * <p><strong>All of it.</strong> Not a fraction, not an efficiency — a crusher turns
     * electricity into broken rock and friction, and the rock is still in the room. Some of it
     * leaves as chemical potential in a reaction, and none of the machines in this tier do
     * that; when one does, that machine can subtract its own enthalpy and say so.
     *
     * <p>This is the same claim {@code HeatBalance.machineWatts} already makes about a
     * hand-cranked machine — the operator's effort ends as heat — so the two energy sources
     * agree about the consequence, which is exactly what {@code machines.md} §7 demands.
     */
    public static double wasteHeatWatts(double drawWatts) {
        return Math.max(drawWatts, 0);
    }

    /**
     * How much faster a machine runs on this much of its rated draw.
     *
     * <p>Capped at the rated draw: power buys the rate, and only up to the rate the machine
     * was built for. <strong>Faster, never better</strong> — {@code machines.md} §7 wrote that
     * contract before this tier existed, and it is what stops the energy source changing the
     * physics. The grind, the liberation and the cost curve are untouched.
     */
    public static double speedFactor(double suppliedWatts, double ratedWatts) {
        if (ratedWatts <= 0) {
            return 0;
        }
        return Math.clamp(suppliedWatts / ratedWatts, 0.0, 1.0);
    }

    private PowerBalance() {}
}
