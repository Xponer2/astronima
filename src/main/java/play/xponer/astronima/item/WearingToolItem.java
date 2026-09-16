package play.xponer.astronima.item;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import play.xponer.astronima.registry.ModBlocks;
import play.xponer.astronima.registry.ModDataComponents;
import play.xponer.astronima.sim.tool.ToolHead;
import play.xponer.astronima.sim.tool.ToolWear;

/**
 * A tool that goes blunt instead of shattering.
 *
 * <p>Vanilla durability is backwards in two ways: a tool at 1 durability cuts exactly
 * as well as a new one, and then it evaporates. Real tools lose their edge gradually,
 * and a blunt tool still works — which is also the only structural guarantee against a
 * player being stranded with no way back to metal.
 *
 * <p>So this never calls vanilla's damage path. The durability bar is repurposed to show
 * edge condition and coloured by band, and the damage value is kept strictly below the
 * maximum so the stack can never be destroyed.
 *
 * @see ToolWear for the wear rules and the reasoning behind them
 */
public class WearingToolItem extends Item {

    public WearingToolItem(Properties properties) {
        super(properties);
    }

    /** Reads the tool's state, falling back to a plain middling head for older stacks. */
    public static ToolHead headOf(ItemStack stack) {
        ToolHead head = stack.get(ModDataComponents.TOOL_STATE.get());
        return head != null ? head : ToolHead.forged(0.5, false);
    }

    public static void setHead(ItemStack stack, ToolHead head) {
        stack.set(ModDataComponents.TOOL_STATE.get(), head);
        syncBar(stack, head);
    }

    /**
     * Mirrors the edge onto the vanilla damage value so the bar draws itself.
     *
     * <p>Capped one point below the maximum: at maximum damage vanilla destroys the
     * stack, and this tool is never destroyed.
     */
    private static void syncBar(ItemStack stack, ToolHead head) {
        int max = stack.getMaxDamage();
        if (max <= 0) {
            return;
        }
        int damage = (int) Math.round((1.0 - head.edge()) * max);
        stack.setDamageValue(Math.min(damage, max - 1));
    }

    /**
     * Wears the edge by one block's work, in place of taking durability damage.
     *
     * <p>Deliberately does not call {@code super}, which is what stops the tool ever
     * breaking.
     */
    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos,
                             LivingEntity owner) {
        if (level.isClientSide() || state.getDestroySpeed(level, pos) == 0.0F) {
            return true;
        }
        ToolHead head = headOf(stack);
        // Harder rock wears a tool faster, which is Archard's load term with the only
        // measure of "how hard this was" the game actually has.
        double load = Math.clamp(state.getDestroySpeed(level, pos) / 3.0, 0.25, 4.0);
        setHead(stack, head.mine(load));
        return true;
    }

    /**
     * Grinding the edge back against packed tailings.
     *
     * <p>Tailings are the fine silicate powder left after magnetic separation — the
     * silicates the magnet did not want. That is genuinely what a whetstone is: bonded
     * fine abrasive. So the waste from making metal is what maintains the tools made
     * from it, which is a real relationship rather than a crafting convenience.
     *
     * <p>Each grind removes material, so a head has only {@link ToolHead#MAX_RESHARPENINGS}
     * of them. A spent head with no grinds left is refused rather than silently doing
     * nothing, and it still cuts at the floor speed either way.
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!level.getBlockState(pos).is(ModBlocks.PACKED_TAILINGS.get())) {
            return super.useOn(context);
        }
        ItemStack stack = context.getItemInHand();
        ToolHead head = headOf(stack);
        if (!head.canResharpen()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            setHead(stack, head.resharpen());
        }
        level.playSound(context.getPlayer(), pos, SoundEvents.GRINDSTONE_USE,
                SoundSource.BLOCKS, 0.7F, 1.0F);
        return InteractionResult.SUCCESS;
    }

    /**
     * Mining speed, scaled by how sharp the tool still is.
     *
     * <p>A blunt pick is slow rather than useless; the floor in {@link ToolWear} is what
     * guarantees it still cuts.
     */
    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        float base = super.getDestroySpeed(stack, state);
        return base * (float) headOf(stack).speedMultiplier();
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return headOf(stack).edge() < ToolWear.SHARP;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * (float) headOf(stack).edge());
    }

    /** Coloured by band, so a glance says sharp/dulled/blunt without reading a number. */
    @Override
    public int getBarColor(ItemStack stack) {
        return switch (headOf(stack).condition()) {
            case SHARP -> 0x4CD964;
            case DULLED -> 0xC8D94C;
            case BLUNT -> 0xD98E4C;
            case SPENT -> 0xD9534C;
        };
    }
}
