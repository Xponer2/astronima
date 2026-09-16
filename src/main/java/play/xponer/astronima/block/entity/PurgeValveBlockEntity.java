package play.xponer.astronima.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import play.xponer.astronima.atmosphere.Atmosphere;
import play.xponer.astronima.block.PurgeValveBlock;
import play.xponer.astronima.registry.ModBlockEntities;
import play.xponer.astronima.sim.GasFlow;
import play.xponer.astronima.sim.RoomState;

/**
 * An open purge valve, emptying the room behind it.
 *
 * <p>Holds no state of its own: whether it is dumping is on the block, where the player can see
 * it (rule 9), and how empty the room is belongs to the room. So this exists purely to have a
 * tick — which is also why the venting is not folded into the atmosphere's own loop. A room
 * knows how many boundary faces it has but not where they are, so finding an open valve from
 * the room's side would mean a scan every tick for something that is almost never there.
 */
public class PurgeValveBlockEntity extends BlockEntity {

    public PurgeValveBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PURGE_VALVE.get(), pos, state);
    }

    /**
     * Dumps a little more of the room, on the atmosphere's own cadence.
     *
     * <p><strong>Exponential rather than linear</strong>, through the same
     * {@link GasFlow#ventToVacuum} a hull breach uses — because it is the same physics, and
     * because it gives the emptying the shape a player expects: fast while there is pressure
     * behind it, slower and slower as there stops being any. A valve that emptied at a
     * constant rate and then stopped dead would read as a timer.
     *
     * <p>The check that there is still vacuum outside is repeated every tick, not trusted from
     * when it was opened: a player who walls the outside up mid-purge has stopped the purge,
     * and the valve should notice that the way it would notice anything else about the world.
     */
    public void serverTick(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)
                || level.getGameTime() % Atmosphere.TICK_INTERVAL != 0) {
            return;
        }
        BlockState state = getBlockState();
        if (!PurgeValveBlock.isVenting(state)
                || !PurgeValveBlock.facesVacuum(serverLevel, state, pos)) {
            return;
        }
        // The room on the INWARD side, not merely a room this block touches. A purge valve
        // sits in a shell with a habitat behind it and vacuum in front, so it touches two
        // volumes and "whichever one the lookup found first" is a coin toss - one that landed
        // on the exterior every time in testing, leaving the valve reporting itself open,
        // facing vacuum, and quietly emptying the void into the void.
        RoomState room = Atmosphere.get(serverLevel)
                .roomAt(pos.relative(state.getValue(PurgeValveBlock.FACING).getOpposite()));
        if (room == null) {
            return;
        }
        GasFlow.ventToVacuum(room, PurgeValveBlock.CONDUCTANCE_PER_SECOND,
                Atmosphere.TICK_INTERVAL / 20.0);
        Atmosphere.get(serverLevel).setDirty();
    }
}
