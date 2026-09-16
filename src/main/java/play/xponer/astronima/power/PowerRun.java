package play.xponer.astronima.power;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import play.xponer.astronima.atmosphere.Atmosphere;
import play.xponer.astronima.sim.circuit.Conductor;
import play.xponer.astronima.sim.circuit.ConductorMaterial;
import play.xponer.astronima.sim.circuit.Delivery;

/**
 * Putting a run's losses where they physically go.
 *
 * <p>A cable turns what it does not deliver into heat, <strong>along its own body</strong> — so
 * the joules are spread across the cable blocks rather than dropped at either end. That is not
 * pedantry: the array is usually outdoors and the machine is usually in the workshop, and
 * depositing the loss at either would put the warmth somewhere the player did not lay it.
 *
 * <p>Which is the whole reason cables are worth having as a mechanic rather than a tax. In a
 * game about fighting cold, <em>where the loss lands</em> is a decision.
 */
public final class PowerRun {

    /**
     * Sends energy down a legacy cable run and puts what it costs where the cable is.
     *
     * <p><strong>One call, because two were a trap.</strong> Every caller used to deliver and
     * shed separately, and forgetting the second half made joules cease to exist — the exact
     * disappearance {@code PowerWiringTest} was written after somebody did it. A single method
     * that returns what arrives <em>and</em> deposits what did not makes that impossible to write.
     *
     * <p><strong>And it is the same law the routed traces use.</strong> {@code Delivery} takes a
     * resistance and a voltage and does {@code I²R}; the cable path was the last thing in the mod
     * still asking for a flat percentage per block, which {@code design/electrical.md} opens by
     * promising to delete. A percentage cannot tell a short run from a long one at a different
     * load, and this can.
     *
     * @param seconds the interval this energy was made or drawn over — {@code I²R} is a rate
     * @return what reaches the far end, in joules
     */
    public static double sendAlong(ServerLevel level, CableNetworks.Resolved run, double joules,
                                   double seconds) {
        Delivery.Split split = Delivery.send(joules, seconds, resistanceOhms(run),
                Delivery.BUS_VOLTS);
        shedAlong(level, run, split.heatJ());
        return split.deliveredJ();
    }

    /**
     * What a legacy cable run resists, in ohms.
     *
     * <p>Iron at the standard gauge over its own length: a cable block is a metre, and the metal
     * is the one the recipe was always made of. Stated here rather than stored on the block
     * because a legacy cable carries no material of its own — it predates the conductor ladder,
     * and inventing a field on it now would mean migrating save data to describe something that
     * can no longer be built.
     */
    public static double resistanceOhms(CableNetworks.Resolved run) {
        return new Conductor(ConductorMaterial.IRON, Conductor.mm2(Conductor.STANDARD_MM2),
                run.length()).resistance(ConductorMaterial.REFERENCE_K);
    }

    /**
     * What fraction of a delivery this size would survive the run, 0..1.
     *
     * <p><strong>Side-effect free, and that is why it exists separately.</strong> A machine has to
     * know what a full draw would cost <em>before</em> it asks the cells for it, so that it is
     * starved rather than quietly having the loss forgiven — and asking {@link #sendAlong} would
     * deposit heat for a delivery that never happened. Two methods because they are two
     * questions: one asks, one does.
     */
    public static double efficiency(CableNetworks.Resolved run, double joules, double seconds) {
        return Delivery.send(joules, seconds, resistanceOhms(run), Delivery.BUS_VOLTS)
                .efficiency();
    }

    /** Spreads {@code joules} of resistive loss evenly over the cables that produced it. */
    public static void shedAlong(ServerLevel level, CableNetworks.Resolved run, double joules) {
        if (joules <= 0 || run.cables().isEmpty()) {
            return;
        }
        double each = joules / run.cables().size();
        Atmosphere atmosphere = Atmosphere.get(level);
        for (BlockPos cable : run.cables()) {
            atmosphere.addHeatJoules(cable, each);
        }
    }

    private PowerRun() {}
}
