package play.xponer.astronima.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import play.xponer.astronima.advancement.AirlockTrigger;
import play.xponer.astronima.advancement.ModCriteria;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import play.xponer.astronima.airlock.AirlockCommissioning;
import play.xponer.astronima.airlock.DoorLatches;
import play.xponer.astronima.airlock.AirlockResolver;
import play.xponer.astronima.airlock.AirlockStatus;
import play.xponer.astronima.atmosphere.Atmosphere;
import play.xponer.astronima.block.AirlockControllerBlock;
import play.xponer.astronima.block.BulkheadDoorBlock;
import play.xponer.astronima.registry.ModBlockEntities;
import play.xponer.astronima.sim.GasMixture;
import play.xponer.astronima.sim.RoomState;
import play.xponer.astronima.sim.airlock.AirlockCycle;
import play.xponer.astronima.sim.airlock.DeviceBinding;
import play.xponer.astronima.sim.airlock.DeviceBinding.Problem;
import play.xponer.astronima.sim.airlock.DeviceBinding.Role;
import play.xponer.astronima.wire.WireSignal;
import play.xponer.astronima.sim.pipe.PressureVessel;

import java.util.EnumMap;
import java.util.Map;

/**
 * Sequences the airlock the player commissioned.
 *
 * <p>It owns nothing that moves gas — the pump, the tank, the doors and the chamber are all
 * the player's own blocks, and since PLAN rule 17 it does not go looking for them either.
 * The player lands each device on a terminal ({@link DeviceBinding}); we check that the
 * device they named can do that job ({@link AirlockCommissioning}) and name the fault
 * <em>on their device</em> when it cannot. That swap is the whole change: every fault this
 * block used to report — "needs two doors", "no pump" — described our search rather than
 * their build, and refused correct layouts such as both doors in one wall.
 *
 * <p>Once commissioned, each tick reads the bound blocks into a {@link AirlockCycle.Sense},
 * steps the pure cycle machine, and carries out what it decides: it holds the pump's gate
 * open only while pumping down, keeps the locked door of the moment shut, vents the residue
 * when the pump can do no more, and lets stored gas back into the chamber to come home. The
 * one guarantee — never both doors open across a pressure difference — lives in the cycle
 * machine and is unit-tested there.
 */
public class AirlockControllerBlockEntity extends ReadableBlockEntity {
    /** Standard atmosphere, used as the repressurise target when no habitat room is found. */
    private static final double DEFAULT_HABITAT_KPA = 101.325;
    /** Fraction of the tank's gas fed into the chamber each tick while repressurising. */
    private static final double FILL_FRACTION = 0.15;

    private final DeviceBinding bindings = new DeviceBinding();

    private AirlockCycle.Phase phase = AirlockCycle.Phase.SEALED;
    private int ticksInPhase;
    private boolean commanded;

    /**
     * Whether this panel has been through commissioning at all.
     *
     * <p>False only for a controller written to disk before commissioning existed, because
     * the flag is saved and a save that predates it has no key to read. That is precisely
     * the migration trigger: those airlocks worked yesterday and must work today, so on
     * their first tick the old resolver runs once and its answer is written in as bindings.
     * A newly placed controller starts true — a fresh panel is <em>meant</em> to be empty,
     * and silently commissioning it for the player is the guessing rule 17 removed.
     */
    private boolean commissioned = true;

    /**
     * Whether the "you have commissioned it" moment has already fired.
     *
     * <p>Not saved: it is worth one announcement per session at most, and an advancement
     * the player already holds is a no-op anyway — so a flag on disk would buy nothing and
     * would have to be migrated like everything else.
     */
    private boolean announcedCommissioning;

