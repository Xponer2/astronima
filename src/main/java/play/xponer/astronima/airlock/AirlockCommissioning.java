package play.xponer.astronima.airlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.jspecify.annotations.Nullable;
import play.xponer.astronima.atmosphere.Atmosphere;
import play.xponer.astronima.block.BulkheadDoorBlock;
import play.xponer.astronima.block.GasPumpBlock;
import play.xponer.astronima.block.GasTankBlock;
import play.xponer.astronima.block.entity.GasPumpBlockEntity;
import play.xponer.astronima.sim.RoomState;
import play.xponer.astronima.sim.airlock.DeviceBinding;
import play.xponer.astronima.sim.airlock.DeviceBinding.Problem;
import play.xponer.astronima.sim.airlock.DeviceBinding.Role;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Checks the devices the player commissioned — the half of the deal that is ours.
 *
 * <p>Rule 17 splits an assembled machine in two: the player says <em>which</em> door is the
 * outer one and which pump is theirs, and we say whether the thing they named can actually
 * do that job. That split is the whole gain. A fault about a device the player chose points
 * at something they put there on purpose — <em>"OUTER DOOR — not on the chamber"</em> — so
 * they know exactly which block to move. A fault about a device we failed to find
 * (<em>"no pump"</em>) describes our search and, when the search was too narrow, blames them
 * for a build that was correct.
 *
 * <p>What is still ours, and all of it is here:
 * <ul>
 *   <li>the chamber, because it is not a bound device — it is whatever volume the panel is
 *       mounted on, read straight from the room scan;</li>
 *   <li>whether a bound door is a door, still exists, and sits on that chamber's boundary;</li>
 *   <li>whether the two doors are two different doors;</li>
 *   <li>whether another controller already holds either door;</li>
 *   <li>whether the bound pump draws from that chamber and can reach the bound tank;</li>
 *   <li>whether another controller already holds that pump.</li>
 * </ul>
 *
 * <p>Everything it does <em>not</em> check is deliberate. Where the doors are in the walls,
 * how long the pipe run is, whether the tank is shared with the habitat line — none of that
 * is ours to have an opinion about, and having one is exactly what refused a chamber with
 * both doors in the same wall.
 */
public final class AirlockCommissioning {

    /**
     * How long a door claim survives without renewal — the same window
     * {@link play.xponer.astronima.airlock.DoorLatches#LEASE_TICKS} already uses for the
     * latch itself, so a controller that stops ticking (broken, unloaded, killed) frees
     * every hold it had within the same few seconds, not some of them sooner than others.
     */
    private static final long DOOR_CLAIM_LEASE_TICKS =
            play.xponer.astronima.airlock.DoorLatches.LEASE_TICKS;

    private record DoorClaim(long holder, long expiresAt) {}

    /**
     * Which controller currently holds each door — the check a pump already has
     * ({@link GasPumpBlockEntity#heldBy()}) and a door had none of. Without it, nothing
     * stops two controllers from both binding the same physical door and fighting over its
     * latch every tick: whichever one ticks last on a given tick decides whether the door is
     * actually locked, which makes the interlock's real state a coin flip rather than a
     * guarantee.
     *
     * <p>Per dimension, same reasoning as {@code DoorLatches}: two doors at the same
     * coordinates in different dimensions are two different doors.
     */
    private static final Map<ResourceKey<Level>, Map<Long, DoorClaim>> DOOR_CLAIMS =
            new HashMap<>();

    private static Map<Long, DoorClaim> doorClaims(ServerLevel level) {
        return DOOR_CLAIMS.computeIfAbsent(level.dimension(), key -> new HashMap<>());
    }

    /**
     * Whether some controller other than {@code claimant} holds a live claim on this door.
     * A peek, not a claim: {@link #validate} calls this every tick to report the fault, and
     * the actual claim is taken separately, once commissioning as a whole checks out — the
     * same split the pump makes between {@code checkPump}'s peek and {@code pump.claim()}.
     */
    private static boolean heldByAnotherController(ServerLevel level, BlockPos foot,
                                                   BlockPos claimant, long now) {
        DoorClaim claim = doorClaims(level).get(foot.asLong());
        return claim != null && claim.holder() != claimant.asLong() && now <= claim.expiresAt();
    }

    /**
     * Takes or renews the claim on a door for the controller commissioning it.
     *
     * @return true if {@code claimant} now holds the door; false if another controller holds
     *         a live claim on it — the same refusal-to-guess the pump makes when it finds
     *         two volumes on a face
     */
    public static boolean claimDoor(ServerLevel level, BlockPos foot, BlockPos claimant,
                                    long now) {
        if (heldByAnotherController(level, foot, claimant, now)) {
            return false;
        }
        doorClaims(level).put(foot.asLong(),
                new DoorClaim(claimant.asLong(), now + DOOR_CLAIM_LEASE_TICKS));
        return true;
    }

