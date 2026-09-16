package play.xponer.astronima.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import play.xponer.astronima.astra.AstraFieldStorage;
import play.xponer.astronima.registry.ModAttachments;
import play.xponer.astronima.sim.astra.AstraFieldGeometry;
import play.xponer.astronima.sim.magic.AstraEvidence;
import play.xponer.astronima.sim.magic.ResearchState;
import play.xponer.astronima.sim.sky.SkyEventOverride;
import play.xponer.astronima.sim.world.AsteroidBody;

import java.util.Locale;

/**
 * Handheld astra field meter — design/astra-extraction-loop.md §2, the tier's first real reading
 * instrument (layer I, before extraction per astra-systems.md §2's own layer ordering). A
 * right-click reports the real, depletion-aware density at the player's own position, mirroring
 * {@code CoherenceMeterItem}'s exact chat-fallback shape.
 *
 * <p><strong>Simplified, named per rule 8:</strong> no live HUD yet — {@code CoherenceMeterItem}
 * itself treats its own chat report as a real, first-class reading ("a right-click prints the same
 * reading to chat as a fallback that needs no HUD to read"), and the ambient glow
 * ({@code client.render.AstraGlowFx}) already gives a passive, wordless signal; a bar-and-needle
 * panel is real future work, not required for this instrument to be honest and useful today.
 */
public class AstraFieldMeterItem extends Item {

    /** Must match {@code AtmosphereEvents.FLARE_SEED} / {@code AsteroidSkyRenderer.FLARE_SEED}
     *  exactly — one real flare schedule read from several places. */
    private static final long FLARE_SEED = 20260810L;

    public AstraFieldMeterItem(Properties properties) {
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
        double r = Math.hypot(player.getX(), player.getZ());
        double shellCoordinate = AsteroidBody.shellCoordinate(r, player.getY());
        double flareIntensity = SkyEventOverride.resolveFlareIntensity(level.getGameTime(), FLARE_SEED);
        double baseline = AstraFieldGeometry.baselineDensity(shellCoordinate, flareIntensity);
        double real = AstraFieldStorage.get(level).densityAt(
                player.getBlockX(), player.getBlockY(), player.getBlockZ(), baseline, level.getGameTime());

        player.sendSystemMessage(Component.translatable("astronima.astra_field_meter.header")
                .withStyle(ChatFormatting.AQUA));
        player.sendSystemMessage(Component.literal(
                        String.format(Locale.ROOT, "  %.3f", real))
                .withStyle(real < baseline * 0.99 ? ChatFormatting.RED : ChatFormatting.WHITE));

        identify(level, player, shellCoordinate);
    }

    /**
     * The claim {@code astra_gradient}'s own stage 1 (design/astra-atlas-scope.md §3's
     * "Understanding" realm): a real reading taken at the gradient's poor end or its rich end
     * identifies that end, exactly once, through the same {@code ResearchState.withIdentified}
     * the sky's decode already uses — a reading taken in between (ordinary working depth) proves
     * nothing about the gradient's shape and identifies neither.
     */
    private static void identify(ServerLevel level, Player player, double shellCoordinate) {
        String token;
        if (shellCoordinate >= AstraEvidence.SHALLOW_THRESHOLD) {
            token = AstraEvidence.SHALLOW_READING;
        } else if (shellCoordinate < AstraEvidence.DEEP_THRESHOLD) {
            token = AstraEvidence.DEEP_READING;
        } else {
            return;
        }
        ResearchState before = player.getData(ModAttachments.RESEARCH.get());
        if (before.identified(token)) {
            return;
        }
        player.setData(ModAttachments.RESEARCH.get(), before.withIdentified(token));
        player.sendSystemMessage(Component.translatable("astronima.astra_field_meter.identified",
                        token.replace('_', ' '))
                .withStyle(ChatFormatting.GREEN));
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP,
                SoundSource.PLAYERS, 0.6f, 1.6f);
    }
}
