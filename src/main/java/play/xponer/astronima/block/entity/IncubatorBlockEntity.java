package play.xponer.astronima.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import play.xponer.astronima.item.PetriDishItem;
import play.xponer.astronima.registry.ModBlockEntities;
import play.xponer.astronima.registry.ModItems;
import play.xponer.astronima.sim.lab.Culture;

/**
 * A warm box with a thermostat, and the one machine the laboratory cannot do without.
 *
 * <h2>Why this is a machine and the stain is not</h2>
 * Staining a slide is hand work. <strong>Holding 37 °C for eighteen hours is not</strong> — it needs
 * a box, a heater and something watching the temperature, which is exactly what makes an incubator
 * a piece of equipment rather than a technique.
 *
 * <p>It writes growth back onto the dish, because {@code design/laboratory.md} makes the plate the
 * notebook: what happens in here has to leave with the dish, or the machines become the memory and
 * the plates become interchangeable.
 *
 * <h2>Too hot is worse than too cold</h2>
 * Below twenty it simply does not grow — no harm done, put it back. Above forty-seven the plate is
 * <strong>cooked, and looks exactly like one that was sterile</strong>. Nothing distinguishes them.
 * That is real, it is why a laboratory owns a thermometer rather than a dial marked hot, and it is
 * the reason this machine shows a number and not three coloured lights.
 */
public class IncubatorBlockEntity extends ReadableBlockEntity {

    /** One slot, because one plate at a time is what a small incubator is. */
    public static final int SLOT_DISH = 0;

    /** Where the thermostat starts: blood heat, which is what it is for. */
    public static final double DEFAULT_K = Culture.IDEAL_K;

    /** What the dial can be set to, in kelvin — both ends are mistakes worth being able to make. */
    public static final double COLDEST_K = 275.15;
    public static final double HOTTEST_K = 333.15;

    /**
     * How much incubation one real second buys.
     *
     * <p>An hour a second, so eighteen hours of growth is eighteen seconds of waiting. Real time
     * would be correct and unplayable; this is the one place the tier compresses, and it says so
     * rather than pretending a plate grows in a moment.
     */
    public static final double HOURS_PER_SECOND = 1.0;

    private final net.minecraft.core.NonNullList<ItemStack> slots =
            net.minecraft.core.NonNullList.withSize(1, ItemStack.EMPTY);

    private final SimpleContainer dish = new SimpleContainer(1) {
        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return stack.is(ModItems.PETRI_DISH.get());
        }
    };

    private double setPointK = DEFAULT_K;

    public IncubatorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.INCUBATOR.get(), pos, state);
    }

    public Container container() {
        return dish;
    }

    public double setPointK() {
        return setPointK;
    }

    /** Moves the thermostat. Clamped, because a dial that could be set anywhere is not a dial. */
    public void setPointK(double kelvin) {
        setPointK = Math.clamp(kelvin, COLDEST_K, HOTTEST_K);
        setChanged();
    }

    /**
     * Where the dial is, 0..1 — which is what crosses the wire.
     *
     * <p>A fraction rather than a temperature, so this reuses the setting packet every other
     * machine already uses. The kelvin lives here, where the range is defined; sending a raw
     * temperature would have meant a second packet that says the same thing in different units.
     */
    public double dial() {
        return (setPointK - COLDEST_K) / (HOTTEST_K - COLDEST_K);
    }

    public void setFromDial(double part) {
        setPointK(COLDEST_K + Math.clamp(part, 0, 1) * (HOTTEST_K - COLDEST_K));
    }

    /** What is on the plate inside, or a blank one when the box is empty. */
    public Culture culture() {
        ItemStack stack = dish.getItem(SLOT_DISH);
        return stack.isEmpty() ? Culture.BLANK : PetriDishItem.cultureOf(stack);
    }

    public boolean hasDish() {
        return !dish.getItem(SLOT_DISH).isEmpty();
    }

    /**
     * A second of incubation, written back onto the plate.
     *
     * <p>Nothing happens with the door open — which is to say, with no dish in it — and nothing
     * happens to a blank plate, so an empty incubator is not quietly running a clock.
     */
    public void serverTick() {
        ItemStack stack = dish.getItem(SLOT_DISH);
        if (stack.isEmpty()) {
            return;
        }
        Culture before = PetriDishItem.cultureOf(stack);
        if (before.isBlank()) {
            return;
        }
        Culture after = before.incubated(HOURS_PER_SECOND / 20.0, setPointK);
        if (!after.equals(before)) {
            PetriDishItem.setCulture(stack, after);
            setChanged();
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putDouble("set_point_k", setPointK);
        slots.set(0, dish.getItem(SLOT_DISH));
        net.minecraft.world.ContainerHelper.saveAllItems(output, slots);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        setPointK = input.getDoubleOr("set_point_k", DEFAULT_K);
        dish.clearContent();
        slots.set(0, ItemStack.EMPTY);
        net.minecraft.world.ContainerHelper.loadAllItems(input, slots);
        dish.setItem(SLOT_DISH, slots.get(0));
    }

    public static void tick(Level level, BlockPos pos, BlockState state,
                            IncubatorBlockEntity incubator) {
        if (!level.isClientSide()) {
            incubator.serverTick();
        }
    }
}
