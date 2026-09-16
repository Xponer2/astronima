package play.xponer.astronima.wire;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import play.xponer.astronima.Astronima;
import play.xponer.astronima.registry.ModAttachments;
import play.xponer.astronima.sim.logic.Circuit;
import play.xponer.astronima.sim.logic.CompiledCircuit;
import play.xponer.astronima.sim.logic.PartType;
import play.xponer.astronima.sim.processor.Machine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Keeping the wire layer up to date: what its parts are doing, and whether any of it is still
 * hanging on something.
 *
 * <h2>Why a timer, and not an event</h2>
 * <strong>The wire layer is not blocks.</strong> Re-routing a trace changes no blockstate and
 * notifies nothing, so a gate that only recomputed when a neighbour changed would show a stale
 * answer from the moment somebody moved a wire — and would look, to a player, exactly like a gate
 * that does not work. The gate <em>blocks</em> this replaces solved it by rescheduling their own
 * tick every five ticks; parts have no block to schedule, so the refresh lives here.
 *
 * <p>Five ticks is a quarter of a second, which is a relay's own switching time and far below
 * anything a player can see.
 *
 * <h2>Why an index, and not a scan</h2>
 * Walking every loaded chunk five times a second to find the handful that hold a gate is the same
 * mistake the stud renderer made when it walked 117 649 blockstates a frame. Chunks announce
 * themselves when they load, when they unload and when a part is bolted to one, so the tick
 * touches only chunks that have something in them.
 *
 * <p><strong>And a chunk is written back only when something actually changed.</strong> Most
 * refreshes find a gate saying what it said before; saving and syncing anyway would put a base's
 * worth of logic on the network continuously for a picture that is not moving.
 *
 * <h2>Support is checked here too, for both parts and traces</h2>
 * PLAN rule 24: <em>do not hang a rule about the world on an event about a player.</em>
 * {@code BreakBlockEvent} fires for a pickaxe and for nothing else — not an explosion, not a
 * piston, not a {@code /setblock}, not another mod's machine — so anything that came down only on
 * that event was left hanging in mid-air by every other way a block can stop existing. Parts moved
 * here first; the traces beside them are now the second half of the same fix, and the debt
 * {@code design/wire-parts.md} recorded is paid.
 */
@EventBusSubscriber(modid = Astronima.MODID)
public final class WireTicker {

    /** A relay's own switching time, near enough, and cheap. */
    public static final int REFRESH_TICKS = 5;

    /** Half a {@link PartType#CLOCK}'s own period — ten ticks high, ten low: a visible, once-a-second blink. */
    public static final long CLOCK_HALF_PERIOD_TICKS = 10;

    /**
     * How many full clock pulses a plate driven by a real {@link PartType#CLOCK} runs internally
     * per refresh, rather than the one a plain pulse would give it.
     *
     * <p>{@code design/computer.md} Phase 2's own number, chosen generously rather than tightly: at
     * {@code REFRESH_TICKS}, a plate wired to nothing faster than the clock's own visible blink is
     * capped at one count per second — a demonstration, not a machine. A hundred internal cycles
     * per refresh moves that to twenty counts a second, which is what "faster than the world" has
     * to mean for this to be worth building at all. Not yet player-configurable — see {@code
     * design/computer.md} Phase 2's own note on the gap — so every clocked plate runs at this one
     * fixed rate for now.
     *
     * <p><strong>Must be odd, not merely "not a multiple of 16" — a real bug, found and fixed.</strong>
     * The value shipped here was 100, on the reasoning that a rate divisible by 16 would land a
     * 4-bit demo counter back on its own starting nibble every refresh, looking frozen. That
     * reasoning was correct and the number chosen did not satisfy it: {@code gcd(100, 16) = 4}, not
     * 1, so a clock-driven 4-bit counter did not freeze — it cycled through exactly four of its
     * sixteen values forever (0, 4, 8, 12, 0, 4, …) and never reached the other twelve. Reported
     * back as <em>"постоянно одни и те же две цифры показывало"</em> after the exact combination
     * the design intended to prove worked ({@code Register4Bit_Edge} driven by a real
     * {@code CLOCK}) — the existing gametest for that combination only ever checked one refresh's
     * arithmetic against this same constant, so it passed while reproducing the bug's own math
     * rather than catching it — PLAN.md rule 64, and {@code aClockDrivenCounterEventuallyVisitsEveryValue}
     * is the guard the previous test's own blind spot needed.
     * <strong>Any power of two shares this hazard with any non-coprime step</strong> — 16, 32, 256 —
     * so the fix is not "avoid 16 specifically" but "be odd," which is coprime with every power of
     * two a binary counter of any width could ever be.
     */
    public static final int INTERNAL_CYCLES_PER_REFRESH = 101;

