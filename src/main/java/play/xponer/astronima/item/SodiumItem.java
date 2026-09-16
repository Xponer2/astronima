package play.xponer.astronima.item;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import play.xponer.astronima.atmosphere.Atmosphere;
import play.xponer.astronima.sim.Gas;
import play.xponer.astronima.sim.RoomState;

/**
 * Real, reactive sodium metal off the Downs cell — see {@code design/halogens.md} §1.3 and §4.
 *
 * <p>The mod's most reactive metal gets a real interaction rather than a background hazard: held
 * against water, it runs the actual reaction (2 Na + 2 H2O -> 2 NaOH + H2, exothermic) instead of
 * sitting inert. No new item for the caustic soda byproduct — it is named in the chat line and
 * nowhere else, the same "not every real product needs an item" restraint
 * {@code design/electrolysis.md} §7 already takes for oxide residue.
 *
 * <p>Consumed by an interaction {@code CraftingTree} cannot express, the same shape
 * {@code NoDeadEndsTest.TERMINAL} already keeps entries for — real, tested functionality, not a
 * dead end dressed up as one.
 */
public class SodiumItem extends Item {

    /** 1 mol H2 per 2 mol Na — the real stoichiometry. */
    public static final double H2_PER_NA = 0.5;

    /** Moles of sodium one item stands for, the same {@code MOL_PER_ITEM} convention
     * {@code DownsCellBlockEntity} already uses for its own output. */
    public static final double MOL_PER_ITEM = 1.0;

    public SodiumItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || context.getLevel().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!level.getBlockState(pos).is(Blocks.WATER) && !level.getFluidState(pos).is(
                net.minecraft.tags.FluidTags.WATER)) {
            return InteractionResult.PASS;
        }

        ItemStack held = context.getItemInHand();
        held.shrink(1);

        if (level instanceof ServerLevel serverLevel) {
            double h2Mol = MOL_PER_ITEM * H2_PER_NA;
            Atmosphere.RoomReading reading = Atmosphere.get(serverLevel).readingNear(pos);
            if (reading != null) {
                RoomState room = reading.state();
                room.addGasAt(Gas.HYDROGEN, h2Mol, room.temperatureK());
            }
        }

        level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8f, 1.4f);
        player.sendSystemMessage(Component.literal(
                "The sodium reacts violently: 2 Na + 2 H2O -> 2 NaOH + H2 - hydrogen vents into"
                        + " the air around you."));
        return InteractionResult.SUCCESS;
    }
}
