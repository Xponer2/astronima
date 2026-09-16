package play.xponer.astronima.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import play.xponer.astronima.astra.CrustColumn;
import play.xponer.astronima.sim.astra.CrustSounding;
import play.xponer.astronima.sim.ore.Mineral;
import play.xponer.astronima.sim.ore.OreBody;
import play.xponer.astronima.sim.world.AsteroidBody;

import java.util.Locale;

/**
 * Handheld crust sounder — design/astra-crust-sounding.md §3, the tier's real aspect system made
 * a real instrument. A right-click reads the real column beneath the player
 * ({@code astra.CrustColumn}, shared with the debug command per rule 46) and reports the real
 * signature ({@code sim.astra.CrustSounding}), mirroring {@code AstraFieldMeterItem}'s exact
 * chat-fallback shape.
 */
public class AstraSounderItem extends Item {

    public AstraSounderItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level instanceof ServerLevel serverLevel) {
            report(serverLevel, player);
        }
        return InteractionResult.SUCCESS;
    }

    private static void report(ServerLevel level, Player player) {
        OreBody column = CrustColumn.scanBelow(level, player.blockPosition());
        double r = Math.hypot(player.getX(), player.getZ());
        double shellCoordinate = AsteroidBody.shellCoordinate(r, player.getY());
        CrustSounding.Signature signature = CrustSounding.signatureOf(column, shellCoordinate);

        player.sendSystemMessage(Component.translatable("astronima.astra_sounder.header")
                .withStyle(ChatFormatting.AQUA));
        boolean anyBand = false;
        for (Mineral mineral : CrustSounding.namedBands()) {
            double fraction = signature.fractionOf(mineral);
            if (fraction > 0.01) {
                player.sendSystemMessage(Component.literal(
                                String.format(Locale.ROOT, "  %s: %.2f", mineral.displayName(), fraction))
                        .withStyle(ChatFormatting.WHITE));
                anyBand = true;
            }
        }
        if (signature.unaccountedFraction() > 0.01) {
            player.sendSystemMessage(Component.translatable("astronima.astra_sounder.unaccounted",
                            String.format(Locale.ROOT, "%.2f", signature.unaccountedFraction()))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            anyBand = true;
        }
        if (!anyBand) {
            player.sendSystemMessage(Component.translatable("astronima.astra_sounder.nothing")
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
