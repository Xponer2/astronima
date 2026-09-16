package play.xponer.astronima.item;

import java.util.Locale;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import play.xponer.astronima.atmosphere.CoherenceReading;
import play.xponer.astronima.sim.magic.Coherence;

/**
 * Handheld coherence survey meter — design/astra-incognita.md §8.2. Carried into the rock and
 * walked; the live readout is {@code client.CoherenceHud} while held, and a right-click prints
 * the same reading to chat as a fallback that needs no HUD to read.
 */
public class CoherenceMeterItem extends Item {
    public CoherenceMeterItem(Properties properties) {
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
        Map<Coherence.Term, Double> breakdown = CoherenceReading.at(level, eyePos, true);
        double totalSeconds = Coherence.totalSeconds(breakdown);

        player.sendSystemMessage(Component.translatable("astronima.coherence_meter.header")
                .withStyle(ChatFormatting.AQUA));
        player.sendSystemMessage(Component.translatable("astronima.coherence_meter.total",
                        Coherence.formatSeconds(totalSeconds))
                .withStyle(ChatFormatting.WHITE));
        for (Coherence.Term term : Coherence.worstFirst(breakdown)) {
            player.sendSystemMessage(line(term, breakdown.get(term)));
        }
    }

    private static MutableComponent line(Coherence.Term term, double seconds) {
        return Component.literal(String.format(Locale.ROOT, "  %-9s %s",
                        term.name().toLowerCase(Locale.ROOT), Coherence.formatSeconds(seconds)))
                .withStyle(ChatFormatting.GRAY);
    }
}