    /**
     * How many {@link play.xponer.astronima.sim.processor.Machine} instructions a
     * {@link PartType#PROCESSOR} runs per refresh before its slice ends on its own budget rather
     * than on {@code STROBE} — {@code design/processor.md} §4's own number: sixty-four instructions
     * at four refreshes a second is the roughly 256-instructions-a-second figure that table states,
     * and the runaway guard a program with no {@code STROBE} in a loop needs to never hang a tick.
     */
    public static final int INSTRUCTIONS_PER_REFRESH = 64;

    /** Chunks known to hold wire of any kind, per level. Server-side only. */
    private static final Map<ResourceKey<Level>, Set<Long>> INDEX = new HashMap<>();

    /**
     * Notes that a chunk now holds parts.
     *
     * <p>Called from {@link Wires} rather than inferred, because wire laid in a chunk that was
     * already loaded raises no chunk event at all — which is the one case a load-time index alone
     * would miss, and it is the common one.
     */
    public static void remember(Level level, ChunkPos pos) {
        if (level.isClientSide()) {
            return;
        }
        INDEX.computeIfAbsent(level.dimension(), key -> new HashSet<>()).add(pos.pack());
    }

    @SubscribeEvent
    private static void onChunkLoad(ChunkEvent.Load event) {
        LevelChunk chunk = event.getChunk();
        if (chunk.getLevel().isClientSide()
                || chunk.getData(ModAttachments.WIRES.get()).isEmpty()) {
            return;
        }
        remember(chunk.getLevel(), chunk.getPos());
    }

    @SubscribeEvent
    private static void onChunkUnload(ChunkEvent.Unload event) {
        LevelChunk chunk = event.getChunk();
        if (chunk.getLevel().isClientSide()) {
            return;
        }
        Set<Long> known = INDEX.get(chunk.getLevel().dimension());
        if (known != null) {
            known.remove(chunk.getPos().pack());
        }
    }

    @SubscribeEvent
    private static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        // Every tick, not every fifth: this is the wire's own clock, and it must tick once per
        // tick or a run's temperature advances at a rate that depends on how the work happened to
        // be scheduled rather than on how much current went through it.
        age(level, 1 / 20.0);

