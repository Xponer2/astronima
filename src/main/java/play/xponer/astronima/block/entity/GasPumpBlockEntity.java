package play.xponer.astronima.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import play.xponer.astronima.block.GasPumpBlock;
import play.xponer.astronima.pipe.PipeNetworks;
import play.xponer.astronima.registry.ModBlockEntities;
import play.xponer.astronima.sim.GasTransfer;
import play.xponer.astronima.sim.RoomState;
import play.xponer.astronima.sim.pipe.PressureVessel;
import play.xponer.astronima.sim.pipe.PumpRouting;
import play.xponer.astronima.sim.pipe.Valve;

/**
 * Does the work a pipe cannot.
 *
 * <p>Resolves the run on its inlet face and the run on its outlet face as two separate
 * networks — that is what "inline" means — and moves gas from one to the other with
 * {@link GasTransfer#pump}, which is positive-displacement: a fixed swept volume per
 * second, stalling once the outlet reaches its rated pressure.
 *
 * <p><strong>One volume per side.</strong> A pump has one inlet and one outlet, so a
 * side teed into three rooms has no defined direction. Rather than pick one and produce
 * behaviour nobody can predict, an ambiguous pump reports itself stopped — and because
 * the running state is on the block, a pump that will not start says so without a menu.
 *
 * <p>It stalls at the tank's <em>working</em> pressure, not its burst pressure, so
 * filling normally can never burst a vessel. Getting into the safety margin has to be
 * deliberate.
 */
public class GasPumpBlockEntity extends ReadableBlockEntity {
    /**
     * Swept volume per second.
     *
     * <p>A real roughing pump of this size moves a few m³/min, which would take about
     * four hours to fill a tank to its rating and is unplayable. Scaled up in the same
     * spirit as the mod's existing 60x metabolism compression: the <em>relationships</em>
     * — slowing as the inlet empties, stalling at the rating, throttling behind a valve
     * — are all real, and only the clock is compressed.
     *
     * <p><strong>Sized by the hardest duty it has, which is the airlock.</strong> A
     * standard 3×3×3 chamber is 27 m³; evacuating it to the vent threshold takes
     * {@code ln(threshold/start) / ln(1 - displacement/volume)} strokes, so at the old
     * 1.6 m³/s that was over a minute — past the cycle's own backstop, so the outer door
     * never released and the airlock simply did not work. This value clears a chamber in
     * about twenty seconds, and {@code scenario_airlock_full_cycle} holds it to that: the
     * constant cannot drift back without a test saying a player is being made to wait.
     */
    public static final double DISPLACEMENT_M3_PER_S = 6.0;

    /** Game ticks between pump strokes. Public so a scenario can drive it at the real rate. */
    public static final int INTERVAL_TICKS = 20;
    private static final double STEP_SECONDS = INTERVAL_TICKS / 20.0;

    /**
     * Whether the last tick found one volume on each side.
     *
     * <p>Kept so the field indicator can distinguish a pump that is stalled from one
     * that is not plumbed into anything. They look identical from outside and they need
     * completely different fixes, which is exactly the confusion an indicator exists to
     * prevent.
     */
    private boolean routed;

    /**
     * Which airlock controller, if any, is driving this pump — and whether it is currently
     * letting it run.
     *
     * <p>A standalone pump has no controller and runs whenever it is routed, exactly as
     * before. An airlock controller <em>claims</em> a pump and then holds its gate shut
     * except while pumping the chamber down, so a pump plumbed to an airlock does not drain
     * the chamber the rest of the time. Transient: on reload the pump free-runs until the
     * controller re-claims it on its next tick, and a controller releases its pump when it
     * is broken, so a lost controller can never leave a pump stuck off.
     */
    private @Nullable BlockPos heldBy;
    private long leaseUntil;
    private boolean gateOpen = true;

    /**
     * How long a claim lasts without renewal. A controller renews it every tick, so a
     * controller that is broken stops renewing and the pump resumes free-running within
     * this window — the claim is a lease, not a lock, so a lost controller can never leave
     * a pump silently stuck off.
     */
    public static final long CLAIM_LEASE_TICKS = 60;

