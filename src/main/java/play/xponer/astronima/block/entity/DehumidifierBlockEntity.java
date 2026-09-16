package play.xponer.astronima.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import play.xponer.astronima.atmosphere.Atmosphere;
import play.xponer.astronima.registry.ModBlockEntities;
import play.xponer.astronima.sim.Gas;
import play.xponer.astronima.sim.Humidity;
import play.xponer.astronima.sim.RoomState;

/**
 * Pulls vapor toward a comfortable ~45 % relative humidity and banks the water.
 * Every {@value #MOL_PER_BOTTLE} moles collected (1 mol H2O ≈ 18 g; a bottle is
 * ~0.5 L ≈ 28 mol) materializes a real water-bottle item in the output slot.
 *
 * <p>The one water-related block in the mod that used to be unreachable by a hopper —
 * every other machine in the regenerative loop is a real {@code Container} a hopper can
 * feed or drain, and this was the step that still had to be walked over and clicked by
 * hand, every time, forever. See {@code design/water-plumbing.md}. Bottom face only,
 * out only: the same "in from the top and sides, out from the bottom" grammar
 * {@code ProcessingBlockEntity} already established for every other machine.
 */
public class DehumidifierBlockEntity extends ReadableBlockEntity
        implements net.minecraft.world.WorldlyContainer {
    /** Vapor removed per second per kPa above the target — a chilled coil's first-order rate. */
    private static final double RATE_MOL_PER_S_PER_KPA = 0.6;
    private static final double TARGET_RH = 0.45;
    /** Water vapour needed for one bottle. Public so a gauge can read against it. */
    public static final double MOL_PER_BOTTLE = 28.0;

    public static final int SLOT_OUTPUT = 0;
    private static final int[] OUTPUT_SLOTS = {SLOT_OUTPUT};

    private double collectedMol;
    private final NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);

    public DehumidifierBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DEHUMIDIFIER.get(), pos, state);
    }

    public void serverTick(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)
                || level.getGameTime() % Atmosphere.TICK_INTERVAL != 0) {
            return;
        }
        RoomState room = Atmosphere.get(serverLevel).roomTouching(pos);
        if (room == null) {
            return;
        }
        // Anything already past saturation has condensed on the walls anyway; the
        // unit's job is to keep the air below that line.
        collectedMol += Humidity.condense(room);

        double targetKPa = TARGET_RH * Humidity.saturationPressureKPa(room.temperatureK());
        double excessKPa = room.partialPressureKPa(Gas.WATER_VAPOR) - targetKPa;
        if (excessKPa > 0) {
            double want = RATE_MOL_PER_S_PER_KPA * excessKPa * (Atmosphere.TICK_INTERVAL / 20.0);
            collectedMol += room.removeGas(Gas.WATER_VAPOR, want);
        }
        // Materialize whole bottles into the real slot as soon as they're ready - never more
        // than the slot has room for. A full reservoir backs the condensate up rather than
        // discarding it: nothing collected here is ever destroyed, only delayed.
        while (collectedMol >= MOL_PER_BOTTLE && bottleStack()) {
            collectedMol -= MOL_PER_BOTTLE;
        }
        setChanged();
    }

    /** Grows the output slot by one bottle if there is room; false when it is full. */
    private boolean bottleStack() {
        ItemStack slot = items.get(SLOT_OUTPUT);
        if (slot.isEmpty()) {
            items.set(SLOT_OUTPUT, PotionContents.createItemStack(Items.POTION, Potions.WATER));
            return true;
        }
        if (slot.getCount() >= slot.getMaxStackSize()) {
            return false;
        }
        slot.grow(1);
        return true;
    }

    /** Whole bottles waiting to be collected, materialized or not. */
    public int bottlesReady() {
        return items.get(SLOT_OUTPUT).getCount();
    }

    /** How far through the next bottle it is, 0..1 — what the gauge draws. */
    public double bottleProgress() {
        return Math.clamp((collectedMol % MOL_PER_BOTTLE) / MOL_PER_BOTTLE, 0.0, 1.0);
    }

    /**
     * Hands over whole bottles of reclaimed water.
     *
     * <p>No chat either way. Taking the bottles is its own feedback — they land in the hand
     * with the sound of glass filling, and the gauge on the block visibly drops back to
     * near-empty. Clicking with nothing ready gets the hollow knock of an empty reservoir,
     * and the panel under the crosshair is already telling you how far off the next one is.
     * The old version printed "Condensate reservoir: 42 % of a bottle" into chat, which is
     * a gauge reading pretending to be an event (rule 9).
     *
     * <p>Takes from the same slot a hopper would — a player right-clicking and a hopper
     * pulling from the bottom face are the same operation from two different callers.
     *
     * @return how many bottles were handed over, so the caller can pick the sound
     */
    public int collectBottles(Player player) {
        ItemStack slot = items.get(SLOT_OUTPUT);
        if (slot.isEmpty()) {
            return 0;
        }
        int bottles = slot.getCount();
        ItemStack water = slot.copy();
        items.set(SLOT_OUTPUT, ItemStack.EMPTY);
        setChanged();
        if (!player.getInventory().add(water)) {
            player.drop(water, false);
        }
        return bottles;
    }

    // -------------------------------------------------------------------- WorldlyContainer

    @Override
    public int[] getSlotsForFace(Direction side) {
        return side == Direction.DOWN ? OUTPUT_SLOTS : new int[0];
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return false;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return side == Direction.DOWN;
    }

    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        return items.get(SLOT_OUTPUT).isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack result = net.minecraft.world.ContainerHelper.removeItem(items, slot, amount);
        if (!result.isEmpty()) {
            setChanged();
        }
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return net.minecraft.world.ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        setChanged();
    }

    /** Only a hopper (or similar) below may place anything, and this slot never accepts one. */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return false;
    }

    @Override
    public boolean stillValid(Player player) {
        return net.minecraft.world.Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putDouble("collected_mol", collectedMol);
        net.minecraft.world.ContainerHelper.saveAllItems(output, items);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        collectedMol = input.getDoubleOr("collected_mol", 0.0);
        items.clear();
        net.minecraft.world.ContainerHelper.loadAllItems(input, items);
    }
}
