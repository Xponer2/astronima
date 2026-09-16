package play.xponer.astronima.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import play.xponer.astronima.atmosphere.RadiationSources;
import play.xponer.astronima.registry.ModAttachments;
import play.xponer.astronima.sim.rad.RadiationDose;

import java.util.Locale;

/**
 * Handheld dosimeter: right-click reports the current real gamma dose rate and the body's own
 * accumulated dose (design/radiation.md §4) — mirrors {@link GasAnalyzerItem}'s chat report.
 * Gamma only; there is no neutron channel because nothing in this mod produces a neutron yet
 * (design/radiation.md §0).
 *
 * <p><strong>No live-while-held HUD yet</strong> — the same honest gap {@link AstraFieldMeterItem}
 * already carries for the same reason: a right-click report satisfies rule 7 (the player can
 * check exposure before it becomes dangerous), and a continuously-updating overlay is real
 * additional client scope (a new payload, a render layer, an icon) that this phase's own hazard
 * does not depend on to be instrumentable. Named here rather than silently deferred.
 */
public class GeigerCounterItem extends Item {

    public GeigerCounterItem(Properties properties) {
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
        BlockPos eyePos = BlockPos.containing(player.getEyePosition());
        double rateSvPerH = RadiationSources.totalGammaSvPerH(level, eyePos, player.getEyePosition());
        double bodyDoseSv = player.getData(ModAttachments.RADIATION_DOSE);
        RadiationDose.Severity severity = RadiationDose.Severity.classify(bodyDoseSv);

        player.sendSystemMessage(Component.translatable("astronima.geiger.header")
                .withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.literal(
                        String.format(Locale.ROOT, "  rate: %.4f Sv/h", rateSvPerH))
                .withStyle(rateColor(rateSvPerH)));
        player.sendSystemMessage(Component.literal(
                        String.format(Locale.ROOT, "  body dose: %.4f Sv (%s)", bodyDoseSv,
                                severity.name().toLowerCase(Locale.ROOT)))
                .withStyle(severityColor(severity)));
    }

    private static ChatFormatting rateColor(double svPerH) {
        if (svPerH >= 0.5) {
            return ChatFormatting.RED;
        }
        if (svPerH >= 0.05) {
            return ChatFormatting.YELLOW;
        }
        return ChatFormatting.GREEN;
    }

    private static ChatFormatting severityColor(RadiationDose.Severity severity) {
        return switch (severity) {
            case NONE -> ChatFormatting.GREEN;
            case MILD -> ChatFormatting.YELLOW;
            case SEVERE, CRITICAL -> ChatFormatting.RED;
        };
    }
}
