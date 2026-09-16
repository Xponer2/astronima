package play.xponer.astronima.wire;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import play.xponer.astronima.atmosphere.Atmosphere;
import play.xponer.astronima.sim.circuit.Ampacity;
import play.xponer.astronima.sim.circuit.Cooling;
import play.xponer.astronima.sim.circuit.Delivery;
import play.xponer.astronima.sim.circuit.Scorch;
import play.xponer.astronima.sim.circuit.ThermalMass;
import play.xponer.astronima.sim.logic.PartType;
import play.xponer.astronima.sim.wire.WirePixel;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * How hot the wire is, and what happens when that is too hot.
 *
 * <h2>Why the temperature is stored rather than computed</h2>
 * {@link Cooling} says where a wire <em>ends up</em> at a given current. It cannot say where the
 * wire is <em>now</em>, and now is what the player sees and what decides whether anything is lost.
 * A run that failed the instant a machine drew through it would be right about the destination and
 * wrong about every second of the journey — and would fire before its own warning could be read,
 * which rule 7 forbids outright.
 *
 * <p>So each trace carries its own temperature. It is a property of a real, addressable thing —
 * one cell, one face, one colour — and not of a <em>network</em>, which is the distinction rule 16
 * is about: a network has no identity to hang state on, and a trace has nothing else.
 *
 * <h2>Heat where the current is, cooling everywhere</h2>
 * Warming and cooling both happen in {@code WireTicker}, once per trace per tick, over every trace
 * whether or not anybody is using it — a wire that only cooled while it was being used would never
 * cool at all, and one warmed by each consumer in turn ran its own clock once per machine.
 */
public final class WireHeat {

    /**
     * How coarsely a temperature has to change before the clients are told.
     *
     * <p>Ten kelvin, matching the quantisation the chunk's own stream codec applies — so a packet
     * is only sent when the number on the far side would actually differ.
     *
     * <p><strong>The value itself is always kept.</strong> Skipping the write for a small change
     * is what broke the first version: a wire warming a degree a second read its stored value,
     * added a degree, decided that was not worth writing and discarded it, so it never warmed at
     * all. Saving is not the same decision as syncing.
     */
    private static final double WORTH_SENDING_K = 10.0;

    /** Where a trace sits when nothing is driving it: the room it is in, or the rock outside. */
    public static double ambientAt(ServerLevel level, BlockPos cell) {
        Atmosphere.RoomReading room = Atmosphere.get(level).readingAt(cell);
        return room == null ? Cooling.VACUUM_AMBIENT_K : room.state().temperatureK();
    }

    /** How much air there is around that cell to carry heat away, 0..1. */
    public static double airAt(ServerLevel level, BlockPos cell) {
        Atmosphere.RoomReading room = Atmosphere.get(level).readingAt(cell);
        return room == null ? 0 : Cooling.airFraction(room.state().pressureKPa());
    }

    /**
     * Relaxes a trace toward whatever it would settle at, and fails it if that is too hot.
     *
     * <p>One call for both warming and cooling, because they are the same equation with a
     * different current — and two of them would be two places for the time constant to disagree.
     */
    public static void step(ServerLevel level, WireTrace trace, double amps, double seconds) {
        BlockPos cell = trace.cell();
        double ambient = ambientAt(level, cell);
        double air = airAt(level, cell);
        var metre = trace.gauge().metre(trace.material());

        double steady = Ampacity.steadyTemperatureK(metre, amps, ambient, air);
        double tau = ThermalMass.timeConstantSeconds(metre, trace.temperatureK(), ambient, air);
        double now = ThermalMass.after(trace.temperatureK(), steady, tau, seconds);

        if (Scorch.hasFailed(now)) {
            burn(level, trace);
            return;
        }
        boolean worthSending = Math.round(now / WORTH_SENDING_K)
                != Math.round(trace.temperatureK() / WORTH_SENDING_K);
        Wires.replace(level, trace.at(now), worthSending);
    }

