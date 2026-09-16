package play.xponer.astronima.sim;

import play.xponer.astronima.sim.physio.Barotrauma;

/**
 * Active gas movement — what pumps and compressors do, as opposed to the passive
 * equalisation in {@link GasFlow}.
 *
 * <p>A positive-displacement pump sweeps a fixed <em>volume</em> per second at inlet
 * conditions, so the moles it moves fall as the inlet empties ({@code n = PV/RT}).
 * It cannot compress without limit either: past its rated outlet pressure it stalls,
 * which is why a small pump cannot fill a tank to arbitrary pressure.
 *
 * <p><strong>It also governs its own intake rate</strong> so it never depressurises
 * whatever it draws from faster than an unprotected person standing in it could survive —
 * real equipment moving gas out of an occupied volume is built with exactly this kind of
 * flow governor, for exactly this reason. A fixed swept volume is proportional to source
 * pressure ({@code n = PV/RT} again), so its <em>largest</em> fractional bite — and largest
 * absolute kPa/s — lands at the moment the source is fullest, not once it has thinned out.
 * A chamber-sized pump sized for a brisk pump-down (rule 41: a playtest number, "clears a
 * chamber in about twenty seconds") clears {@link Barotrauma#SAFE_FALL_KPA_PER_S} by nearly
 * double at one atmosphere — found only once the transfer was actually spread across real
 * ticks instead of dumped once per {@code GasPumpBlockEntity.INTERVAL_TICKS} (PLAN.md); the
 * lump had been hiding it, not avoiding it. Governing the source rate, not merely the
 * per-tick shape of an already-too-fast average, is what actually keeps this safe regardless
 * of how the caller schedules its calls.
 */
public final class GasTransfer {
    /** Below this volumetric efficiency the pump is treated as fully stalled. */
    private static final double STALL_EFFICIENCY = 0.01;

    /**
     * How far under {@link Barotrauma#SAFE_FALL_KPA_PER_S} the governor actually caps at.
     * Real margin, not a rounding buffer: {@code severity} floors at
     * {@code Barotrauma.MINIMUM_DAMAGE} for a rate even fractionally over the limit, so
     * landing exactly on the boundary is indistinguishable from landing just past it.
     */
    private static final double SAFETY_MARGIN = 0.9;

    /**
     * Runs a pump for {@code dtSeconds}.
     *
     * @param displacementM3PerS swept volume per second at the inlet
     * @param maxOutletKPa       pressure at which the pump stalls
     * @return moles actually moved (0 when stalled or the inlet is empty)
     */
    public static double pump(RoomState from, RoomState to,
                              double displacementM3PerS, double dtSeconds, double maxOutletKPa) {
        if (displacementM3PerS <= 0 || dtSeconds <= 0) {
            return 0;
        }
        double outletPressure = to.pressureKPa();
        if (outletPressure >= maxOutletKPa) {
            return 0; // stalled against its own outlet
        }
        double available = from.gases().totalMoles();
        if (available <= 0) {
            return 0;
        }
        // Volumetric efficiency falls as the pump works against its outlet: back-flow
        // past the seals grows with pressure ratio, so throughput tapers to zero at
        // the rated limit rather than stopping abruptly (and never overshoots it).
        double efficiency = 1.0 - outletPressure / maxOutletKPa;
        if (efficiency < STALL_EFFICIENCY) {
            return 0; // doing no useful work; treat as stalled so machines can idle
        }
        // Moles contained in the volume swept this step, at inlet conditions.
        double sweptMoles = GasMixture.totalMolesFor(
                from.pressureKPa(), displacementM3PerS * dtSeconds, from.temperatureK()) * efficiency;
        // The governor: never remove more than would drop the source's own pressure at
        // Barotrauma's own safe rate, over this exact step's own duration - see the class
        // doc for why the uncapped law's worst case lands at full source pressure, not empty.
        // Capped to a genuine margin below the threshold, not the exact boundary: severity
        // floors at MINIMUM_DAMAGE the instant the rate is even fractionally over the limit
        // (Barotrauma.damage has no partial credit below it), so landing exactly on a
        // floating-point-noisy boundary is not actually safe - only comfortably under it is.
        double maxSafeMoles = GasMixture.totalMolesFor(
                SAFETY_MARGIN * Barotrauma.SAFE_FALL_KPA_PER_S * dtSeconds,
                from.volumeM3(), from.temperatureK());
        sweptMoles = Math.min(sweptMoles, maxSafeMoles);
        double toMove = Math.min(sweptMoles, available);
        if (toMove <= 0) {
            return 0;
        }
        GasMixture taken = from.gases().extractFraction(toMove / available);
        double moved = taken.totalMoles();
        to.addMixtureAt(taken, from.temperatureK());
        return moved;
    }

    /**
     * Pulls one species out of a stream into a collector, first-order in its partial
     * pressure — the behaviour of a real sorbent bed or membrane.
     *
     * @param rateMolPerSPerKPa capture rate per kPa of the target's partial pressure
     * @param capacityMol       remaining capacity of the collector
     * @return moles captured
     */
    public static double filter(RoomState stream, Gas target, RoomState collector,
                                double rateMolPerSPerKPa, double dtSeconds, double capacityMol) {
        if (capacityMol <= 0) {
            return 0;
        }
        double want = rateMolPerSPerKPa * stream.partialPressureKPa(target) * dtSeconds;
        double captured = stream.removeGas(target, Math.min(want, capacityMol));
        if (captured > 0) {
            collector.addGasAt(target, captured, stream.temperatureK());
        }
        return captured;
    }

    private GasTransfer() {}
}
