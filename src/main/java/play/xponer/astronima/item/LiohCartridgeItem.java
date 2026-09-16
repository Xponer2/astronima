package play.xponer.astronima.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import play.xponer.astronima.registry.ModItems;
import play.xponer.astronima.sim.suit.CartridgeRefit;
import play.xponer.astronima.sim.suit.SuitCondition;
import play.xponer.astronima.sim.suit.SuitSubsystem;

/**
 * Lithium hydroxide, which does three jobs and has to be all three at once.
 *
 * <p>It is the consumable that scrubs your helmet, the charge a room scrubber runs on,
 * and — because an empty scrubber bay is repaired by putting a cartridge in it — the
 * part that fixes the bay itself. That last role is why this is a
 * {@link SuitRepairItem} rather than a plain item: the repair bench only opens for
 * items that declare which subsystem they mend.
 *
 * <p>Unlike the dedicated repair parts it stays quiet when there is no suit to work
 * on. A sealant patch has no other purpose, so telling you to hold the suit is
 * helpful; a cartridge you are carrying for your own helmet should not scold you every
 * time you right-click.
 *
 * <p><strong>The bay repair is a one-time job; wearing the suit is not.</strong> Once
 * the bay works, {@link SuitRepairItem#openBench} correctly refuses to "repair" it
 * again — but this item still has its second, recurring job: fitting a fresh cartridge
 * into the worn slot every time the old one wears out. Falling through to
 * {@code openBench}'s refusal for that case left the second cartridge in every kit
 * dead on arrival, with no message explaining why (PLAN.md rule 56).
 */
public class LiohCartridgeItem extends SuitRepairItem {
    public LiohCartridgeItem(Properties properties) {
        super(properties, SuitSubsystem.SCRUBBER_BAY);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        InteractionResult bench = openBench(level, player, hand, false);
        if (bench != InteractionResult.PASS) {
            return bench;
        }
        return fitWorn(level, player, hand);
    }

    /**
     * The bay already works and nothing was held that needed repairing — so this
     * click's only remaining job is swapping the cartridge you are wearing for the
     * one in your hand, exactly the way you just fixed the bay with the first one.
     * The decision itself is {@link CartridgeRefit}; this is only the wiring.
     */
    private InteractionResult fitWorn(Level level, Player player, InteractionHand hand) {
        ItemStack suit = SuitLoadout.suit(player);
        boolean suitWorn = !suit.isEmpty();
        boolean bayWorking = suitWorn && SuitCondition.isWorking(EvaSuitItem.conditionOf(suit), subsystem());
        ItemStack fitted = SuitLoadout.cartridge(player);
        boolean fittedPresent = fitted.is(ModItems.LITHIUM_HYDROXIDE_CARTRIDGE.get());
        boolean fittedFresh = fittedPresent && fitted.getDamageValue() == 0;

        CartridgeRefit.Decision decision = CartridgeRefit.decide(suitWorn, bayWorking, fittedPresent, fittedFresh);
        return switch (decision) {
            case NOT_APPLICABLE -> InteractionResult.PASS;
            case ALREADY_FRESH -> {
                if (!level.isClientSide()) {
                    player.sendSystemMessage(Component.translatable("astronima.suit.cartridge_fresh")
                            .withStyle(ChatFormatting.GRAY));
                }
                yield InteractionResult.FAIL;
            }
            case SWAP -> {
                if (!level.isClientSide()) {
                    SuitLoadout.fit(player, SuitLoadout.SLOT_CARTRIDGE,
                            new ItemStack(ModItems.LITHIUM_HYDROXIDE_CARTRIDGE.get()));
                    player.getItemInHand(hand).shrink(1);
                    if (!fitted.isEmpty() && !player.getInventory().add(fitted)) {
                        player.drop(fitted, false);
                    }
                    level.playSound(null, player.blockPosition(), SoundEvents.BUNDLE_INSERT,
                            SoundSource.PLAYERS, 0.6f, 1.2f);
                    player.sendSystemMessage(Component.translatable("astronima.suit.cartridge_fitted")
                            .withStyle(ChatFormatting.GREEN));
                }
                yield InteractionResult.SUCCESS;
            }
        };
    }
}