    // Last-known status, for the panel, the block's gauge and Jade. Not the source of
    // truth — the blocks are.
    private AirlockResolver.Fault chamberFault = AirlockResolver.Fault.NONE;
    private AirlockCycle.Fault cycleFault = AirlockCycle.Fault.NONE;
    private final Map<Role, Problem> problems = new EnumMap<>(Role.class);
    private double chamberPressureKPa;
    private double tankPressureKPa;
    private int chamberVolume;
    private boolean innerShut = true;
    private boolean outerShut = true;
    private double habitatPressureKPa = DEFAULT_HABITAT_KPA;

    public AirlockControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.AIRLOCK_CONTROLLER.get(), pos, state);
        for (Role role : Role.values()) {
            problems.put(role, Problem.UNBOUND);
        }
    }

    /** Pressed by the player: advance the cycle at the next tick. */
    /**
     * Was the control line live last tick — so a held signal is one command, not a stream.
     *
     * <p>Saved. A field that resets to {@code false} across a reload would forget a wire
     * that has been held high the whole time, so the very next tick sees "was not live, now
     * is" and treats a continuously-held signal as a brand new rising edge — one unwanted
     * extra cycle command fired the moment the world comes back, for a player who never
     * touched anything. See PLAN's rule for this class of bug.
     */
    private boolean signalWasLive;

    /**
     * Whether gas actually moved from the tank into the chamber during the leg of
     * REPRESSURIZING now in progress (or the one that just ended).
     *
     * <p>{@code AirlockCycle} completes REPRESSURIZING two ways — the chamber reaches its
     * target, or the tank runs dry — and both are legitimate completions of the phase. But
     * "reached SEALED from REPRESSURIZING" is not the same claim as "came home on stored
     * air": a tank that was already empty at the start of the leg completes it on the very
     * first tick, having moved nothing. Not saved, on the same reasoning as
     * {@link #announcedCommissioning} — the worst a reload can do is miss crediting a leg
     * that was already most of the way through spending its tank, and an advancement already
     * held is a no-op regardless.
     */
    private boolean usedStoredAirThisLeg;

    /**
     * The moment {@link #awardLeg} most recently decided to award, regardless of whether any
     * player was standing close enough to actually receive it.
     *
     * <p>Exposed for scenarios: the award itself is an AABB scan of real level entities, and
     * a gametest's mock player is never spawned as one (reference-gametest-harness-limits),
     * so nothing in the harness can observe a grant actually landing. This is the decision
     * the grant is built on, which is the thing that was wrong — CAME_HOME used to fire on
     * every REPRESSURIZING → SEALED transition, not only the ones that drew on the tank.
     */
    private AirlockTrigger.@Nullable Moment lastLegMoment;

    /** See {@link #lastLegMoment}. */
    public AirlockTrigger.@Nullable Moment lastLegMoment() {
        return lastLegMoment;
    }

    /**
     * A live control wire presses Cycle.
     *
     * <p><strong>Edge-triggered, and that is the whole of it.</strong> A sensor with somebody
     * standing on it drives its line for as long as they stand there, and a level-triggered
     * airlock would re-command every tick — which reads as an airlock that will not finish a
     * cycle. Firing on the <em>rising</em> edge makes a held signal mean "go", once, exactly as
     * a finger on the button does.
     *
     * <p>And the cycle direction is not wired: the sequence already knows which way it is going
     * from the phase it is in. Making the player run two wires to tell a machine something it
     * can see for itself would be ceremony, not engineering.
     */
    private void readSignalWire(ServerLevel level, BlockPos pos) {
        boolean live = WireSignal.anyInputLive(level, pos,
                play.xponer.astronima.block.AirlockControllerBlock.CYCLE);
        if (live && !signalWasLive) {
            command();
        }
        if (live != signalWasLive) {
            signalWasLive = live;
            setChanged();
        }
    }

    public void command() {
        this.commanded = true;
    }

    public AirlockCycle.Phase phase() {
        return phase;
    }

    /** Whether the panel is mounted on a readable, sealed volume at all. */
    public AirlockResolver.Fault chamberFault() {
        return chamberFault;
    }

    public AirlockCycle.Fault cycleFault() {
        return cycleFault;
    }

    public Problem problem(Role role) {
        return problems.getOrDefault(role, Problem.UNBOUND);
    }

    public double chamberPressureKPa() {
        return chamberPressureKPa;
    }

    public double habitatPressureKPa() {
        return habitatPressureKPa;
    }

    /** What the player put on each terminal. Read-only to everyone outside. */
    public DeviceBinding bindings() {
        return bindings;
    }

    // ---- commissioning -------------------------------------------------------

    /**
     * Lands a device on a terminal. The player's half of rule 17 — no validity check here
     * beyond the one the click already made, because being able to bind something that
     * turns out not to work is what lets the panel name it.
     */
    public void bind(Role role, BlockPos device) {
        bindings.bind(role, device.asLong());
        setChanged();
    }

    /** Empties one terminal — the panel's per-terminal CLEAR. */
    public void clearBinding(Role role) {
        bindings.clear(role);
        setChanged();
    }

    /**
     * SCAN: proposes bindings for the terminals still empty, by running the old resolver.
     *
     * <p>Auto-detection survives here as a convenience and never as the mechanism (rule
     * 17). A proposal is allowed to be wrong — the player overrides it by binding the
     * terminal themselves — which is exactly why it is a button and not something that
     * happens behind their back. It fills only empty terminals, so pressing SCAN never
     * undoes a decision somebody made on purpose.
     *
     * @return true when the scan found a complete chain to propose
     */
    public boolean scan(ServerLevel level) {
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof AirlockControllerBlock)) {
            return false;
        }
        AirlockResolver.Result resolved = AirlockResolver.resolve(level, worldPosition,
                state.getValue(AirlockControllerBlock.FACING));
        if (!resolved.ok()) {
            return false;
        }
        AirlockResolver.Resolved airlock = resolved.airlock();
        Doors doors = classifyDoors(level, airlock.chamber().id(), airlock.doorFeet());
        proposeIfEmpty(Role.INNER_DOOR, doors.inner());
        proposeIfEmpty(Role.OUTER_DOOR, doors.outer());
        proposeIfEmpty(Role.PUMP, airlock.pump());
        proposeIfEmpty(Role.TANK, airlock.tank());
        setChanged();
        return true;
    }

    private void proposeIfEmpty(Role role, @Nullable BlockPos device) {
        if (device != null && !bindings.isBound(role)) {
            bindings.bind(role, device.asLong());
        }
    }

    /**
     * Everything the panel draws, gathered in one place.
     *
     * <p>Built from the last tick's validation rather than re-validating here: the panel
     * must show what the controller is <em>actually acting on</em>, and a second scan taken
     * for the screen could disagree with the one the cycle used, which is the exact way a
     * readout starts lying.
     */
    public AirlockStatus status() {
        Map<Role, AirlockStatus.Terminal> terminals = new EnumMap<>(Role.class);
        for (Role role : Role.values()) {
            terminals.put(role, new AirlockStatus.Terminal(problem(role), offsetOf(role)));
        }
        return new AirlockStatus(phase, chamberFault, cycleFault,
                chamberPressureKPa, tankPressureKPa, habitatPressureKPa,
                chamberVolume, innerShut, outerShut, Map.copyOf(terminals));
    }

    /** Where the device on a terminal sits, relative to the panel. */
    private int offsetOf(Role role) {
        long device = bindings.device(role);
        if (device == DeviceBinding.NONE) {
            return AirlockStatus.OFFSET_UNBOUND;
        }
        BlockPos at = BlockPos.of(device);
        return AirlockStatus.packOffset(at.getX() - worldPosition.getX(),
                at.getY() - worldPosition.getY(), at.getZ() - worldPosition.getZ());
    }

    // ---- the tick ------------------------------------------------------------

    public void serverTick(Level level, BlockPos pos, BlockState state) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        tick(serverLevel, pos, state);
    }

    /**
     * One step of the sequence, separated from the schedule so a scenario test can drive it
     * without depending on the wall clock — the same split the pump makes.
     */
    public void tick(ServerLevel level, BlockPos pos, BlockState state) {
        Direction facing = state.getValue(AirlockControllerBlock.FACING);

        readSignalWire(level, pos);

        // An airlock built before commissioning existed keeps working: its bindings are
        // whatever the old resolver would have found, written in once, here.
        if (!commissioned) {
            commissioned = true;
            scan(level);
            setChanged();
        }

        AirlockCommissioning.Result checked =
                AirlockCommissioning.validate(level, pos, facing, bindings, pos);
        chamberFault = checked.chamberFault();
        problems.putAll(checked.problems());

        if (!checked.ok()) {
            // Not commissioned, or a bound device cannot do its job. Drop any stale
            // command and fail safe: back to SEALED with nothing held shut, so a player
            // whose door was broken mid-cycle is never sealed into the chamber. The panel
            // names the terminal — their device, not our search.
            commanded = false;
            chamberPressureKPa = 0;
            tankPressureKPa = 0;
            chamberVolume = 0;
            releaseHeldPump(level, pos);
            releaseHeldDoors(level, pos);
            resetPhase(level);
            return;
        }

        AirlockCommissioning.Commissioned airlock = checked.airlock();
        awardCommissioning(level);
        chamberVolume = airlock.chamber().volumeBlocks();
        tankPressureKPa = tankPressure(level, airlock.tank());

        if (!(level.getBlockEntity(airlock.pump()) instanceof GasPumpBlockEntity pump)) {
            problems.put(Role.PUMP, Problem.MISSING);
            resetPhase(level);
            return;
        }
        if (!pump.claim(pos, level.getGameTime())) {
            problems.put(Role.PUMP, Problem.IN_USE); // do not fight another controller
            resetPhase(level);
            return;
        }
        if (!claimDoors(level, pos, airlock)) {
            resetPhase(level);
            return;
        }

        chamberPressureKPa = airlock.chamber().pressureKPa();
        habitatPressureKPa = habitatPressure(level, airlock);
        innerShut = isShut(level, airlock.innerDoor());
        outerShut = isShut(level, airlock.outerDoor());
        // Whichever mover serves the current phase: the pump while evacuating (helpless
        // once its outlet reaches rating), the tank while refilling (helpless once empty).
        boolean moverCanHelp = phase == AirlockCycle.Phase.REPRESSURIZING
                ? tankMoles(level, airlock.tank()) > 1e-6
                : tankPressureKPa < PressureVessel.WORKING_PRESSURE_KPA;

        AirlockCycle.Sense sense = new AirlockCycle.Sense(
                consumeCommand(), innerShut, outerShut,
                chamberPressureKPa, habitatPressureKPa, moverCanHelp, ticksInPhase);
        AirlockCycle.Result step = AirlockCycle.step(phase, sense);
        boolean statusChanged = step.phase() != phase || step.fault() != cycleFault;
        if (step.phase() != phase) {
            awardLeg(level, phase, step.phase());
            phase = step.phase();
            ticksInPhase = 0;
            setChanged();
            if (phase == AirlockCycle.Phase.REPRESSURIZING) {
                // Fresh accounting for the leg that is just starting — see the field's doc.
                usedStoredAirThisLeg = false;
            }
        } else {
            ticksInPhase++;
        }
        cycleFault = step.fault();
        // The block's gauge lives on the client, so the status has to be sent there: on
        // every phase or fault change, and at a slow heartbeat while working so the
        // pressure bar moves. One small packet a second for one block; the readout is
        // worth far more.
        if (statusChanged || (phase != AirlockCycle.Phase.SEALED && ticksInPhase % 10 == 0)) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(),
                    Block.UPDATE_CLIENTS);
        }

        // Apply the decisions to the real blocks.
        pump.setGate(pos, AirlockCycle.pumpEnabled(phase));
        latch(level, airlock.innerDoor(), AirlockCycle.innerLocked(phase));
        latch(level, airlock.outerDoor(), AirlockCycle.outerLocked(phase));
        mediateGas(level, airlock);
    }

    /**
     * Lets go of a pump this controller was driving when its commissioning stops checking
     * out. Without it, a panel whose door got broken would keep a pump gated shut with no
     * cycle running and no way to say why — the standalone pump the player built would look
     * dead for a reason nothing points at.
     */
    private void releaseHeldPump(ServerLevel level, BlockPos pos) {
        long device = bindings.device(Role.PUMP);
        if (device == DeviceBinding.NONE) {
            return;
        }
        if (level.getBlockEntity(BlockPos.of(device)) instanceof GasPumpBlockEntity pump) {
            pump.release(pos);
        }
    }

    /**
     * Takes or renews this controller's claim on both doors, so a second controller bound to
     * the same physical door is told it is in use rather than fighting over its latch every
     * tick — the door's half of what the pump already does in {@link #tick}.
     *
     * @return true when both doors are (now) held by this controller
     */
    private boolean claimDoors(ServerLevel level, BlockPos pos,
                               AirlockCommissioning.Commissioned airlock) {
        long now = level.getGameTime();
        if (!AirlockCommissioning.claimDoor(level, airlock.innerDoor(), pos, now)) {
            problems.put(Role.INNER_DOOR, Problem.IN_USE);
            return false;
        }
        if (!AirlockCommissioning.claimDoor(level, airlock.outerDoor(), pos, now)) {
            problems.put(Role.OUTER_DOOR, Problem.IN_USE);
            return false;
        }
        return true;
    }

    /**
     * Lets go of both door claims when this controller's commissioning stops checking out —
     * the door's half of {@link #releaseHeldPump}. Without it a controller that goes on
     * failing safe would still keep another controller locked out of a door it is no longer
     * using, until the claim's own lease happened to lapse.
     */
    private void releaseHeldDoors(ServerLevel level, BlockPos pos) {
        releaseDoorIfBound(level, pos, Role.INNER_DOOR);
        releaseDoorIfBound(level, pos, Role.OUTER_DOOR);
    }

    private void releaseDoorIfBound(ServerLevel level, BlockPos pos, Role role) {
        long device = bindings.device(role);
        if (device != DeviceBinding.NONE) {
            AirlockCommissioning.releaseDoor(level, BlockPos.of(device), pos);
        }
    }

    /**
     * The first tick every terminal checks out, tell whoever is standing at the panel.
     *
     * <p>Commissioning is where a player gets stuck: until the fourth device lands the
     * panel does nothing at all, and "nothing happens" is the worst feedback a machine can
     * give. Once only — a panel does not re-congratulate you every tick it is working.
     */
    private void awardCommissioning(ServerLevel level) {
        if (announcedCommissioning) {
            return;
        }
        announcedCommissioning = true;
        AABB near = new AABB(worldPosition).inflate(8.0);
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, near)) {
            ModCriteria.AIRLOCK.get().trigger(player, AirlockTrigger.Moment.COMMISSIONED);
        }
    }

    /**
     * Credits an advancement to nearby players when a leg of the cycle completes: reaching
     * vacuum is a real exit, returning to SEALED is coming home on stored air. Awarded to
     * everyone within a few blocks rather than "the presser", because there is no presser
     * on the tick a physical condition trips the transition.
     */
    private void awardLeg(ServerLevel level, AirlockCycle.Phase from, AirlockCycle.Phase to) {
        AirlockTrigger.Moment moment = null;
        if (to == AirlockCycle.Phase.VACUUM) {
            moment = AirlockTrigger.Moment.CYCLED;
        } else if (from == AirlockCycle.Phase.REPRESSURIZING && to == AirlockCycle.Phase.SEALED
                && usedStoredAirThisLeg) {
            // Gated on the leg having actually drawn on the tank — see usedStoredAirThisLeg.
            // Without this, a return that completed because the tank was already dry (a
            // real, legal way for AirlockCycle to finish REPRESSURIZING) still earned "came
            // home on stored air" for stored air it never touched.
            moment = AirlockTrigger.Moment.CAME_HOME;
        }
        lastLegMoment = moment;
        if (moment == null) {
            return;
        }
        AABB near = new AABB(worldPosition).inflate(6.0);
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, near)) {
            ModCriteria.AIRLOCK.get().trigger(player, moment);
        }
    }

    /** The gas movements the pump does not: dumping the residue, and refilling from store. */
    private void mediateGas(ServerLevel level, AirlockCommissioning.Commissioned airlock) {
        switch (phase) {
            case VENTING -> {
                // The residual gas the pump could not reach, vented overboard — spread across
                // the phase's own VENT_TICKS duration rather than dumped in the single tick
                // VENTING is entered. VENT_TICKS' own doc already promised "a brief hiss, not
                // instantaneous"; dumping it all at once broke that promise and read to the
                // per-tick barotrauma monitor as an instantaneous atmosphere loss regardless
                // of how little was actually left — a real chamber pump-down was misread as a
                // catastrophic collapse (PLAN.md). 1/ticksLeft of whatever remains each tick
                // is a clean linear ramp to exactly zero on the phase's last tick: after k
                // ticks, (VENT_TICKS - k) / VENT_TICKS of the starting amount remains.
                RoomState chamber = airlock.chamber();
                int ticksLeft = Math.max(1, AirlockCycle.VENT_TICKS - ticksInPhase);
                double fraction = 1.0 / ticksLeft;
                for (var gas : play.xponer.astronima.sim.Gas.values()) {
                    double amount = chamber.gases().get(gas);
                    if (amount > 0) {
                        chamber.removeGas(gas, amount * fraction);
                    }
                }
            }
            case REPRESSURIZING -> {
                // Let stored gas back into the chamber — the free half of the trip.
                if (level.getBlockEntity(airlock.tank()) instanceof GasTankBlockEntity tank) {
                    RoomState store = tank.contents();
                    double take = store.gases().totalMoles() * FILL_FRACTION;
                    if (take > 1e-6) {
                        GasMixture moved = store.gases().extractFraction(FILL_FRACTION);
                        airlock.chamber().addMixtureAt(moved, store.temperatureK());
                        usedStoredAirThisLeg = true;
                    }
                }
            }
            default -> { }
        }
    }

    /**
     * What pressure "home" is: whatever sealed room lies beyond the door the player called
     * the inner one. Their word for it, not our guess — which is the point of the whole
     * change. Falls back to one standard atmosphere when the habitat side is not a sealed
     * room, because a repressurise target of zero would make the refill a no-op.
     */
    private double habitatPressure(ServerLevel level, AirlockCommissioning.Commissioned airlock) {
        RoomState beyond = farSealedRoom(Atmosphere.get(level), airlock.innerDoor(),
                airlock.chamber().id());
        return beyond != null ? beyond.pressureKPa() : DEFAULT_HABITAT_KPA;
    }

    /**
     * Sorts a scanned pair of doors into inner and outer, for {@link #scan}.
     *
     * <p>A proposal only — the sealed room beyond a door says "habitat side", and that
     * heuristic is wrong for, say, two sealed rooms with the chamber between them. Which is
     * fine: SCAN proposes and the player overrides.
     */
    private Doors classifyDoors(ServerLevel level, long chamberId,
                                java.util.List<BlockPos> doorFeet) {
        Atmosphere atmosphere = Atmosphere.get(level);
        BlockPos inner = null;
        BlockPos outer = null;
        for (BlockPos foot : doorFeet) {
            if (farSealedRoom(atmosphere, foot, chamberId) != null) {
                inner = foot;
            } else {
                outer = foot;
            }
        }
        // Two doors onto the same kind of space: split them deterministically rather than
        // leaving a terminal empty, since a wrong proposal is correctable and a blank one
        // teaches nothing.
        if (inner == null && outer == null && doorFeet.size() == 2) {
            inner = doorFeet.get(0);
            outer = doorFeet.get(1);
        } else if (inner == null) {
            inner = otherThan(doorFeet, outer);
        } else if (outer == null) {
            outer = otherThan(doorFeet, inner);
        }
        return new Doors(inner, outer);
    }

    private static @Nullable BlockPos otherThan(java.util.List<BlockPos> feet,
                                                @Nullable BlockPos taken) {
        for (BlockPos foot : feet) {
            if (!foot.equals(taken)) {
                return foot;
            }
        }
        return null;
    }

    /**
     * The sealed room on the far side of a door from the chamber, or null if it opens to
     * space. Sealed only: an unsealed volume beyond a door — a cave, the open surface — is
     * space as far as an airlock cares, and treating its near-vacuum as "habitat pressure"
     * would make the repressurise target zero and the refill a no-op.
     */
    private static @Nullable RoomState farSealedRoom(Atmosphere atmosphere, BlockPos foot,
                                                     long chamberId) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            RoomState side = atmosphere.roomAt(foot.relative(dir));
            if (side != null && side.id() == chamberId) {
                Atmosphere.RoomReading far = atmosphere.readingAt(foot.relative(dir.getOpposite()));
                return far != null && far.sealed() ? far.state() : null;
            }
        }
        return null;
    }

    private static boolean isShut(ServerLevel level, BlockPos foot) {
        BlockState state = level.getBlockState(foot);
        return !(state.getBlock() instanceof DoorBlock) || !state.getValue(BlockStateProperties.OPEN);
    }

    /**
     * Throws or releases the interlock latch on one of the airlock's doors.
     *
     * <p>Shuts it first when it is being locked, because a latch thrown on an open door
     * would hold it open — the one state the interlock exists to make impossible. After
     * that the door genuinely cannot move (BulkheadDoorBlock refuses the hand, redstone and
     * pistons alike), so there is nothing left to correct on later ticks.
     *
     * <p>This replaces force-shutting the door every tick. That repaired the invariant
     * instead of enforcing it: the door opened, gas moved, and it was pulled shut up to a
     * tick later — reported as <em>"могу дёргать двери и ничего"</em> — and redstone never
     * went through the repair at all. See design/door-interlock.md.
     */
    private static void latch(ServerLevel level, BlockPos foot, boolean locked) {
        if (locked) {
            forceShut(level, foot);
            // Renewed every tick. Stop renewing — because this controller was mined, or
            // the chunk unloaded, or the server was killed — and the latch opens by itself
            // within a few seconds. Nothing has to remember to release it.
            DoorLatches.renew(level, foot);
        } else {
            DoorLatches.release(level, foot);
        }
        BulkheadDoorBlock.setLocked(level, foot, locked);
    }

    /** Shuts both halves of a door if it is open, and re-scans the atmosphere it seals. */
    private static void forceShut(ServerLevel level, BlockPos foot) {
        BlockState footState = level.getBlockState(foot);
        if (!(footState.getBlock() instanceof DoorBlock) || !footState.getValue(BlockStateProperties.OPEN)) {
            return;
        }
        setHalfShut(level, foot);
        setHalfShut(level, foot.above());
        Atmosphere.get(level).invalidate(foot);
    }

    private static void setHalfShut(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof DoorBlock && state.getValue(BlockStateProperties.OPEN)) {
            level.setBlock(pos, state.setValue(BlockStateProperties.OPEN, false), Block.UPDATE_ALL);
        }
    }

    private static double tankPressure(ServerLevel level, BlockPos tank) {
        return level.getBlockEntity(tank) instanceof GasTankBlockEntity vessel
                ? vessel.pressureKPa() : 0;
    }

    private static double tankMoles(ServerLevel level, BlockPos tank) {
        return level.getBlockEntity(tank) instanceof GasTankBlockEntity vessel
                ? vessel.contents().gases().totalMoles() : 0;
    }

    private boolean consumeCommand() {
        boolean was = commanded;
        commanded = false;
        return was;
    }

    private void resetPhase(ServerLevel level) {
        if (phase != AirlockCycle.Phase.SEALED) {
            phase = AirlockCycle.Phase.SEALED;
            ticksInPhase = 0;
            cycleFault = AirlockCycle.Fault.NONE;
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(),
                    Block.UPDATE_CLIENTS);
        }
    }

    // ---- persistence ---------------------------------------------------------

    /**
     * A phase is real state, and so are the bindings — a commissioned panel that forgot
     * what the player told it after a reload would be worse than one that never asked.
     * The status fields ride along because the same serialisation is the client-sync
     * payload the block's gauge reads; on a fresh server load they are stale for at most
     * one tick.
     */
    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString("phase", phase.name());
        output.putInt("ticks_in_phase", ticksInPhase);
        output.putString("chamber_fault", chamberFault.name());
        output.putString("cycle_fault", cycleFault.name());
        output.putDouble("chamber_kpa", chamberPressureKPa);
        output.putDouble("habitat_kpa", habitatPressureKPa);
        output.putBoolean("commissioned", true);
        output.putBoolean("signal_was_live", signalWasLive);
        for (Role role : Role.values()) {
            long device = bindings.device(role);
            if (device != DeviceBinding.NONE) {
                output.putLong(bindingKey(role), device);
            }
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        phase = enumOr(input.getStringOr("phase", ""), AirlockCycle.Phase.SEALED);
        ticksInPhase = input.getIntOr("ticks_in_phase", 0);
        chamberFault = enumOr(AirlockResolver.Fault.values(),
                input.getStringOr("chamber_fault", ""), AirlockResolver.Fault.NONE);
        cycleFault = enumOr(AirlockCycle.Fault.values(),
                input.getStringOr("cycle_fault", ""), AirlockCycle.Fault.NONE);
        chamberPressureKPa = input.getDoubleOr("chamber_kpa", 0);
        habitatPressureKPa = input.getDoubleOr("habitat_kpa", DEFAULT_HABITAT_KPA);
        // Absent on every controller saved before commissioning existed — which is exactly
        // the airlocks that need migrating, and no others.
        commissioned = input.getBooleanOr("commissioned", false);
        // Absent on a save from before this was tracked; false is the same one-tick cost a
        // fresh controller already pays the first time a held-high wire is ever seen, not
        // an ongoing bug.
        signalWasLive = input.getBooleanOr("signal_was_live", false);
        bindings.clearAll();
        for (Role role : Role.values()) {
            long device = input.getLongOr(bindingKey(role), DeviceBinding.NONE);
            if (device != DeviceBinding.NONE) {
                bindings.bind(role, device);
            }
        }
    }

    private static String bindingKey(Role role) {
        return "bound_" + role.name().toLowerCase(java.util.Locale.ROOT);
    }

    // The chunk-load and block-update payload used to be written out here by hand. It is
    // ReadableBlockEntity's job now: this was the one machine in the mod that synced at all, and
    // leaving a second copy of the mechanism is how the other nine came to be missing it.

    private static AirlockCycle.Phase enumOr(String name, AirlockCycle.Phase fallback) {
        return enumOr(AirlockCycle.Phase.values(), name, fallback);
    }

    private static <E extends Enum<E>> E enumOr(E[] values, String name, E fallback) {
        for (E value : values) {
            if (value.name().equals(name)) {
                return value;
            }
        }
        return fallback;
    }

    private record Doors(@Nullable BlockPos inner, @Nullable BlockPos outer) {}
}
