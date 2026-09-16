package play.xponer.astronima.sim.thermal;

/**
 * How much heat a sealed ammonia heat pipe moves between its two ends, in one tick.
 *
 * <p>A heat pipe is not a machine and not a heat source: it is a passive conductor, sealed
 * and charged once, that moves heat from whichever end is hotter to whichever end is colder —
 * far more per unit area than a bare wall, because the working fluid evaporates at the hot end
 * and condenses at the cold end rather than conducting through solid metal alone.
 *
 * <p>{@link #K_EFF_W_PER_M_K} is the real figure, not tuned: a published ammonia-based flat
 * loop heat pipe (additively manufactured, spacecraft-representative) measures an effective
 * thermal conductivity of 6050-7730 W/(m*K) across -45 to 20 deg C — one to two orders of
 * magnitude above solid copper (~400 W/(m*K)). This uses the conservative end of that range.
 * {@link #CROSS_SECTION_M2} is a real, modest heat-pipe bore (about 36 mm across), stated as an
 * assumption rather than measured from anything in-game — nothing else in this mod specifies
 * pipe diameter either (see {@code design/ammonia-heat-pipes.md} §2).
 *
 * <p>Minecraft-free (rule 1): temperatures in, watts out.
 */
public final class HeatPipeTransfer {

    /** Effective thermal conductivity of the working fluid, W/(m*K). */
    public static final double K_EFF_W_PER_M_K = 6050.0;

    /** A real, modest heat-pipe bore, m^2 (about 36 mm across). */
    public static final double CROSS_SECTION_M2 = 0.001;

    /** One block, the same "1 block = 1 m" scale {@code RoomState.volumeM3()} already assumes. */
    public static final double LENGTH_M = 1.0;

    /** Conductance of one pipe segment, W/K. */
    public static double conductanceWPerK() {
        return K_EFF_W_PER_M_K * CROSS_SECTION_M2 / LENGTH_M;
    }

    /**
     * Heat flow through the pipe, in watts — always zero or positive, and the same regardless
     * of which end is passed first.
     *
     * <p>Proportional to the real temperature difference, never a fixed rate: two ends at the
     * same temperature move no heat, and the pipe does not care which end started hotter — real
     * heat pipes are bidirectional, so the direction is decided by the caller reading which end
     * this result should come out of, not by argument order here.
     */
    public static double watts(double tempAK, double tempBK) {
        return Math.abs(conductanceWPerK() * (tempAK - tempBK));
    }

    private HeatPipeTransfer() {}
}
