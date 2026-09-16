package play.xponer.astronima.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import play.xponer.astronima.atmosphere.Atmosphere;
import play.xponer.astronima.sim.Breathing;
import play.xponer.astronima.sim.Gas;
import play.xponer.astronima.sim.RoomState;
import play.xponer.astronima.sim.burn.Flammability;

/**
 * Furnaces burn fuel, and burning needs oxygen.
 *
 * <p>A vanilla furnace happily smelts in hard vacuum, which quietly undermines the
 * whole premise. This stalls it when the surrounding air cannot sustain combustion,
 * and — more interestingly — makes a working furnace <em>consume the room's oxygen
 * and exhale CO2</em>. Smelting indoors competes directly with the player's lungs,
 * which is the tension that later makes the electric induction furnace worth building.
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class AbstractFurnaceBlockEntityMixin {
    /**
     * Oxygen drawn per tick by a burning furnace. Real charcoal burns roughly a mole
     * of O2 per second at a forge's rate; this is scaled to the same gameplay clock
     * the player's metabolism uses, so a furnace is worth several people.
     */
    private static final double ASTRONIMA$O2_PER_TICK =
            Breathing.O2_CONSUMPTION_MOL_PER_S * 4.0 * 0.05;

    @Inject(method = "serverTick", at = @At("HEAD"), cancellable = true)
    private static void astronima$requireOxygen(ServerLevel level, BlockPos pos, BlockState state,
                                                AbstractFurnaceBlockEntity entity, CallbackInfo callback) {
        RoomState room = Atmosphere.get(level).roomTouching(pos);
        if (room == null) {
            // Not in any enclosed volume we track: outdoors in a normal world, or in
            // solid rock. Leave vanilla behaviour alone rather than guessing.
            return;
        }
        boolean starved = room.partialPressureKPa(Gas.OXYGEN) <= Flammability.MIN_O2_KPA;
        boolean showsLit = state.hasProperty(AbstractFurnaceBlock.LIT) && state.getValue(AbstractFurnaceBlock.LIT);
        if (starved) {
            // Freeze the burn exactly where it is — and stop *looking* alight, or the
            // furnace would glow and roar while doing nothing at all.
            if (showsLit) {
                level.setBlockAndUpdate(pos, state.setValue(AbstractFurnaceBlock.LIT, false));
            }
            callback.cancel();
            return;
        }
        // Air is breathable again: restore the flame vanilla still believes is burning.
        // Vanilla only writes the state when its own view changes, so without this the
        // furnace would work invisibly after being starved.
        if (!showsLit && state.hasProperty(AbstractFurnaceBlock.LIT)
                && ((FurnaceBurnAccessor) entity).astronima$litTimeRemaining() > 0) {
            level.setBlockAndUpdate(pos, state.setValue(AbstractFurnaceBlock.LIT, true));
        }
        double consumed = room.removeGas(Gas.OXYGEN, ASTRONIMA$O2_PER_TICK);
        if (consumed > 0) {
            // Complete combustion of carbon: one CO2 per O2, exhausted hot.
            room.addGasAt(Gas.CARBON_DIOXIDE, consumed, 600.0);
        }
    }
}