    public GasPumpBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GAS_PUMP.get(), pos, state);
    }

    /**
     * Takes (or renews) control of this pump for a controller, or reports it already taken.
     *
     * @param now the current game time, so the claim can lapse if never renewed
     * @return true if this controller now holds the pump; false if another holds a live
     *         claim — the same refusal-to-guess the pump makes when it finds two volumes on
     *         a face
     */
    public boolean claim(BlockPos controller, long now) {
        if (heldBy != null && !heldBy.equals(controller) && now <= leaseUntil) {
            return false;
        }
        heldBy = controller.immutable();
        leaseUntil = now + CLAIM_LEASE_TICKS;
        return true;
    }

    /** Releases the pump if this controller holds it, opening the gate so it free-runs. */
    public void release(BlockPos controller) {
        if (controller.equals(heldBy)) {
            heldBy = null;
            gateOpen = true;
        }
    }

    /** Opens or shuts the gate, but only for the controller that holds it. */
    public void setGate(BlockPos controller, boolean open) {
        if (controller.equals(heldBy)) {
            gateOpen = open;
        }
    }

    /** The controller holding this pump, or null when it is standalone. */
    public @Nullable BlockPos heldBy() {
        return heldBy;
    }

    /** What the last {@link #resolveRoute} found, reused by every {@link #applyStep} until the next one. */
    private @Nullable RoomState cachedInlet;
    private @Nullable RoomState cachedOutlet;
    private double cachedEffectiveM3PerS;

    /** One real Minecraft tick, in seconds — the game always runs at 20 ticks/second. */
    private static final double TICK_SECONDS = 1.0 / 20.0;

    /**
     * Real per-tick production schedule.
     *
     * <p><strong>Resolves the route — the expensive network walk — only every
     * {@link #INTERVAL_TICKS}</strong>, exactly as before this fix; that cost has not changed.
     * <strong>What changed is when the gas actually moves.</strong> The old code dumped a full
     * interval's worth of transfer into the room on the single tick the interval elapsed, so a
     * room's pressure jumped from its value 20 ticks ago straight to its new value between two
     * consecutive ticks — invisible to a slow gauge, but not to
     * {@code AtmosphereEvents.applyBarotrauma}, which samples pressure every tick and computes
     * a rate from whatever changed since the tick before. Read against a one-tick window, a
     * full second's worth of pressure drop lands as a rate twenty times higher than the pump
     * actually ran at — a real chamber pump-down was misread as an instantaneous catastrophic
     * collapse (PLAN.md; found by a gametest rewrite that finally drove the real machinery
     * instead of a hand-invented ramp). The total moved per real second is unchanged; only its
     * timing is now something a per-tick monitor reads correctly, and — as a real side benefit,
     * not just incidental — {@link GasTransfer#pump}'s stall-efficiency term is now
     * recomputed every tick instead of once per interval, which is a strictly better numerical
     * approximation of the same continuous physics.
     */
    public void serverTick(Level level, BlockPos pos, BlockState state) {
        if (!(level instanceof ServerLevel serverLevel) || !checkClaimAndGate(level, pos, state)) {
            return;
        }
        if (level.getGameTime() % INTERVAL_TICKS == 0) {
            resolveRoute(serverLevel, pos, state);
        }
        applyStep(level, pos, state, TICK_SECONDS);
    }

    /**
     * One pumping step at the old, coarse grain: resolves fresh and moves a full
     * {@link #INTERVAL_TICKS}' worth of gas in a single call.
     *
     * <p>Kept exactly as it has always behaved — same total moles per call, same signature —
     * so every scenario that drives this directly (valve restriction, self-loop conservation,
     * a full airlock cycle) keeps working unchanged. Production no longer calls this — see
     * {@link #serverTick}'s own doc for why the real schedule had to move away from it — but a
     * test that wants "fast-forward by one interval" without simulating every tick in between
     * still has exactly that here.
     */
    public void pumpOnce(Level level, BlockPos pos, BlockState state) {
        if (!(level instanceof ServerLevel serverLevel) || !checkClaimAndGate(level, pos, state)) {
            return;
        }
        resolveRoute(serverLevel, pos, state);
        applyStep(level, pos, state, STEP_SECONDS);
    }

    /**
     * {@link #serverTick}'s own two halves, exposed separately for a scenario keeping its own
     * tick count instead of the world's real one.
     *
     * <p>{@code serverTick} gates its resolve on {@code level.getGameTime() % INTERVAL_TICKS}
     * — correct in real play, where every server tick genuinely advances that clock, but wrong
     * for a gametest that drives a controller and its pump in a manual loop without the world
     * clock moving at all: {@code getGameTime() % INTERVAL_TICKS} would then read the same
     * value on every iteration, so the resolve would either always or never run depending on
     * which value it froze at — {@code pumpOnce}'s own doc names this exact trap for the coarse
     * schedule; this is the same trap for the fine one. A scenario wanting the real per-tick
     * shape calls {@link #resolveRouteOn} every {@link #INTERVAL_TICKS}th iteration of its own
     * counter and {@link #applyOneTick} every iteration, mirroring exactly what
     * {@code serverTick} does against real time.
     */
    public void resolveRouteOn(Level level, BlockPos pos, BlockState state) {
        if (level instanceof ServerLevel serverLevel && checkClaimAndGate(level, pos, state)) {
            resolveRoute(serverLevel, pos, state);
        }
    }

    /** See {@link #resolveRouteOn} — the other half, run every tick regardless. */
    public void applyOneTick(Level level, BlockPos pos, BlockState state) {
        if (checkClaimAndGate(level, pos, state)) {
            applyStep(level, pos, state, TICK_SECONDS);
        }
    }

    /**
     * A claim that stopped being renewed has lapsed (the controller is gone, so resume
     * free-running), and a gate a controller is holding shut idles the pump without moving
     * gas. Checked every real tick now rather than once per {@link #INTERVAL_TICKS} — a
     * controller shutting the gate used to take up to nineteen ticks to actually take effect.
     *
     * @return false if the pump must do nothing else this call (gate shut)
     */
    private boolean checkClaimAndGate(Level level, BlockPos pos, BlockState state) {
        if (heldBy != null && level.getGameTime() > leaseUntil) {
            heldBy = null;
            gateOpen = true;
        }
        if (heldBy != null && !gateOpen) {
            if (state.getValue(GasPumpBlock.RUNNING)) {
                level.setBlock(pos, state.setValue(GasPumpBlock.RUNNING, false),
                        Block.UPDATE_ALL);
            }
            return false;
        }
        return true;
    }

    /**
     * Walks both faces' networks and caches what {@link #applyStep} needs until the next
     * resolve — the one expensive part of pumping, deliberately not run every tick.
     */
    private void resolveRoute(ServerLevel level, BlockPos pos, BlockState state) {
        PipeNetworks.Resolved inletSide =
                sideNetwork(level, GasPumpBlock.inletSide(state, pos));
        PipeNetworks.Resolved outletSide =
                sideNetwork(level, GasPumpBlock.outletSide(state, pos));
        cachedInlet = soleVolume(inletSide);
        cachedOutlet = soleVolume(outletSide);
        // The single shared fact for "does this pump have somewhere to push gas" -
        // PumpRouting.isUsable, not two counts eyeballed here, because a room ported to
        // itself reads as "1 volume, 1 volume" by count alone and is not a route at all.
        routed = PumpRouting.isUsable(PipeNetworks.volumeCount(inletSide),
                PipeNetworks.volumeCount(outletSide), cachedInlet != null && cachedInlet == cachedOutlet);
        if (routed) {
            // The pump pumps THROUGH its plumbing, so the tightest valve on either side
            // throttles it. Ignoring this is what made valves decorative: a shut valve
            // changed the passive edges and the pump carried on regardless.
            //
            // The r^4 bore law is Valve.flowFraction, once - not restated here. It used
            // to be inlined as restriction*restriction*restriction*restriction, which
            // happens to equal the same fourth power today but has nothing keeping it in
            // step with Valve's own formula if that one is ever changed.
            double restriction = Math.min(inletSide.openFraction(), outletSide.openFraction());
            cachedEffectiveM3PerS = DISPLACEMENT_M3_PER_S * Valve.flowFraction(restriction);
        }
    }

    /** Moves gas for {@code dtSeconds} using whatever the last {@link #resolveRoute} found. */
    private void applyStep(Level level, BlockPos pos, BlockState state, double dtSeconds) {
        boolean running = false;
        if (routed) {
            double moved = GasTransfer.pump(cachedInlet, cachedOutlet, cachedEffectiveM3PerS,
                    dtSeconds, PressureVessel.WORKING_PRESSURE_KPA);
            running = moved > 0;
        }
        if (state.getValue(GasPumpBlock.RUNNING) != running) {
            level.setBlock(pos, state.setValue(GasPumpBlock.RUNNING, running),
                    Block.UPDATE_ALL);
        }
    }

    /**
     * How many volumes are plumbed to each face.
     *
     * <p>Reported separately rather than as one "routed" flag so a tooltip can say
     * <em>which end</em> is wrong. "No route" alone sends a player to check both faces,
     * which is barely better than saying nothing.
     *
     * <p><strong>{@code inlet == 1 && outlet == 1} does not by itself mean the pump is
     * routed</strong> — a room ported to itself puts exactly one volume on each face and
     * it is the <em>same</em> volume, which {@link #isRouted()} correctly refuses and a
     * reading built only from these two counts cannot tell. {@link PumpRouting#isUsable}
     * is the one place that question is answered; a caller wanting "is this pump usable"
     * should ask {@link #isRouted()}, not reconstruct it from these counts.
     */
    public int volumesOnInlet() {
        return countVolumes(GasPumpBlock.inletSide(getBlockState(), getBlockPos()));
    }

    public int volumesOnOutlet() {
        return countVolumes(GasPumpBlock.outletSide(getBlockState(), getBlockPos()));
    }

    private int countVolumes(BlockPos side) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return -1;
        }
        return PipeNetworks.volumesOnSide(serverLevel, side);
    }

    /** Whether this pump has somewhere to push gas — see {@link PumpRouting#isUsable}. */
    public boolean isRouted() {
        return routed;
    }

    /**
     * The single volume on one side of the pump, or null when there is not exactly one.
     *
     * <p>Null covers both "nothing plumbed here" and "several things plumbed here", and
     * both mean the same thing to a pump: no defined direction to push in.
     */
    private static PipeNetworks.@Nullable Resolved sideNetwork(ServerLevel level, BlockPos side) {
        return PipeNetworks.resolveSide(level, side);
    }

    private static @Nullable RoomState soleVolume(PipeNetworks.@Nullable Resolved resolved) {
        if (resolved == null || resolved.network().nodes().size() != 1) {
            return null;
        }
        return resolved.network().nodes().get(0);
    }
}
