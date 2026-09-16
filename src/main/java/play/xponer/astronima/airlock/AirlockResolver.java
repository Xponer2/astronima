package play.xponer.astronima.airlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.jspecify.annotations.Nullable;
import play.xponer.astronima.atmosphere.Atmosphere;
import play.xponer.astronima.block.BulkheadDoorBlock;
import play.xponer.astronima.block.GasPortBlock;
import play.xponer.astronima.block.GasPumpBlock;
import play.xponer.astronima.pipe.PipeNetworks;
import play.xponer.astronima.sim.RoomState;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Finds the pieces of an airlock the player built, from the controller outward.
 *
 * <p>An airlock is not one block — it is a small sealed chamber with two bulkhead doors, a
 * gas port opening into it, and a pump running to a tank. The controller sequences those
 * pieces, so first it has to find them, and this is where. Nothing here decides <em>what to
 * do</em>; it only answers "is the airlock the player built complete, and if not, which
 * piece is missing?" — the named reasons the controller shows so a wrong build explains
 * itself at the block that is wrong, per {@code plumbing-rebuild.md} PART C.
 *
 * <p>The pump and tank are found by following the plumbing rather than by proximity: the
 * pump is whatever draws from the chamber's port run, and the tank is on that pump's
 * outlet. So the controller drives the chain the player already built and understood,
 * wherever it runs, instead of demanding a fixed layout.
 */
public final class AirlockResolver {

    /** Why an airlock will not resolve — a build fault, shown before the cycle can run. */
    public enum Fault {
        NONE,
        /** The controller's front does not open into any enclosed volume. */
        NO_CHAMBER,
        /** The chamber leaks — it can never hold or lose pressure on purpose. */
        NOT_SEALED,
        /** An airlock needs exactly two doors: one to the habitat, one to space. */
        NEEDS_TWO_DOORS,
        /** Nothing on the chamber's plumbing draws from it. */
        NO_PUMP,
        /**
         * More than one pump draws from this chamber, so which one the cycle drives is
         * undefined — the same refusal-to-guess a pump makes with two volumes on a face.
         * Distinct from {@link #NO_PUMP} because "no pump" while two are plumbed in is a
         * lie, and a player told the wrong thing looks in the wrong place.
         */
        TOO_MANY_PUMPS,
        /** The pump has nowhere to put the chamber's air. */
        NO_TANK
    }

    /** A complete airlock: the chamber, its two door feet, and the pump and tank serving it. */
    public record Resolved(RoomState chamber, List<BlockPos> doorFeet, BlockPos pump,
                           BlockPos tank) {}

    /** Either a resolved airlock or the reason it is incomplete. */
    public record Result(@Nullable Resolved airlock, Fault fault) {
        public boolean ok() {
            return airlock != null;
        }

        static Result fail(Fault fault) {
            return new Result(null, fault);
        }
    }

    /**
     * Resolves the airlock a controller at {@code controllerPos} facing {@code facing} sits
     * on. {@code facing} points into the chamber.
     */
    public static Result resolve(ServerLevel level, BlockPos controllerPos, Direction facing) {
        BlockPos inside = controllerPos.relative(facing);
        Atmosphere atmosphere = Atmosphere.get(level);

        Atmosphere.RoomReading reading = atmosphere.readingAt(inside);
        if (reading == null) {
            return Result.fail(Fault.NO_CHAMBER);
        }
        if (!reading.sealed()) {
            return Result.fail(Fault.NOT_SEALED);
        }

        List<BlockPos> doorFeet = doorFeet(atmosphere, inside, level);
        if (doorFeet.size() != 2) {
            return Result.fail(Fault.NEEDS_TWO_DOORS);
        }

        Set<BlockPos> pumps = findPumps(level, atmosphere, inside);
        if (pumps.isEmpty()) {
            return Result.fail(Fault.NO_PUMP);
        }
        if (pumps.size() > 1) {
            return Result.fail(Fault.TOO_MANY_PUMPS);
        }
        BlockPos pump = pumps.iterator().next();

        BlockPos tank = findTank(level, pump);
        if (tank == null) {
            return Result.fail(Fault.NO_TANK);
        }

        return new Result(new Resolved(reading.state(), doorFeet, pump, tank), Fault.NONE);
    }

    /**
     * The distinct bulkhead doors bounding the chamber, as their foot positions.
     *
     * <p>A door is two blocks; collapsing each to its foot means two real doors read as two,
     * not four, so a legitimate airlock is not mistaken for one with too many doors.
     */
    private static List<BlockPos> doorFeet(Atmosphere atmosphere, BlockPos inside,
                                           ServerLevel level) {
        Set<BlockPos> feet = new HashSet<>();
        for (BlockPos door : atmosphere.boundaryMatching(inside,
                state -> state.getBlock() instanceof BulkheadDoorBlock)) {
            BlockState state = level.getBlockState(door);
            boolean upper = state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                    && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER;
            feet.add(upper ? door.below() : door);
        }
        // Sorted, because this is what SCAN proposes from. Straight off the hash set the
        // order varied run to run, so a chamber whose two doors both open onto vacuum got
        // a different inner/outer proposal each time the button was pressed — a proposal
        // that changes when nothing changed is worse than a wrong one you can correct.
        return feet.stream().sorted(java.util.Comparator.comparingLong(BlockPos::asLong))
                .map(BlockPos::immutable).toList();
    }

    /**
     * The pump drawing from the chamber: the one whose inlet face sits on a pipe of a run
     * opening into the chamber through one of its ports.
     */
    private static Set<BlockPos> findPumps(ServerLevel level, Atmosphere atmosphere,
                                           BlockPos inside) {
        Set<BlockPos> pumps = new HashSet<>();
        for (BlockPos port : atmosphere.boundaryMatching(inside,
                state -> state.getBlock() instanceof GasPortBlock)) {
            PipeNetworks.Resolved run = PipeNetworks.resolveSide(level, port);
            if (run == null) {
                continue;
            }
            // The pump is adjacent to the port or to any pipe on its run, drawing from it.
            Set<BlockPos> runParts = new HashSet<>(run.pipes());
            runParts.add(port);
            for (BlockPos part : runParts) {
                for (Direction dir : Direction.values()) {
                    BlockPos neighbour = part.relative(dir);
                    BlockState state = level.getBlockState(neighbour);
                    if (state.getBlock() instanceof GasPumpBlock
                            && GasPumpBlock.inletSide(state, neighbour).equals(part)) {
                        pumps.add(neighbour.immutable());
                    }
                }
            }
        }
        // Returned whole so the caller can tell "none" from "several" — the same
        // refusal-to-guess a pump makes with two volumes on a face, but said honestly.
        return pumps;
    }

    /** The tank on the pump's outlet run. */
    private static @Nullable BlockPos findTank(ServerLevel level, BlockPos pump) {
        BlockState pumpState = level.getBlockState(pump);
        PipeNetworks.Resolved outlet = PipeNetworks.resolveSide(level,
                GasPumpBlock.outletSide(pumpState, pump));
        if (outlet == null || outlet.tanks().isEmpty()) {
            return null;
        }
        return outlet.tanks().get(0);
    }

    private AirlockResolver() {}
}
