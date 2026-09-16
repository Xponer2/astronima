package play.xponer.astronima.sim.pipe;

/**
 * A bore you can narrow.
 *
 * <p>A valve is not a percentage applied to a flow rate. It is a smaller hole, and a
 * smaller hole obeys the same Hagen-Poiseuille law as a pipe — so conductance goes as
 * the fourth power of the opening.
 *
 * <p>That has a consequence worth teaching: <strong>a valve at half open passes a
 * sixteenth of the flow, not half.</strong> Almost all of a valve's useful range sits in
 * its last quarter of travel, which is exactly what anyone who has throttled a real line
 * discovers. It falls straight out of physics already in {@link Conduit} rather than
 * being a curve chosen to feel good.
 */
public final class Valve {
    /** Settings a player can select, as fractions of full bore. */
    public static final int SETTINGS = 5;

    /**
     * Effective bore at a setting, 0 (shut) to {@link #SETTINGS}-1 (full open).
     */
    public static double openFraction(int setting) {
        if (setting <= 0) {
            return 0;
        }
        return Math.min(1.0, setting / (double) (SETTINGS - 1));
    }

    /**
     * The fraction of full flow a bore this wide passes — the fourth-power law, once.
     *
     * <p>Separate from {@link #conductance} because two different things ask it: the
     * physics, which wants a conductance, and every gauge in the mod, which wants a
     * percentage to show. They must not be two calculations, or the number on the valve
     * eventually stops matching the gas that comes out of it.
     */
    public static double flowFraction(double openFraction) {
        double open = Math.clamp(openFraction, 0.0, 1.0);
        double squared = open * open;
        return squared * squared;
    }

    /**
     * Conductance of a run through a valve at the given setting.
     *
     * <p>Shut means shut: zero, not a trickle. A valve that still passed gas when closed
     * would make isolating a section impossible, and isolating a section is the entire
     * reason to fit one.
     */
    public static double conductance(double fullBoreConductance, int setting) {
        // Conductance goes as r^4, so scaling the bore by f scales conductance by f^4.
        return fullBoreConductance * flowFraction(openFraction(setting));
    }

    private Valve() {}
}
