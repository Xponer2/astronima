package play.xponer.astronima.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import play.xponer.astronima.menu.ProcessingMenu;
import play.xponer.astronima.registry.ModBlockEntities;
import play.xponer.astronima.registry.ModBlocks;
import play.xponer.astronima.registry.ModItems;
import play.xponer.astronima.sim.chem.FluoriteDigestion;

/**
 * Real fluorite digestion: {@code CaF2 + H2SO4 -> CaSO4 + 2 HF}. See
 * {@code design/halogens.md} §15-17 (Part C1).
 *
 * <p><strong>Both feed slots are mandatory, unlike {@link IronSmelterBlockEntity}.</strong> The
 * smelter's own flux is an optional yield improvement over a real crude-iron floor; this reaction
 * has no such floor — real 1:1 CaF2:H2SO4 stoichiometry means it structurally cannot proceed with
 * only one reagent present. {@link #hasFeed()} asks for both.
 */
public class HfDigesterBlockEntity extends ProcessingBlockEntity {
    public static final int SLOT_FLUORITE = 0;
    public static final int SLOT_ACID = 1;
    public static final int SLOT_HF = 2;
    public static final int SLOT_GYPSUM = 3;

    /** Real, endothermic (design/halogens.md §15.2) — drawn from the same tier as the sulfuric
     *  acid plant this machine's own reagent comes from. */
    public static final int BATCH_WORK = 300;

    /** A designer's choice (fluorite ore has no real per-item mass any more than titania or
     *  halite do), picked so a full charge needs exactly three whole sulfuric acid items rather
     *  than a fractional one — the real 1:1 CaF2:H2SO4 ratio. */
    public static final double FLUORITE_MOL_PER_CHARGE = 3.0;

    public static final double FLUORITE_GRAMS_PER_ITEM =
            FLUORITE_MOL_PER_CHARGE * FluoriteDigestion.CAF2_MOLAR_MASS;

    /** Whole sulfuric acid items one full charge consumes — real 1:1 molar ratio, at this mod's
     *  standard one-item-per-mole convention. */
    public static final int SULFURIC_ACID_PER_CHARGE = (int) FLUORITE_MOL_PER_CHARGE;

    /** Real stoichiometric HF/gypsum items one full charge yields, at the same one-item-per-mole
     *  convention {@code SodiumItem}/{@code ElectrolysisCellBlockEntity} already use. */
    public static final double MOL_PER_ITEM = 1.0;

    public HfDigesterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.HF_DIGESTER.get(), pos, state, 4);
    }

    @Override
    public play.xponer.astronima.sim.machine.Calibration.Drift drift() {
        return play.xponer.astronima.sim.machine.Calibration.Drift.NONE;
    }

    @Override
    public ProcessingMenu.Kind kind() {
        return ProcessingMenu.Kind.HF_DIGESTER;
    }

    @Override
    public int workRequired() {
        return BATCH_WORK;
    }

    @Override
    public boolean hasFeed() {
        return getItem(SLOT_FLUORITE).is(ModBlocks.FLUORITE_ORE.get().asItem())
                && getItem(SLOT_ACID).is(ModItems.SULFURIC_ACID.get())
                && getItem(SLOT_ACID).getCount() >= SULFURIC_ACID_PER_CHARGE;
    }

    @Override
    public boolean hasRoomForProduct() {
        if (!hasFeed()) {
            return true;
        }
        int hfItems = (int) Math.floor(
                FluoriteDigestion.hfMolFrom(FLUORITE_MOL_PER_CHARGE) / MOL_PER_ITEM);
        int gypsumItems = (int) Math.floor(
                FluoriteDigestion.gypsumMolFrom(FLUORITE_MOL_PER_CHARGE) / MOL_PER_ITEM);
        ItemStack hfProjected = hfItems > 0
                ? new ItemStack(ModItems.HYDROFLUORIC_ACID.get(), hfItems) : ItemStack.EMPTY;
        ItemStack gypsumProjected = gypsumItems > 0
                ? new ItemStack(ModItems.GYPSUM.get(), gypsumItems) : ItemStack.EMPTY;
        return hasRoom(SLOT_HF, hfProjected) && hasRoom(SLOT_GYPSUM, gypsumProjected);
    }

    @Override
    public boolean canRun() {
        return workState().isWorking();
    }

    @Override
    protected void finishBatch() {
        if (!hasFeed()) {
            return;
        }
        int hfItems = (int) Math.floor(
                FluoriteDigestion.hfMolFrom(FLUORITE_MOL_PER_CHARGE) / MOL_PER_ITEM);
        int gypsumItems = (int) Math.floor(
                FluoriteDigestion.gypsumMolFrom(FLUORITE_MOL_PER_CHARGE) / MOL_PER_ITEM);
        if (hfItems > 0) {
            pushOutput(SLOT_HF, new ItemStack(ModItems.HYDROFLUORIC_ACID.get(), hfItems));
        }
        if (gypsumItems > 0) {
            pushOutput(SLOT_GYPSUM, new ItemStack(ModItems.GYPSUM.get(), gypsumItems));
        }
        getItem(SLOT_FLUORITE).shrink(1);
        getItem(SLOT_ACID).shrink(SULFURIC_ACID_PER_CHARGE);
    }

    // ------------------------------------------------------------------ two-feed slot rules
    //
    // Overridden rather than inherited, the same reason IronSmelterBlockEntity's own copy is:
    // the shared base class's own SLOT_FEED = 0 assumption only ever expected one feed slot.

    @Override
    public int[] getSlotsForFace(Direction side) {
        if (side == Direction.DOWN) {
            return new int[] {SLOT_HF, SLOT_GYPSUM};
        }
        return new int[] {SLOT_FLUORITE, SLOT_ACID};
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot == SLOT_HF || slot == SLOT_GYPSUM;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return switch (slot) {
            case SLOT_FLUORITE -> stack.is(ModBlocks.FLUORITE_ORE.get().asItem());
            case SLOT_ACID -> stack.is(ModItems.SULFURIC_ACID.get());
            default -> false;
        };
    }
}
