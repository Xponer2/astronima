package play.xponer.astronima.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import play.xponer.astronima.astra.AstraFieldStorage;
import play.xponer.astronima.registry.ModDataComponents;
import play.xponer.astronima.registry.ModItems;
import play.xponer.astronima.sim.astra.AstraCharge;
import play.xponer.astronima.sim.astra.AstraFieldGeometry;
import play.xponer.astronima.sim.astra.AstraPrecipitation;
import play.xponer.astronima.sim.astra.AstraVessel;
import play.xponer.astronima.sim.sky.SkyEventOverride;
import play.xponer.astronima.sim.world.AsteroidBody;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * Portable astra collector — design/astra-extraction-loop.md §3, act 1's own instrument
 * (astra-systems.md §4.2). A plain right-click sweeps the real field at the player's own position,
 * exactly the same sweep the debug door {@code /astronima magic astra sweep} already performs;
 * <strong>sneaking</strong> and right-clicking attempts precipitation instead — design/
 * astra-precipitation.md §1/§2, the tier's first material, made real.
 *
 * <p>The held charge ({@link ModDataComponents#ASTRA_HELD}, an {@link AstraCharge}) leaks for
 * real (design/astra-core.md §5, {@link play.xponer.astronima.sim.astra.AstraVessel}) — computed
 * lazily on every touch, never ticked, the same shape {@code AstraFieldStorage} already uses for
 * ground. This is design/astra-extraction-loop.md §3.3's own named simplification, upgraded: still
 * a small buffer standing in for a real vessel, but the leak itself is no longer a placeholder.
 */
public class AstraCollectorItem extends Item {

    /** Must match {@code AtmosphereEvents.FLARE_SEED} exactly — see that field's own doc. */
    private static final long FLARE_SEED = 20260810L;

    public AstraCollectorItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level instanceof ServerLevel serverLevel) {
            ItemStack stack = player.getItemInHand(hand);
            if (player.isShiftKeyDown()) {
                precipitate(serverLevel, player, stack);
            } else {
                sweep(serverLevel, player, stack);
            }
        }
        return InteractionResult.SUCCESS;
    }

    /** What a held charge really amounts to right now — {@link AstraVessel#afterLeak}, entered
     *  here rather than through a convenience method on {@link AstraCharge} itself, so that model
     *  keeps a real consumer outside {@code sim/astra} (rule 13). */
    private static double currentHeldAmount(AstraCharge charge, long nowGameTime) {
        double elapsedSeconds = Math.max(0.0, nowGameTime - charge.lastUpdateGameTime()) / 20.0;
        return AstraVessel.afterLeak(charge.amount(), elapsedSeconds);
    }

    private static double baselineHere(ServerLevel level, Player player) {
        double r = Math.hypot(player.getX(), player.getZ());
        double shellCoordinate = AsteroidBody.shellCoordinate(r, player.getY());
        double flareIntensity = SkyEventOverride.resolveFlareIntensity(level.getGameTime(), FLARE_SEED);
        return AstraFieldGeometry.baselineDensity(shellCoordinate, flareIntensity);
    }

    private static void sweep(ServerLevel level, Player player, ItemStack stack) {
        double baseline = baselineHere(level, player);
        double collected = AstraFieldStorage.get(level).sweep(
                player.getBlockX(), player.getBlockY(), player.getBlockZ(), baseline, level.getGameTime());

        if (collected <= 0.0) {
            player.sendSystemMessage(Component.translatable("astronima.astra_collector.nothing")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        AstraCharge before = stack.getOrDefault(ModDataComponents.ASTRA_HELD.get(), AstraCharge.EMPTY);
        double leaked = currentHeldAmount(before, level.getGameTime());
        AstraCharge after = new AstraCharge((float) (leaked + collected), level.getGameTime());
        stack.set(ModDataComponents.ASTRA_HELD.get(), after);

        player.sendSystemMessage(Component.translatable("astronima.astra_collector.collected",
                        String.format(Locale.ROOT, "%.3f", collected))
                .withStyle(ChatFormatting.AQUA));
    }

    private static void precipitate(ServerLevel level, Player player, ItemStack stack) {
        AstraCharge before = stack.getOrDefault(ModDataComponents.ASTRA_HELD.get(), AstraCharge.EMPTY);
        double held = currentHeldAmount(before, level.getGameTime());

        if (!AstraPrecipitation.canPrecipitate(held)) {
            player.sendSystemMessage(Component.translatable("astronima.astra_collector.not_enough",
                            String.format(Locale.ROOT, "%.3f", held))
                    .withStyle(ChatFormatting.RED));
            return;
        }

        int yielded = AstraPrecipitation.itemsYielded(held);
        double remaining = AstraPrecipitation.remainingAfterPrecipitation(held);
        stack.set(ModDataComponents.ASTRA_HELD.get(), new AstraCharge((float) remaining, level.getGameTime()));

        ItemStack grains = new ItemStack(ModItems.ASTERIUM_GRAINS.get(), yielded);
        if (!player.getInventory().add(grains)) {
            player.drop(grains, false);
        }

        player.sendSystemMessage(Component.translatable("astronima.astra_collector.precipitated",
                        yielded)
                .withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                 Consumer<Component> lines, TooltipFlag flag) {
        AstraCharge charge = stack.getOrDefault(ModDataComponents.ASTRA_HELD.get(), AstraCharge.EMPTY);
        lines.accept(Component.translatable("astronima.astra_collector.held",
                        String.format(Locale.ROOT, "%.3f", (double) charge.amount()))
                .withStyle(charge.amount() > 0.0F ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY));
    }
}