    /** Lets go of a door claim on purpose — a controller whose commissioning stopped checking out. */
    public static void releaseDoor(ServerLevel level, BlockPos foot, BlockPos claimant) {
        doorClaims(level).computeIfPresent(foot.asLong(),
                (key, claim) -> claim.holder() == claimant.asLong() ? null : claim);
    }

    /** A commissioned, validated airlock: everything the cycle needs, all of it named. */
    public record Commissioned(RoomState chamber, BlockPos innerDoor, BlockPos outerDoor,
                               BlockPos pump, BlockPos tank) {}

    /**
     * The state of one panel: the chamber it is mounted on, the problem with each terminal,
     * and — only when every terminal is clean — the airlock to run.
     */
    public record Result(@Nullable Commissioned airlock,
                         AirlockResolver.Fault chamberFault,
                         Map<Role, Problem> problems) {

        public boolean ok() {
            return airlock != null;
        }

        public Problem problem(Role role) {
            return problems.getOrDefault(role, Problem.NONE);
        }

        /**
         * The first terminal with something wrong, in panel order, or null.
         *
         * <p>One at a time on purpose: a panel that lists four faults at once is a wall of
         * text, and the player can only walk to one block anyway.
         */
        public @Nullable Role firstFault() {
            for (Role role : Role.values()) {
                if (problem(role) != Problem.NONE) {
                    return role;
                }
            }
            return null;
        }
    }

    /**
     * Validates the bindings of the controller at {@code controllerPos} facing
     * {@code facing}, which points into its chamber.
     *
     * @param claimant the controller asking, so a pump it already holds does not read as
     *                 in use by someone else
     */
    public static Result validate(ServerLevel level, BlockPos controllerPos, Direction facing,
                                  DeviceBinding bindings, BlockPos claimant) {
        Map<Role, Problem> problems = new EnumMap<>(Role.class);
        Atmosphere atmosphere = Atmosphere.get(level);

        // The chamber is not a bound device: it is the volume the panel is bolted to, and
        // moving the panel is how you change it. Reported separately for that reason.
        BlockPos inside = controllerPos.relative(facing);
        Atmosphere.RoomReading reading = atmosphere.readingAt(inside);
        AirlockResolver.Fault chamberFault = reading == null ? AirlockResolver.Fault.NO_CHAMBER
                : !reading.sealed() ? AirlockResolver.Fault.NOT_SEALED
                : AirlockResolver.Fault.NONE;

        // Every terminal is checked whatever the chamber is doing. Bailing out early on a
        // chamber fault looked harmless and was not: a door broken mid-cycle opens the
        // chamber, so the very moment the panel most needs to name a device it would have
        // gone quiet and reported nothing wrong with any of them. Only the one question
        // that genuinely needs a chamber — is this door on it — is skipped.
        BlockPos inner = boundPos(bindings, Role.INNER_DOOR);
        BlockPos outer = boundPos(bindings, Role.OUTER_DOOR);
        long chamberId = reading == null ? Long.MIN_VALUE : reading.state().id();
        boolean haveChamber = chamberFault == AirlockResolver.Fault.NONE;
        long now = level.getGameTime();
        problems.put(Role.INNER_DOOR, checkDoor(level, atmosphere, bindings, Role.INNER_DOOR,
                chamberId, haveChamber, claimant, now));
        problems.put(Role.OUTER_DOOR, checkDoor(level, atmosphere, bindings, Role.OUTER_DOOR,
                chamberId, haveChamber, claimant, now));

        BlockPos pump = boundPos(bindings, Role.PUMP);
        BlockPos tank = boundPos(bindings, Role.TANK);
        problems.put(Role.TANK, checkTank(level, tank));
        problems.put(Role.PUMP, checkPump(level, pump, claimant));

        boolean clean = haveChamber
                && problems.values().stream().allMatch(problem -> problem == Problem.NONE);
        Commissioned airlock = clean
                ? new Commissioned(reading.state(), inner, outer, pump, tank) : null;
        return new Result(airlock, chamberFault, Map.copyOf(problems));
    }

    private static Problem checkDoor(ServerLevel level, Atmosphere atmosphere,
                                     DeviceBinding bindings, Role role, long chamberId,
                                     boolean haveChamber, BlockPos claimant, long now) {
        BlockPos foot = boundPos(bindings, role);
        if (foot == null) {
            return Problem.UNBOUND;
        }
        BlockState state = level.getBlockState(foot);
        if (state.isAir()) {
            return Problem.MISSING;
        }
        if (!(state.getBlock() instanceof BulkheadDoorBlock)) {
            return Problem.WRONG_KIND;
        }
        if (bindings.isShared(role)) {
            return Problem.SAME_DOOR;
        }
        // A pump already refuses to be driven by two controllers at once
        // (GasPumpBlockEntity#heldBy); a door had no equivalent, so two airlocks could bind
        // the same physical door and fight over its latch every tick.
        if (heldByAnotherController(level, foot, claimant, now)) {
            return Problem.IN_USE;
        }

        if (haveChamber && !touchesChamber(atmosphere, foot, chamberId)) {
            return Problem.NOT_ON_CHAMBER;
        }
        return Problem.NONE;
    }

