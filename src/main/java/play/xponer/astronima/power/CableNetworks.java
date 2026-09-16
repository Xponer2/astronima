package play.xponer.astronima.power;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import play.xponer.astronima.block.PowerCableBlock;
import play.xponer.astronima.block.entity.PowerCellBlockEntity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * What is on the other end of this cable.
 *
 * <p><strong>The same walk {@code PipeNetworks} does, deliberately.</strong> It is the same
 * graph problem — flood out from a block, collect the parts you meet, stop at anything that is
 * not part of the run — and inventing a second answer to a solved problem is how two subsystems
 * end up disagreeing about what "connected" means.
 *
 * <p>What differs is the physics, and it differs completely. A gas run is limited by pressure
 * and throttled by a valve; a cable run is limited by <strong>length</strong>, and what it loses
 * it turns into heat along its own body. See {@code sim/circuit/Delivery}.
 */
public final class CableNetworks {

    /**
     * A resolved run.
     *
     * @param cables every cable block in it, in the order the walk found them — which is what
     *               the heat is spread across
     * @param cells  the stores it touches
     */
    public record Resolved(List<BlockPos> cables, List<PowerCellBlockEntity> cells) {
        /** Length of the run, in blocks, which is what the loss is charged against. */
        public int length() {
            return cables.size();
        }

        public boolean isEmpty() {
            return cells.isEmpty();
        }
    }

    /**
     * Walks the run touching {@code start}, which is expected to be a source or a draw.
     *
     * <p>Starts from the <em>neighbours</em> rather than from the block itself, because a solar
     * array is not a cable: the machine at each end sits beside the run rather than in it, and
     * a walk that demanded its start be part of the network would find nothing at all.
     */
    public static @Nullable Resolved resolveFrom(ServerLevel level, BlockPos start) {
        Set<BlockPos> visited = new HashSet<>();
        List<BlockPos> cables = new ArrayList<>();
        List<PowerCellBlockEntity> cells = new ArrayList<>();
        Deque<BlockPos> frontier = new ArrayDeque<>();

        // Anything bolted straight on counts as a zero-length run, so adjacency keeps working
        // exactly as it did before cables existed — no player's base breaks by adding this.
        for (Direction side : Direction.values()) {
            BlockPos neighbour = start.relative(side);
            if (level.getBlockEntity(neighbour) instanceof PowerCellBlockEntity cell) {
                cells.add(cell);
            } else if (isCable(level.getBlockState(neighbour)) && visited.add(neighbour)) {
                frontier.add(neighbour);
            }
        }

        while (!frontier.isEmpty()) {
            BlockPos pos = frontier.poll();
            cables.add(pos);
            for (Direction side : Direction.values()) {
                BlockPos neighbour = pos.relative(side);
                if (!visited.add(neighbour)) {
                    continue;
                }
                if (level.getBlockEntity(neighbour) instanceof PowerCellBlockEntity cell) {
                    cells.add(cell);
                } else if (isCable(level.getBlockState(neighbour))) {
                    frontier.add(neighbour);
                }
            }
        }
        return cells.isEmpty() && cables.isEmpty() ? null : new Resolved(cables, cells);
    }

    public static boolean isCable(BlockState state) {
        return state.getBlock() instanceof PowerCableBlock;
    }

    private CableNetworks() {}
}