        if (level.getGameTime() % REFRESH_TICKS != 0) {
            return;
        }
        Set<Long> known = INDEX.get(level.dimension());
        if (known == null || known.isEmpty()) {
            return;
        }
        List<Long> forgotten = new ArrayList<>();
        for (long packed : List.copyOf(known)) {
            ChunkPos pos = ChunkPos.unpack(packed);
            LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x(), pos.z());
            if (chunk == null) {
                continue; // not loaded right now; the unload event will tidy it up
            }
            if (!refresh(level, chunk)) {
                forgotten.add(packed);
            }
        }
        forgotten.forEach(known::remove);
    }

    /**
     * Re-reads one chunk's worth of wire layer.
     *
     * @return false when the chunk turned out to hold nothing at all, so the index can forget it
     */
    private static boolean refresh(ServerLevel level, ChunkAccess chunk) {
        WireChunk wires = chunk.getData(ModAttachments.WIRES.get());
        if (wires.isEmpty()) {
            return false;
        }
        for (WirePart part : wires.parts()) {
            if (fell(level, part)) {
                continue;
            }
            WirePart next = clocked(level, released(level, part));
            boolean isPlate = next.type() == PartType.PLATE || next.type() == PartType.MACRO_PLATE;
            if (isPlate) {
                boolean[] ins = PartLogic.readInputs(level, next);
                // Named explicitly rather than chained: `compiled()` is the expensive step (built
                // once per edit, cached on the Circuit itself - see design/computer.md Phase 1.2),
                // `evaluateStateful` the cheap one run every refresh, and the two are worth telling
                // apart at the one real call site outside sim/logic that asks for either.
                CompiledCircuit compiled = next.circuit().compiled();
                int clockPin = clockPin(level, next);
                Circuit.Settled result = clockPin < 0
                        ? compiled.evaluateStateful(ins, next.gateState())
                        : steppedInternally(compiled, ins, clockPin, next.gateState());
                next = next.withOutputs(result.outputs()).withGateState(result.gateState());
            } else if (next.type() == PartType.RAM || next.type() == PartType.FRAMEBUFFER) {
                boolean[] ins = PartLogic.readInputs(level, next);
                MemoryLogic.Result result = MemoryLogic.evaluate(ins, next.memory(), next.held());
                next = next.withOutputs(result.outputBits())
                        .withMemory(result.memory())
                        .withHeld(result.clockHigh(), 0L);
            } else if (next.type() == PartType.PROCESSOR) {
                next = processed(level, next);
            } else {
                next = next.withOutputs(PartLogic.evaluate(level, next));
            }
            Wires.update(level, part, next);
        }
        // Traces, one support lookup each. A trace is a whole face's worth of conductor, not a
        // pixel, so a base-spanning run costs about as many lookups as it does blocks — which is
        // nothing next to being the only thing that notices an exploded wall.
        for (WireTrace trace : wires.traces()) {
            // Cooling, for anything that has been used. A wire that only cooled while somebody
            // was drawing through it would never cool at all — and the cheap guard matters: a
            // base holds thousands of traces and almost none of them are warm, so a comparison
            // stands in for a room lookup on all but the handful that are.
            if (trace.temperatureK() > WireTrace.REST_K + 1) {
                // Smoke only. The temperature is stepped every tick by `heat`, from the whole
                // run's current — a wire that also cooled here would be fighting its own heating
                // on a different clock.
                WireHeat.smoke(level, trace);
            }
            if (!supported(level, trace.support(), trace.face())) {
                // Nothing is handed back, the same as when a pickaxe takes the wall: conductor
                // is not an item yet, and wire left floating on a block that no longer exists
                // would be the worse of the two (design/electrical.md §13).
                Wires.removeAll(level, trace.cell(), trace.face());
            }
        }
        return true;
    }

    /** Whether that face of that block can still hold something fastened to it. */
    private static boolean supported(ServerLevel level, BlockPos support, Direction face) {
        return level.getBlockState(support).isFaceSturdy(level, support, face.getOpposite());
    }

    /**
     * A part whose wall has gone comes off it, and drops.
     *
     * <p><strong>Checked here rather than on the break event, deliberately.</strong> The event a
     * player's pickaxe raises is not the only way a block stops existing — an explosion, a piston,
     * a command and another mod's machine all do it silently, and a part left hanging on nothing
     * would keep working, keep drawing, and be impossible to take down. One blockstate lookup per
     * part, five times a second, catches every one of them; the price is that a part may hang in
     * the air for a quarter of a second, which is under the time it takes the block's own break
     * particles to clear.
     *
     * <p>The item lands where the <em>part</em> was, not where the block was. A gate on a ceiling
     * three metres up should not drop into the floor below it.
     */
    private static boolean fell(ServerLevel level, WirePart part) {
        if (supported(level, part.support(), part.face())) {
            return false;
        }
        Wires.unmount(level, part);
        Block.popResource(level, part.cell(), WireEvents.asItem(part));
        return true;
    }

    /**
     * A button that has been down long enough springs back.
     *
     * <p>Its deadline is a game-time reading, so it survives a save and a reload the way a
     * scheduled tick would not have to be re-armed. A button that stayed down through a relog
     * would be a source nobody could turn off.
     */
    private static WirePart released(ServerLevel level, WirePart part) {
        if (!part.held() || part.releaseAt() == 0L || level.getGameTime() < part.releaseAt()) {
            return part;
        }
        return part.withHeld(false, 0L);
    }

    /**
     * A {@link PartType#CLOCK}'s own output, derived fresh from the world's game time rather than
     * stored — see {@link PartType#CLOCK}'s own note on why a click never sets it. Deterministic:
     * two clocks a minute apart, or one just loaded from a save, read the identical phase the
     * instant either is asked, with nothing to desync and nothing to re-arm.
     */
    private static WirePart clocked(ServerLevel level, WirePart part) {
        if (part.type() != PartType.CLOCK) {
            return part;
        }
        boolean high = (level.getGameTime() / CLOCK_HALF_PERIOD_TICKS) % 2 == 0;
        return high == part.held() ? part : part.withHeld(high, 0L);
    }

    /**
     * Which of a plate's real input pads is fed by a real {@link PartType#CLOCK}, or {@code -1}
     * for none. Only the first one found — a real machine has exactly one clock line, and picking
     * one deterministically (pad declaration order, the same order {@link PartLogic#readInputs}
     * already reads them in) is enough for that; wiring a second clock to a second pin is simply
     * not treated as fast, the same as it would not be on a real board.
     */
    private static int clockPin(ServerLevel level, WirePart part) {
        List<PartType.Pad> reading = part.type().inputs();
        for (int i = 0; i < reading.size(); i++) {
            if (WireSignal.clockDrivesPad(level, part, reading.get(i))) {
                return i;
            }
        }
        return -1;
    }

    /**
     * {@code design/computer.md} Phase 2: a plate whose clock pin is fed by a real clock chip does
     * not wait for that chip's own slow, human-visible blink — it runs
     * {@link #INTERNAL_CYCLES_PER_REFRESH} full pulses on that one pin here, in software, threading
     * gate state from one to the next, and only the final settled result is ever published to the
     * wire network. Every other input is sampled once and held constant across every internal
     * step, the same way the existing hand-built counters already hold their "+1" line constant
     * across one real press-and-release.
     */
    private static Circuit.Settled steppedInternally(CompiledCircuit compiled, boolean[] baseInputs,
                                                      int clockPin, List<Long> gateState) {
        boolean[] high = baseInputs.clone();
        high[clockPin] = true;
        boolean[] low = baseInputs.clone();
        low[clockPin] = false;
        Circuit.Settled result = compiled.evaluateStateful(low, gateState);
        for (int cycle = 0; cycle < INTERNAL_CYCLES_PER_REFRESH; cycle++) {
            result = compiled.evaluateStateful(high, result.gateState());
            result = compiled.evaluateStateful(low, result.gateState());
        }
        return result;
    }

    /**
     * A {@link PartType#PROCESSOR}'s own refresh: unpack its {@code machine}, run
     * {@link #INSTRUCTIONS_PER_REFRESH} instructions against it if {@code run} is live, and publish
     * exactly what {@code design/processor.md} §2.2 says the pads show.
     *
     * <p>{@code held} is reused once more, as {@code memory} and {@link PartType#CLOCK} already
     * were, for edge-detecting {@code run} — a rising edge restarts the program from {@code PC = 0}
     * (§2.2's own wording; {@link Machine#restarted()} carries this out) rather than every refresh
     * `run` merely reads high triggering a restart, which would make a program that leaves `run`
     * closed unable to ever get past its first instruction.
     */
    private static WirePart processed(ServerLevel level, WirePart part) {
        if (part.program().isEmpty()) {
            return part.held() ? part.withHeld(false, 0L) : part;
        }
        boolean[] ins = PartLogic.readInputs(level, part);
        boolean runNowHigh = ins.length > 4 && ins[4];
        Machine machine = Machine.unpack(part.machine());
        if (runNowHigh && !part.held()) {
            machine = machine.restarted();
        }

        boolean strobed = false;
        if (runNowHigh) {
            int input = 0;
            for (int i = 0; i < 4 && i < ins.length; i++) {
                if (ins[i]) {
                    input |= 1 << i;
                }
            }
            Machine.RunResult result = machine.run(INSTRUCTIONS_PER_REFRESH, input);
            machine = result.machine();
            strobed = result.strobed();
        }

        int bits = machine.outPort(0) | (machine.outPort(1) << 4) | (strobed ? 1 << 8 : 0);
        return part.withOutputs(bits).withMachine(machine.pack()).withHeld(runNowHigh, 0L);
    }

    /**
     * Ages every trace that is carrying something or has something to forget.
     *
     * <p>One step per trace per tick, with the current its <em>whole run</em> carried — the only
     * arrangement in which a trunk feeding six machines is hotter than a spur feeding one. A trace
     * at rest and unloaded is skipped, and almost all of them are: a base holds thousands, and a
     * comparison stands in for a room lookup on all but the handful that matter.
     */
    public static void age(ServerLevel level, double seconds) {
        heat(level, WireLoad.settle(level, seconds), seconds);
    }

    private static void heat(ServerLevel level, WireLoad.Loaded carried, double seconds) {
        Set<Long> known = INDEX.get(level.dimension());
        if (known == null || known.isEmpty()) {
            return;
        }
        for (long packed : List.copyOf(known)) {
            ChunkPos pos = ChunkPos.unpack(packed);
            LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x(), pos.z());
            if (chunk == null) {
                continue;
            }
            for (WireTrace trace : chunk.getData(ModAttachments.WIRES.get()).traces()) {
                double amps = carried.at(trace);
                if (amps <= 0 && trace.temperatureK() <= WireTrace.REST_K + 1) {
                    continue;
                }
                WireHeat.step(level, trace, amps, seconds);
            }
        }
    }

    /** A world going away takes its index with it. */
    @SubscribeEvent
    private static void onLevelUnload(net.neoforged.neoforge.event.level.LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            INDEX.remove(level.dimension());
            WireLoad.forget(level);
        }
    }

    private WireTicker() {}
}
