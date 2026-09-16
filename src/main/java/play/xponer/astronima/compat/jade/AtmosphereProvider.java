package play.xponer.astronima.compat.jade;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import play.xponer.astronima.Astronima;
import play.xponer.astronima.atmosphere.Atmosphere;
import play.xponer.astronima.registry.ModItems;
import play.xponer.astronima.sim.Gas;
import play.xponer.astronima.sim.O2Status;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * The air around whatever you are looking at — but only while you are carrying the
 * instrument that could actually measure it.
 *
 * <p>This gate is the whole point. Oxygen partial pressure is not something a person
 * perceives; the gas analyzer exists because reading an atmosphere takes hardware, and
 * building one is a real step in the progression. An overlay that showed ppO2 on every
 * block for free would make that step pointless and quietly undo rule 7.
 *
 * <p>With the gate, Jade becomes what it should be: a better <em>display</em> for a
 * tool you already earned. You still have to build the analyzer, and you still have to
 * be holding it — you simply no longer have to stop and read a separate panel to check
 * the room you are standing in.
 */
public enum AtmosphereProvider implements IBlockComponentProvider {
    INSTANCE;

    private static final Identifier UID =
            Identifier.fromNamespaceAndPath(Astronima.MODID, "atmosphere");

    @Override
    public Identifier getUid() {
        return UID;
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (!holdingAnalyzer(accessor)) {
            return;
        }
        // The atmosphere lives on the server; on a dedicated server there is nothing
        // to read here, and inventing a number would be worse than saying nothing.
        if (!(accessor.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Atmosphere.RoomReading reading =
                Atmosphere.get(level).readingNear(accessor.getPosition().relative(accessor.getSide()));
        if (reading == null) {
            tooltip.add(Component.translatable("astronima.jade.vacuum")
                    .withStyle(ChatFormatting.DARK_RED));
            return;
        }

        double ppO2 = reading.state().partialPressureKPa(Gas.OXYGEN);
        tooltip.add(Component.translatable("astronima.jade.ppo2",
                        Component.literal(String.format("%.1f", ppO2)))
                .withStyle(colourFor(O2Status.classify(ppO2))));
        tooltip.add(Component.translatable("astronima.jade.pressure",
                        Component.literal(String.format("%.0f", reading.state().pressureKPa())))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("astronima.jade.temperature",
                        Component.literal(String.format("%.0f",
                                reading.state().temperatureK() - 273.15)))
                .withStyle(ChatFormatting.GRAY));
    }

    private static boolean holdingAnalyzer(BlockAccessor accessor) {
        if (accessor.getPlayer() == null) {
            return false;
        }
        for (InteractionHand hand : InteractionHand.values()) {
            if (accessor.getPlayer().getItemInHand(hand).is(ModItems.GAS_ANALYZER.get())) {
                return true;
            }
        }
        return false;
    }

    private static ChatFormatting colourFor(O2Status status) {
        return switch (status) {
            case NORMAL -> ChatFormatting.GREEN;
            case MILD_HYPOXIA, OXYGEN_TOXICITY -> ChatFormatting.GOLD;
            case SEVERE_HYPOXIA, SUFFOCATING -> ChatFormatting.RED;
        };
    }
}
