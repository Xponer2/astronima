package play.xponer.astronima.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import play.xponer.astronima.menu.ProcessingMenu;
import play.xponer.astronima.registry.ModBlockEntities;
import play.xponer.astronima.registry.ModItems;
import play.xponer.astronima.sim.chem.WaferEtching;

/**
 * Real wet oxide etching: {@code SiO2 + 6 HF -> H2SiF6 + 2 H2O}. See {@code design/halogens.md}
 * §40-43 (Part C4).
 *
 * <p>The mod's third {@code twoFeedsTwoProducts} machine, both feed slots mandatory like {@link
 * HfDigesterBlockEntity} — this reaction has the same no-crude-without-flux shortcut, real
 * stoichiometry either has both reagents or it has nothing.
 *
 * <p><strong>A third real gate, beyond both feeds:</strong> {@link #nearbyCleanroom()} must find
 * a {@link CleanroomControllerBlockEntity} touching one of this block's own six faces, and its
 * cleanliness must clear {@link WaferEtching#ETCH_MIN_CLEANLINESS} — real wafer fabs run wet-etch
 * benches inside certified clean space for the same real reason.
 */
public class EtchStationBlockEntity extends ProcessingBlockEntity {
    public static final int SLOT_WAFER = 0;
    public static final int SLOT_HF = 1;
    public static final int SLOT_DIE = 2;
    public static final int SLOT_FLUOROSILICIC_ACID = 3;

    /** Real process-control power draw, not thermodynamic — see {@code design/halogens.md} §40. */
    public static final int BATCH_WORK = 300;

    /** One wafer, the natural unit — this mod's own new mole-per-item convention for
     *  {@code wafer_silicon}, nothing prior to conflict with. */
    public static final double WAFER_MOL_PER_CHARGE = 1.0;

    /** Whole HF items one full charge consumes — real 1:6 molar ratio, at this mod's standard
     *  one-item-per-mole convention. */
    public static final int HF_PER_CHARGE = (int) WaferEtching.hfMolRequired(WAFER_MOL_PER_CHARGE);

    /** Real stoichiometric die/fluorosilicic-acid items one full charge yields, at the same
     *  one-item-per-mole convention {@code SodiumItem}/{@code HfDigesterBlockEntity} already use. */
    public static final double MOL_PER_ITEM = 1.0;

    public EtchStationBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ETCH_STATION.get(), pos, state, 4);
    }

    @Override
    public play.xponer.astronima.sim.machine.Calibration.Drift drift() {
        return play.xponer.astronima.sim.machine.Calibration.Drift.NONE;
    }

    @Override
    public ProcessingMenu.Kind kind() {
        return ProcessingMenu.Kind.ETCH_STATION;
    }

    @Override
    public int workRequired() {
        return BATCH_WORK;
    }

    /** The one adjacent controller, if any — the same direct-touch shape {@code roomTouching}
     *  already uses for a room, applied here to a specific device (design/halogens.md §41). */
    private @Nullable CleanroomControllerBlockEntity nearbyCleanroom() {
        if (level == null) {
            return null;
        }
        for (Direction dir : Direction.values()) {
            if (level.getBlockEntity(worldPosition.relative(dir))
                    instanceof CleanroomControllerBlockEntity controller) {
                return controller;
            }
        }
        return null;
    }

    /** 0 when no controller touches this station — what the panel's own cleanliness row reads
     *  (design/halogens.md §43). */
    public double nearbyCleanlinessFraction() {
        CleanroomControllerBlockEntity controller = nearbyCleanroom();
        return controller == null ? 0.0 : controller.cleanliness();
    }

    private boolean roomReady() {
        return WaferEtching.readyRoom(nearbyCleanlinessFraction());
    }

    @Override
    public boolean hasFeed() {
        return getItem(SLOT_WAFER).is(ModItems.WAFER_SILICON.get())
                && getItem(SLOT_HF).is(ModItems.HYDROFLUORIC_ACID.get())
                && getItem(SLOT_HF).getCount() >= HF_PER_CHARGE
                && roomReady();
    }

    @Override
    public boolean hasRoomForProduct() {
        if (!hasFeed()) {
            return true;
        }
        int dieItems = (int) Math.floor(
                WaferEtching.dieMolFrom(WAFER_MOL_PER_CHARGE) / MOL_PER_ITEM);
        int acidItems = (int) Math.floor(
                WaferEtching.fluorosilicicAcidMolFrom(WAFER_MOL_PER_CHARGE) / MOL_PER_ITEM);
        ItemStack dieProjected = dieItems > 0
                ? new ItemStack(ModItems.ETCHED_DIE.get(), dieItems) : ItemStack.EMPTY;
        ItemStack acidProjected = acidItems > 0
                ? new ItemStack(ModItems.FLUOROSILICIC_ACID.get(), acidItems) : ItemStack.EMPTY;
        return hasRoom(SLOT_DIE, dieProjected) && hasRoom(SLOT_FLUOROSILICIC_ACID, acidProjected);
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
        int dieItems = (int) Math.floor(
                WaferEtching.dieMolFrom(WAFER_MOL_PER_CHARGE) / MOL_PER_ITEM);
        int acidItems = (int) Math.floor(
                WaferEtching.fluorosilicicAcidMolFrom(WAFER_MOL_PER_CHARGE) / MOL_PER_ITEM);
        if (dieItems > 0) {
            pushOutput(SLOT_DIE, new ItemStack(ModItems.ETCHED_DIE.get(), dieItems));
        }
        if (acidItems > 0) {
            pushOutput(SLOT_FLUOROSILICIC_ACID, new ItemStack(ModItems.FLUOROSILICIC_ACID.get(), acidItems));
        }
        getItem(SLOT_WAFER).shrink(1);
        getItem(SLOT_HF).shrink(HF_PER_CHARGE);
    }

    // ------------------------------------------------------------------ two-feed slot rules

    @Override
    public int[] getSlotsForFace(Direction side) {
        if (side == Direction.DOWN) {
            return new int[] {SLOT_DIE, SLOT_FLUOROSILICIC_ACID};
        }
        return new int[] {SLOT_WAFER, SLOT_HF};
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot == SLOT_DIE || slot == SLOT_FLUOROSILICIC_ACID;
    }

    /** The wafer loads normally — a solid, hopper-safe, no special hazard. HF never does: {@link
     *  ProcessingMenu#feedAccepts(ProcessingMenu.Kind, int, ItemStack)} refuses it unconditionally
     *  for this slot, so the only door in is {@code EtchStationBlock#useItemOn}'s own real contact
     *  event (design/halogens.md §42). */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return switch (slot) {
            case SLOT_WAFER -> stack.is(ModItems.WAFER_SILICON.get());
            default -> false;
        };
    }
}
