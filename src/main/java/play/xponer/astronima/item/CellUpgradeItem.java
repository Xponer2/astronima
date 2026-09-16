package play.xponer.astronima.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import play.xponer.astronima.registry.ModDataComponents;
import play.xponer.astronima.registry.ModItems;
import play.xponer.astronima.sim.storage.DataLedger;

/**
 * A real compression component: applied directly to a loaded {@code data_cell}, it advances the
 * cell's own {@code itemsPerSlot} exactly one step up {@link DataLedger#ITEMS_PER_SLOT_TIERS},
 * preserving every logged entry by construction — see {@code design/data-cells.md} §15.
 *
 * <p>Same interaction shape as {@code SuitRepairItem}: hold the target in one hand, use the
 * component in the other, no crafting grid — a shapeless recipe cannot preserve one specific
 * input stack's own data component (the ledger), and this item is applied to exactly one real
 * cell, not a fresh one. Simplified from the suit's own multi-step repair bench to a single
 * direct application, since an upgrade has no procedure to walk through.
 */
public class CellUpgradeItem extends Item {
    public CellUpgradeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        InteractionHand otherHand = hand == InteractionHand.MAIN_HAND
                ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack cell = player.getItemInHand(otherHand);
        if (!cell.is(ModItems.DATA_CELL.get())) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        DataLedger ledger = DataCellItem.ledgerOf(cell);
        int tierIndex = tierIndexOf(ledger.itemsPerSlot());
        if (tierIndex < 0 || tierIndex >= DataLedger.ITEMS_PER_SLOT_TIERS.length - 1) {
            player.sendSystemMessage(Component.translatable("astronima.cell_upgrade.maxed")
                    .withStyle(ChatFormatting.GRAY));
            return InteractionResult.FAIL;
        }
        int nextTier = DataLedger.ITEMS_PER_SLOT_TIERS[tierIndex + 1];
        cell.set(ModDataComponents.DATA_CELL_LEDGER.get(), ledger.upgraded(nextTier));
        player.getItemInHand(hand).shrink(1);
        player.sendSystemMessage(Component.translatable("astronima.cell_upgrade.upgraded", nextTier)
                .withStyle(ChatFormatting.GRAY));
        return InteractionResult.SUCCESS;
    }

    private static int tierIndexOf(int itemsPerSlot) {
        int[] tiers = DataLedger.ITEMS_PER_SLOT_TIERS;
        for (int i = 0; i < tiers.length; i++) {
            if (tiers[i] == itemsPerSlot) {
                return i;
            }
        }
        return -1;
    }
}
