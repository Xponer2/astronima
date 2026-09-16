package play.xponer.astronima.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import play.xponer.astronima.power.CableNetworks;
import play.xponer.astronima.power.PowerRun;
import play.xponer.astronima.registry.ModBlockEntities;
import play.xponer.astronima.sim.power.PowerBalance;
import play.xponer.astronima.wire.WirePower;

/**
 * A radioisotope thermoelectric generator: power that needs no fuel, no sun and no reagent at
 * all, and the reason it is not free (design/radiation.md, design/power.md §P5's own promise —
 * "the RTG is blocked on v0.79").
 *
 * <p>Mirrors {@link SolarArrayBlockEntity} almost exactly — passive, no internal buffer, no
 * input slot — with one swap: a constant output instead of an environmental read. Real Pu-238
 * decays on an 87.7-year half-life, far longer than any playthrough, so treating the output as
 * constant is accuracy, not laziness (design/radiation.md §2).
 *
 * <p><strong>It is also, unconditionally, a gamma source</strong> — see
 * {@code AtmosphereEvents.applyRadiation}, which finds every {@code RtgBlockEntity} within range
 * of a player each tick the same way v0.65's cryocooler found its bound tank. There is no
 * shielding built into the block itself: the existing wall/room grammar is the whole answer to
 * where you may safely put one, the same "nothing new invented" shape the cryo tank's BLEVE
 * counter already uses.
 */
public class RtgBlockEntity extends ReadableBlockEntity {

    /** Real order-of-magnitude for a small radioisotope generator, W — comparable to one solar
     * panel's average output, but day/night and weather independent. */
    public static final double ELECTRICAL_W = 40.0;

    public RtgBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RTG.get(), pos, state);
    }

    /** What it is making right now, W — always {@link #ELECTRICAL_W}, for the block's readout. */
    public double watts() {
        return ELECTRICAL_W;
    }

    public void serverTick(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        double made = PowerBalance.joules(ELECTRICAL_W, 1.0 / 20.0);

        WirePower.Run traced = WirePower.runFrom(serverLevel, pos);
        if (traced != null && !traced.isEmpty()) {
            double delivered = WirePower.send(serverLevel, traced, made, 1.0 / 20.0);
            for (BlockPos terminal : traced.terminals()) {
                if (delivered <= 0) {
                    break;
                }
                if (serverLevel.getBlockEntity(terminal) instanceof PowerCellBlockEntity cell) {
                    delivered = cell.charge(delivered);
                }
            }
            return;
        }

        CableNetworks.Resolved run = CableNetworks.resolveFrom(serverLevel, pos);
        if (run == null || run.isEmpty()) {
            return;
        }
        double arrived = PowerRun.sendAlong(serverLevel, run, made, 1.0 / 20.0);
        double each = arrived / run.cells().size();
        for (PowerCellBlockEntity cell : run.cells()) {
            cell.charge(each);
        }
    }
}
