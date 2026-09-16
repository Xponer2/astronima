package play.xponer.astronima.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import play.xponer.astronima.atmosphere.Atmosphere;
import play.xponer.astronima.sim.Acoustics;
import play.xponer.astronima.sim.Co2Status;
import play.xponer.astronima.sim.Gas;
import play.xponer.astronima.sim.Humidity;
import play.xponer.astronima.sim.burn.Flammability;

import java.util.Locale;
import play.xponer.astronima.sim.O2Status;
import play.xponer.astronima.sim.RoomState;

/**
 * Handheld gas analyzer: reports the composition, pressure, temperature, and sealing
 * of the space the player is standing in. The single most important instrument in the
 * mod — every atmosphere failure should be diagnosable with it.
 */
public class GasAnalyzerItem extends Item {
    /** Partial pressures below this are trace and omitted from the readout. */
    private static final double TRACE_KPA = 0.005;

    /** Debug-cloud limits: enough to see a base room, bounded for packet cost. */
    private static final int MAX_DEBUG_PARTICLES = 400;
    private static final double DEBUG_RADIUS_SQ = 12 * 12;

    public GasAnalyzerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level instanceof ServerLevel serverLevel) {
            if (player.isShiftKeyDown()) {
                debugReport(serverLevel, player);
            } else {
                report(serverLevel, player);
            }
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Sneak-use: diagnostic mode. Prints the room's bookkeeping and renders its cells
     * as a particle cloud (leak blocks flagged in flame) — if your "sealed" room's
     * cloud extends through a wall, you are looking at the hole.
     */
    private static void debugReport(ServerLevel level, Player player) {
        BlockPos eyePos = BlockPos.containing(player.getEyePosition());
        Atmosphere.DebugReading debug = Atmosphere.get(level).debugReadingAt(eyePos);
        player.sendSystemMessage(Component.translatable("astronima.analyzer.debug.header")
                .withStyle(ChatFormatting.GOLD));
        if (debug == null) {
            player.sendSystemMessage(Component.translatable("astronima.analyzer.debug.no_room")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        player.sendSystemMessage(Component.translatable("astronima.analyzer.debug.info",
                debug.state().id(), debug.state().volumeBlocks(),
                debug.sealed(), debug.overCap(), debug.leakCount(), debug.ticksSinceScan()));
        player.sendSystemMessage(Component.translatable("astronima.analyzer.debug.moles",
                String.format("%.2f", debug.state().gases().totalMoles()),
                String.format("%.1f", debug.state().pressureKPa()),
                String.format("%.1f", debug.state().temperatureK() - 273.15)));

        // Cloud of the room's extent near the player; leaks burn orange.
        int shown = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (var it = debug.cells().iterator(); it.hasNext() && shown < MAX_DEBUG_PARTICLES; ) {
            cursor.set(it.nextLong());
            if (cursor.distSqr(eyePos) <= DEBUG_RADIUS_SQ) {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                        cursor.getX() + 0.5, cursor.getY() + 0.5, cursor.getZ() + 0.5,
                        1, 0, 0, 0, 0);
                shown++;
            }
        }
        for (var it = debug.leaks().iterator(); it.hasNext(); ) {
            cursor.set(it.nextLong());
            level.sendParticles(ParticleTypes.FLAME,
                    cursor.getX() + 0.5, cursor.getY() + 0.5, cursor.getZ() + 0.5,
                    3, 0.2, 0.2, 0.2, 0);
        }
    }

    private static void report(ServerLevel level, Player player) {
        BlockPos eyePos = BlockPos.containing(player.getEyePosition());
        Atmosphere atmosphere = Atmosphere.get(level);
        Atmosphere.RoomReading reading = atmosphere.readingNear(eyePos);

        player.sendSystemMessage(Component.translatable("astronima.analyzer.header")
                .withStyle(ChatFormatting.GOLD));

        if (reading == null || (reading.openToSpace() && atmosphere.outsideIsVacuum())) {
            player.sendSystemMessage(Component.translatable("astronima.analyzer.vacuum")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        if (reading.openToSpace()) {
            player.sendSystemMessage(Component.translatable("astronima.analyzer.open_to_outside")
                    .withStyle(ChatFormatting.YELLOW));
            return;
        }

        // Enclosed volumes always report their contents — including tunnel networks
        // too large to pressurize, where breached pocket gas collects.
        RoomState room = reading.state();
        double tempC = room.temperatureK() - 273.15;
        player.sendSystemMessage(Component.translatable(
                reading.unsealableEnclosure() ? "astronima.analyzer.unsealable" : "astronima.analyzer.room",
                room.volumeBlocks(),
                String.format("%.1f", room.pressureKPa()),
                String.format("%.1f", tempC)));

        // Why the mixture will or won't burn — the answer to "I found methane and
        // my torch did nothing".
        Flammability.IgnitionStatus ignition = Flammability.assess(room);
        if (ignition != Flammability.IgnitionStatus.NO_FUEL) {
            boolean explosive = ignition == Flammability.IgnitionStatus.EXPLOSIVE;
            player.sendSystemMessage(Component.translatable(
                            "astronima.analyzer.ignition." + ignition.name().toLowerCase(Locale.ROOT))
                    .withStyle(explosive ? ChatFormatting.RED : ChatFormatting.GRAY));
        }

        int relativeHumidity = (int) Math.round(100 * Humidity.relativeHumidity(room));
        player.sendSystemMessage(Component.translatable("astronima.analyzer.humidity", relativeHumidity)
                .withStyle(relativeHumidity > 70 ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
        long noiseDb = Math.round(atmosphere.noiseDbAt(eyePos));
        player.sendSystemMessage(Component.translatable("astronima.analyzer.noise", noiseDb)
                .withStyle(Acoustics.blocksRest(noiseDb) ? ChatFormatting.YELLOW : ChatFormatting.GRAY));

        double ppO2 = room.partialPressureKPa(Gas.OXYGEN);
        double ppCO2 = room.partialPressureKPa(Gas.CARBON_DIOXIDE);
        player.sendSystemMessage(line(Gas.OXYGEN, ppO2, o2Color(O2Status.classify(ppO2))));
        player.sendSystemMessage(line(Gas.CARBON_DIOXIDE, ppCO2, co2Color(Co2Status.classify(ppCO2))));
        for (Gas gas : Gas.values()) {
            if (gas == Gas.OXYGEN || gas == Gas.CARBON_DIOXIDE) {
                continue;
            }
            double pp = room.partialPressureKPa(gas);
            if (pp >= TRACE_KPA) {
                player.sendSystemMessage(line(gas, pp, ChatFormatting.GRAY));
            }
        }
    }

    private static MutableComponent line(Gas gas, double kPa, ChatFormatting color) {
        return Component.literal(String.format("  %-4s %7.2f kPa", gas.symbol(), kPa)).withStyle(color);
    }

    private static ChatFormatting o2Color(O2Status status) {
        return switch (status) {
            case NORMAL -> ChatFormatting.GREEN;
            case MILD_HYPOXIA, OXYGEN_TOXICITY -> ChatFormatting.YELLOW;
            case SEVERE_HYPOXIA, SUFFOCATING -> ChatFormatting.RED;
        };
    }

    private static ChatFormatting co2Color(Co2Status status) {
        return switch (status) {
            case NORMAL -> ChatFormatting.GREEN;
            case ELEVATED -> ChatFormatting.YELLOW;
            case HIGH, TOXIC -> ChatFormatting.RED;
        };
    }
}
