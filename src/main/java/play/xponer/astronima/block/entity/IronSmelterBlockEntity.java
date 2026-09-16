package play.xponer.astronima.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import play.xponer.astronima.menu.ProcessingMenu;
import play.xponer.astronima.registry.ModBlockEntities;
import play.xponer.astronima.registry.ModBlocks;
import play.xponer.astronima.registry.ModItems;
import play.xponer.astronima.sim.metal.Fluxing;

/**
 * Real fluxing and slagging, at last: hematite ore and a real MgO flux in, iron and a real
 * magnesium-silicate slag out. See {@code design/iron-smelter.md}.
 *
 * <p><strong>The first machine in this mod with two consumed feed slots at once.</strong> Every
 * sibling {@code ProcessingBlockEntity} leans on the shared base class's own hardcoded {@code
 * SLOT_FEED = 0} — "every automation rule hangs off it," its own doc says — which is true for one
 * feed slot and false the moment a second one exists. {@link #hasFeed}, {@link #canPlaceItem},
 * {@link #getSlotsForFace} and {@link #canPlaceItemThroughFace} are all overridden here rather
 * than inherited for exactly that reason.
 *
 * <p><strong>Flux is optional, not required.</strong> {@link #hasFeed()} only asks for ore —
 * {@code design/iron-smelter.md} §3's own "crude but real without it" simplification: real
 * bloomery iron predates deliberate fluxing, just dirtier. {@link #finishBatch()} reads whatever
 * flux happens to be present (zero or one item) rather than stalling without a full charge of it.
 */
public class IronSmelterBlockEntity extends ProcessingBlockEntity {
    public static final int SLOT_ORE = 0;
    public static final int SLOT_FLUX = 1;
    public static final int SLOT_IRON = 2;
    public static final int SLOT_SLAG = 3;

    /** Work for one charge — the same order as the retort's own 220 and the induction furnace's
     *  batch, a Tier 2 machine among Tier 2 machines. */
    public static final int BATCH_WORK = 240;

    /** Grams one hematite ore item stands for. */
    public static final double ORE_GRAMS_PER_ITEM = 1000.0;

    /** Grams one magnesium oxide item stands for — already more than one ore item's own real
     *  stoichiometric need (design/iron-smelter.md §2), so one flux item per one ore item is the
     *  natural ratio a player actually meets, with real headroom rather than a knife-edge. */
    public static final double FLUX_GRAMS_PER_ITEM = 100.0;

    /** Grams per iron ingot — chosen so even a crude, unfluxed batch clears one ingot (139.9 g at
     *  the real 0.2 floor), so the lesson reads as "flux gives you more," not "flux gives you
     *  anything at all." */
    public static final double IRON_GRAMS_PER_INGOT = 100.0;

    /** Grams per slag item — a real chunk you can hold and pack, the same convention every other
     *  bulk byproduct in this tier already uses. */
    public static final double SLAG_GRAMS_PER_ITEM = 50.0;

    public IronSmelterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.IRON_SMELTER.get(), pos, state, 4);
    }

    @Override
    public ProcessingMenu.Kind kind() {
        return ProcessingMenu.Kind.IRON_SMELTER;
    }

    @Override
    public int workRequired() {
        return BATCH_WORK;
    }

    @Override
    public boolean hasFeed() {
        return getItem(SLOT_ORE).is(ModBlocks.HEMATITE_ORE.get().asItem());
    }

    @Override
    public boolean hasRoomForProduct() {
        if (!hasFeed()) {
            return true;
        }
        Fluxing.Batch batch = projectedBatch();
        int ironItems = (int) Math.floor(batch.ironGrams() / IRON_GRAMS_PER_INGOT);
        int slagItems = (int) Math.floor(batch.slagGrams() / SLAG_GRAMS_PER_ITEM);
        ItemStack ironProjected = ironItems > 0 ? new ItemStack(Items.IRON_INGOT, ironItems) : ItemStack.EMPTY;
        ItemStack slagProjected = slagItems > 0 ? new ItemStack(ModItems.SLAG.get(), slagItems) : ItemStack.EMPTY;
        return hasRoom(SLOT_IRON, ironProjected) && hasRoom(SLOT_SLAG, slagProjected);
    }

    /** What finishing right now would actually produce, from whatever is in the slots. */
    private Fluxing.Batch projectedBatch() {
        double fluxGrams = getItem(SLOT_FLUX).is(ModItems.MAGNESIUM_OXIDE.get())
                ? FLUX_GRAMS_PER_ITEM : 0.0;
        return Fluxing.smelt(ORE_GRAMS_PER_ITEM, fluxGrams);
    }

    private double lastFluxRatio;

    /** How much of the real stoichiometric flux need the last batch actually got, 0..1 — the
     *  panel's own honest "how clean was that" reading. */
    public double lastFluxRatio() {
        return lastFluxRatio;
    }

    @Override
    protected void finishBatch() {
        ItemStack ore = getItem(SLOT_ORE);
        if (!ore.is(ModBlocks.HEMATITE_ORE.get().asItem())) {
            return;
        }
        ItemStack flux = getItem(SLOT_FLUX);
        boolean fluxed = flux.is(ModItems.MAGNESIUM_OXIDE.get()) && !flux.isEmpty();
        Fluxing.Batch batch = Fluxing.smelt(ORE_GRAMS_PER_ITEM, fluxed ? FLUX_GRAMS_PER_ITEM : 0.0);
        lastFluxRatio = batch.fluxRatio();

        int ironItems = (int) Math.floor(batch.ironGrams() / IRON_GRAMS_PER_INGOT);
        int slagItems = (int) Math.floor(batch.slagGrams() / SLAG_GRAMS_PER_ITEM);
        if (ironItems > 0) {
            pushOutput(SLOT_IRON, new ItemStack(Items.IRON_INGOT, ironItems));
        }
        if (slagItems > 0) {
            pushOutput(SLOT_SLAG, new ItemStack(ModItems.SLAG.get(), slagItems));
        }
        ore.shrink(1);
        if (fluxed) {
            flux.shrink(1);
        }
    }

    // ------------------------------------------------------------------ two-feed slot rules
    //
    // Overridden rather than inherited: the shared base class's own SLOT_FEED = 0 assumption
    // (and everything downstream of it - side access, hopper insertion, the screen's own feed
    // predicate) only ever expected one feed slot to defend.

    @Override
    public int[] getSlotsForFace(Direction side) {
        if (side == Direction.DOWN) {
            return new int[] {SLOT_IRON, SLOT_SLAG};
        }
        return new int[] {SLOT_ORE, SLOT_FLUX};
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot == SLOT_IRON || slot == SLOT_SLAG;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return switch (slot) {
            case SLOT_ORE -> stack.is(ModBlocks.HEMATITE_ORE.get().asItem());
            case SLOT_FLUX -> stack.is(ModItems.MAGNESIUM_OXIDE.get());
            default -> false;
        };
    }
}