    /**
     * Gives up any fuse on this run that is over its rating.
     *
     * @return true when one went, so the wire is not also cooked by the same current in the same
     *         call — the fuse interrupted it, and the run it was protecting is already two runs
     */
    /**
     * Gives up any fuse on this run that is over its rating.
     *
     * <p>The fuse goes <strong>first</strong>, and first is the whole point of it: a fuse is rated
     * below the conductor it protects, so at any current that would eventually cook the wire it is
     * already past its own limit — and it gives up at once where the wire takes minutes, because
     * being faster than the cable is what a fuse is for.
     *
     * @return true when one went, so the wire is not also cooked by the same current in the same
     *         tick — the fuse interrupted it, and what it was protecting is already two runs
     */
    public static boolean protect(ServerLevel level, WirePower.Run run, double amps) {
        boolean went = false;
        for (Surface surface : surfaces(run)) {
            for (WirePart part : Wires.partsOn(level, surface.cell(), surface.face())) {
                // Asked of the part, not compared against a constant here: a breaker and a fuse
                // give up at the same current and this check does not need to know that, so
                // whatever protection comes next is covered by the code that already exists.
                // Rated by the run it is spliced into, not by a number of its own: a fuse is a
                // deliberately weak length of the same conductor, so it gives up where that
                // conductor would. Twelve amps flat protected 4 mm2 iron and nothing thinner - a
                // signal run gives up at 2.9 A and the fuse was still waiting for twelve.
                if (!part.type().isBridge() || part.held() || !onRun(run, part)
                        || amps <= play.xponer.astronima.sim.circuit.Protection
                                .ratingAmps(run.gauge(), run.metal())) {
                    continue;
                }
                // A breaker that is thrown back into a fault trips again right here, on the very
                // next settlement, because nothing about a reset asks whether the fault is gone.
                Wires.mount(level, part.withHeld(true, 0L));
                level.playSound(null, part.cell(),
                        part.type() == PartType.BREAKER
                                ? SoundEvents.LEVER_CLICK : SoundEvents.COPPER_BULB_TURN_OFF,
                        SoundSource.BLOCKS, 0.8f, 0.6f);
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE,
                        part.cell().getX() + 0.5, part.cell().getY() + 0.5,
                        part.cell().getZ() + 0.5, 8, 0.15, 0.15, 0.15, 0.01);
                went = true;
            }
        }
        return went;
    }

    /** Whether this run actually passes through that part, rather than merely sharing a face. */
    private static boolean onRun(WirePower.Run run, WirePart part) {
        for (PartType.Pad pad : part.type().pads()) {
            if (run.pixels().contains(part.padPixel(pad))) {
                return true;
            }
        }
        return false;
    }

    /**
     * A cooking run gives itself away, without the goggles.
     *
     * <p><strong>The instrument must not be the only way to learn this</strong> (rule 9's order of
     * preference): a hazard you can only see by wearing the right eyewear is one most players meet
     * for the first time as a hole in their base. Smoke is readable across a room by anybody, it
     * is what overheating insulation actually does, and it thickens as the margin disappears.
     *
     * <p>Rate rises with the cooking, so an early warning is a wisp and a wire about to go is
     * obvious. Called on the ticker's cadence rather than per delivery, because a machine drawing
     * twenty times a second would otherwise bury the world in particles.
     */
    public static void smoke(ServerLevel level, WireTrace trace) {
        double cooked = trace.scorch();
        if (cooked < 0.15) {
            return;
        }
        int puffs = (int) Math.ceil(cooked * 3);
        var at = trace.pixels().isEmpty() ? null : trace.pixels().get(0);
        double x = trace.cell().getX() + 0.5;
        double y = trace.cell().getY() + 0.5;
        double z = trace.cell().getZ() + 0.5;
        if (at != null) {
            double[] centre = WireTrace.centreOf(at, 0.05);
            x = trace.cell().getX() + centre[0];
            y = trace.cell().getY() + centre[1];
            z = trace.cell().getZ() + centre[2];
        }
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE,
                x, y, z, puffs, 0.08, 0.08, 0.08, 0.005);
    }

    /**
     * The insulation gave out: this face's worth of conductor is gone.
     *
     * <p>One surface, not the whole circuit. A series run carries the same current everywhere, so
     * what decides where it fails is where the cooling was worst — and taking only that face
     * leaves the player a fault they can see, reach and re-lay, rather than a base-wide outage
     * with no clue where it started.
     *
     * <p>Nothing is handed back. The metal did not survive; that is what melting means.
     */
    private static void burn(ServerLevel level, WireTrace trace) {
        Wires.removeOne(level, trace);
        level.playSound(null, trace.cell(), SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS,
                0.7f, 1.6f);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE,
                trace.cell().getX() + 0.5, trace.cell().getY() + 0.5, trace.cell().getZ() + 0.5,
                6, 0.2, 0.2, 0.2, 0.01);
    }

    /** One face's worth of a run, so a trace is touched once however much of it the run uses. */
    private record Surface(BlockPos cell, Direction face, net.minecraft.world.item.DyeColor colour) { }

    private static Set<Surface> surfaces(WirePower.Run run) {
        Set<Surface> found = new LinkedHashSet<>();
        for (WirePixel pixel : run.pixels()) {
            found.add(new Surface(Wires.cellOf(pixel), Faces.of(pixel.face()), run.colour()));
        }
        return found;
    }

    private static WireTrace traceOn(ServerLevel level, Surface surface) {
        for (WireTrace trace : Wires.bundleOn(level, surface.cell(), surface.face())) {
            if (trace.colour() == surface.colour()) {
                return trace;
            }
        }
        return null;
    }

    private WireHeat() {}
}
