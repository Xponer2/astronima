package play.xponer.astronima.sim.cryo;

/**
 * Heat leaking into a stored cryogenic liquid, and what that does to it (design/cryogenics.md
 * §1.2). The same conduction law {@code design/thermal.md} already uses for a habitat's hull,
 * applied to a much colder set point: a dewar sitting in a warm room always has heat flowing
 * *in*, and since the liquid is already at its own boiling point, every joule that arrives goes
 * straight into boiling some of it rather than raising its temperature.
 */
public final class BoilOff {

    /** A real vacuum-multilayer-insulated dewar's effective heat transfer coefficient, W/(m^2*K).
     * Published range is 0.05-1 W/m^2K depending on build quality; this is the honest middle for
     * something built in a workshop rather than a national lab. */
    public static final double DEWAR_U_VALUE = 0.30;

    /** The aerogel-wrapped upgrade's U-value, W/(m^2*K) — a real high-performance
     * vacuum-jacket-plus-aerogel dewar. Aerogel's own conductivity is ~0.013-0.02 W/(m*K); a
     * hand-wrapped layer does not reach a second full vacuum jacket, so this is the honest
     * middle rather than aerogel's own best-case number. */
    public static final double AEROGEL_WRAPPED_U_VALUE = 0.05;

    /** The vessel's surface area, m^2 — a *sphere* of {@code PressureVessel.TANK_VOLUME_M3}
     * (2 m^3), via {@code A = (36*pi*V^2)^(1/3)}. A sphere minimises surface area for a given
     * volume, so a real cylindrical dewar of the same capacity leaks more, never less — this
     * errs toward the player, and says so (design/cryogenics.md §2). */
    public static final double TANK_SURFACE_AREA_M2 = 7.67;

    /**
     * Heat leaking into the tank from a room at {@code roomK}, watts. Clamped to zero rather
     * than negative: a room colder than the liquid's own boiling point does not actively
     * refrigerate the tank further (this model has no mechanism for that — the cryocooler is the
     * only thing that removes heat), it simply stops adding any.
     */
    public static double heatLeakWatts(double uValue, double roomK, double boilingPointK) {
        return Math.max(0.0, uValue * TANK_SURFACE_AREA_M2 * (roomK - boilingPointK));
    }

    /**
     * How many moles boil off in {@code dtSeconds} given a heat leak of {@code heatLeakWatts},
     * capped at whatever liquid is actually left.
     */
    public static double molesBoiledOff(double heatLeakWatts, double dtSeconds,
                                         double latentHeatJPerMol, double liquidMolesAvailable) {
        if (heatLeakWatts <= 0.0 || liquidMolesAvailable <= 0.0) {
            return 0.0;
        }
        double moles = heatLeakWatts * dtSeconds / latentHeatJPerMol;
        return Math.min(moles, liquidMolesAvailable);
    }

    private BoilOff() {}
}
