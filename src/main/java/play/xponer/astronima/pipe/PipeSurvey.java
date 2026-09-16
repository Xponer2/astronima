package play.xponer.astronima.pipe;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import play.xponer.astronima.block.GasPumpBlock;
import play.xponer.astronima.block.GasValveBlock;
import play.xponer.astronima.sim.pipe.RunDiagnosis;

/**
 * Reads one run of pipe and hands it to {@link RunDiagnosis} to be judged.
 *
 * <p>The world half of the wrench's survey mode (plumbing-rebuild.md D3). Every fitting in
 * the mod already has a gauge; the <em>line</em> had none, and the line is what people get
 * wrong. Splitting it this way keeps the part worth proving — which of several things wrong
 * with a run to tell the player about — free of the world, and leaves here only the reading
 * off of blocks.
 */
public final class PipeSurvey {

    /**
     * Surveys the run {@code pos} belongs to, or null when that is not plumbing at all.
     *
     * <p>Uses the one-volume walk rather than the two-volume one on purpose: a run with a
     * single room on it is exactly the build this is meant to diagnose, so refusing to
     * resolve it would leave the player with the same silence they started with.
     */
    public static RunDiagnosis.@Nullable Survey of(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!PipeNetworks.isNetworkPart(state)) {
            return null;
        }
        PipeNetworks.Resolved run = PipeNetworks.resolveSide(level, pos);
        if (run == null) {
            // A shut valve severs its own run, so a walk that starts on one finds nothing.
            // That is not "no plumbing here" — it is the single most useful thing the
            // survey can say, and reporting it as an absence would throw it away.
            return new RunDiagnosis.Survey(1, 0, 0, 0, false, false, null);
        }
        boolean pumpOnRun = false;
        boolean pumpRunning = false;
        boolean shutValveOnLine = false;
        for (BlockPos part : parts(run)) {
            for (Direction dir : Direction.values()) {
                BlockState neighbour = level.getBlockState(part.relative(dir));
                if (neighbour.getBlock() instanceof GasPumpBlock) {
                    pumpOnRun = true;
                    pumpRunning |= neighbour.getValue(GasPumpBlock.RUNNING);
                } else if (neighbour.getBlock() instanceof GasValveBlock
                        && neighbour.getValue(GasValveBlock.SETTING) <= 0) {
                    shutValveOnLine = true;
                }
            }
        }
        // A shut valve is not *on* the resolved run: the walk stops dead at it, so from
        // either side the line reads as one that simply goes nowhere. That is technically
        // true and completely useless — the player can see there is more pipe past it, and
        // being told the line has one end sends them to rebuild something that is correct.
        // So the survey looks one block past the end of the walk and reports what it finds.
        double openFraction = shutValveOnLine ? 0 : run.openFraction();
        return new RunDiagnosis.Survey(run.pipes().size(), run.roomCount(),
                run.tanks().size(), openFraction, pumpOnRun, pumpRunning,
                RunDiagnosis.dominantGas(run.network().nodes()));
    }

    /** Every block of the run a neighbour could hang off: its pipes, ports and tanks. */
    private static java.util.List<BlockPos> parts(PipeNetworks.Resolved run) {
        java.util.List<BlockPos> parts = new java.util.ArrayList<>(run.pipes());
        parts.addAll(run.ports());
        parts.addAll(run.tanks());
        return parts;
    }

    private PipeSurvey() {}
}
