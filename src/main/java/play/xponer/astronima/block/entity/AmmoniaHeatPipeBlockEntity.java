package play.xponer.astronima.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import play.xponer.astronima.atmosphere.Atmosphere;
import play.xponer.astronima.block.AmmoniaHeatPipeBlock;
import play.xponer.astronima.registry.ModBlockEntities;
import play.xponer.astronima.sim.RoomState;
import play.xponer.astronima.sim.thermal.HeatBalance;
import play.xponer.astronima.sim.thermal.HeatPipeTransfer;

/**
 * A sealed, passive conductor: it moves real heat from whichever of its own two ends is hotter
 * to whichever is colder, at the real rate {@link HeatPipeTransfer} computes — never a fixed
 * rate, never a source of heat itself. See {@code design/ammonia-heat-pipes.md}.
 *
 * <p>No gauge of its own (unlike the gas fittings this shares an axis-property shape with):
 * what a player needs to see is whether the two rooms it joins are actually moving toward each
 * other, and the gas analyzer already reads a room's own temperature — a second instrument on
 * the pipe itself would duplicate that rather than say something new.
 */
public class AmmoniaHeatPipeBlockEntity extends BlockEntity {

    public AmmoniaHeatPipeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.AMMONIA_HEAT_PIPE.get(), pos, state);
    }

    public void serverTick(Level level, BlockPos pos, BlockState state) {
        if (!(level instanceof ServerLevel serverLevel)
                || level.getGameTime() % Atmosphere.TICK_INTERVAL != 0) {
            return;
        }
        Direction.Axis axis = state.getValue(AmmoniaHeatPipeBlock.AXIS);
        Direction positive = Direction.get(Direction.AxisDirection.POSITIVE, axis);
        Atmosphere atmosphere = Atmosphere.get(serverLevel);
        RoomState roomA = atmosphere.roomAt(pos.relative(positive));
        RoomState roomB = atmosphere.roomAt(pos.relative(positive.getOpposite()));
        // Two different sealed rooms only: one end open, unsealed, or both ends reading the
        // same room (the pipe sits inside one room rather than bridging two) moves nothing.
        if (roomA == null || roomB == null || roomA.id() == roomB.id()) {
            return;
        }

        double tempA = roomA.temperatureK();
        double tempB = roomB.temperatureK();
        double watts = HeatPipeTransfer.watts(tempA, tempB);
        if (watts <= 0) {
            return;
        }
        double joules = watts * (Atmosphere.TICK_INTERVAL / 20.0);
        double capacityA = HeatBalance.SHELL_CAPACITY_J_PER_K_PER_M2
                * HeatBalance.hullAreaM2(roomA.volumeBlocks());
        double capacityB = HeatBalance.SHELL_CAPACITY_J_PER_K_PER_M2
                * HeatBalance.hullAreaM2(roomB.volumeBlocks());

        RoomState hot = tempA >= tempB ? roomA : roomB;
        RoomState cold = tempA >= tempB ? roomB : roomA;
        double hotCapacity = tempA >= tempB ? capacityA : capacityB;
        double coldCapacity = tempA >= tempB ? capacityB : capacityA;
        // Clamped so one tick can never push the hot end past the cold end's own starting
        // temperature - real heat pipes settle toward equilibrium, they do not overshoot it.
        hot.setTemperatureK(Math.max(cold.temperatureK(), hot.temperatureK() - joules / hotCapacity));
        cold.setTemperatureK(cold.temperatureK() + joules / coldCapacity);
    }
}
