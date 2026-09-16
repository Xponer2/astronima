package play.xponer.astronima.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import play.xponer.astronima.atmosphere.Atmosphere;
import play.xponer.astronima.block.AlarmBlock;
import play.xponer.astronima.registry.ModBlockEntities;
import play.xponer.astronima.sim.Gas;
import play.xponer.astronima.sim.RoomState;
import play.xponer.astronima.sim.burn.Flammability;
import play.xponer.astronima.sim.tox.GasToxicity;

/** Danger evaluation for {@link AlarmBlock}: same thresholds the player's body uses. */
public class AlarmBlockEntity extends BlockEntity {
    private static final double LOW_O2_KPA = 16.0;
    private static final double CO_ALARM_KPA = 0.05;
    private static final int KLAXON_PERIOD_TICKS = 40;

    public AlarmBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ALARM.get(), pos, state);
    }

    public void serverTick(Level level, BlockPos pos, BlockState state) {
        if (!(level instanceof ServerLevel serverLevel)
                || level.getGameTime() % Atmosphere.TICK_INTERVAL != 0) {
            return;
        }
        boolean danger = evaluate(serverLevel, pos);
        if (state.getValue(AlarmBlock.LIT) != danger) {
            level.setBlockAndUpdate(pos, state.setValue(AlarmBlock.LIT, danger));
        }
        if (danger && level.getGameTime() % KLAXON_PERIOD_TICKS == 0) {
            level.playSound(null, pos, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.BLOCKS, 1.2f, 0.6f);
        }
    }

    private static boolean evaluate(ServerLevel level, BlockPos pos) {
        RoomState room = Atmosphere.get(level).roomTouching(pos);
        if (room == null) {
            return false;
        }
        if (room.partialPressureKPa(Gas.OXYGEN) < LOW_O2_KPA
                || room.partialPressureKPa(Gas.CARBON_MONOXIDE) > CO_ALARM_KPA
                || Flammability.ignitable(room)) {
            return true;
        }
        for (Gas gas : Gas.values()) {
            if (GasToxicity.acute(gas, room.partialPressureKPa(gas)).ordinal()
                    >= GasToxicity.Severity.IRRITATION.ordinal()) {
                return true;
            }
        }
        return false;
    }
}
