package play.xponer.astronima.compat.jade;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import play.xponer.astronima.Astronima;
import play.xponer.astronima.block.AlarmBlock;
import play.xponer.astronima.block.GasPumpBlock;
import play.xponer.astronima.block.GasTankBlock;
import play.xponer.astronima.sim.pipe.PressureVessel;
import play.xponer.astronima.block.OxygenCandleBlock;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * What a machine can honestly say about itself.
 *
 * <p>Everything reported here is something the physical object would actually show:
 * a scrubber has a charge gauge, a candle is visibly part-burned, an alarm is either
 * sounding or it is not. None of it requires an instrument the player has not built,
 * which is what separates it from {@link AtmosphereProvider}.
 *
 * <p>Consumable levels live in block entities that never sync to the client, so they
 * arrive through {@link MachineDataProvider} rather than being read locally — reading
 * them client-side would be correct in single-player and report zero on a real server,
 * which is the worst kind of bug. That provider is a separate class because Jade
 * forbids one class being both since 1.21.6.
 */
public enum MachineStateProvider implements IBlockComponentProvider {
    INSTANCE;

    private static final Identifier UID =
            Identifier.fromNamespaceAndPath(Astronima.MODID, "machine_state");

    @Override
    public Identifier getUid() {
        return UID;
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();

        if (accessor.getBlock() instanceof play.xponer.astronima.block.ScrubberBlock) {
            float charge = data.getFloatOr(MachineDataProvider.CHARGE, -1f);
            if (charge >= 0) {
                tooltip.add(Component.translatable("astronima.jade.cartridge",
                        percent(charge)).withStyle(colourFor(charge)));
            }
        }

        if (accessor.getBlock() instanceof OxygenCandleBlock) {
            boolean lit = accessor.getBlockState().getValue(BlockStateProperties.LIT);
            float burn = data.getFloatOr(MachineDataProvider.BURN, -1f);
            if (accessor.getBlockState().getValue(OxygenCandleBlock.SPENT)) {
                tooltip.add(Component.translatable("astronima.jade.candle_spent")
                        .withStyle(ChatFormatting.DARK_GRAY));
            } else if (lit && burn >= 0) {
                tooltip.add(Component.translatable("astronima.jade.candle_burning",
                        percent(burn)).withStyle(colourFor(burn)));
            } else {
                tooltip.add(Component.translatable("astronima.jade.candle_unlit")
                        .withStyle(ChatFormatting.GRAY));
            }
        }

        if (accessor.getBlock() instanceof GasPumpBlock) {
            appendPump(tooltip, accessor, data);
        }

        if (accessor.getBlock() instanceof GasTankBlock) {
            double kPa = data.getDoubleOr(MachineDataProvider.TANK_PRESSURE, -1);
            if (kPa >= 0) {
                tooltip.add(Component.translatable("astronima.jade.tank",
                        String.format("%.0f", kPa),
                        String.format("%.0f", PressureVessel.WORKING_PRESSURE_KPA))
                        .withStyle(PressureVessel.classify(kPa)
                                == PressureVessel.Condition.NOMINAL
                                ? ChatFormatting.GRAY : ChatFormatting.RED));
            }
        }

        if (accessor.getBlock() instanceof AlarmBlock
                && accessor.getBlockState().getValue(BlockStateProperties.LIT)) {
            // The alarm is latched: it is telling you something is wrong *now*.
            tooltip.add(Component.translatable("astronima.jade.alarm_latched")
                    .withStyle(ChatFormatting.RED));
        }

    }

    /**
     * The pump, saying what is actually wrong with it.
     *
     * <p>This is the block a player is most likely to be staring at while not
     * understanding why nothing is happening, so it gets the most direct language in
     * the mod and names the specific face at fault. "No route" alone would send someone
     * to check both ends.
     */
    private static void appendPump(ITooltip tooltip, BlockAccessor accessor,
                                   CompoundTag data) {
        int inlet = data.getIntOr(MachineDataProvider.PUMP_INLET, -1);
        int outlet = data.getIntOr(MachineDataProvider.PUMP_OUTLET, -1);
        if (inlet < 0 || outlet < 0) {
            return;
        }
        if (inlet == 0) {
            tooltip.add(Component.translatable("astronima.jade.pump_no_inlet")
                    .withStyle(ChatFormatting.RED));
        } else if (inlet > 1) {
            tooltip.add(Component.translatable("astronima.jade.pump_many_inlet", inlet)
                    .withStyle(ChatFormatting.RED));
        }
        if (outlet == 0) {
            tooltip.add(Component.translatable("astronima.jade.pump_no_outlet")
                    .withStyle(ChatFormatting.RED));
        } else if (outlet > 1) {
            tooltip.add(Component.translatable("astronima.jade.pump_many_outlet", outlet)
                    .withStyle(ChatFormatting.RED));
        }
        // The real verdict, not inlet == 1 && outlet == 1 reconstructed here: a room ported
        // to itself puts one volume on each face and it is the same volume, which the two
        // counts alone cannot see (PLAN.md, "duplicated pump-connectivity predicates").
        if (data.getBooleanOr(MachineDataProvider.PUMP_ROUTED, false)) {
            boolean running = accessor.getBlockState().getValue(GasPumpBlock.RUNNING);
            tooltip.add(Component.translatable(running
                            ? "astronima.jade.pump_running"
                            : "astronima.jade.pump_stalled")
                    .withStyle(running ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
        } else if (inlet == 1 && outlet == 1) {
            // Both faces see exactly one volume and yet the pump is not routed - the one
            // case count alone cannot explain: it is the same volume on both sides.
            tooltip.add(Component.translatable("astronima.jade.pump_self_routed")
                    .withStyle(ChatFormatting.RED));
        }
    }

    private static Component percent(float fraction) {
        return Component.literal(Math.round(fraction * 100) + "%");
    }

    /** Same thresholds as the suit panel, so one colour means one thing everywhere. */
    private static ChatFormatting colourFor(float fraction) {
        if (fraction <= 0.1f) {
            return ChatFormatting.RED;
        }
        return fraction <= 0.25f ? ChatFormatting.GOLD : ChatFormatting.GREEN;
    }
}