    /**
     * Whether a door actually seals the chamber — asked of the room scan, not of the
     * chamber's list of solid boundary blocks.
     *
     * <p>This distinction is the reported bug, and it is worth the extra method.
     * {@code boundaryMatching} collects <em>solid</em> neighbours, and an open door is not
     * solid: it stops being on the boundary the instant it swings. That is why an airlock
     * whose doors were pulled by hand reported <em>"needs two doors"</em> — the doors were
     * right there, and we had lost sight of both of them because they were open.
     * <blockquote>"при любых обстоятельствах могу дёргать двери и ничего, просто аирлок
     * сразу ломается и говорит что у него не две двери"</blockquote>
     *
     * <p>So the question asked here is geometric and survives the door moving: is either
     * half of this door in the chamber's air, or next to it? An open door merges into the
     * room it was sealing, a shut one abuts it, and both are "on the chamber". A cycle with
     * a door open is then a <em>cycle</em> fault that names the open door, which is what a
     * real panel says, instead of a build fault claiming the door does not exist.
     */
    private static boolean touchesChamber(Atmosphere atmosphere, BlockPos foot, long chamberId) {
        for (BlockPos half : new BlockPos[] {foot, foot.above()}) {
            RoomState here = atmosphere.roomAt(half);
            if (here != null && here.id() == chamberId) {
                return true; // open, and merged into the chamber's own air
            }
            for (Direction dir : Direction.values()) {
                RoomState beside = atmosphere.roomAt(half.relative(dir));
                if (beside != null && beside.id() == chamberId) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Problem checkTank(ServerLevel level, @Nullable BlockPos tank) {
        if (tank == null) {
            return Problem.UNBOUND;
        }
        BlockState state = level.getBlockState(tank);
        if (state.isAir()) {
            return Problem.MISSING;
        }
        return state.getBlock() instanceof GasTankBlock ? Problem.NONE : Problem.WRONG_KIND;
    }

    /**
     * The pump: is it a pump, is it still there, and is anyone else already driving it.
     *
     * <p><strong>Deliberately not checked: whether gas can currently get from the chamber
     * to this pump, or from this pump to the tank.</strong> That reads like an obvious
     * commissioning check and it is a trap, because reachability is not a property of the
     * build — it is the <em>current position of every valve on the line</em>. A player who
     * shuts a valve would be told their pump was bound wrong, which is a lie: the pump is
     * exactly where they put it, and the pipe is right there. Worse, it would fail the
     * commissioning check and reset the cycle instead of stalling it, quietly gutting
     * {@code scenario_airlock_shut_valve_stalls_not_hangs} — the one test that ties the
     * valve to the airlock after the valve shipped decorative once already.
     *
     * <p>So commissioning answers "is that the right kind of block, and is it yours"; the
     * cycle answers "is gas actually moving", and it already does, by stalling into
     * {@code PUMP_TOO_SLOW} with the pump's own gauge showing why.
     */
    private static Problem checkPump(ServerLevel level, @Nullable BlockPos pump,
                                     BlockPos claimant) {
        if (pump == null) {
            return Problem.UNBOUND;
        }
        BlockState state = level.getBlockState(pump);
        if (state.isAir()) {
            return Problem.MISSING;
        }
        if (!(state.getBlock() instanceof GasPumpBlock)
                || !(level.getBlockEntity(pump) instanceof GasPumpBlockEntity pumpEntity)) {
            return Problem.WRONG_KIND;
        }
        BlockPos holder = pumpEntity.heldBy();
        if (holder != null && !holder.equals(claimant)
                && level.getBlockEntity(holder)
                        instanceof play.xponer.astronima.block.entity.AirlockControllerBlockEntity) {
            return Problem.IN_USE;
        }
        return Problem.NONE;
    }

    private static @Nullable BlockPos boundPos(DeviceBinding bindings, Role role) {
        long device = bindings.device(role);
        return device == DeviceBinding.NONE ? null : BlockPos.of(device);
    }

    /**
     * The foot of a door, whichever half was clicked.
     *
     * <p>A door is two blocks and a player clicks whichever one is at eye height, so the
     * binding is normalised the moment it is made — otherwise the same door bound twice
     * from two different heights would read as two different devices and the "same door"
     * check would never fire.
     */
    public static BlockPos doorFoot(ServerLevel level, BlockPos clicked) {
        BlockState state = level.getBlockState(clicked);
        boolean upper = state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER;
        return upper ? clicked.below().immutable() : clicked.immutable();
    }

    private AirlockCommissioning() {}
}
