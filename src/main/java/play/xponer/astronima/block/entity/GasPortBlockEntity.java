package play.xponer.astronima.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import play.xponer.astronima.pipe.PipeNetworks;
import play.xponer.astronima.registry.ModBlockEntities;

/**
 * Drives the run of pipe this port belongs to.
 *
 * <p>The port carries the ticker rather than the pipe because a run without two ports
 * moves nothing, so there would be nothing for a lone pipe to do.
 *
 * <p><strong>One port ticks each network, not all of them.</strong> Every port on a run
 * resolves the same network, so letting each tick it would move gas once per opening —
 * a network with four ports would settle four times faster than one with two. That
 * would read as "more ports is better" rather than as a bug, which is the worst kind.
 * The port at the lowest packed position does the work and the rest stand down.
 *
 * <p>Deciding who stands down still means every port on the run asking, on the same
 * interval, "what does this run look like" — which used to mean every port walking the
 * whole run itself just to compare positions, so a network's real cost was (ports on it)
 * times (its own length) despite this class once claiming cost scaled with openings
 * alone. {@link PipeNetworks#resolveForLeaderElection} shares one walk per run per
 * interval across every port that asks, so the openings claim is now actually true: it
 * no longer matters how many ports share a run, only how many intervals pass.
 */
public class GasPortBlockEntity extends BlockEntity {
    private static final int INTERVAL_TICKS = 20;

    /** Real seconds per step, handed to a model that is stable at any step size. */
    private static final double STEP_SECONDS = INTERVAL_TICKS / 20.0;

    public GasPortBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GAS_PORT.get(), pos, state);
    }

    public void serverTick(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)
                || level.getGameTime() % INTERVAL_TICKS != 0) {
            return;
        }
        PipeNetworks.Resolved resolved =
                PipeNetworks.resolveForLeaderElection(serverLevel, pos, level.getGameTime());
        if (resolved == null || !isLeader(resolved, pos)) {
            return;
        }
        resolved.network().tick(STEP_SECONDS);
    }

    /** True when this is the port responsible for ticking the shared network. */
    private static boolean isLeader(PipeNetworks.Resolved resolved, BlockPos pos) {
        long self = pos.asLong();
        for (BlockPos port : resolved.ports()) {
            if (port.asLong() < self) {
                return false;
            }
        }
        return true;
    }
}
